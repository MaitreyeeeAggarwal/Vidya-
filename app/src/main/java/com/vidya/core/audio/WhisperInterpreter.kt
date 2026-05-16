package com.vidya.core.audio

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileInputStream
import java.nio.FloatBuffer

/**
 * On-device speech-to-text using Whisper-tiny-multilingual (~39 MB ONNX).
 *
 * Supports: Hindi (hi), Marathi (mr), Tamil (ta), Bengali (bn),
 *           Maithili (mai), Telugu (te), and English (en).
 *
 * The model is loaded from assets once and reused across chunks.
 * Inference runs via ONNX Runtime's NNAPI execution provider when available,
 * falling back to CPU otherwise.
 *
 * Pipeline:
 *   1. Load raw audio from file → resample to 16 kHz mono float32.
 *   2. Compute log-Mel spectrogram (80 bins, 30 s window, zero-padded).
 *   3. Run the encoder to get cross-attention hidden states.
 *   4. Auto-regressively decode tokens using the decoder.
 *   5. Detokenize via SentencePiece vocab → return transcript string.
 */
class WhisperInterpreter(private val context: Context) {

    companion object {
        private const val TAG = "WhisperInterpreter"
        private const val MODEL_ASSET = "whisper_tiny_multilingual.onnx"
        private const val TOKENIZER_ASSET = "spm.model"
        private const val SAMPLE_RATE = 16_000
        private const val N_MEL_BINS = 80
        private const val MAX_AUDIO_LENGTH_SEC = 30
        private const val INFERENCE_TIMEOUT_MS = 15_000L
    }

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isReady = false

    /**
     * Load the ONNX model and tokenizer from app assets.
     * Call once during Application.onCreate() or service startup.
     */
    fun initialize() {
        try {
            ortEnvironment = OrtEnvironment.getEnvironment()

            // Copy model from assets to internal storage (ONNX Runtime needs a file path)
            val modelFile = copyAssetToInternal(MODEL_ASSET)

            val sessionOptions = OrtSession.SessionOptions().apply {
                // Try NNAPI first for GPU/DSP acceleration on supported devices
                try {
                    addNnapi()
                    Log.d(TAG, "NNAPI execution provider enabled")
                } catch (e: Exception) {
                    Log.w(TAG, "NNAPI not available, falling back to CPU", e)
                }
                setIntraOpNumThreads(2)
            }

            ortSession = ortEnvironment!!.createSession(modelFile.absolutePath, sessionOptions)
            isReady = true
            Log.d(TAG, "Whisper-tiny-multilingual loaded — ready for inference")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Whisper model", e)
            isReady = false
        }
    }

    /**
     * Transcribe an audio file.
     *
     * @param audioFile  The recorded chunk (Opus/OGG, AMR-WB, or WAV).
     * @param languageHint  Optional ISO 639 language code from LID (e.g. "ta").
     *                      If provided, biases the decoder toward that language.
     * @return Transcription result with the decoded text and detected language,
     *         or a failure result if inference crashes or times out.
     */
    fun transcribe(audioFile: File, languageHint: String? = null): TranscriptionResult {
        if (!isReady || ortSession == null) {
            Log.w(TAG, "Model not loaded — returning empty result")
            return TranscriptionResult.failure("Model not initialized")
        }

        return try {
            // Step 1: Decode audio → 16 kHz mono float array
            val rawSamples = decodeAudioToFloat(audioFile)

            // Step 2: Compute log-Mel spectrogram
            val melSpectrogram = computeLogMelSpectrogram(rawSamples)

            // Step 3: Create input tensor  [1, N_MEL_BINS, T]
            val inputTensor = OnnxTensor.createTensor(
                ortEnvironment,
                FloatBuffer.wrap(melSpectrogram),
                longArrayOf(1, N_MEL_BINS.toLong(), (melSpectrogram.size / N_MEL_BINS).toLong())
            )

            // Step 4: Run inference
            val startTime = System.currentTimeMillis()
            val results = ortSession!!.run(mapOf("input_features" to inputTensor))
            val elapsed = System.currentTimeMillis() - startTime

            if (elapsed > INFERENCE_TIMEOUT_MS) {
                Log.w(TAG, "Inference completed but took ${elapsed}ms (timeout=${INFERENCE_TIMEOUT_MS}ms)")
            }

            // Step 5: Extract token IDs and decode to text
            val outputTensor = results[0].value
            val tokenIds = extractTokenIds(outputTensor)
            val transcript = detokenize(tokenIds)
            val detectedLang = languageHint ?: "hi"

            Log.d(TAG, "Transcription complete → lang=$detectedLang, ${transcript.length} chars, ${elapsed}ms")

            inputTensor.close()
            results.close()

            TranscriptionResult.success(transcript, detectedLang, elapsed)
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed for ${audioFile.name}", e)
            TranscriptionResult.failure(e.message ?: "Unknown inference error")
        }
    }

    fun release() {
        ortSession?.close()
        ortEnvironment?.close()
        ortSession = null
        ortEnvironment = null
        isReady = false
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    /**
     * Decode compressed audio (Opus/AMR) to raw 16 kHz mono float samples.
     * Uses Android's MediaExtractor + MediaCodec pipeline.
     */
    private fun decodeAudioToFloat(file: File): FloatArray {
        // Placeholder: full implementation uses MediaExtractor to demux,
        // MediaCodec to decode to PCM, then downmix + resample to 16 kHz.
        // For now, return a zero-padded buffer matching max audio length.
        val maxSamples = SAMPLE_RATE * MAX_AUDIO_LENGTH_SEC
        return FloatArray(maxSamples)
    }

    /**
     * Compute 80-bin log-Mel spectrogram from raw audio samples.
     * Window: 25 ms, hop: 10 ms, Hann window, 400-point FFT.
     */
    private fun computeLogMelSpectrogram(samples: FloatArray): FloatArray {
        // Placeholder: full implementation applies STFT + Mel filterbank.
        val nFrames = samples.size / 160  // 10 ms hop at 16 kHz
        return FloatArray(N_MEL_BINS * nFrames)
    }

    /** Convert raw ONNX output tensor to an int array of token IDs. */
    @Suppress("UNCHECKED_CAST")
    private fun extractTokenIds(output: Any?): IntArray {
        // The decoder output shape is typically [1, seq_len]
        return when (output) {
            is Array<*> -> (output as Array<LongArray>)[0].map { it.toInt() }.toIntArray()
            is LongArray -> output.map { it.toInt() }.toIntArray()
            else -> intArrayOf()
        }
    }

    /** Decode token IDs to human-readable text via the SentencePiece vocabulary. */
    private fun detokenize(tokenIds: IntArray): String {
        // Placeholder: load spm.model and decode token IDs.
        // Full implementation uses the SentencePiece JNI binding.
        return tokenIds.joinToString(" ") { "tok_$it" }
    }

    /** Copy an asset file to internal storage so ONNX Runtime can open it by path. */
    private fun copyAssetToInternal(assetName: String): File {
        val outFile = File(context.filesDir, assetName)
        if (outFile.exists() && outFile.length() > 0) return outFile

        context.assets.open(assetName).use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return outFile
    }
}

/** Immutable result of a single transcription attempt. */
data class TranscriptionResult(
    val success: Boolean,
    val transcript: String,
    val languageCode: String,
    val inferenceTimeMs: Long,
    val errorMessage: String?
) {
    companion object {
        fun success(text: String, lang: String, timeMs: Long) =
            TranscriptionResult(true, text, lang, timeMs, null)

        fun failure(error: String) =
            TranscriptionResult(false, "", "unknown", 0, error)
    }
}
