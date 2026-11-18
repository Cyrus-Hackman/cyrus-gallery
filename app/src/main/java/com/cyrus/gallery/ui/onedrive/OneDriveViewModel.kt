package com.cyrus.gallery.ui.onedrive

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyrus.gallery.data.auth.AuthState
import com.cyrus.gallery.data.auth.OneDriveAuthManager
import com.cyrus.gallery.data.repository.OneDriveRepository
import com.cyrus.gallery.domain.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {

    val authState: StateFlow<AuthState> = authManager.authState

    val cloudMedia: StateFlow<List<MediaItem>> = oneDriveRepository.cloudItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        // Attempt silent session restoration on start
        viewModelScope.launch {
            authManager.restoreSession()
        }
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
            _statusMessage.value = "Signed out"
        }
    }

    fun sync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _statusMessage.value = "Syncing media from OneDrive..."
            val result = oneDriveRepository.performDeltaSync()
            _isSyncing.value = false
            if (result.isSuccess) {
                _statusMessage.value = "Synchronized ${result.getOrNull()} items"
            } else {
                _statusMessage.value = "Sync error: ${result.exceptionOrNull()?.message}"
            }
        }
    }
}
