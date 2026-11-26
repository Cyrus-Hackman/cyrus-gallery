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
import java.io.ByteArrayInputStream
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

    private val foldersCacheFile by lazy {
        File(context.filesDir, "onedrive_folders_cache.json")
    }

    private val syncMutex = Mutex()
    @Volatile
    private var syncEpoch = 0L

    private var currentDeltaLink: String? = null

    companion object {
        private const val PREF_DELTA_LINK = "delta_link"
        private const val PREF_CLASSIFIER_V2_MIGRATED = "classifier_v2_migrated"
        private const val PREF_FOLDERS_V1_MIGRATED = "folders_v1_migrated"
    }

    init {
        // One-time reset of legacy delta cursors so skipped media types and unpersisted folders are discovered cleanly
        val needsClassifierMigration = !prefs.getBoolean(PREF_CLASSIFIER_V2_MIGRATED, false)
        val needsFoldersMigration = !prefs.getBoolean(PREF_FOLDERS_V1_MIGRATED, false)
        if (needsClassifierMigration || needsFoldersMigration) {
            prefs.edit()
                .remove(PREF_DELTA_LINK)
                .putBoolean(PREF_CLASSIFIER_V2_MIGRATED, true)
                .putBoolean(PREF_FOLDERS_V1_MIGRATED, true)
                .apply()
            currentDeltaLink = null
        } else {
            // Restore delta token so sync continues incrementally across app restarts
            currentDeltaLink = prefs.getString(PREF_DELTA_LINK, null)
        }

        // Hydrate items and folders from local caches immediately on startup before marking hydration complete
        appScope.launch(Dispatchers.IO) {
            try {
                val cachedFolders = OneDriveFolderCacheCodec.readFromFile(foldersCacheFile)
                if (cachedFolders.isNotEmpty() && _cloudFolders.value.isEmpty()) {
                    _cloudFolders.value = cachedFolders
                }
                val cachedEntities = cacheDao.getRecent(5000)
                if (cachedEntities.isNotEmpty() && _cloudItems.value.isEmpty()) {
                    _cloudItems.value = cachedEntities.map { entity ->
                        val isVideo = entity.mimeType.startsWith("video/")
                        val itemDate = (entity.createdAtMillis ?: entity.lastSynchronizedAtMillis)?.let { java.time.Instant.ofEpochMilli(it) }
                        val folderName = entity.driveId.takeIf { it.isNotBlank() && it != "root" }?.let { id ->
                            _cloudFolders.value.find { it.id == id }?.name
                        }
                        MediaItem(
                            id = "onedrive:${entity.oneDriveItemId}",
                            displayName = entity.fileName,
                            type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                            source = MediaSource.ONEDRIVE,
                            mimeType = entity.mimeType,
                            createdAt = itemDate,
                            modifiedAt = itemDate,
                            oneDriveItemId = entity.oneDriveItemId,
                            thumbnailUrl = entity.thumbnailCachedPath?.takeIf { it.isNotBlank() }
                                ?: "https://graph.microsoft.com/v1.0/me/drive/items/${entity.oneDriveItemId}/thumbnails/0/large/content",
                            highResUrl = "https://graph.microsoft.com/v1.0/me/drive/items/${entity.oneDriveItemId}/content",
                            cloudStorageState = entity.storageState,
                            albumId = entity.driveId.takeIf { it.isNotBlank() && it != "root" },
                            albumName = folderName,
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
        val resolvedAlbumName = albumIdOverride?.let { folderId ->
            _cloudFolders.value.find { it.id == folderId }?.name
        }
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
            durationMillis = durationMillis,
            albumId = albumIdOverride,
            albumName = resolvedAlbumName,
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
        val isFullSyncPass = nextLinkOrToken == null
        var totalSynced = 0
        val existingCacheById = cacheDao.getAll().associateBy { it.cacheId }.toMutableMap()
        val fullSyncFolders = mutableListOf<Album>()

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
                if (item.isFolder && !(item.parentFolderId.isNullOrEmpty() && item.name.equals("root", ignoreCase = true))) {
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

                    // For pinned items whose eTag changed, preserve the pinned state and cached file
                    // path until the worker downloads the updated version. Without this guard, a delta
                    // sync page completing mid-download would overwrite storageState → REMOTE_ONLY and
                    // localCachedPath → null, losing the offline copy reference entirely.
                    val isPinnedOffline = existing?.storageState == CloudStorageState.PINNED_OFFLINE
                    val preservePinnedFile = isPinnedOffline &&
                        !existing?.localCachedPath.isNullOrEmpty() &&
                        File(existing!!.localCachedPath!!).exists()

                    val entity = CloudMediaCacheEntity(
                        cacheId = cacheId,
                        oneDriveItemId = item.id,
                        driveId = item.parentFolderId ?: "root",
                        fileName = item.name,
                        mimeType = item.mimeType,
                        originalSizeBytes = item.sizeBytes,
                        localCachedPath = if (localFileValid || preservePinnedFile) existing?.localCachedPath else null,
                        thumbnailCachedPath = item.thumbnailUrl,
                        storageState = if (localFileValid || preservePinnedFile) existing!!.storageState else CloudStorageState.REMOTE_ONLY,
                        downloadStatus = if (localFileValid || preservePinnedFile) existing!!.downloadStatus else CloudDownloadStatus.NOT_DOWNLOADED,
                        downloadProgress = if (localFileValid || preservePinnedFile) existing!!.downloadProgress else 0,
                        downloadedAtMillis = if (localFileValid || preservePinnedFile) existing?.downloadedAtMillis else null,
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
            val deletedIdSet = result.deletedIds.toSet()
            _cloudItems.value = (newMedia + _cloudItems.value.filter { it.oneDriveItemId !in deletedIdSet })
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt ?: java.time.Instant.EPOCH }

            val mergedFolders = OneDriveFolderCacheCodec.mergeSyncedFolders(
                existing = _cloudFolders.value,
                incoming = newFolders,
                deletedIds = deletedIdSet,
            )
            val foldersChanged = mergedFolders != _cloudFolders.value
            _cloudFolders.value = mergedFolders
            if (isFullSyncPass) {
                fullSyncFolders.addAll(newFolders)
            }

            if (!result.nextLink.isNullOrEmpty()) {
                if (foldersChanged) {
                    OneDriveFolderCacheCodec.writeToFileAtomically(foldersCacheFile, mergedFolders)
                }
                // Continue fetching subsequent pages
                nextLinkOrToken = result.nextLink
            } else {
                val finalFolders = if (isFullSyncPass) {
                    OneDriveFolderCacheCodec.mergeSyncedFolders(
                        existing = emptyList(),
                        incoming = fullSyncFolders,
                        deletedIds = deletedIdSet,
                    )
                } else {
                    mergedFolders
                }
                _cloudFolders.value = finalFolders
                if (foldersChanged || isFullSyncPass) {
                    OneDriveFolderCacheCodec.writeToFileAtomically(foldersCacheFile, finalFolders)
                }
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
            foldersCacheFile.delete()
            File(foldersCacheFile.parentFile, "${foldersCacheFile.name}.tmp").delete()
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
