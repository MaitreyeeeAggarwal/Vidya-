package com.vidya.core.data.local

import androidx.room.*
import java.util.UUID

enum class ProcessingStatus { PENDING, PROCESSING, DONE, FAILED }

@Entity(
    tableName = "student_doubts",
    indices = [Index(value = ["session_id"]), Index(value = ["processing_status"])]
)
data class StudentDoubtEntity(
    @PrimaryKey
    @ColumnInfo(name = "doubt_id") val doubtId: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "processing_status") var processingStatus: ProcessingStatus = ProcessingStatus.PENDING,
    @ColumnInfo(name = "extracted_text") var extractedText: String? = null,
    @ColumnInfo(name = "ai_response") var aiResponse: String? = null,
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false,
    @ColumnInfo(name = "captured_at") val capturedAt: Long = System.currentTimeMillis()
)

@Dao
interface StudentDoubtDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoubt(doubt: StudentDoubtEntity)

    @Query("SELECT * FROM student_doubts WHERE processing_status = 'PENDING' ORDER BY captured_at ASC LIMIT 1")
    suspend fun getNextPendingDoubt(): StudentDoubtEntity?

    @Update
    suspend fun updateDoubtStatus(doubt: StudentDoubtEntity)

    @Query("SELECT COUNT(*) FROM student_doubts WHERE processing_status = 'PENDING'")
    suspend fun getPendingCount(): Int

    @Query("SELECT * FROM student_doubts WHERE session_id = :sessionId AND processing_status = 'DONE' ORDER BY captured_at ASC")
    suspend fun getResolvedDoubtsForSession(sessionId: String): List<StudentDoubtEntity>

    @Query("SELECT * FROM student_doubts WHERE is_synced = 0 ORDER BY captured_at ASC")
    suspend fun getUnsyncedDoubts(): List<StudentDoubtEntity>

    @Query("UPDATE student_doubts SET is_synced = 1 WHERE doubt_id = :doubtId")
    suspend fun markAsSynced(doubtId: String)
}
