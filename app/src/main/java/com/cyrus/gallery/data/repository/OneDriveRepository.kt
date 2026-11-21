package com.cyrus.gallery.data.repository

import android.content.Context
import com.cyrus.gallery.data.database.CloudDownloadStatus
import com.cyrus.gallery.data.database.CloudMediaCacheDao
import com.cyrus.gallery.data.database.CloudMediaCacheEntity
import com.cyrus.gallery.data.remote.graph.GraphDriveItem
import com.cyrus.gallery.data.remote.graph.OneDriveGraphClient
import com.cyrus.gallery.di.ApplicationScope
import com.cyrus.gallery.domain.model.Album
import com.cyrus.gallery.domain.model.CloudStorageState
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.MediaSource
import com.cyrus.gallery.domain.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OneDriveRepository @Inject constructor(
    private val graphClient: OneDriveGraphClient,
    private val cacheDao: CloudMediaCacheDao,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val _cloudItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val cloudItems: Flow<List<MediaItem>> = _cloudItems.asStateFlow()

    private val _cloudFolders = MutableStateFlow<List<Album>>(emptyList())
    val cloudFolders: Flow<List<Album>> = _cloudFolders.asStateFlow()

    private val _isHydrating = MutableStateFlow(true)
    val isHydrating: StateFlow<Boolean> = _isHydrating.asStateFlow()

    private val prefs by lazy {
        context.getSharedPreferences("onedrive_sync_prefs", Context.MODE_PRIVATE)
    }

    private var currentDeltaLink: String? = null

    init {
        // Restore delta token so sync continues incrementally across app restarts
        currentDeltaLink = prefs.getString("delta_link", null)

        // Hydrate from Room cache immediately on repository creation using structured appScope
        appScope.launch(Dispatchers.IO) {
            try {
                val cachedEntities = cacheDao.getRecent(500)
                if (cachedEntities.isNotEmpty() && _cloudItems.value.isEmpty()) {
                    _cloudItems.value = cachedEntities.map { entity ->
                        val isVideo = entity.mimeType.startsWith("video/")
                        val itemDate = (entity.createdAtMillis ?: entity.lastSynchronizedAtMillis)?.let { java.time.Instant.ofEpochMilli(it) }
                        MediaItem(
                            id = "onedrive:${entity.oneDriveItemId}",
                            displayName = entity.fileName,
                            type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                            source = MediaSource.ONEDRIVE,
                            mimeType = entity.mimeType,
                            createdAt = itemDate,
                            modifiedAt = itemDate,
                            oneDriveItemId = entity.oneDriveItemId,
                            thumbnailUrl = entity.thumbnailCachedPath
                                ?: "https://graph.microsoft.com/v1.0/me/drive/items/${entity.oneDriveItemId}/thumbnails/0/large/content",
                            highResUrl = "https://graph.microsoft.com/v1.0/me/drive/items/${entity.oneDriveItemId}/content",
                            cloudStorageState = entity.storageState,
                            albumId = entity.driveId.takeIf { it != "root" },
                        )
                    }.sortedByDescending { it.createdAt ?: java.time.Instant.EPOCH }
                }
            } finally {
                _isHydrating.value = false
            }
        }
    }

    /**
     * Performs a complete delta sync, following all paged nextLinks in a loop so the
     * full OneDrive library is discovered in a single sync operation.
     *
     * @param forceFullSync If true, ignores the saved delta cursor and re-scans the full drive from root.
     */
    suspend fun performDeltaSync(forceFullSync: Boolean = false): Result<Int> {
        if (forceFullSync) {
            currentDeltaLink = null
            prefs.edit().remove("delta_link").apply()
        }
        var nextLinkOrToken = currentDeltaLink
        var totalSynced = 0

        while (true) {
            val deltaResult = graphClient.getDelta(nextLinkOrToken)
            if (deltaResult.isFailure) {
                val errorMsg = deltaResult.exceptionOrNull()?.message.orEmpty()
                if (errorMsg.contains("resyncRequired") || errorMsg.contains("410") || errorMsg.contains("invalidRequest") || errorMsg.contains("badArgument") || errorMsg.contains("400")) {
                    currentDeltaLink = null
                    prefs.edit().remove("delta_link").apply()
                }
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
                        albumId = item.parentFolderId,
                    )
                    newMedia.add(mediaItem)

                    // Record in Room cache entity with thumbnail path and accurate creation timestamp
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
                            createdAtMillis = item.createdAt?.toEpochMilli(),
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
                if (!result.deltaLink.isNullOrEmpty()) {
                    prefs.edit().putString("delta_link", result.deltaLink).apply()
                }
                break
            }
        }

        return Result.success(totalSynced)
    }

    /** Clears in-memory and persisted sync state on sign-out. */
    fun clearSyncState() {
        currentDeltaLink = null
        prefs.edit().remove("delta_link").apply()
        _cloudItems.value = emptyList()
        _cloudFolders.value = emptyList()
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
            }.sortedByDescending { it.createdAt ?: java.time.Instant.EPOCH }
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
