package com.cyrus.gallery.data.repository

import com.cyrus.gallery.domain.model.Album
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.repository.GalleryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Phase 1 source. MediaStore and OneDrive implementations replace this in later phases. */
class EmptyGalleryRepository : GalleryRepository {
    override fun observeMedia(): Flow<List<MediaItem>> = flowOf(emptyList())
    override fun observeAlbums(): Flow<List<Album>> = flowOf(emptyList())
    override suspend fun setFavorite(mediaId: String, isFavorite: Boolean) = Unit
    override suspend fun deleteLocalMedia(mediaItem: MediaItem): Result<Unit> = Result.success(Unit)
    override suspend fun deleteCloudMedia(mediaItem: MediaItem): Result<Unit> = Result.success(Unit)
    override suspend fun uploadToOneDrive(mediaItem: MediaItem): Result<String> = Result.success("ok")
}

