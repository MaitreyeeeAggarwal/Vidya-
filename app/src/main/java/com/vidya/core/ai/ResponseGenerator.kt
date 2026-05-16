package com.vidya.core.ai

import com.vidya.core.ai.models.AiResponse
import com.vidya.core.ai.models.KnowledgeChunk
import com.vidya.core.ai.models.SessionState

/**
 * Handles Step 3C & 4: Answer Generation & Class-Wise Linguistic Differentiation.
 * Driven directly by the local Curriculum Graph nodes.
 */
class ResponseGenerator(
    private val llmEngine: LlmInferenceEngine
) {

    /**
     * Synthesizes the final AI response combining English PhD context with the detected
     * regional language, applying grade-specific linguistic guards and Pedagogy constraints.
     */
    suspend fun generateResponse(
        studentInputAudioPlaceholder: String,
        retrievedContext: List<KnowledgeChunk>,
        sessionState: SessionState,
        conversationContext: String,
        targetLanguageCode: String
    ): AiResponse {
        
        val contextString = retrievedContext.joinToString(separator = "\n") { "- ${it.text}" }
        val activeConcept = sessionState.activeConcept
        
        // Linguistic Guard: Balances language complexity based on grade
        val linguisticGuard = if (sessionState.classGrade <= 6) {
            "Explain using common household terms. Avoid heavy technical vocabulary. Use local analogies."
        } else {
            "Use exact technical vocabulary (e.g., provide the English scientific term alongside the translated term in brackets). Keep the explanation academic."
        }

        // Structural Runtime Prompt compiled dynamically from Curriculum Graph
        val prompt = """
            System: You are an expert teacher for Class ${sessionState.classGrade} ${sessionState.subject}.
            Topic: ${sessionState.chapter} -> ${activeConcept.name}
            
            Pedagogy Mode Constraints: Run this exchange strictly using the ${activeConcept.pedagogyMode} style.
            Target Difficulty Structural Constraint: Level ${activeConcept.difficulty}/10. 
            $linguisticGuard
            
            You must respond fluently in the exact language matching this language code: "$targetLanguageCode".
            
            Use the provided English Expert Context to answer accurately. Do not use information outside this context unless general knowledge is needed.
            
            Output ONLY a JSON object with this schema:
            {
              "audio_text": "Translated/Synthesized answer to be spoken aloud by TTS in $targetLanguageCode",
              "screen_text": "Detailed, formal answer for the teacher's screen in $targetLanguageCode",
              "suggested_nomination": "A tip for who the teacher should call on next",
              "bridge_question": "A follow-up question to ask the class in $targetLanguageCode, matching the ${activeConcept.pedagogyMode} pedagogy."
            }
            
            Context Data (English):
            $contextString
            
            Conversation History:
            $conversationContext
            
            JSON Output:
        """.trimIndent()

        val jsonResponse = llmEngine.generateResponse(prompt)
        
        return parseResponseJson(jsonResponse)
    }

    private fun parseResponseJson(json: String): AiResponse {
        fun extractString(key: String): String {
            val regex = "\"$key\"\\s*:\\s*\"(.*?)\"".toRegex(RegexOption.DOT_MATCHES_ALL)
            return regex.find(json)?.groups?.get(1)?.value ?: ""
        }

        return AiResponse(
            audioText = extractString("audio_text"),
            screenText = extractString("screen_text"),
            suggestedNomination = extractString("suggested_nomination"),
            bridgeQuestion = extractString("bridge_question")
        )
    }
}
