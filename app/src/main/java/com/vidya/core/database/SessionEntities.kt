package com.vidya.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

enum class SessionState {
    CREATED,
    ACTIVE,
    PAUSED,
    ENDED
}

enum class EventType {
    CONCEPT_STARTED,
    QUESTION_ASKED,
    STUDENT_NOMINATED,
    ANSWER_RECORDED,
    DOUBT_RAISED
}

@Entity(
    tableName = "sessions",
    indices = [Index(value = ["teacherId", "classId"])]
)
data class LocalSession(
    @PrimaryKey val id: String,
    val teacherId: String,
    val classId: String,
    val chapterId: String,
    var state: SessionState = SessionState.CREATED,
    val startedAt: Long = System.currentTimeMillis(),
    var endedAt: Long? = null,
    var syncState: Boolean = false
)

@Entity(
    tableName = "session_events",
    foreignKeys = [
        ForeignKey(
            entity = LocalSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId", "timestamp"])]
)
data class LocalSessionEvent(
    @PrimaryKey val id: String,
    val sessionId: String,
    val eventType: EventType,
    val timestamp: Long = System.currentTimeMillis(),
    val payload: String = "{}"
)
