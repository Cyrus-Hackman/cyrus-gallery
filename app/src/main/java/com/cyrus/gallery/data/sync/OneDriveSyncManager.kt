package com.cyrus.gallery.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.cyrus.gallery.domain.model.CloudCachePolicy
import com.cyrus.gallery.domain.model.SyncNetworkMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OneDriveSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    companion object {
        const val PERIODIC_SYNC_WORK_NAME = "cyrus_onedrive_periodic_sync"
        const val IMMEDIATE_SYNC_WORK_NAME = "cyrus_onedrive_immediate_sync"
    }

    fun schedulePeriodicSync(policy: CloudCachePolicy) {
        val requiredNetwork = if (policy.networkMode == SyncNetworkMode.WIFI_ONLY) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(requiredNetwork)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<OneDriveSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest,
        )
    }

    fun enqueueImmediateSync(policy: CloudCachePolicy = CloudCachePolicy()) {
        val requiredNetwork = if (policy.networkMode == SyncNetworkMode.WIFI_ONLY) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(requiredNetwork)
            .build()

        val request = OneTimeWorkRequestBuilder<OneDriveSyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            IMMEDIATE_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun enqueueDownload(
        itemId: String,
        driveId: String,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        isPinned: Boolean = false,
        policy: CloudCachePolicy = CloudCachePolicy(),
    ) {
        val requiredNetwork = if (policy.networkMode == SyncNetworkMode.WIFI_ONLY) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(requiredNetwork)
            .build()

        val data = workDataOf(
            MediaDownloadWorker.KEY_ITEM_ID to itemId,
            MediaDownloadWorker.KEY_DRIVE_ID to driveId,
            MediaDownloadWorker.KEY_FILE_NAME to fileName,
            MediaDownloadWorker.KEY_MIME_TYPE to mimeType,
            MediaDownloadWorker.KEY_SIZE_BYTES to sizeBytes,
            MediaDownloadWorker.KEY_IS_PINNED to isPinned,
        )

        val request = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .addTag("download_$itemId")
            .build()

        workManager.enqueueUniqueWork(
            "download_$itemId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
