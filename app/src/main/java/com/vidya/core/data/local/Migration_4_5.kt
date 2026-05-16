package com.vidya.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration from schema version 4 to 5.
 * Adds the tts_language_registry table for tracking offline TTS language packs.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tts_language_registry (
                language_code TEXT NOT NULL PRIMARY KEY,
                engine_type TEXT NOT NULL,
                model_file_path TEXT DEFAULT NULL,
                is_downloaded INTEGER NOT NULL DEFAULT 0,
                model_size_bytes INTEGER NOT NULL DEFAULT 0,
                last_verified_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        // Seed English and Hindi as SYSTEM-backed defaults (always available)
        db.execSQL(
            """
            INSERT OR IGNORE INTO tts_language_registry (language_code, engine_type, model_file_path, is_downloaded, model_size_bytes, last_verified_at)
            VALUES ('en', 'SYSTEM', NULL, 1, 0, ${System.currentTimeMillis()})
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO tts_language_registry (language_code, engine_type, model_file_path, is_downloaded, model_size_bytes, last_verified_at)
            VALUES ('hi', 'SYSTEM', NULL, 1, 0, ${System.currentTimeMillis()})
            """.trimIndent()
        )
    }
}
