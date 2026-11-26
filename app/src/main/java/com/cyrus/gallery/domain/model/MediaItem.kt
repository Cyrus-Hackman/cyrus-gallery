package com.cyrus.gallery.domain.model

import android.net.Uri
import java.time.Instant

/** Shared, source-agnostic media contract. Cloud media deliberately has no required local URI. */
data class MediaItem(
    val id: String,
    val displayName: String,
    val type: MediaType,
    val source: MediaSource,
    val mimeType: String,
    val createdAt: Instant?,
    val modifiedAt: Instant?,
    val localContentUri: Uri? = null,
    val oneDriveItemId: String? = null,
    val thumbnailUrl: String? = null,
    val highResUrl: String? = null,
    /** Null for permanent device media; required for OneDrive media. */
    val cloudStorageState: CloudStorageState? = null,
    val durationMillis: Long? = null,
    val isFavourite: Boolean = false,
    val albumId: String? = null,
    val albumName: String? = null,
) {
    /** Case-insensitive search match across display name, album name, and MIME type. */
    fun matchesQuery(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return displayName.lowercase().contains(q) ||
            (albumName?.lowercase()?.contains(q) == true) ||
            mimeType.lowercase().contains(q)
    }
}

enum class MediaSource { LOCAL, ONEDRIVE }
enum class MediaType { IMAGE, VIDEO }

/** Lifecycle of an original stored in Cyrus Gallery private app storage. */
enum class CloudStorageState { REMOTE_ONLY, CACHED, PINNED_OFFLINE }

