package com.cyrus.gallery.data.repository

import android.net.Uri
import com.cyrus.gallery.data.database.CloudMediaCacheDao
import com.cyrus.gallery.domain.model.Album
import com.cyrus.gallery.domain.model.CloudStorageState
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.MediaSource
import com.cyrus.gallery.domain.repository.GalleryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedGalleryRepository @Inject constructor(
    private val localRepository: LocalGalleryRepository,
    private val oneDriveRepository: OneDriveRepository,
    private val cacheDao: CloudMediaCacheDao,
) : GalleryRepository {

    override fun observeMedia(): Flow<List<MediaItem>> {
        return combine(
            localRepository.observeMedia(),
            oneDriveRepository.cloudItems,
            cacheDao.observeAll(),
        ) { localItems, cloudItems, cachedEntities ->
            val cacheEntityMap = cachedEntities.associateBy { it.oneDriveItemId }

            // Enrich cloud items with their current local cache state and local URI
            val enrichedCloudItems = cloudItems.map { cloudItem ->
                val entity = cloudItem.oneDriveItemId?.let { cacheEntityMap[it] }
                if (entity != null) {
                    val localFile = entity.localCachedPath?.let { File(it) }
                    val hasLocalFile = localFile != null && localFile.exists()

                    cloudItem.copy(
                        cloudStorageState = if (hasLocalFile) entity.storageState else CloudStorageState.REMOTE_ONLY,
                        localContentUri = if (hasLocalFile) Uri.fromFile(localFile) else null,
                        thumbnailUrl = cloudItem.thumbnailUrl ?: entity.thumbnailCachedPath,
                    )
                } else {
                    cloudItem
                }
            }

            // Deduplicate local vs cloud copies of the exact same media
            val localNameSet = localItems.map { it.displayName.lowercase() }.toSet()

            // Keep all local items, plus cloud-only items that do not duplicate a local original
            val distinctCloudItems = enrichedCloudItems.filter { cloudItem ->
                cloudItem.displayName.lowercase() !in localNameSet
            }

            // Merge into unified newest-first stream
            (localItems + distinctCloudItems).sortedByDescending {
                it.createdAt ?: it.modifiedAt ?: java.time.Instant.EPOCH
            }
        }
    }

    override fun observeAlbums(): Flow<List<Album>> {
        return combine(
            localRepository.observeAlbums(),
            oneDriveRepository.cloudFolders,
        ) { localAlbums, cloudFolders ->
            // Merge local device albums and OneDrive cloud folders into one clean list
            localAlbums + cloudFolders
        }
    }
}
