package com.vidya.core.ai

import com.vidya.core.ai.models.Exchange
import java.util.LinkedList

/**
 * Maintains the conversation history to handle follow-ups and Socratic transitions.
 * Keeps the last 5 exchanges in Gemma's 128K context window.
 */
class ConversationBuffer(private val maxExchanges: Int = 5) {
    private val history = LinkedList<Exchange>()

    fun addExchange(exchange: Exchange) {
        if (history.size >= maxExchanges) {
            history.removeFirst()
        }
        history.addLast(exchange)
    }

    fun getHistory(): List<Exchange> {
        return history.toList()
    }

    fun clear() {
        history.clear()
    }

    /**
     * Formats the recent history into a string that can be injected into Gemma's prompt.
     */
    fun getFormattedContextForPrompt(): String {
        if (history.isEmpty()) return "No prior conversation context."
        
        return buildString {
            append("Recent Conversation History:\n")
            history.forEachIndexed { index, exchange ->
                append("Exchange ${index + 1}:\n")
                append("Student: ${exchange.studentInput}\n")
                append("AI: ${exchange.aiResponse.audioText}\n")
            }
        }
    }
}
