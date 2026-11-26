package com.cyrus.gallery.data.repository

import com.cyrus.gallery.data.cache.PrivateStorageManager
import com.cyrus.gallery.data.database.CloudMediaCacheDao
import com.cyrus.gallery.domain.model.CloudCachePolicy
import com.cyrus.gallery.domain.model.CloudStorageState
import com.cyrus.gallery.domain.model.MediaType
import com.cyrus.gallery.domain.repository.CacheClearResult
import com.cyrus.gallery.domain.repository.CloudCacheRepository
import com.cyrus.gallery.domain.repository.CloudCacheStorageSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudCacheRepositoryImpl @Inject constructor(
    private val storageManager: PrivateStorageManager,
    private val cacheDao: CloudMediaCacheDao,
) : CloudCacheRepository {

    override fun observeStorageSummary(): Flow<CloudCacheStorageSummary> {
        return cacheDao.observeAll().map { items ->
            calculateSummary(items)
        }
    }

    private fun calculateSummary(items: List<com.cyrus.gallery.data.database.CloudMediaCacheEntity>): CloudCacheStorageSummary {
        val cachedPhotos = storageManager.calculateDirectoryBytes(storageManager.cachePhotosDir)
        val cachedVideos = storageManager.calculateDirectoryBytes(storageManager.cacheVideosDir)
        val thumbs = storageManager.calculateDirectoryBytes(storageManager.cacheThumbnailsDir)
        val pinnedPhotos = storageManager.calculateDirectoryBytes(storageManager.offlinePhotosDir)
        val pinnedVideos = storageManager.calculateDirectoryBytes(storageManager.offlineVideosDir)

        val cachedCount = items.count { it.storageState == CloudStorageState.CACHED }
        val lastSync = items.mapNotNull { it.lastSynchronizedAtMillis }.maxOrNull()

        return CloudCacheStorageSummary(
            cachedPhotoBytes = cachedPhotos,
            cachedVideoBytes = cachedVideos,
            thumbnailBytes = thumbs,
            pinnedBytes = pinnedPhotos + pinnedVideos,
            cachedFileCount = cachedCount,
            lastSynchronizationMillis = lastSync,
        )
    }

    override suspend fun findByItemId(itemId: String): com.cyrus.gallery.data.database.CloudMediaCacheEntity? =
        withContext(Dispatchers.IO) { cacheDao.findByItemId(itemId) }

    override suspend fun setPinned(driveId: String, itemId: String, pinned: Boolean): Unit = withContext(Dispatchers.IO) {
        // Look up by itemId first because driveId stores parentFolderId for items inside OneDrive folders
        val entity = cacheDao.findByItemId(itemId) ?: cacheDao.find(driveId, itemId) ?: return@withContext
        val currentPath = entity.localCachedPath
        val hasLocalFile = !currentPath.isNullOrEmpty() && File(currentPath).exists()

        val isVideo = entity.mimeType.startsWith("video")
        val mediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE

        val newPath = if (hasLocalFile) {
            storageManager.migratePinnedFile(currentPath!!, mediaType, pinned) ?: currentPath
        } else {
            null
        }
        val newTargetState = when {
            pinned -> CloudStorageState.PINNED_OFFLINE
            hasLocalFile -> CloudStorageState.CACHED
            else -> CloudStorageState.REMOTE_ONLY
        }

        cacheDao.updateStorageState(
            cacheId = entity.cacheId,
            state = newTargetState,
            path = newPath,
            accessedTime = System.currentTimeMillis(),
        )
    }

    override suspend fun clearLocalCopy(itemId: String): Unit = withContext(Dispatchers.IO) {
        val entity = cacheDao.findByItemId(itemId) ?: return@withContext
        entity.localCachedPath?.let { storageManager.deleteManagedLocalCopySafely(it) }
        cacheDao.clearLocalCopyState(
            cacheId = entity.cacheId,
            accessedTime = System.currentTimeMillis(),
        )
    }

    override suspend fun clearTemporaryCache(): CacheClearResult = withContext(Dispatchers.IO) {
        val (reclaimedBytes, removedCount) = storageManager.clearAllTemporaryCache()

        // Reset CACHED rows after temporary files are removed (PINNED_OFFLINE remains untouched)
        val evictable = cacheDao.getEvictableItems()
        val now = System.currentTimeMillis()
        for (item in evictable) {
            cacheDao.clearLocalCopyState(
                cacheId = item.cacheId,
                accessedTime = now,
            )
        }

        CacheClearResult(reclaimedBytes = reclaimedBytes, removedFiles = removedCount)
    }

    override suspend fun evict(policy: CloudCachePolicy): CacheClearResult = withContext(Dispatchers.IO) {
        var reclaimed = 0L
        var removed = 0
        val currentCacheBytes = storageManager.calculateDirectoryBytes(storageManager.cachePhotosDir) +
                storageManager.calculateDirectoryBytes(storageManager.cacheVideosDir)

        val evictableItems = cacheDao.getEvictableItems()
        val expiryThreshold = System.currentTimeMillis() - (policy.evictCachedAfterDays.toLong() * 24 * 60 * 60 * 1000)

        var runningBytes = currentCacheBytes

        for (item in evictableItems) {
            val lastAccess = item.lastAccessedAtMillis ?: 0L
            val isExpired = lastAccess > 0 && lastAccess < expiryThreshold
            val isOverCapacity = runningBytes > policy.maxCacheBytes

            if (isExpired || isOverCapacity) {
                val path = item.localCachedPath
                if (path.isNullOrEmpty()) {
                    cacheDao.clearLocalCopyState(item.cacheId, System.currentTimeMillis())
                    continue
                }
                val file = File(path)
                if (!file.exists()) {
                    // Clean up orphaned DB cache state if file was already removed from disk
                    cacheDao.clearLocalCopyState(item.cacheId, System.currentTimeMillis())
                    continue
                }
                val actualFileBytes = file.length()
                if (storageManager.deleteCachedFileSafely(path)) {
                    val reclaimedSize = resolveEvictedBytes(actualFileBytes, item.originalSizeBytes)
                    reclaimed += reclaimedSize
                    removed++
                    runningBytes = (runningBytes - reclaimedSize).coerceAtLeast(0L)
                    cacheDao.clearLocalCopyState(
                        cacheId = item.cacheId,
                        accessedTime = System.currentTimeMillis(),
                    )
                }
            }
        }

        CacheClearResult(reclaimedBytes = reclaimed, removedFiles = removed)
    }

    companion object {
        internal fun resolveEvictedBytes(actualFileBytes: Long, fallbackOriginalBytes: Long): Long =
            if (actualFileBytes > 0L) actualFileBytes else fallbackOriginalBytes.coerceAtLeast(0L)
    }
}
