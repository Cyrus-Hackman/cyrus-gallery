package com.cyrus.gallery.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cyrus.gallery.data.auth.OneDriveAuthManager
import com.cyrus.gallery.data.cache.PrivateStorageManager
import com.cyrus.gallery.data.database.CloudDownloadStatus
import com.cyrus.gallery.data.database.CloudMediaCacheDao
import com.cyrus.gallery.domain.model.CloudStorageState
import com.cyrus.gallery.domain.model.MediaType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@HiltWorker
class MediaDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val authManager: OneDriveAuthManager,
    private val httpClient: OkHttpClient,
    private val storageManager: PrivateStorageManager,
    private val cacheDao: CloudMediaCacheDao,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_ITEM_ID = "item_id"
        const val KEY_DRIVE_ID = "drive_id"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_MIME_TYPE = "mime_type"
        const val KEY_SIZE_BYTES = "size_bytes"
        const val KEY_IS_PINNED = "is_pinned"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val itemId = inputData.getString(KEY_ITEM_ID) ?: return@withContext Result.failure()
        val driveId = inputData.getString(KEY_DRIVE_ID) ?: "root"
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "media_$itemId"
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: "image/jpeg"
        val sizeBytes = inputData.getLong(KEY_SIZE_BYTES, 0L)
        val isPinned = inputData.getBoolean(KEY_IS_PINNED, false)
        val cacheId = "onedrive_$itemId"

        val token = authManager.getAccessToken() ?: return@withContext Result.retry()

        // Re-read the current pin state from Room — the enqueue-time input data may be stale if the
        // user toggled pin after the work request was already queued but before it started running.
        val currentEntity = cacheDao.find(driveId, itemId)
        val effectiveIsPinned = when (currentEntity?.storageState) {
            CloudStorageState.PINNED_OFFLINE -> true
            else -> isPinned
        }
        // Preserve the existing localCachedPath so that a concurrent delta sync that reads from the DB
        // between now and download completion does not see a spurious null path and demote the item.
        val existingPath = currentEntity?.localCachedPath

        val downloadUrl = "https://graph.microsoft.com/v1.0/me/drive/items/$itemId/content"
        val request = Request.Builder()
            .url(downloadUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        cacheDao.updateDownloadStatus(
            cacheId = cacheId,
            status = CloudDownloadStatus.DOWNLOADING,
            progress = 0,
            path = existingPath,   // preserve existing path rather than nulling it out
            downloadedTime = null,
        )

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    cacheDao.updateDownloadStatus(
                        cacheId = cacheId,
                        status = CloudDownloadStatus.FAILED,
                        progress = 0,
                        path = existingPath,  // preserve on failure too — file may still be valid
                        downloadedTime = null,
                    )
                    return@withContext if (response.code in 500..599) Result.retry() else Result.failure()
                }

                val body = response.body ?: throw IOException("Empty response body")
                val isVideo = mimeType.startsWith("video")
                val type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE

                val finalFile = storageManager.writeStreamAtomically(
                    itemId = itemId,
                    fileName = fileName,
                    type = type,
                    isPinned = effectiveIsPinned,
                    sourceStream = body.byteStream(),
                    expectedSizeBytes = if (sizeBytes > 0) sizeBytes else null,
                )

                val targetState = if (effectiveIsPinned) CloudStorageState.PINNED_OFFLINE else CloudStorageState.CACHED
                val now = System.currentTimeMillis()

                cacheDao.updateStorageState(
                    cacheId = cacheId,
                    state = targetState,
                    path = finalFile.absolutePath,
                    accessedTime = now,
                )
                cacheDao.updateDownloadStatus(
                    cacheId = cacheId,
                    status = CloudDownloadStatus.COMPLETE,
                    progress = 100,
                    path = finalFile.absolutePath,
                    downloadedTime = now,
                )

                Result.success()
            }
        } catch (e: Exception) {
            cacheDao.updateDownloadStatus(
                cacheId = cacheId,
                status = CloudDownloadStatus.FAILED,
                progress = 0,
                path = existingPath,  // preserve any previously cached path — don't wipe on exception
                downloadedTime = null,
            )
            Result.retry()
        }
    }
}
