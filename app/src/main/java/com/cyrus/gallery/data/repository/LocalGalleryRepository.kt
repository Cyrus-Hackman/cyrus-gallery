package com.cyrus.gallery.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.cyrus.gallery.domain.model.Album
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.MediaSource
import com.cyrus.gallery.domain.model.MediaType
import com.cyrus.gallery.domain.repository.GalleryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalGalleryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : GalleryRepository {

    override fun observeMedia(): Flow<List<MediaItem>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(queryAll())
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer,
        )
        context.contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer,
        )
        // Emit immediately on first collection
        trySend(queryAll())
        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    override fun observeAlbums(): Flow<List<Album>> = observeMedia().map { mediaList ->
        mediaList
            .groupBy { it.albumId ?: it.albumName ?: "device" }
            .map { (albumId, items) ->
                val first = items.firstOrNull()
                Album(
                    id = albumId,
                    name = first?.albumName ?: "Device Media",
                    coverUri = first?.localContentUri,
                    itemCount = items.size,
                    source = MediaSource.LOCAL,
                )
            }
            .sortedByDescending { it.itemCount }
    }

    override suspend fun setFavorite(mediaId: String, isFavorite: Boolean) = Unit

    override suspend fun deleteLocalMedia(mediaItem: MediaItem): Result<Unit> = runCatching {
        val uri = mediaItem.localContentUri ?: throw IllegalArgumentException("No local URI for media")
        val deleted = context.contentResolver.delete(uri, null, null)
        if (deleted <= 0) throw IllegalStateException("Could not delete item")
    }

    override suspend fun deleteCloudMedia(mediaItem: MediaItem): Result<Unit> =
        Result.failure(UnsupportedOperationException("Use cloud repository for OneDrive deletion"))

    override suspend fun uploadToOneDrive(mediaItem: MediaItem): Result<String> =
        Result.failure(UnsupportedOperationException("Use cloud repository for upload"))

    private fun queryAll(): List<MediaItem> =
        (queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaType.IMAGE) +
                queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaType.VIDEO))
            .sortedByDescending { it.createdAt ?: Instant.EPOCH }

    private fun queryCollection(collectionUri: Uri, type: MediaType): List<MediaItem> {
        val resolver: ContentResolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            if (type == MediaType.VIDEO) MediaStore.Video.Media.DURATION else MediaStore.MediaColumns.WIDTH,
        )

        // API 30+ can use the bundle query form; below that, use sort arg
        val items = mutableListOf<MediaItem>()
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                putInt(ContentResolver.QUERY_ARG_LIMIT, 5000)
            }
            resolver.query(collectionUri, projection, queryArgs, null)
        } else {
            resolver.query(collectionUri, projection, null, null, sortOrder)
        } ?: return emptyList()

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val addedCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val modifiedCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val bucketIdCol = it.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameCol = it.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val extraCol = it.getColumnIndex(
                if (type == MediaType.VIDEO) MediaStore.Video.Media.DURATION
                else MediaStore.MediaColumns.WIDTH,
            )

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(collectionUri, id)
                val dateAdded = it.getLong(addedCol).takeIf { v -> v > 0 }
                val dateModified = it.getLong(modifiedCol).takeIf { v -> v > 0 }
                val bucketId = if (bucketIdCol >= 0) it.getString(bucketIdCol) else null
                val bucketName = if (bucketNameCol >= 0) it.getString(bucketNameCol) else null
                val duration = if (type == MediaType.VIDEO && extraCol >= 0) it.getLong(extraCol) else null

                items.add(
                    MediaItem(
                        id = "local:$id",
                        displayName = it.getString(nameCol) ?: "",
                        type = type,
                        source = MediaSource.LOCAL,
                        mimeType = it.getString(mimeCol) ?: "",
                        createdAt = dateAdded?.let { s -> Instant.ofEpochSecond(s) },
                        modifiedAt = dateModified?.let { s -> Instant.ofEpochSecond(s) },
                        localContentUri = contentUri,
                        durationMillis = duration,
                        albumId = bucketId,
                        albumName = bucketName,
                    ),
                )
            }
        }
        return items
    }
}
