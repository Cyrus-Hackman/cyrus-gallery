package com.cyrus.gallery.data.repository

import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.repository.GalleryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Phase 1 source. MediaStore and OneDrive implementations replace this in later phases. */
class EmptyGalleryRepository : GalleryRepository {
    override fun observeMedia(): Flow<List<MediaItem>> = flowOf(emptyList())
}

