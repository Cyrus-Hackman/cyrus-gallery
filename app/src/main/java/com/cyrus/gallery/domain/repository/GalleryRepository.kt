package com.cyrus.gallery.domain.repository

import com.cyrus.gallery.domain.model.Album
import com.cyrus.gallery.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface GalleryRepository {
    /** Emits one unified, newest-first library without retaining originals in memory. */
    fun observeMedia(): Flow<List<MediaItem>>

    /** Emits albums / folders derived from media collections. */
    fun observeAlbums(): Flow<List<Album>>
}

