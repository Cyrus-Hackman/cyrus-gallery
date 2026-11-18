package com.cyrus.gallery.domain.repository

import com.cyrus.gallery.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface GalleryRepository {
    /** Emits one unified, newest-first library without retaining originals in memory. */
    fun observeMedia(): Flow<List<MediaItem>>
}

