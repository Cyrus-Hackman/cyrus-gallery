package com.cyrus.gallery.data.repository

import android.content.Context
import com.cyrus.gallery.data.cache.PrivateStorageManager
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OneDriveRepository @Inject constructor(
    private val graphClient: OneDriveGraphClient,
    private val cacheDao: CloudMediaCacheDao,
    private val storageManager: PrivateStorageManager,
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

    private val syncMutex = Mutex()
    @Volatile
    private var syncEpoch = 0L

    private var currentDeltaLink: String? = null

    companion object {
        private const val PREF_DELTA_LINK = "delta_link"
        private const val PREF_CLASSIFIER_V2_MIGRATED = "classifier_v2_migrated"
    }

    init {
        // One-time reset of pre-classifier delta cursor so previously skipped media types are discovered cleanly
        if (!prefs.getBoolean(PREF_CLASSIFIER_V2_MIGRATED, false)) {
            prefs.edit()
                .remove(PREF_DELTA_LINK)
                .putBoolean(PREF_CLASSIFIER_V2_MIGRATED, true)
                .apply()
            currentDeltaLink = null
        } else {
            // Restore delta token so sync continues incrementally across app restarts
            currentDeltaLink = prefs.getString(PREF_DELTA_LINK, null)
        }

        // Hydrate from Room cache immediately on repository creation using structured appScope
        appScope.launch(Dispatchers.IO) {
            try {
                val cachedEntities = cacheDao.getRecent(5000)
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

    private fun GraphDriveItem.toMediaItem(albumIdOverride: String? = parentFolderId): MediaItem {
        val mediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE
        return MediaItem(
            id = "onedrive:$id",
            displayName = name,
            type = mediaType,
            source = MediaSource.ONEDRIVE,
            mimeType = mimeType,
            createdAt = createdAt,
            modifiedAt = createdAt,
            oneDriveItemId = id,
            thumbnailUrl = thumbnailUrl,
            highResUrl = downloadUrl ?: "https://graph.microsoft.com/v1.0/me/drive/items/$id/content",
            cloudStorageState = CloudStorageState.REMOTE_ONLY,
            albumId = albumIdOverride,
        )
    }

    /**
     * Performs a complete delta sync, following all paged nextLinks in a loop so the
     * full OneDrive library is discovered in a single sync operation.
     *
     * @param forceFullSync If true, ignores the saved delta cursor and re-scans the full drive from root.
     */
    suspend fun performDeltaSync(forceFullSync: Boolean = false): Result<Int> = syncMutex.withLock {
        val startEpoch = syncEpoch
        if (forceFullSync) {
            currentDeltaLink = null
            prefs.edit().remove(PREF_DELTA_LINK).apply()
        }
        var nextLinkOrToken = currentDeltaLink
        var totalSynced = 0
        val existingCacheById = cacheDao.getAll().associateBy { it.cacheId }.toMutableMap()

        while (true) {
            if (syncEpoch != startEpoch) return@withLock Result.success(0)

            val deltaResult = graphClient.getDelta(nextLinkOrToken)
            if (syncEpoch != startEpoch) return@withLock Result.success(0)

            if (deltaResult.isFailure) {
                val errorMsg = deltaResult.exceptionOrNull()?.message.orEmpty()
                if (errorMsg.contains("resyncRequired") || errorMsg.contains("410") || errorMsg.contains("invalidRequest") || errorMsg.contains("badArgument") || errorMsg.contains("400")) {
                    currentDeltaLink = null
                    prefs.edit().remove(PREF_DELTA_LINK).apply()
                }
                return@withLock if (totalSynced > 0) {
                    Result.success(totalSynced)
                } else {
                    Result.failure(deltaResult.exceptionOrNull() ?: Exception("Delta sync failed"))
                }
            }

            val result = deltaResult.getOrThrow()

            // Process additions and modifications for the current page
            val newMedia = mutableListOf<MediaItem>()
            val newFolders = mutableListOf<Album>()
            val entitiesToUpsert = mutableListOf<CloudMediaCacheEntity>()
            val syncTimeMillis = System.currentTimeMillis()

            for (item in result.items) {
                if (item.isFolder) {
                    newFolders.add(
                        Album(
                            id = item.id,
                            name = item.name,
                            coverThumbnailUrl = item.thumbnailUrl,
                            source = MediaSource.ONEDRIVE,
                            parentFolderId = item.parentFolderId,
                        ),
                    )
                } else if (item.isImage || item.isVideo) {
                    newMedia.add(item.toMediaItem())

                    val cacheId = "onedrive_${item.id}"
                    val existing = existingCacheById[cacheId]
                    val localFileValid = existing != null &&
                        existing.eTag == item.eTag &&
                        !existing.localCachedPath.isNullOrEmpty() &&
                        File(existing.localCachedPath).exists()

                    val entity = CloudMediaCacheEntity(
                        cacheId = cacheId,
                        oneDriveItemId = item.id,
                        driveId = item.parentFolderId ?: "root",
                        fileName = item.name,
                        mimeType = item.mimeType,
                        originalSizeBytes = item.sizeBytes,
                        localCachedPath = if (localFileValid) existing?.localCachedPath else null,
                        thumbnailCachedPath = item.thumbnailUrl,
                        storageState = if (localFileValid) existing!!.storageState else CloudStorageState.REMOTE_ONLY,
                        downloadStatus = if (localFileValid) existing!!.downloadStatus else CloudDownloadStatus.NOT_DOWNLOADED,
                        downloadProgress = if (localFileValid) existing!!.downloadProgress else 0,
                        downloadedAtMillis = if (localFileValid) existing?.downloadedAtMillis else null,
                        lastAccessedAtMillis = existing?.lastAccessedAtMillis,
                        lastSynchronizedAtMillis = syncTimeMillis,
                        createdAtMillis = item.createdAt?.toEpochMilli(),
                        eTag = item.eTag,
                        cTag = item.cTag,
                    )
                    entitiesToUpsert.add(entity)
                    existingCacheById[cacheId] = entity
                }
            }

            if (syncEpoch != startEpoch) return@withLock Result.success(0)

            // Batch Room mutations per page to avoid emitting invalidations on every single item
            if (entitiesToUpsert.isNotEmpty()) {
                cacheDao.upsertAll(entitiesToUpsert)
            }
            if (result.deletedIds.isNotEmpty()) {
                result.deletedIds.map { "onedrive_$it" }.chunked(500).forEach { chunk ->
                    cacheDao.removeRecords(chunk)
                    chunk.forEach { existingCacheById.remove(it) }
                }
            }

            totalSynced += newMedia.size

            // Progressively update in-memory lists after each page so UI updates live
            _cloudItems.value = (newMedia + _cloudItems.value.filter { it.oneDriveItemId !in result.deletedIds })
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt ?: java.time.Instant.EPOCH }

            _cloudFolders.value = (newFolders + _cloudFolders.value.filter { it.id !in result.deletedIds })
                .distinctBy { it.id }

            if (!result.nextLink.isNullOrEmpty()) {
                // Continue fetching subsequent pages
                nextLinkOrToken = result.nextLink
            } else {
                // Completed all pages; persist deltaLink for incremental updates
                currentDeltaLink = result.deltaLink
                if (!result.deltaLink.isNullOrEmpty()) {
                    prefs.edit().putString(PREF_DELTA_LINK, result.deltaLink).apply()
                }
                break
            }
        }

        Result.success(totalSynced)
    }

    /** Clears in-memory, persisted sync state, and cached cloud records on sign-out. */
    fun clearSyncState() {
        syncEpoch++
        currentDeltaLink = null
        prefs.edit().remove(PREF_DELTA_LINK).apply()
        _cloudItems.value = emptyList()
        _cloudFolders.value = emptyList()
        appScope.launch(Dispatchers.IO) {
            cacheDao.clearAll()
            storageManager.clearAllTemporaryCache()
        }
    }

    /** Browse items within a specific OneDrive folder with pagination. */
    suspend fun browseFolder(folderId: String, pageToken: String? = null): Result<List<MediaItem>> {
        val result = graphClient.getFolderChildren(folderId, pageToken)
        if (result.isFailure) return Result.failure(result.exceptionOrNull() ?: Exception("Folder browse failed"))

        val paged = result.getOrThrow()
        val media = paged.items
            .filter { it.isImage || it.isVideo }
            .map { it.toMediaItem(albumIdOverride = folderId) }
            .sortedByDescending { it.createdAt ?: java.time.Instant.EPOCH }
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

    suspend fun uploadMedia(fileName: String, bytes: ByteArray, mimeType: String): Result<MediaItem> =
        uploadStream(fileName, bytes.size.toLong(), mimeType) { ByteArrayInputStream(bytes) }

    suspend fun uploadStream(
        fileName: String,
        contentLength: Long,
        mimeType: String,
        openStream: () -> InputStream?,
    ): Result<MediaItem> {
        val result = graphClient.uploadStream(fileName, contentLength, mimeType, openStream)
        if (result.isFailure) return Result.failure(result.exceptionOrNull() ?: Exception("Upload failed"))

        val item = result.getOrThrow()
        val mediaItem = item.toMediaItem()
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
        _cloudItems.value = (listOf(mediaItem) + _cloudItems.value)
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt ?: java.time.Instant.EPOCH }
        return Result.success(mediaItem)
    }
}
