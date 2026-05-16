package com.vidya.core.audio

import android.util.Log
import com.vidya.core.data.local.AudioChunkDao
import com.vidya.core.data.local.AudioChunkEntity
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/**
 * Orchestrates continuous chunk-based audio recording during a classroom session.
 *
 * Architecture:
 *   1. Records audio in 30-second chunks using [VidyaAudioRecorder].
 *   2. After each chunk completes, persists metadata in Room via [AudioChunkDao].
 *   3. Runs LID on the first chunk to detect the classroom language.
 *   4. Dispatches each chunk to [WhisperInterpreter] for on-device transcription.
 *   5. Always saves the raw audio file regardless of transcription outcome (never lose audio).
 */
class AudioPipelineOrchestrator(
    private val audioRecorder: VidyaAudioRecorder,
    private val chunkDao: AudioChunkDao,
    private val whisperInterpreter: WhisperInterpreter,
    private val languageIdModel: LanguageIdModel? = null,
    private val chunkDurationMs: Long = 30_000L,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "AudioOrchestrator"
    }

    private var rotationJob: Job? = null
    private var isRunning = false

    /** Language detected by LID on the first chunk; reused for the rest of the session. */
    private var sessionLanguage: String = "hi"

    /**
     * Start the continuous recording loop for a session.
     *
     * @param sessionEventId  The parent session-event ID that all chunks belong to.
     * @param onChunkReady    Optional callback invoked after each chunk is persisted
     *                        (useful for UI updates or triggering immediate inference).
     */
    fun startSessionCaptureLoop(
        sessionEventId: String,
        onChunkReady: ((AudioChunkEntity) -> Unit)? = null
    ) {
        if (isRunning) {
            Log.w(TAG, "Capture loop already running — ignoring duplicate start")
            return
        }

        isRunning = true
        Log.d(TAG, "Starting capture loop for event=$sessionEventId, chunk=${chunkDurationMs}ms")

        rotationJob = scope.launch {
            var chunkIndex = 0

            while (isActive && isRunning) {
                val chunkId = UUID.randomUUID().toString()
                val chunkEventId = "${sessionEventId}_CHK${chunkIndex}"

                // 1. Start recording
                val activeFile: File
                try {
                    activeFile = audioRecorder.startChunkRecording(chunkEventId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start chunk $chunkIndex — retrying in 2s", e)
                    delay(2000)
                    continue
                }

                // 2. Hold the mic open for the chunk duration
                delay(chunkDurationMs)

                // 3. Stop recording and measure file size
                val fileSize = audioRecorder.stopChunkRecording()

                if (fileSize <= 0) {
                    Log.w(TAG, "Chunk $chunkIndex produced 0 bytes — skipping")
                    chunkIndex++
                    continue
                }

                // 4. Build and persist the audio chunk entity (always save raw audio first)
                val codec = audioRecorder.activeCodec
                val entity = AudioChunkEntity(
                    chunkId = chunkId,
                    sessionEventId = sessionEventId,
                    filePath = activeFile.absolutePath,
                    durationMs = chunkDurationMs,
                    codecUsed = codec,
                    fileSizeBytes = fileSize,
                    languageCode = sessionLanguage
                )
                chunkDao.insertChunk(entity)
                Log.d(TAG, "Chunk $chunkIndex persisted → ${activeFile.name} (${fileSize} bytes)")

                // 5. Run LID on the first chunk to lock the session language
                if (chunkIndex == 0 && languageIdModel != null) {
                    val (lang, _) = languageIdModel.predict(null) // TODO: extract MFCCs from activeFile
                    sessionLanguage = lang
                    Log.d(TAG, "Session language locked → $sessionLanguage")
                }

                // 6. Attempt on-device transcription (non-blocking — audio is already saved)
                launch {
                    transcribeAndUpdate(chunkId, activeFile)
                }

                onChunkReady?.invoke(entity)
                chunkIndex++
            }
        }
    }

    /** Stop the recording loop cleanly. */
    fun stopSessionCaptureLoop() {
        Log.d(TAG, "Stopping capture loop")
        isRunning = false
        rotationJob?.cancel()
        try {
            audioRecorder.stopChunkRecording()
        } catch (_: Exception) {
            // Best-effort stop — chunk may already be finalized
        }
    }

    /**
     * Run Whisper transcription on a chunk and update the DB record.
     * If inference fails or crashes, the raw audio file is still preserved
     * for cloud-side transcription during the next sync.
     */
    private suspend fun transcribeAndUpdate(chunkId: String, audioFile: File) {
        try {
            val result = whisperInterpreter.transcribe(audioFile, sessionLanguage)
            if (result.success) {
                chunkDao.updateTranscript(chunkId, result.transcript, result.languageCode)
                Log.d(TAG, "Transcript saved for chunk=$chunkId (${result.inferenceTimeMs}ms)")
            } else {
                Log.w(TAG, "Transcription failed for chunk=$chunkId: ${result.errorMessage}")
                // Audio file is still on disk — cloud will handle it during sync
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription crash for chunk=$chunkId — audio preserved for cloud fallback", e)
        }
    }
}
