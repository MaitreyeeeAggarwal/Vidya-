package com.vidya.core.network

import com.vidya.core.database.LocalSessionEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Network DTOs for the delta sync protocol.
 * These map directly to the FastAPI Pydantic models on the server.
 */

// ── Request DTOs (client → server) ──────────────────────────────────────

data class EventSyncItemDto(
    val id: String,
    val sessionId: String,
    val eventType: String,
    val timestamp: String,      // ISO 8601
    val payload: String,
    val language_code: String? = null,
    val transcript: String? = null
)

data class SessionSyncDto(
    val id: String,
    val teacherId: String,
    val classId: String,
    val chapterId: String,
    val state: String,
    val startedAt: String,
    val endedAt: String? = null,
    val events: List<EventSyncItemDto>
)

// ── Response DTOs (server → client) ─────────────────────────────────────

data class SyncAcknowledgementDto(
    val client_event_id: String,
    val server_sequence_id: Long,
    val status: String           // "COMPLETED" or "EXISTED"
)

// ── Mapping extensions ──────────────────────────────────────────────────

private val iso8601Format: SimpleDateFormat
    get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

fun LocalSessionEvent.toNetworkDto(): EventSyncItemDto {
    return EventSyncItemDto(
        id = this.id,
        sessionId = this.sessionId,
        eventType = this.eventType.name,
        timestamp = iso8601Format.format(Date(this.timestamp)),
        payload = this.payload,
        language_code = this.languageCode,
        transcript = this.transcript
    )
}
