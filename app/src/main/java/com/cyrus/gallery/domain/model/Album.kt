package com.cyrus.gallery.domain.model

import android.net.Uri

/** Unified album / folder representation for local device collections and OneDrive folders. */
data class Album(
    val id: String,
    val name: String,
    val coverUri: Uri? = null,
    val coverThumbnailUrl: String? = null,
    val itemCount: Int = 0,
    val source: MediaSource = MediaSource.LOCAL,
)
