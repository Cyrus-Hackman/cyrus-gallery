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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    init {
        // Hydrate from Room cache immediately on repository creation so cloud media
        // is available instantly on app launch without needing to re-sync
        CoroutineScope(Dispatchers.IO).launch {
            val cachedEntities = cacheDao.getAll()
            if (cachedEntities.isNotEmpty() && _cloudItems.value.isEmpty()) {
                _cloudItems.value = cachedEntities.map { entity ->
                    val isVideo = entity.mimeType.startsWith("video/")
                    MediaItem(
                        id = "onedrive:${entity.oneDriveItemId}",
                        displayName = entity.fileName,
                        type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                        source = MediaSource.ONEDRIVE,
                        mimeType = entity.mimeType,
                        createdAt = entity.lastSynchronizedAtMillis?.let { java.time.Instant.ofEpochMilli(it) },
                        modifiedAt = entity.lastSynchronizedAtMillis?.let { java.time.Instant.ofEpochMilli(it) },
                        oneDriveItemId = entity.oneDriveItemId,
                        thumbnailUrl = entity.thumbnailCachedPath
                            ?: "https://graph.microsoft.com/v1.0/me/drive/items/${entity.oneDriveItemId}/thumbnails/0/large/content",
                        highResUrl = "https://graph.microsoft.com/v1.0/me/drive/items/${entity.oneDriveItemId}/content",
                        cloudStorageState = entity.storageState,
                    )
                }
            }
        }
    }

    /**
     * Performs a complete delta sync, following all paged nextLinks in a loop so the
     * full OneDrive library is discovered in a single sync operation.
     */
    suspend fun performDeltaSync(): Result<Int> {
        var nextLinkOrToken = currentDeltaLink
        var totalSynced = 0

        while (true) {
            val deltaResult = graphClient.getDelta(nextLinkOrToken)
            if (deltaResult.isFailure) {
                return if (totalSynced > 0) {
                    Result.success(totalSynced)
                } else {
                    Result.failure(deltaResult.exceptionOrNull() ?: Exception("Delta sync failed"))
                }
            }

            val result = deltaResult.getOrThrow()

            // Process additions and modifications for the current page
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
                        highResUrl = item.downloadUrl ?: "https://graph.microsoft.com/v1.0/me/drive/items/${item.id}/content",
                        cloudStorageState = CloudStorageState.REMOTE_ONLY,
                    )
                    newMedia.add(mediaItem)

                    // Record in Room cache entity with thumbnail path
                    cacheDao.upsert(
                        CloudMediaCacheEntity(
                            cacheId = "onedrive_${item.id}",
                            oneDriveItemId = item.id,
                            driveId = item.parentFolderId ?: "root",
                            fileName = item.name,
                            mimeType = item.mimeType,
                            originalSizeBytes = item.sizeBytes,
                            thumbnailCachedPath = item.thumbnailUrl,
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

            totalSynced += newMedia.size

            // Progressively update in-memory list after each page so UI updates live
            _cloudItems.value = (newMedia + _cloudItems.value.filter { it.oneDriveItemId !in result.deletedIds })
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt ?: java.time.Instant.EPOCH }

            _cloudFolders.value = (_cloudFolders.value + newFolders).distinctBy { it.id }

            if (!result.nextLink.isNullOrEmpty()) {
                // Continue fetching subsequent pages
                nextLinkOrToken = result.nextLink
            } else {
                // Completed all pages; persist deltaLink for incremental updates
                currentDeltaLink = result.deltaLink
                break
            }
        }

        return Result.success(totalSynced)
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
                    highResUrl = item.downloadUrl ?: "https://graph.microsoft.com/v1.0/me/drive/items/${item.id}/content",
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
            highResUrl = item.downloadUrl ?: "https://graph.microsoft.com/v1.0/me/drive/items/${item.id}/content",
            cloudStorageState = CloudStorageState.REMOTE_ONLY,
        )
        _cloudItems.value = (_cloudItems.value + mediaItem).sortedByDescending { it.createdAt ?: java.time.Instant.EPOCH }
        return Result.success(mediaItem)
    }
}
