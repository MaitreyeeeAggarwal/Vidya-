package com.vidya.core.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Schedules and manages delta sync work requests via WorkManager.
 *
 * Two scheduling modes:
 *   1. **Immediate** — Triggered when a session ends or the teacher taps "Sync Now".
 *      Uses a OneTimeWorkRequest with exponential backoff.
 *   2. **Periodic** — Background heartbeat every 15 minutes (minimum WorkManager interval).
 *      Catches any events that slipped through immediate sync attempts.
 *
 * Both modes require an active network connection and adequate storage.
 * WorkManager handles all retry logic, constraint enforcement, and persistence
 * across app kills and device reboots.
 */
class VidyaSyncScheduler(private val context: Context) {

    companion object {
        private const val TAG = "SyncScheduler"
        private const val UNIQUE_IMMEDIATE_WORK = "VidyaDeltaSyncImmediate"
        private const val UNIQUE_PERIODIC_WORK = "VidyaDeltaSyncPeriodic"

        /** Initial backoff delay for failed sync attempts */
        private const val BACKOFF_DELAY_SECONDS = 30L

        /** Periodic sync interval in minutes (15 = WorkManager minimum) */
        private const val PERIODIC_INTERVAL_MINUTES = 15L
    }

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresStorageNotLow(true)
        .build()

    /**
     * Trigger an immediate, one-shot delta sync.
     *
     * If a sync is already in progress, the new request is dropped (KEEP policy)
     * to prevent overlapping uploads of the same data.
     */
    fun triggerImmediateSync() {
        val request = OneTimeWorkRequestBuilder<DeltaSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag("VIDYA_DELTA_SYNC")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_IMMEDIATE_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )

        Log.d(TAG, "Immediate delta sync enqueued")
    }

    /**
     * Schedule a recurring background sync heartbeat.
     *
     * Call once during Application.onCreate(). WorkManager persists the
     * schedule across reboots — subsequent calls with KEEP policy are no-ops.
     */
    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<DeltaSyncWorker>(
            PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag("VIDYA_PERIODIC_SYNC")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

        Log.d(TAG, "Periodic delta sync scheduled (every ${PERIODIC_INTERVAL_MINUTES}min)")
    }

    /** Cancel all pending and running sync work. */
    fun cancelAllSync() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_IMMEDIATE_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_WORK)
        Log.d(TAG, "All sync work cancelled")
    }
}
