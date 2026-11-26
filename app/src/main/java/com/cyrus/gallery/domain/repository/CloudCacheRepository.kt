package com.cyrus.gallery.domain.repository

import com.cyrus.gallery.domain.model.CloudCachePolicy
import kotlinx.coroutines.flow.Flow

/** Cache operations are strictly local and must never delete OneDrive originals. */
interface CloudCacheRepository {
    fun observeStorageSummary(): Flow<CloudCacheStorageSummary>
    suspend fun setPinned(driveId: String, itemId: String, pinned: Boolean)
    suspend fun clearLocalCopy(itemId: String)
    suspend fun clearTemporaryCache(): CacheClearResult
    suspend fun evict(policy: CloudCachePolicy): CacheClearResult
}

data class CloudCacheStorageSummary(
    val cachedPhotoBytes: Long = 0,
    val cachedVideoBytes: Long = 0,
    val thumbnailBytes: Long = 0,
    val pinnedBytes: Long = 0,
    val cachedFileCount: Int = 0,
    val lastSynchronizationMillis: Long? = null,
)

data class CacheClearResult(val reclaimedBytes: Long, val removedFiles: Int)
