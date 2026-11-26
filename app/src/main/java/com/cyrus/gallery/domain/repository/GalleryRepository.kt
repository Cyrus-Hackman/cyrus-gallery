package com.cyrus.gallery.domain.repository

import com.cyrus.gallery.domain.model.Album
import com.cyrus.gallery.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface GalleryRepository {
    /** Emits one unified, newest-first library without retaining originals in memory. */
    fun observeMedia(): Flow<List<MediaItem>>

    /** Emits all favorited items across local and cloud sources (without local filename deduplication). */
    fun observeFavoriteMedia(): Flow<List<MediaItem>> =
        observeMedia().map { items -> items.filter { it.isFavourite } }

    /** Emits enriched OneDrive-only media (without local filename deduplication), newest-first. */
    fun observeCloudMedia(): Flow<List<MediaItem>> = flowOf(emptyList())

    /** Emits albums / folders derived from media collections. */
    fun observeAlbums(): Flow<List<Album>>

    /** Toggles the favorite persistence state for a given media item. */
    suspend fun setFavorite(mediaId: String, isFavorite: Boolean)

    /** Deletes local device media safely via MediaStore / ContentResolver. */
    suspend fun deleteLocalMedia(mediaItem: MediaItem): Result<Unit>

    /** Deletes cloud original on Microsoft OneDrive. */
    suspend fun deleteCloudMedia(mediaItem: MediaItem): Result<Unit>

    /** Explicitly uploads local media original to Microsoft OneDrive. */
    suspend fun uploadToOneDrive(mediaItem: MediaItem): Result<String>
}

