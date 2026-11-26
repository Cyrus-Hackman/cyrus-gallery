package com.cyrus.gallery.ui.viewer

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.DataSource
import com.cyrus.gallery.data.cache.PrivateStorageManager
import com.cyrus.gallery.data.database.FavoriteMediaDao
import com.cyrus.gallery.data.remote.graph.GraphOkHttpDataSourceFactory
import com.cyrus.gallery.data.repository.MediaDeletePermissionRequiredException
import com.cyrus.gallery.data.sync.OneDriveSyncManager
import com.cyrus.gallery.di.GraphMediaClient
import com.cyrus.gallery.domain.model.Album
import com.cyrus.gallery.domain.model.CloudStorageState
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.MediaSource
import com.cyrus.gallery.domain.model.collectDescendantFolderIds
import com.cyrus.gallery.domain.repository.CloudCacheRepository
import com.cyrus.gallery.domain.repository.GalleryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

object ViewerScopeRoutes {
    const val PHOTOS = "photos"
    const val ALBUMS = "albums"
    const val ONEDRIVE = "onedrive"
    const val FAVOURITES = "favourites"
}

data class ViewerSessionConfig(
    val sourceRoute: String,
    val selectedItemId: String,
    val searchQuery: String = "",
    val albumId: String? = null,
    val albumName: String? = null,
    val initialItems: List<MediaItem> = emptyList(),
)

data class PendingLocalDelete(
    val item: MediaItem,
    val intentSender: IntentSender,
    val requiresManualRetryAfterGrant: Boolean,
    val onDeleted: () -> Unit,
)

