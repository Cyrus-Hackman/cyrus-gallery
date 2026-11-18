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
    /** Null for permanent device media; required for OneDrive media. */
    val cloudStorageState: CloudStorageState? = null,
    val durationMillis: Long? = null,
    val isFavourite: Boolean = false,
)

enum class MediaSource { LOCAL, ONEDRIVE }
enum class MediaType { IMAGE, VIDEO }

/** Lifecycle of an original stored in Cyrus Gallery private app storage. */
enum class CloudStorageState { REMOTE_ONLY, CACHED, PINNED_OFFLINE }

