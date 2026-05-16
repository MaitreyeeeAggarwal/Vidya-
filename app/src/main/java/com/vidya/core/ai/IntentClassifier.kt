package com.vidya.core.ai

import com.vidya.core.ai.models.ClassifiedIntent
import com.vidya.core.ai.models.IntentType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Interface representing the Gemma Inference engine.
 * Updated to support multimodal (audio + text) inputs natively.
 */
interface LlmInferenceEngine {
    suspend fun generateResponse(prompt: String): String
    suspend fun generateResponseMultimodal(jsonPrompt: String): String
}

/**
 * Handles Step 3A: Question Decoding (Speech-to-Intent) using Multimodal Gemma 4.
 */
class IntentClassifier(
    private val llmEngine: LlmInferenceEngine
) {

    /**
     * Uses Gemma 4 Multimodal to classify the student's raw audio input, 
     * identify the language, and expand the query.
     */
    suspend fun classifyIntent(
        audioData: FloatArray, // 16kHz mono-channel float32 array
        conversationContext: String
    ): ClassifiedIntent {
        
        // Convert FloatArray to a recognizable format for the engine (e.g., Base64 or Tensor reference)
        val audioPlaceholder = "base64_encoded_audio_data_here" 

        val textPrompt = """
            Identify the language spoken in the audio. Analyze the question against the conversation context.
            Output ONLY a JSON object with the following schema:
            {
              "type": "DIRECT_QUESTION" | "DOUBT_CONFUSION" | "FOLLOW_UP",
              "expanded_query": "Cleaned up search query for the database IN ENGLISH",
              "language_code": "hi" // detected language code
            }
            Context: $conversationContext
        """.trimIndent()

        // Construct Multimodal JSON Prompt as per the blueprint
        val messageArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "audio")
                        put("audio_data", audioPlaceholder)
                    })
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", textPrompt)
                    })
                })
            })
        }

        val jsonResponse = llmEngine.generateResponseMultimodal(messageArray.toString())
        
        return parseIntentJson(jsonResponse)
    }

    private fun parseIntentJson(json: String): ClassifiedIntent {
        val type = when {
            json.contains("\"DOUBT_CONFUSION\"") -> IntentType.DOUBT_CONFUSION
            json.contains("\"FOLLOW_UP\"") -> IntentType.FOLLOW_UP
            else -> IntentType.DIRECT_QUESTION
        }
        
        val queryRegex = "\"expanded_query\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val expandedQuery = queryRegex.find(json)?.groups?.get(1)?.value ?: ""
        
        val langRegex = "\"language_code\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val languageCode = langRegex.find(json)?.groups?.get(1)?.value ?: "en"

        return ClassifiedIntent(type, expandedQuery, languageCode)
    }
}