@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    private val repository: GalleryRepository,
    private val cacheRepository: CloudCacheRepository,
    private val syncManager: OneDriveSyncManager,
    private val storageManager: PrivateStorageManager,
    private val favoriteDao: FavoriteMediaDao,
    @GraphMediaClient graphHttpClient: OkHttpClient,
) : ViewModel() {

    val mediaDataSourceFactory: DataSource.Factory = GraphOkHttpDataSourceFactory(graphHttpClient)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _pendingLocalDelete = MutableStateFlow<PendingLocalDelete?>(null)
    val pendingLocalDelete: StateFlow<PendingLocalDelete?> = _pendingLocalDelete.asStateFlow()

    // Starts as null until Room emits so static callers avoid an un-favorited 1-frame flicker while still allowing un-favoriting
    val favoriteIds: StateFlow<Set<String>?> = favoriteDao.observeFavoriteIds()
        .map<List<String>, Set<String>?> { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun observeViewerItems(config: ViewerSessionConfig): Flow<List<MediaItem>> {
        return when (config.sourceRoute) {
            ViewerScopeRoutes.ONEDRIVE -> repository.observeCloudMedia().map { items ->
                items.filter { item ->
                    (config.albumId == null || item.albumId == config.albumId) &&
                        (config.searchQuery.isBlank() || item.matchesQuery(config.searchQuery))
                }
            }
            ViewerScopeRoutes.ALBUMS -> combine(
                repository.observeAlbums(),
                repository.observeMedia(),
                repository.observeCloudMedia(),
            ) { albums, allMedia, cloudMedia ->
                val selectedAlbum = config.albumId?.let { id -> albums.find { it.id == id } }
                if (selectedAlbum?.source == MediaSource.ONEDRIVE) {
                    val descendantIds = collectDescendantFolderIds(selectedAlbum.id, albums)
                    cloudMedia.filter { it.albumId in descendantIds }
                } else {
                    allMedia.filter { item ->
                        (config.albumId != null && item.albumId == config.albumId) ||
                            (config.albumName != null && item.albumName == config.albumName)
                    }
                }
            }
            ViewerScopeRoutes.FAVOURITES -> {
                var sessionItemIds = config.initialItems.map { it.id }.toSet().takeIf { it.isNotEmpty() }
                repository.observeMedia().map { items ->
                    val allowedIds = sessionItemIds ?: (
                        items.filter { it.isFavourite }.map { it.id }.toSet() + config.selectedItemId
                        ).also { sessionItemIds = it }
                    items.filter { it.id in allowedIds || it.isFavourite }
                }
            }
            else -> repository.observeMedia().map { items ->
                if (config.searchQuery.isNotBlank()) items.filter { it.matchesQuery(config.searchQuery) } else items
            }
        }
    }

    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch {
            val isFav = favoriteIds.value?.contains(item.id) ?: item.isFavourite
            repository.setFavorite(item.id, !isFav)
        }
    }

    fun togglePinned(item: MediaItem) {
        val itemId = item.oneDriveItemId ?: return
        viewModelScope.launch {
            val isCurrentlyPinned = item.cloudStorageState == CloudStorageState.PINNED_OFFLINE
            val targetPinned = !isCurrentlyPinned
            cacheRepository.setPinned(item.albumId ?: "root", itemId, targetPinned)
            if (targetPinned && item.localContentUri == null) {
                // Read the actual file size from Room so the download worker can validate the stream
                // length. Passing 0L skips size validation entirely, which masks truncated downloads.
                val entity = cacheRepository.findByItemId(itemId)
                syncManager.enqueueDownload(
                    itemId = itemId,
                    driveId = item.albumId ?: "root",
                    fileName = item.displayName,
                    mimeType = item.mimeType,
                    sizeBytes = entity?.originalSizeBytes ?: 0L,
                    isPinned = true,
                )
            }
            _statusMessage.value = if (targetPinned) "Pinned for offline viewing" else "Unpinned"
        }
    }

    fun downloadOnDemand(item: MediaItem) {
        val itemId = item.oneDriveItemId ?: return
        viewModelScope.launch {
            syncManager.enqueueDownload(
                itemId = itemId,
                driveId = item.albumId ?: "root",
                fileName = item.displayName,
                mimeType = item.mimeType,
                sizeBytes = 0L,
                isPinned = false,
            )
            _statusMessage.value = "Downloading ${item.displayName}..."
        }
    }

    fun uploadToOneDrive(item: MediaItem) {
        viewModelScope.launch {
            _statusMessage.value = "Uploading to OneDrive..."
            val result = repository.uploadToOneDrive(item)
            if (result.isSuccess) {
                _statusMessage.value = "Uploaded successfully to OneDrive"
            } else {
                _statusMessage.value = "Upload failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun deleteMedia(item: MediaItem, target: DeleteTarget, onDeleted: () -> Unit) {
        viewModelScope.launch {
            when (target) {
                DeleteTarget.LOCAL_DEVICE -> {
                    val result = repository.deleteLocalMedia(item)
                    if (result.isSuccess) {
                        onDeleted()
                    } else {
                        val permissionEx = result.exceptionOrNull() as? MediaDeletePermissionRequiredException
                        if (permissionEx != null) {
                            _pendingLocalDelete.value = PendingLocalDelete(
                                item = item,
                                intentSender = permissionEx.intentSender,
                                requiresManualRetryAfterGrant = permissionEx.requiresManualRetryAfterGrant,
                                onDeleted = onDeleted,
                            )
                        } else {
                            _statusMessage.value = "Delete failed: ${result.exceptionOrNull()?.message}"
                        }
                    }
                }
                DeleteTarget.CLOUD_CACHE_ONLY -> {
                    val itemId = item.oneDriveItemId
                    if (itemId != null) {
                        cacheRepository.clearLocalCopy(itemId)
                    } else {
                        item.localContentUri?.path?.let { storageManager.deleteManagedLocalCopySafely(it) }
                    }
                    _statusMessage.value = "Cached file removed from device"
                    onDeleted()
                }
                DeleteTarget.ONEDRIVE_PERMANENT -> {
                    val result = repository.deleteCloudMedia(item)
                    if (result.isSuccess) onDeleted()
                    else _statusMessage.value = "Cloud deletion failed: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    fun clearPendingLocalDelete() {
        _pendingLocalDelete.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
