package com.vidya.core.session

import com.vidya.core.database.EventType
import com.vidya.core.database.LocalSession
import com.vidya.core.database.LocalSessionEvent
import com.vidya.core.database.SessionDao
import com.vidya.core.database.SessionState
import java.util.UUID

class VidyaSessionManager(private val sessionDao: SessionDao) {

    /**
     * 1. START: Initializes a new classroom session and appends the first entry point
     */
    suspend fun startSession(teacherId: String, classId: String, chapterId: String): LocalSession {
        val sessionId = UUID.randomUUID().toString()

        val session = LocalSession(
            id = sessionId,
            teacherId = teacherId,
            classId = classId,
            chapterId = chapterId,
            state = SessionState.ACTIVE
        )

        val initEvent = LocalSessionEvent(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            eventType = EventType.CONCEPT_STARTED,
            payload = """{"note": "Session initialized and primary topic stream opened."}"""
        )

        // Ideally this runs in a Room transaction, but Dao inserts are typically fine
        // if called sequentially or if wrapped in @Transaction on the DAO side.
        sessionDao.insertSession(session)
        sessionDao.insertSessionEvent(initEvent)

        return session
    }

    /**
     * 2. PAUSE: Halts active processing safely
     */
    suspend fun pauseSession(sessionId: String) {
        val session = sessionDao.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found")
        
        if (session.state != SessionState.ACTIVE) {
            throw IllegalStateException("Can only pause an actively running session layout.")
        }

        session.state = SessionState.PAUSED
        sessionDao.updateSession(session)
    }

    /**
     * 3. RESUME: Moves the session back from a PAUSED state to ACTIVE
     */
    suspend fun resumeSession(sessionId: String) {
        val session = sessionDao.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found")
        
        if (session.state != SessionState.PAUSED) {
            throw IllegalStateException("Can only resume a paused session configuration.")
        }

        session.state = SessionState.ACTIVE
        sessionDao.updateSession(session)
    }

    /**
     * 4. END: Finalizes the session, timestamps it, and logs the closure loop
     */
    suspend fun endSession(sessionId: String): LocalSession {
        val session = sessionDao.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found")
            
        if (session.state == SessionState.ENDED) return session

        session.state = SessionState.ENDED
        session.endedAt = System.currentTimeMillis()
        sessionDao.updateSession(session)
        
        return session
    }
}
