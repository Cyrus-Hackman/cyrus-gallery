package com.cyrus.gallery.data.cache

import android.content.Context
import com.cyrus.gallery.domain.model.AutomaticDownloadMode
import com.cyrus.gallery.domain.model.CloudCachePolicy
import com.cyrus.gallery.domain.model.SyncNetworkMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Persists cloud sync network and automatic download preferences across app restarts. */
@Singleton
class CloudPolicyPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("cloud_policy_prefs", Context.MODE_PRIVATE)

    private val _policy = MutableStateFlow(loadPolicy())
    val policy: StateFlow<CloudCachePolicy> = _policy.asStateFlow()

    private fun loadPolicy(): CloudCachePolicy {
        val defaultPolicy = CloudCachePolicy()
        val networkMode = prefs.getString(KEY_NETWORK_MODE, null)
            ?.let { runCatching { SyncNetworkMode.valueOf(it) }.getOrNull() }
            ?: defaultPolicy.networkMode
        val downloadMode = prefs.getString(KEY_DOWNLOAD_MODE, null)
            ?.let { runCatching { AutomaticDownloadMode.valueOf(it) }.getOrNull() }
            ?: defaultPolicy.automaticDownload
        return defaultPolicy.copy(
            networkMode = networkMode,
            automaticDownload = downloadMode,
        )
    }

    fun updateNetworkMode(mode: SyncNetworkMode) {
        prefs.edit().putString(KEY_NETWORK_MODE, mode.name).apply()
        _policy.value = _policy.value.copy(networkMode = mode)
    }

    fun updateDownloadMode(mode: AutomaticDownloadMode) {
        prefs.edit().putString(KEY_DOWNLOAD_MODE, mode.name).apply()
        _policy.value = _policy.value.copy(automaticDownload = mode)
    }

    companion object {
        private const val KEY_NETWORK_MODE = "network_mode"
        private const val KEY_DOWNLOAD_MODE = "automatic_download_mode"
    }
}
