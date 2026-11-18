package com.cyrus.gallery.domain.model

/** Storage-conscious OneDrive synchronization choices. */
data class CloudCachePolicy(
    val networkMode: SyncNetworkMode = SyncNetworkMode.WIFI_ONLY,
    val automaticDownload: AutomaticDownloadMode = AutomaticDownloadMode.THUMBNAILS_ONLY,
    val maxCacheBytes: Long = DEFAULT_MAX_CACHE_BYTES,
    val evictCachedAfterDays: Int = 30,
) {
    companion object { const val DEFAULT_MAX_CACHE_BYTES = 5L * 1024 * 1024 * 1024 }
}

enum class SyncNetworkMode { WIFI_ONLY, WIFI_AND_MOBILE, MANUAL }
enum class AutomaticDownloadMode { THUMBNAILS_ONLY, PHOTOS_ONLY, VIDEOS_ONLY, ALL_MEDIA, ON_DEMAND }
