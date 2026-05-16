package com.vidya.core.workers

import android.content.Context
import android.util.Base64
import android.util.Log
import com.vidya.core.data.local.ProcessingStatus
import com.vidya.core.data.local.StudentDoubtDao
import kotlinx.coroutines.*
import java.io.File

/**
 * Background queue processor for student doubt images.
 *
 * Lifecycle:
 *   1. Pulls the oldest PENDING doubt from Room.
 *   2. Marks it PROCESSING (prevents parallel workers from re-picking it).
 *   3. Reads the JPEG → Base64-encodes it for Gemma 4's multimodal input.
 *   4. Constructs a Socratic prompt and fires local inference.
 *   5. Writes the extracted text + AI response back to Room as DONE.
 *   6. On any failure, marks the doubt FAILED — the raw image is still preserved
 *      for retry or cloud-side processing during sync.
 *
 * The worker runs on [Dispatchers.IO] and never touches the UI thread.
 */
class DoubtProcessingWorker(
    private val doubtDao: StudentDoubtDao,
    private val localGemmaEngine: LocalGemmaVisionEngine
) {
    companion object {
        private const val TAG = "DoubtWorker"
        private const val POLL_INTERVAL_MS = 3_000L
    }

    private var processingJob: Job? = null
    private var isRunning = false

    /**
     * Start the continuous background polling loop.
     * Checks for pending doubts every [POLL_INTERVAL_MS] and processes them one at a time.
     */
    fun startProcessingLoop(scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
        if (isRunning) return
        isRunning = true

        processingJob = scope.launch {
            Log.d(TAG, "Doubt processing loop started")
            while (isActive && isRunning) {
                try {
                    processNextDoubtQueueElement()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error in processing loop", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Stop the background processing loop. */
    fun stopProcessingLoop() {
        isRunning = false
        processingJob?.cancel()
        Log.d(TAG, "Doubt processing loop stopped")
    }

    /**
     * Process a single pending doubt. Called by the loop or can be invoked
     * directly for immediate processing after a camera capture.
     */
    suspend fun processNextDoubtQueueElement() = withContext(Dispatchers.IO) {
        // 1. Grab the oldest pending doubt
        val doubt = doubtDao.getNextPendingDoubt() ?: return@withContext

        try {
            // 2. Advance lifecycle to PROCESSING to prevent parallel collisions
            doubt.processingStatus = ProcessingStatus.PROCESSING
            doubtDao.updateDoubtStatus(doubt)
            Log.d(TAG, "Processing doubt=${doubt.doubtId}")

            val imageFile = File(doubt.filePath)
            if (!imageFile.exists()) {
                throw IllegalStateException("JPEG not found at ${doubt.filePath}")
            }

            // 3. Read raw bytes → Base64 (no line-wrapping for clean prompt injection)
            val imageBytes = imageFile.readBytes()
            val base64Payload = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            Log.d(TAG, "Encoded ${imageBytes.size / 1024} KB → ${base64Payload.length} chars Base64")

            // 4. Construct the multimodal Socratic prompt
            val prompt = buildMultimodalPrompt(base64Payload)

            // 5. Run local Gemma 4 inference
            val result = localGemmaEngine.executeInference(prompt)

            // 6. Persist results
            doubt.extractedText = result.transcription
            doubt.aiResponse = result.pedagogySteps
            doubt.processingStatus = ProcessingStatus.DONE
            doubtDao.updateDoubtStatus(doubt)
            Log.d(TAG, "Doubt ${doubt.doubtId} resolved — ${result.transcription.length} chars extracted")

        } catch (e: Exception) {
            Log.e(TAG, "Doubt ${doubt.doubtId} failed — image preserved for cloud fallback", e)
            doubt.processingStatus = ProcessingStatus.FAILED
            doubtDao.updateDoubtStatus(doubt)
        }
    }

    /**
     * Build the multimodal prompt that forces Gemma 4's attention onto
     * handwritten math/science content with a pedagogical response format.
     */
    private fun buildMultimodalPrompt(base64Image: String): String {
        return """
            |[SYSTEM] You are Vidya, an expert classroom teaching assistant for Indian schools.
            |
            |[IMAGE] data:image/jpeg;base64,$base64Image
            |
            |[TASK]
            |1. TRANSCRIBE: Extract the handwritten text/equations from this image exactly as written.
            |2. IDENTIFY: Determine the subject area and topic (e.g., "Grade 10 Mathematics — Quadratic Equations").
            |3. DIAGNOSE: Identify the student's specific misconception or error, if any.
            |4. GUIDE: Provide a step-by-step Socratic response that a teacher can use to guide
            |   the student toward the correct solution WITHOUT giving the answer directly.
            |   Use leading questions that build understanding.
            |
            |[FORMAT]
            |Return your response as:
            |TRANSCRIPTION: <exact handwritten text>
            |TOPIC: <subject and topic>
            |DIAGNOSIS: <what the student got wrong or is confused about>
            |GUIDANCE:
            |  Step 1: <leading question>
            |  Step 2: <leading question>
            |  ...
        """.trimMargin()
    }
}

/** Immutable result from the local vision model. */
data class VisionModelOutput(
    val transcription: String,
    val pedagogySteps: String
)

/**
 * Interface for the on-device Gemma 4 multimodal inference engine.
 * Concrete implementation lives in `core/ai/GemmaInferenceEngine.kt`
 * and wraps MediaPipe's LLM Inference API.
 */
interface LocalGemmaVisionEngine {
    fun executeInference(prompt: String): VisionModelOutput
}
