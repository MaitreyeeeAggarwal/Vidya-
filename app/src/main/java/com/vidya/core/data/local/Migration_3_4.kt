package com.vidya.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration from schema version 3 to 4.
 * Adds the student_doubts table for offline camera-capture doubt processing.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS student_doubts (
                doubt_id TEXT NOT NULL PRIMARY KEY,
                session_id TEXT NOT NULL,
                file_path TEXT NOT NULL,
                processing_status TEXT NOT NULL DEFAULT 'PENDING',
                extracted_text TEXT DEFAULT NULL,
                ai_response TEXT DEFAULT NULL,
                is_synced INTEGER NOT NULL DEFAULT 0,
                captured_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_student_doubts_session_id ON student_doubts (session_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_student_doubts_processing_status ON student_doubts (processing_status)")
    }
}
