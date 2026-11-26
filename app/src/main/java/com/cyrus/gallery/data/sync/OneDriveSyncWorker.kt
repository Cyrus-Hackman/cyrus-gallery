package com.cyrus.gallery.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cyrus.gallery.data.cache.CloudPolicyPreferences
import com.cyrus.gallery.data.repository.OneDriveRepository
import com.cyrus.gallery.domain.repository.CloudCacheRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class OneDriveSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val oneDriveRepository: OneDriveRepository,
    private val cacheRepository: CloudCacheRepository,
    private val policyPreferences: CloudPolicyPreferences,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val result = oneDriveRepository.performDeltaSync()
        if (result.isSuccess) {
            // Enforce configured temporary cache cap/expiry while leaving pinned offline originals untouched
            cacheRepository.evict(policyPreferences.policy.value)
            Result.success()
        } else {
            Result.retry()
        }
    }
}
