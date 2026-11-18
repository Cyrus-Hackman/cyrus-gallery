package com.cyrus.gallery.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyrus.gallery.data.cache.PrivateStorageManager
import com.cyrus.gallery.data.sync.OneDriveSyncManager
import com.cyrus.gallery.domain.model.CloudStorageState
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.repository.CloudCacheRepository
import com.cyrus.gallery.domain.repository.GalleryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    private val repository: GalleryRepository,
    private val cacheRepository: CloudCacheRepository,
    private val syncManager: OneDriveSyncManager,
    private val storageManager: PrivateStorageManager,
) : ViewModel() {

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch {
            repository.setFavorite(item.id, !item.isFavourite)
        }
    }

    fun togglePinned(item: MediaItem) {
        val itemId = item.oneDriveItemId ?: return
        viewModelScope.launch {
            val isCurrentlyPinned = item.cloudStorageState == CloudStorageState.PINNED_OFFLINE
            cacheRepository.setPinned("root", itemId, !isCurrentlyPinned)
            _statusMessage.value = if (!isCurrentlyPinned) "Pinned for offline viewing" else "Unpinned"
        }
    }

    fun downloadOnDemand(item: MediaItem) {
        val itemId = item.oneDriveItemId ?: return
        viewModelScope.launch {
            syncManager.enqueueDownload(
                itemId = itemId,
                driveId = "root",
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
                    if (result.isSuccess) onDeleted()
                    else _statusMessage.value = "Delete failed: ${result.exceptionOrNull()?.message}"
                }
                DeleteTarget.CLOUD_CACHE_ONLY -> {
                    item.localContentUri?.path?.let { storageManager.deleteCachedFileSafely(it) }
                    cacheRepository.setPinned("root", item.oneDriveItemId ?: "", false)
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

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
