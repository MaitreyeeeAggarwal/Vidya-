package com.vidya.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration from schema version 5 to 6.
 * Adds the media_upload_queue table for resumable multipart uploads.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS media_upload_queue (
                file_id TEXT NOT NULL PRIMARY KEY,
                session_event_id TEXT NOT NULL,
                file_path TEXT NOT NULL,
                file_type TEXT NOT NULL,
                content_type TEXT NOT NULL,
                total_bytes INTEGER NOT NULL,
                bytes_uploaded INTEGER NOT NULL DEFAULT 0,
                upload_status TEXT NOT NULL DEFAULT 'QUEUED',
                upload_id TEXT DEFAULT NULL,
                s3_key TEXT DEFAULT NULL,
                last_part_number INTEGER NOT NULL DEFAULT 0,
                enqueued_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_media_upload_queue_session_event_id ON media_upload_queue (session_event_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_media_upload_queue_upload_status ON media_upload_queue (upload_status)")
    }
}
