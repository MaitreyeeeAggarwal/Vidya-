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
     * Predict the language of a short audio segment represented as MFCC features.
     *
     * @param mfccFeatures Pre-computed MFCC matrix [N_FRAMES × N_MFCC].
     *                     If you don't have an MFCC extractor yet, pass null and
     *                     the method returns the default language code.
     * @return Two-letter language code (e.g. "ta") and confidence score.
     */
    fun predict(mfccFeatures: Array<FloatArray>?): Pair<String, Float> {
        if (interpreter == null || mfccFeatures == null) {
            Log.w(TAG, "LID unavailable or no features — defaulting to 'hi'")
            return Pair("hi", 0f)
        }

        // Wrap in batch dimension: [1][N_FRAMES][N_MFCC]
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
