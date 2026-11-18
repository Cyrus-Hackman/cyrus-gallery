package com.cyrus.gallery.data.repository

import com.cyrus.gallery.data.database.CloudDownloadStatus
import com.cyrus.gallery.data.database.CloudMediaCacheDao
import com.cyrus.gallery.data.database.CloudMediaCacheEntity
import com.cyrus.gallery.data.remote.graph.GraphDriveItem
import com.cyrus.gallery.data.remote.graph.OneDriveGraphClient
import com.cyrus.gallery.domain.model.Album
import com.cyrus.gallery.domain.model.CloudStorageState
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.MediaSource
import com.cyrus.gallery.domain.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OneDriveRepository @Inject constructor(
    private val graphClient: OneDriveGraphClient,
    private val cacheDao: CloudMediaCacheDao,
) {
    private val _cloudItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val cloudItems: Flow<List<MediaItem>> = _cloudItems.asStateFlow()

    private val _cloudFolders = MutableStateFlow<List<Album>>(emptyList())
    val cloudFolders: Flow<List<Album>> = _cloudFolders.asStateFlow()

    private var currentDeltaLink: String? = null

    /** Performs an incremental delta sync from OneDrive root to discover media and deletions. */
    suspend fun performDeltaSync(): Result<Int> {
        val deltaResult = graphClient.getDelta(currentDeltaLink)
        if (deltaResult.isFailure) {
            return Result.failure(deltaResult.exceptionOrNull() ?: Exception("Delta sync failed"))
        }

        val result = deltaResult.getOrThrow()
        currentDeltaLink = result.deltaLink ?: result.nextLink

        // Process additions and modifications
        val newMedia = mutableListOf<MediaItem>()
        val newFolders = mutableListOf<Album>()

        for (item in result.items) {
            if (item.isFolder) {
                newFolders.add(
                    Album(
                        id = item.id,
                        name = item.name,
                        coverThumbnailUrl = item.thumbnailUrl,
                        source = MediaSource.ONEDRIVE,
                    ),
                )
            } else if (item.isImage || item.isVideo) {
                val mediaType = if (item.isVideo) MediaType.VIDEO else MediaType.IMAGE
                val mediaItem = MediaItem(
                    id = "onedrive:${item.id}",
                    displayName = item.name,
                    type = mediaType,
                    source = MediaSource.ONEDRIVE,
                    mimeType = item.mimeType,
                    createdAt = item.createdAt,
                    modifiedAt = item.createdAt,
                    oneDriveItemId = item.id,
                    thumbnailUrl = item.thumbnailUrl,
                    cloudStorageState = CloudStorageState.REMOTE_ONLY,
                )
                newMedia.add(mediaItem)

                // Record in Room cache entity
                cacheDao.upsert(
                    CloudMediaCacheEntity(
                        cacheId = "onedrive_${item.id}",
                        oneDriveItemId = item.id,
                        driveId = item.parentFolderId ?: "root",
                        fileName = item.name,
                        mimeType = item.mimeType,
                        originalSizeBytes = item.sizeBytes,
                        storageState = CloudStorageState.REMOTE_ONLY,
                        downloadStatus = CloudDownloadStatus.NOT_DOWNLOADED,
                        eTag = item.eTag,
                        cTag = item.cTag,
                        lastSynchronizedAtMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }

        // Process deletions
        for (deletedId in result.deletedIds) {
            cacheDao.removeRecord("onedrive_$deletedId")
        }

        _cloudItems.value = (newMedia + _cloudItems.value.filter { it.oneDriveItemId !in result.deletedIds })
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt ?: java.time.Instant.EPOCH }

        _cloudFolders.value = newFolders.distinctBy { it.id }

        return Result.success(newMedia.size)
    }

    /** Browse items within a specific OneDrive folder with pagination. */
    suspend fun browseFolder(folderId: String, pageToken: String? = null): Result<List<MediaItem>> {
        val result = graphClient.getFolderChildren(folderId, pageToken)
        if (result.isFailure) return Result.failure(result.exceptionOrNull() ?: Exception("Folder browse failed"))

        val paged = result.getOrThrow()
        val media = paged.items
            .filter { it.isImage || it.isVideo }
            .map { item ->
                MediaItem(
                    id = "onedrive:${item.id}",
                    displayName = item.name,
                    type = if (item.isVideo) MediaType.VIDEO else MediaType.IMAGE,
                    source = MediaSource.ONEDRIVE,
                    mimeType = item.mimeType,
                    createdAt = item.createdAt,
                    modifiedAt = item.createdAt,
                    oneDriveItemId = item.id,
                    thumbnailUrl = item.thumbnailUrl,
                    cloudStorageState = CloudStorageState.REMOTE_ONLY,
                    albumId = folderId,
                )
            }
        return Result.success(media)
    }

    suspend fun deleteCloudItem(itemId: String): Result<Unit> {
        val result = graphClient.deleteItem(itemId)
        if (result.isSuccess) {
            cacheDao.removeRecord("onedrive_$itemId")
            _cloudItems.value = _cloudItems.value.filter { it.oneDriveItemId != itemId }
        }
        return result
    }

    suspend fun uploadMedia(fileName: String, bytes: ByteArray, mimeType: String): Result<MediaItem> {
        val result = graphClient.uploadItem(fileName, bytes, mimeType)
        if (result.isFailure) return Result.failure(result.exceptionOrNull() ?: Exception("Upload failed"))

        val item = result.getOrThrow()
        val mediaItem = MediaItem(
            id = "onedrive:${item.id}",
            displayName = item.name,
            type = if (item.isVideo) MediaType.VIDEO else MediaType.IMAGE,
            source = MediaSource.ONEDRIVE,
            mimeType = item.mimeType,
            createdAt = item.createdAt,
            modifiedAt = item.createdAt,
            oneDriveItemId = item.id,
            thumbnailUrl = item.thumbnailUrl,
            cloudStorageState = CloudStorageState.REMOTE_ONLY,
        )
        _cloudItems.value = (_cloudItems.value + mediaItem).sortedByDescending { it.createdAt ?: java.time.Instant.EPOCH }
        return Result.success(mediaItem)
    }
}
