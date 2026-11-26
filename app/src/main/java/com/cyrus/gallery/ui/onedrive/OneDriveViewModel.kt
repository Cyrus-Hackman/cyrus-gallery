package com.cyrus.gallery.ui.onedrive

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyrus.gallery.data.auth.AuthState
import com.cyrus.gallery.data.auth.OneDriveAuthManager
import com.cyrus.gallery.data.repository.OneDriveRepository
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.repository.GalleryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OneDriveUiState(
    val authState: AuthState = AuthState.SignedOut(),
    val items: List<MediaItem> = emptyList(),
    val isSyncing: Boolean = false,
    val statusMessage: String? = null,
)

@HiltViewModel
class OneDriveViewModel @Inject constructor(
    private val authManager: OneDriveAuthManager,
    private val oneDriveRepository: OneDriveRepository,
    galleryRepository: GalleryRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authManager.authState

    val cloudMedia: StateFlow<List<MediaItem>> = galleryRepository.observeCloudMedia()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isHydrating: StateFlow<Boolean> = oneDriveRepository.isHydrating

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val displayedMedia: StateFlow<List<MediaItem>> = combine(
        cloudMedia,
        _isSearchActive,
        _searchQuery,
    ) { items, isSearch, query ->
        if (isSearch && query.isNotBlank()) {
            items.filter { it.matchesQuery(query) }
        } else {
            items
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Attempt silent session restoration only when not already signed in, avoiding redundant UI flicker
        if (authManager.authState.value !is AuthState.SignedIn) {
            viewModelScope.launch {
                val result = runCatching { authManager.restoreSession() }.getOrElse { Result.failure(it) }
                if (result.isSuccess) {
                    sync()
                }
            }
        }
    }

    fun retryConnection() {
        viewModelScope.launch {
            val result = runCatching { authManager.restoreSession() }.getOrElse { Result.failure(it) }
            if (result.isSuccess) {
                _statusMessage.value = "Connected to OneDrive"
                sync()
            } else {
                _statusMessage.value = result.exceptionOrNull()?.message ?: "Could not connect to OneDrive"
            }
        }
    }

    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) _searchQuery.value = ""
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            val result = authManager.signIn(activity)
            if (result.isSuccess) {
                _statusMessage.value = "Signed in successfully"
                sync()
            } else {
                _statusMessage.value = result.exceptionOrNull()?.message ?: "Sign-in failed"
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
            oneDriveRepository.clearSyncState()
            _statusMessage.value = "Signed out"
        }
    }

    fun sync(forceFullSync: Boolean = false) {
        viewModelScope.launch {
            _isSyncing.value = true
            _statusMessage.value = "Syncing media from OneDrive..."
            // If the library currently has 4 or fewer items, automatically trigger a full re-sync
            val shouldForceFull = forceFullSync || cloudMedia.value.size <= 4
            val result = oneDriveRepository.performDeltaSync(forceFullSync = shouldForceFull)
            _isSyncing.value = false
            if (result.isSuccess) {
                _statusMessage.value = "Synchronized ${result.getOrNull()} items"
            } else {
                _statusMessage.value = "Sync error: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
