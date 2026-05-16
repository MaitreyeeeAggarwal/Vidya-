package com.vidya.core.ai

import com.vidya.core.ai.models.AiResponse
import com.vidya.core.ai.models.Exchange
import com.vidya.core.ai.models.SessionState

/**
 * The "Heartbeat" of Vidya.
 * Orchestrates the Core AI Facilitation Loop: Retrieve -> Reason -> Speak.
 */
class AiTeacherLoop(
    private val intentClassifier: IntentClassifier,
    private val knowledgeRetriever: KnowledgeRetriever,
    private val responseGenerator: ResponseGenerator,
    private val conversationBuffer: ConversationBuffer
) {

    private lateinit var currentSessionState: SessionState

    /**
     * Step 2: Topic Initialization
     */
    fun startSession(sessionState: SessionState) {
        this.currentSessionState = sessionState
        conversationBuffer.clear()
        
        // Prime Gemma's cache with the chapter summary (conceptually handled by MediaPipe session state)
        println("Session Started: ${sessionState.subject} Grade ${sessionState.classGrade}")
    }

    /**
     * The core loop triggered when the Android Microphone captures a student's answer.
     * @param audioData 16kHz mono-channel float32 array
     */
    suspend fun processStudentInput(audioData: FloatArray): AiResponse {
        val historyContext = conversationBuffer.getFormattedContextForPrompt()

        // Step 1 & 3A: Multimodal Input & Intent Classification
        val intent = intentClassifier.classifyIntent(audioData, historyContext)
        println("Decoded Intent: ${intent.type} | Query: ${intent.expandedSearchQuery} | Lang: ${intent.detectedLanguage}")

        // Step 2 (Sub): The "Expert" Retrieval (RAG Pivot)
        // Fetches exact English PhD text chunks using BGE-M3
        val contextChunks = knowledgeRetriever.retrieveKnowledge(
            expandedQuery = intent.expandedSearchQuery,
            sessionState = currentSessionState
        )

        // Step 3C & 4: Answer Generation & Translation-Reasoning Fusion
        val aiResponse = responseGenerator.generateResponse(
            studentInputAudioPlaceholder = "[Audio Input Processed]",
            retrievedContext = contextChunks,
            sessionState = currentSessionState,
            conversationContext = historyContext,
            targetLanguageCode = intent.detectedLanguage
        )

        // Update Conversation Buffer
        conversationBuffer.addExchange(
            Exchange(
                studentInput = "[Audio Input Processed]",
                aiResponse = aiResponse
            )
        )

        return aiResponse
    }
}
