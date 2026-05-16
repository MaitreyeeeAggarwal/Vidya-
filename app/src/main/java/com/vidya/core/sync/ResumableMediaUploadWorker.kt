package com.vidya.core.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vidya.core.data.local.MediaUploadDao
import com.vidya.core.data.local.UploadStatus
import com.vidya.core.network.VidyaMediaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * Resumable multipart upload worker.
 *
 * Reads local media files in 256 KB slices, streams each to the server,
 * and persists byte-offset progress in Room after every successful part.
 * If a 2G drop occurs mid-chunk, WorkManager retries with exponential
 * backoff and the worker resumes from the exact saved offset.
 */
class ResumableMediaUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "MediaUploadWorker"
        /** 256 KB — optimal for unstable 2G/3G rural networks */
        private const val CHUNK_SIZE = 256 * 1024
    }

    // Injected via custom WorkerFactory in production
    private lateinit var uploadDao: MediaUploadDao
    private lateinit var mediaClient: VidyaMediaClient

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Resumable upload started — attempt #$runAttemptCount")

        val queue = uploadDao.getActiveUploadQueue()
        if (queue.isEmpty()) {
            Log.d(TAG, "Upload queue empty")
            return@withContext Result.success()
        }

        Log.d(TAG, "${queue.size} file(s) pending upload")

        for (item in queue) {
            val file = File(item.filePath)
            if (!file.exists()) {
                Log.w(TAG, "File missing: ${item.filePath} — marking FAILED")
                item.uploadStatus = UploadStatus.FAILED
                uploadDao.updateUploadProgress(item)
                continue
            }

            try {
                item.uploadStatus = UploadStatus.UPLOADING
                uploadDao.updateUploadProgress(item)

                // ── Step 1: Initialize S3 multipart session (once per file) ──
                if (item.uploadId == null) {
                    val initResp = mediaClient.initializeUpload(
                        fileId = item.fileId,
                        sessionEventId = item.sessionEventId,
                        fileName = file.name,
                        contentType = item.contentType
                    )
                    item.uploadId = initResp.uploadId
                    item.s3Key = initResp.s3Key
                    uploadDao.updateUploadProgress(item)
                    Log.d(TAG, "Upload initialized → uploadId=${item.uploadId}")
                }

                val uploadId = item.uploadId!!
                val s3Key = item.s3Key!!
                val etags = mutableListOf<Pair<Int, String>>()

                // ── Step 2: Stream file in 256 KB chunks ─────────────────────
                val raf = RandomAccessFile(file, "r")
                var partNumber = item.lastPartNumber + 1
                var offset = item.bytesUploaded

                raf.seek(offset)

                while (offset < item.totalBytes) {
                    val remaining = (item.totalBytes - offset).toInt()
                    val readSize = minOf(CHUNK_SIZE, remaining)
                    val buffer = ByteArray(readSize)
                    val bytesRead = raf.read(buffer)

                    if (bytesRead <= 0) break

                    val chunk = if (bytesRead < readSize) buffer.copyOf(bytesRead) else buffer

                    // ── Step 3: Upload part ──────────────────────────────────
                    val partResp = mediaClient.uploadPart(uploadId, s3Key, partNumber, chunk)

                    if (partResp.success) {
                        etags.add(Pair(partNumber, partResp.etag))
                        offset += bytesRead
                        partNumber++

                        // ── Step 4: Persist progress immediately ─────────────
                        item.bytesUploaded = offset
                        item.lastPartNumber = partNumber - 1
                        uploadDao.updateUploadProgress(item)

                        Log.d(TAG, "Part ${partNumber - 1} ACK'd → ${offset}/${item.totalBytes} bytes")
                    } else {
                        Log.w(TAG, "Part $partNumber rejected — scheduling retry")
                        raf.close()
                        return@withContext Result.retry()
                    }
                }
                raf.close()

                // ── Step 5: Complete multipart upload ────────────────────────
                mediaClient.completeUpload(uploadId, s3Key, etags)

                item.uploadStatus = UploadStatus.COMPLETED
                uploadDao.updateUploadProgress(item)
                Log.d(TAG, "Upload complete → ${file.name}")

                // ── Step 6: Purge local file to reclaim storage ──────────────
                if (file.delete()) {
                    uploadDao.removeUploadRecord(item.fileId)
                    Log.d(TAG, "Local file purged → ${file.name}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Upload failed for ${item.fileId} — will retry", e)
                item.uploadStatus = UploadStatus.PAUSED
                uploadDao.updateUploadProgress(item)
                return@withContext Result.retry()
            }
        }

        Log.d(TAG, "All uploads completed")
        return@withContext Result.success()
    }
}
