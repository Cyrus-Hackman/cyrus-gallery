package com.cyrus.gallery.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyrus.gallery.data.cache.CloudPolicyPreferences
import com.cyrus.gallery.data.sync.OneDriveSyncManager
import com.cyrus.gallery.domain.model.AutomaticDownloadMode
import com.cyrus.gallery.domain.model.CloudCachePolicy
import com.cyrus.gallery.domain.model.SyncNetworkMode
import com.cyrus.gallery.domain.repository.CloudCacheRepository
import com.cyrus.gallery.domain.repository.CloudCacheStorageSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val cacheRepository: CloudCacheRepository,
    private val syncManager: OneDriveSyncManager,
    private val policyPreferences: CloudPolicyPreferences,
) : ViewModel() {

    val storageSummary: StateFlow<CloudCacheStorageSummary> = cacheRepository.observeStorageSummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CloudCacheStorageSummary())

    val policy: StateFlow<CloudCachePolicy> = policyPreferences.policy

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun clearTemporaryCache() {
        viewModelScope.launch {
            val result = cacheRepository.clearTemporaryCache()
            val mb = result.reclaimedBytes / (1024 * 1024)
            _actionMessage.value = "Reclaimed $mb MB (${result.removedFiles} cached files removed)"
        }
    }

    fun updateNetworkMode(mode: SyncNetworkMode) {
        policyPreferences.updateNetworkMode(mode)
        syncManager.schedulePeriodicSync(policyPreferences.policy.value)
    }

    fun updateDownloadMode(mode: AutomaticDownloadMode) {
        policyPreferences.updateDownloadMode(mode)
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
