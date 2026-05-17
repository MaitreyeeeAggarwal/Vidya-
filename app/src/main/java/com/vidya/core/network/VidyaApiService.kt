package com.vidya.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit service interface for the Vidya cloud backend.
 *
 * All endpoints require a JWT Bearer token in the Authorization header.
 * The base URL is configured in the Retrofit builder (DI module).
 */
interface VidyaApiService {

    /**
     * Idempotent delta sync: upload a batch of append-only classroom events.
     *
     * The server uses ON CONFLICT DO NOTHING on the UUIDv7 event ID,
     * so retries of the same payload are safe and produce no duplicates.
     *
     * @return Per-event acknowledgements with server sequence IDs.
     */
    @POST("/api/sync/events")
    suspend fun uploadEventBatch(
        @Body events: List<EventSyncItemDto>,
        @Header("Authorization") token: String? = null
    ): Response<List<SyncAcknowledgementDto>>

    /**
     * Full session sync (sessions + nested events).
     * Used for initial bulk upload after a long offline period.
     */
    @POST("/api/sync/flush-classroom-sessions")
    suspend fun flushClassroomSessions(
        @Body sessions: List<SessionSyncDto>,
        @Header("Authorization") token: String? = null
    ): Response<Map<String, Any>>

    /**
     * Curriculum Delta Check (ETag validated).
     * Returns 304 Not Modified if the client's ETag matches the server.
     * Otherwise returns 200 OK with JSON patch operations to apply.
     */
    @GET("/api/curriculum/delta-check")
    suspend fun getCurriculumDelta(
        @Header("If-None-Match") ifNoneMatchHeader: String,
        @Header("Authorization") token: String? = null
    ): Response<CurriculumDeltaResponseDto>
}
