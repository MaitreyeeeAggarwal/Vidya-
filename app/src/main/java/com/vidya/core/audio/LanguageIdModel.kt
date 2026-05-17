package com.vidya.core.audio

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Lightweight on-device Language Identification model (~200 KB TFLite).
 *
 * Runs on a short MFCC feature window (first ~500 ms of audio) and emits
 * one of the six supported language codes.
 *
 * Expected model input : float[1][N_FRAMES][N_MFCC]  (e.g. 1×50×40)
 * Expected model output: float[1][NUM_LANGUAGES]       (softmax probabilities)
 */
class LanguageIdModel(private val context: Context) {

    companion object {
        private const val TAG = "VidyaLID"
        private const val MODEL_ASSET = "lid.tflite"

        /** Ordered to match the model's output index */
        val SUPPORTED_LANGUAGES = listOf("hi", "mr", "ta", "bn", "mai", "te")
    }

    private var interpreter: Interpreter? = null

    /** Load the TFLite model from assets. Call once at app startup. */
    fun initialize() {
        try {
            val modelBuffer = loadModelFile()
            interpreter = Interpreter(modelBuffer)
            Log.d(TAG, "LID model loaded — ${SUPPORTED_LANGUAGES.size} languages")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LID model; will default to 'hi'", e)
        }
    }

    /**
     * Predict the language from a raw PCM audio byte buffer.
     * Extracts MFCC features from the first ~500ms of audio and runs inference.
     *
     * @param rawAudio Raw 16-bit PCM audio chunk.
     * @return Two-letter language code (e.g. "ta") and confidence score.
     */
    fun predict(rawAudio: ByteArray?): Pair<String, Float> {
        if (interpreter == null || rawAudio == null || rawAudio.isEmpty()) {
            Log.w(TAG, "LID unavailable or empty buffer — defaulting to 'hi'")
            return Pair("hi", 0f)
        }

        // 1. Extract MFCC Features
        val mfccFeatures = extractMFCCs(rawAudio)

        // 2. Wrap in batch dimension: [1][N_FRAMES][N_MFCC]
        val input = arrayOf(mfccFeatures)
        val output = Array(1) { FloatArray(SUPPORTED_LANGUAGES.size) }

        interpreter!!.run(input, output)

        val probabilities = output[0]
        val maxIdx = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        val confidence = probabilities[maxIdx]
        val language = SUPPORTED_LANGUAGES[maxIdx]

        Log.d(TAG, "LID result → $language (${(confidence * 100).toInt()}%)")
        return Pair(language, confidence)
    }

    /**
     * Structural representation of MFCC extraction.
     * In a production environment, this delegates to an optimized JNI DSP library.
     */
    private fun extractMFCCs(pcmBytes: ByteArray): Array<FloatArray> {
        // Target model requires specific framing (e.g., 50 frames of 40 MFCCs)
        val nFrames = 50
        val nMfcc = 40
        val features = Array(nFrames) { FloatArray(nMfcc) }

        // Naive translation from 16-bit PCM to float
        val floatAudio = FloatArray(pcmBytes.size / 2)
        for (i in floatAudio.indices) {
            val sample = (pcmBytes[i * 2 + 1].toInt() shl 8) or (pcmBytes[i * 2].toInt() and 0xFF)
            floatAudio[i] = sample / 32768.0f
        }

        // Mock FFT & DCT process mapping frequencies to Mel-frequency cepstral coefficients.
        // A true implementation involves windowing (Hann), rfft, Mel filterbank dot product, and DCT-II.
        for (i in 0 until nFrames) {
            for (j in 0 until nMfcc) {
                // Simplified structural mock placeholder:
                // Map the normalized float amplitude roughly into the expected feature matrix shape.
                val idx = (i * nMfcc + j) % floatAudio.size
                features[i][j] = Math.log(Math.abs(floatAudio[idx]) + 1e-6f).toFloat() 
            }
        }
        
        return features
    }

    fun release() {
        interpreter?.close()
        interpreter = null
    }

    private fun loadModelFile(): MappedByteBuffer {
        val assetFd = context.assets.openFd(MODEL_ASSET)
        val inputStream = FileInputStream(assetFd.fileDescriptor)
        val channel = inputStream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, assetFd.startOffset, assetFd.declaredLength)
    }
}
