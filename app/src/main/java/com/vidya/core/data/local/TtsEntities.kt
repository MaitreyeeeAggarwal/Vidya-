package com.vidya.core.data.local

import androidx.room.*

@Entity(tableName = "tts_language_registry")
data class TtsLanguageRegistryEntity(
    @PrimaryKey
    @ColumnInfo(name = "language_code") val languageCode: String,       // e.g. "hi", "ta", "te", "bn", "mr", "mai"
    @ColumnInfo(name = "engine_type") val engineType: String,           // "SYSTEM" | "INDIC_TTS" | "COQUI"
    @ColumnInfo(name = "model_file_path") val modelFilePath: String?,   // Path to .onnx or .bin on disk
    @ColumnInfo(name = "is_downloaded") val isDownloaded: Boolean = false,
    @ColumnInfo(name = "model_size_bytes") val modelSizeBytes: Long = 0,
    @ColumnInfo(name = "last_verified_at") val lastVerifiedAt: Long = System.currentTimeMillis()
)

@Dao
interface TtsLanguageRegistryDao {
    @Query("SELECT * FROM tts_language_registry WHERE language_code = :langCode AND is_downloaded = 1")
    suspend fun getActiveEngineForLanguage(langCode: String): TtsLanguageRegistryEntity?

    @Query("SELECT * FROM tts_language_registry WHERE is_downloaded = 1")
    suspend fun getAllDownloadedLanguages(): List<TtsLanguageRegistryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLanguage(entity: TtsLanguageRegistryEntity)

    @Query("UPDATE tts_language_registry SET is_downloaded = :downloaded, last_verified_at = :verifiedAt WHERE language_code = :langCode")
    suspend fun updateDownloadStatus(langCode: String, downloaded: Boolean, verifiedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM tts_language_registry WHERE language_code = :langCode")
    suspend fun removeLanguage(langCode: String)
}
