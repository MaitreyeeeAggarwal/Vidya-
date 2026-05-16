package com.vidya.core.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real implementation of the LlmInferenceEngine using MediaPipe Tasks GenAI.
 * This runs the quantized Gemma 4 model directly on the Android device's CPU/GPU.
 */
class GemmaInferenceEngine(
    private val context: Context, 
    private val modelPath: String = "/data/local/tmp/gemma-4b-it-gpu-int4.bin" // Path to the downloaded .bin or .task Gemma model
) : LlmInferenceEngine {

    private var llmInference: LlmInference? = null

    /**
     * Initializes the Gemma model into memory.
     * This should be called during the Session Initialization phase (Step 2).
     */
    fun initialize() {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024) // Limit output length to ensure fast generation
            .setTemperature(0.7f)
            .build()
            
        llmInference = LlmInference.createFromOptions(context, options)
    }

    /**
     * Generates a response synchronously, but wrapped in a coroutine to avoid blocking the Main thread.
     */
    override suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.Default) {
        val inference = llmInference ?: throw IllegalStateException("LlmInference not initialized. Download Gemma model first.")
        
        // MediaPipe's generateResponse runs the entire inference loop
        return@withContext inference.generateResponse(prompt)
    }

    /**
     * Frees memory when the session is closed.
     */
    fun close() {
        llmInference?.close()
        llmInference = null
    }
}
