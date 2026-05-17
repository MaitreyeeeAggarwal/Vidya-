package com.vidya.core.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.vidya.core.workers.LocalGemmaVisionEngine
import com.vidya.core.workers.VisionModelOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation using MediaPipe Tasks GenAI to run the quantized Gemma 4 model.
 */
class GemmaInferenceEngine(
    private val context: Context, 
    private val modelPath: String = "models/gemma/gemma-4b-it-gpu-int4.bin"
) : LlmInferenceEngine, LocalGemmaVisionEngine {

    companion object {
        private const val TAG = "GemmaInference"
    }

    private var llmInference: LlmInference? = null

    /**
     * Initializes the Gemma model into memory.
     */
    fun initialize() {
        if (llmInference != null) return
        
        Log.d(TAG, "Initializing MediaPipe LlmInference engine for Gemma...")
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelPath).build())
                .setMaxTokens(1024) 
                .setTemperature(0.2f)
                .build()
                
            llmInference = LlmInference.createFromOptions(context, options)
            Log.d(TAG, "Gemma Engine initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Gemma Engine: ${e.message}", e)
        }
    }

    /**
     * Basic string-in, string-out text generation.
     */
    override suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.Default) {
        if (llmInference == null) initialize()
        val inference = llmInference ?: throw IllegalStateException("LlmInference not initialized.")
        return@withContext inference.generateResponse(prompt)
    }

    override suspend fun generateResponseMultimodal(jsonPrompt: String): String = withContext(Dispatchers.Default) {
        if (llmInference == null) initialize()
        val inference = llmInference ?: throw IllegalStateException("LlmInference not initialized.")
        // Fallback: MediaPipe LlmInference might not support JSON array of multimodal inputs directly
        // depending on the version and model. We pass the raw string for now.
        return@withContext inference.generateResponse(jsonPrompt)
    }

    /**
     * Executes the multimodal vision prompt formulated by the DoubtProcessingWorker.
     * Parses the LLM's raw string output into the VisionModelOutput structure.
     */
    override fun executeInference(prompt: String): VisionModelOutput {
        if (llmInference == null) initialize()
        val inference = llmInference ?: return VisionModelOutput("Engine Offline", "Please check model assets.")

        try {
            Log.d(TAG, "Executing multimodal Socratic inference...")
            // The prompt already contains the Base64 image and the formatting instructions.
            // Note: MediaPipe LLM task natively treats standard inputs as text unless specialized
            // for Vision-Language models. We assume the specific quantized .bin used is compatible.
            val rawOutput = inference.generateResponse(prompt)
            
            // Basic regex/string splitting to extract the parts requested in the prompt format:
            // TRANSCRIPTION: ...
            // TOPIC: ...
            // DIAGNOSIS: ...
            // GUIDANCE: ...
            
            var transcription = "Unreadable"
            var pedagogy = rawOutput

            val transcriptionMatch = Regex("TRANSCRIPTION:\\s*(.*?)(?=TOPIC:|DIAGNOSIS:|\$)", RegexOption.DOT_MATCHES_ALL).find(rawOutput)
            if (transcriptionMatch != null) {
                transcription = transcriptionMatch.groupValues[1].trim()
            }
            
            val guidanceMatch = Regex("GUIDANCE:\\s*(.*)", RegexOption.DOT_MATCHES_ALL).find(rawOutput)
            if (guidanceMatch != null) {
                pedagogy = guidanceMatch.groupValues[1].trim()
            }

            Log.d(TAG, "Inference successful.")
            return VisionModelOutput(transcription, pedagogy)

        } catch (e: Exception) {
            Log.e(TAG, "Gemma Inference crashed", e)
            return VisionModelOutput("Error", "I'm having trouble analyzing this right now.")
        }
    }

    /**
     * Frees memory when the session is closed.
     */
    fun close() {
        llmInference?.close()
        llmInference = null
    }
}
