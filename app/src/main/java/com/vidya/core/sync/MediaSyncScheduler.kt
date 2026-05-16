package com.vidya.core.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Schedules resumable media uploads via WorkManager.
 *
 * Enforces strict constraints so heavy binary transfers only run
 * when the device is:
 *   - Connected to Wi-Fi (unmetered)
 *   - Plugged into a charger
 *   - Idle (screen off / after school hours)
 *
 * This keeps the classroom experience buttery smooth during active lessons.
 */
class MediaSyncScheduler(private val context: Context) {

    companion object {
        private const val TAG = "MediaSyncScheduler"
        private const val UNIQUE_WORK_NAME = "VidyaResumableMediaUpload"
        private const val BACKOFF_SECONDS = 45L
    }

    /**
     * Enqueue a one-shot resumable upload pass.
     *
     * Call after a session ends or when new media is added to the queue.
     * REPLACE policy ensures the latest queue state is always processed.
     */
    fun enqueueResumableMediaUploads() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .build()

        val request = OneTimeWorkRequestBuilder<ResumableMediaUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag("VIDYA_MEDIA_UPLOAD")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        Log.d(TAG, "Resumable media upload enqueued (Wi-Fi + charging + idle)")
    }

    /**
     * Enqueue an urgent upload that only requires connectivity (no Wi-Fi/charging).
     * Use sparingly — e.g., when storage is critically low.
     */
    fun enqueueUrgentMediaUploads() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ResumableMediaUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag("VIDYA_MEDIA_UPLOAD_URGENT")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${UNIQUE_WORK_NAME}_URGENT",
            ExistingWorkPolicy.KEEP,
            request
        )
        Log.d(TAG, "Urgent media upload enqueued (any network)")
    }

    fun cancelAllMediaUploads() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork("${UNIQUE_WORK_NAME}_URGENT")
        Log.d(TAG, "All media uploads cancelled")
    }
}
