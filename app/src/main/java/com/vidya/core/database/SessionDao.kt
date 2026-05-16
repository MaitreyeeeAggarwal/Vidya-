package com.vidya.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: LocalSession)

    @Update
    suspend fun updateSession(session: LocalSession)

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): LocalSession?

    @Query("SELECT * FROM sessions WHERE syncState = 0")
    suspend fun getUnsyncedSessions(): List<LocalSession>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSessionEvent(event: LocalSessionEvent)

    @Query("SELECT * FROM session_events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getSessionEvents(sessionId: String): List<LocalSessionEvent>

    @Transaction
    suspend fun markSessionSynced(sessionId: String) {
        val session = getSession(sessionId)
        if (session != null) {
            session.syncState = true
            updateSession(session)
        }
    }
}
