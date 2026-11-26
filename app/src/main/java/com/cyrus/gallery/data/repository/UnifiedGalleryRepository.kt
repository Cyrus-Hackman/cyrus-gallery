package com.cyrus.gallery.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.cyrus.gallery.data.database.CloudMediaCacheDao
import com.cyrus.gallery.data.database.CloudMediaCacheEntity
import com.cyrus.gallery.data.database.FavoriteMediaDao
import com.cyrus.gallery.data.database.FavoriteMediaEntity
import com.cyrus.gallery.domain.model.Album
import com.cyrus.gallery.domain.model.CloudStorageState
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.MediaSource
import com.cyrus.gallery.domain.repository.GalleryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedGalleryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localRepository: LocalGalleryRepository,
    private val oneDriveRepository: OneDriveRepository,
    private val cacheDao: CloudMediaCacheDao,
    private val favoriteDao: FavoriteMediaDao,
) : GalleryRepository {

    override fun observeMedia(): Flow<List<MediaItem>> {
        return combine(
            localRepository.observeMedia(),
            oneDriveRepository.cloudItems,
            cacheDao.observeAll(),
            favoriteDao.observeFavoriteIds(),
        ) { localItems, cloudItems, cachedEntities, favoriteIds ->
            val favSet = favoriteIds.toSet()
            val enrichedCloudItems = enrichCloudItems(cloudItems, cachedEntities, favSet)
            mergeUnifiedMedia(localItems, enrichedCloudItems, favSet)
        }
    }

    override fun observeFavoriteMedia(): Flow<List<MediaItem>> {
        return combine(
            localRepository.observeMedia(),
            oneDriveRepository.cloudItems,
            cacheDao.observeAll(),
            favoriteDao.observeFavoriteIds(),
        ) { localItems, cloudItems, cachedEntities, favoriteIds ->
            val favSet = favoriteIds.toSet()
            if (favSet.isEmpty()) return@combine emptyList()
            val favCloud = enrichCloudItems(
                cloudItems.filter { it.id in favSet },
                cachedEntities,
                favSet,
            )
            buildFavoriteMedia(localItems, favCloud, favSet)
        }
    }

    override fun observeCloudMedia(): Flow<List<MediaItem>> {
        return combine(
            oneDriveRepository.cloudItems,
            cacheDao.observeAll(),
            favoriteDao.observeFavoriteIds(),
        ) { cloudItems, cachedEntities, favoriteIds ->
            enrichCloudItems(cloudItems, cachedEntities, favoriteIds.toSet())
        }
    }

    private fun enrichCloudItems(
        cloudItems: List<MediaItem>,
        cachedEntities: List<CloudMediaCacheEntity>,
        favSet: Set<String>,
    ): List<MediaItem> {
        val cacheEntityMap = cachedEntities.associateBy { it.oneDriveItemId }
        return cloudItems.map { cloudItem ->
            val entity = cloudItem.oneDriveItemId?.let { cacheEntityMap[it] }
            val isFav = cloudItem.id in favSet
            if (entity != null) {
                val localFile = entity.localCachedPath?.let { File(it) }
                val hasLocalFile = localFile != null && localFile.exists()

                cloudItem.copy(
                    cloudStorageState = if (hasLocalFile) entity.storageState else CloudStorageState.REMOTE_ONLY,
                    localContentUri = if (hasLocalFile) Uri.fromFile(localFile) else null,
                    thumbnailUrl = cloudItem.thumbnailUrl?.takeIf { it.isNotBlank() }
                        ?: entity.thumbnailCachedPath?.takeIf { it.isNotBlank() },
                    highResUrl = cloudItem.highResUrl ?: "https://graph.microsoft.com/v1.0/me/drive/items/${cloudItem.oneDriveItemId}/content",
                    isFavourite = isFav,
                )
            } else {
                cloudItem.copy(isFavourite = isFav)
            }
        }
    }

    override fun observeAlbums(): Flow<List<Album>> {
        return combine(
            localRepository.observeAlbums(),
            oneDriveRepository.cloudFolders,
            oneDriveRepository.cloudItems,
        ) { localAlbums, cloudFolders, cloudItems ->
            val folderById = cloudFolders.associateBy { it.id }
            val counts = mutableMapOf<String, Int>()
            val covers = mutableMapOf<String, String>()

            // Roll up item counts and cover thumbnails across nested OneDrive folder ancestors
            for (item in cloudItems) {
                var currentFolderId = item.albumId
                val visited = mutableSetOf<String>()
                while (currentFolderId != null && visited.add(currentFolderId)) {
                    counts[currentFolderId] = (counts[currentFolderId] ?: 0) + 1
                    if (currentFolderId !in covers && !item.thumbnailUrl.isNullOrEmpty()) {
                        covers[currentFolderId] = item.thumbnailUrl
                    }
                    currentFolderId = folderById[currentFolderId]?.parentFolderId
                }
            }

            val enrichedCloudFolders = cloudFolders.map { folder ->
                folder.copy(
                    itemCount = counts[folder.id] ?: folder.itemCount,
                    coverThumbnailUrl = covers[folder.id] ?: folder.coverThumbnailUrl,
                )
            }
            localAlbums + enrichedCloudFolders
        }
    }

    override suspend fun setFavorite(mediaId: String, isFavorite: Boolean): Unit = withContext(Dispatchers.IO) {
        if (isFavorite) {
            favoriteDao.addFavorite(FavoriteMediaEntity(mediaId = mediaId))
        } else {
            favoriteDao.removeFavorite(mediaId = mediaId)
        }
    }

    override suspend fun deleteLocalMedia(mediaItem: MediaItem): Result<Unit> {
        return localRepository.deleteLocalMedia(mediaItem)
    }

    override suspend fun deleteCloudMedia(mediaItem: MediaItem): Result<Unit> {
        val itemId = mediaItem.oneDriveItemId
            ?: return Result.failure(IllegalArgumentException("Item is not a OneDrive cloud media item"))
        return oneDriveRepository.deleteCloudItem(itemId)
    }

    override suspend fun uploadToOneDrive(mediaItem: MediaItem): Result<String> = withContext(Dispatchers.IO) {
        val uri = mediaItem.localContentUri
            ?: return@withContext Result.failure(IllegalArgumentException("No local file URI to upload"))
        val sizeBytes = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (col >= 0 && cursor.moveToFirst()) cursor.getLong(col) else -1L
        } ?: -1L

        val result = oneDriveRepository.uploadStream(
            fileName = mediaItem.displayName,
            contentLength = sizeBytes,
            mimeType = mediaItem.mimeType,
            openStream = { context.contentResolver.openInputStream(uri) },
        )
        result.map { it.id }
    }
}
