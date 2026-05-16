package com.vidya.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration from schema version 2 to 3.
 * Adds language_code and transcript columns to the audio_chunks table
 * for multilingual Whisper-tiny transcription support.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add the language code column with a safe default
        db.execSQL(
            "ALTER TABLE audio_chunks ADD COLUMN language_code TEXT NOT NULL DEFAULT 'en'"
        )
        // Add the transcript column (nullable — empty until ASR completes)
        db.execSQL(
            "ALTER TABLE audio_chunks ADD COLUMN transcript TEXT DEFAULT NULL"
        )
        // Index on language_code for analytics queries
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_audio_chunks_language_code ON audio_chunks (language_code)"
        )
    }
}
