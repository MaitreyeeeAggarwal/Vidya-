package com.vidya.core.audio

import android.util.Log

/**
 * Kotlin JNI bridge to the native C++ Indic-TTS engine.
 * Wraps FastPitch (acoustic) + HiFi-GAN (vocoder) ONNX sessions.
 * ~15 MB per language pack, 4-bit quantised.
 */
class NativeIndicEngine {

    companion object {
        private const val TAG = "NativeIndicTTS"
        init {
            try {
                System.loadLibrary("indic_tts_jit")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load libindic_tts_jit.so", e)
            }
        }
    }

    private var currentModelPath: String? = null

    fun loadModel(modelDir: String) {
        if (modelDir == currentModelPath) return
        nativeLoadModel(modelDir)
        currentModelPath = modelDir
    }

    fun synthesizeSpeechNative(inputText: String): ShortArray {
        if (currentModelPath == null) return shortArrayOf()
        return try { nativeSynthesize(inputText) } catch (e: Exception) { shortArrayOf() }
    }

    fun release() { nativeRelease(); currentModelPath = null }

    private external fun nativeLoadModel(modelDir: String)
    private external fun nativeSynthesize(inputText: String): ShortArray
    private external fun nativeRelease()
}
