package com.vidya.core.data.local

import androidx.room.*
import java.util.UUID

@Entity(
    tableName = "audio_chunks",
    indices = [
        Index(value = ["session_event_id"]),
        Index(value = ["is_synced"]),
        Index(value = ["language_code"])
    ]
)
data class AudioChunkEntity(
    @PrimaryKey
    @ColumnInfo(name = "chunk_id") val chunkId: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "session_event_id") val sessionEventId: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "codec_used") val codecUsed: String,
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long,
    @ColumnInfo(name = "language_code") val languageCode: String = "en",
    @ColumnInfo(name = "transcript") val transcript: String? = null,
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false,
    @ColumnInfo(name = "captured_at") val capturedAt: Long = System.currentTimeMillis()
)

@Dao
interface AudioChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: AudioChunkEntity)

    @Query("SELECT * FROM audio_chunks WHERE is_synced = 0 ORDER BY captured_at ASC")
    suspend fun getUnsyncedChunks(): List<AudioChunkEntity>

    @Query("UPDATE audio_chunks SET is_synced = 1 WHERE chunk_id = :chunkId")
    suspend fun markAsSynced(chunkId: String)

    @Query("UPDATE audio_chunks SET transcript = :transcript, language_code = :languageCode WHERE chunk_id = :chunkId")
    suspend fun updateTranscript(chunkId: String, transcript: String, languageCode: String)

    @Query("SELECT * FROM audio_chunks WHERE is_synced = 1 AND captured_at < :expirationTime")
    suspend fun getExpiredSyncedFiles(expirationTime: Long): List<AudioChunkEntity>

    @Query("DELETE FROM audio_chunks WHERE chunk_id = :chunkId")
    suspend fun deleteRecord(chunkId: String)

    @Query("DELETE FROM audio_chunks WHERE is_synced = 1 AND captured_at < :expirationTime")
    suspend fun purgeExpiredLocalAudio(expirationTime: Long)
}
