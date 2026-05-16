package com.vidya.core.data.local

import androidx.room.*

enum class UploadStatus { QUEUED, UPLOADING, PAUSED, COMPLETED, FAILED }

@Entity(
    tableName = "media_upload_queue",
    indices = [Index(value = ["session_event_id"]), Index(value = ["upload_status"])]
)
data class MediaUploadEntity(
    @PrimaryKey
    @ColumnInfo(name = "file_id") val fileId: String,
    @ColumnInfo(name = "session_event_id") val sessionEventId: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "file_type") val fileType: String,            // "AUDIO" or "IMAGE"
    @ColumnInfo(name = "content_type") val contentType: String,      // "audio/ogg" or "image/jpeg"
    @ColumnInfo(name = "total_bytes") val totalBytes: Long,
    @ColumnInfo(name = "bytes_uploaded") var bytesUploaded: Long = 0L,
    @ColumnInfo(name = "upload_status") var uploadStatus: UploadStatus = UploadStatus.QUEUED,
    @ColumnInfo(name = "upload_id") var uploadId: String? = null,    // S3 multipart upload ID
    @ColumnInfo(name = "s3_key") var s3Key: String? = null,          // Target object key
    @ColumnInfo(name = "last_part_number") var lastPartNumber: Int = 0,
    @ColumnInfo(name = "enqueued_at") val enqueuedAt: Long = System.currentTimeMillis()
)

@Dao
interface MediaUploadDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueMedia(media: MediaUploadEntity)

    @Query("SELECT * FROM media_upload_queue WHERE upload_status IN ('QUEUED', 'UPLOADING', 'PAUSED') ORDER BY enqueued_at ASC")
    suspend fun getActiveUploadQueue(): List<MediaUploadEntity>

    @Update
    suspend fun updateUploadProgress(media: MediaUploadEntity)

    @Query("DELETE FROM media_upload_queue WHERE file_id = :fileId")
    suspend fun removeUploadRecord(fileId: String)

    @Query("SELECT SUM(total_bytes - bytes_uploaded) FROM media_upload_queue WHERE upload_status != 'COMPLETED'")
    suspend fun getPendingUploadBytes(): Long?

    @Query("SELECT COUNT(*) FROM media_upload_queue WHERE upload_status IN ('QUEUED', 'UPLOADING', 'PAUSED')")
    suspend fun getActiveUploadCount(): Int
}
