package com.cyrus.gallery.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cyrus.gallery.domain.model.CloudStorageState

/** App-private OneDrive-original metadata. Completed means its atomic file move succeeded. */
@Entity(tableName = "cloud_media_cache", indices = [Index(value = ["driveId", "oneDriveItemId"], unique = true)])
data class CloudMediaCacheEntity(
    @PrimaryKey val cacheId: String,
    val oneDriveItemId: String,
    val driveId: String,
    val fileName: String,
    val mimeType: String,
    val originalSizeBytes: Long,
    val localCachedPath: String? = null,
    val thumbnailCachedPath: String? = null,
    val storageState: CloudStorageState = CloudStorageState.REMOTE_ONLY,
    val downloadStatus: CloudDownloadStatus = CloudDownloadStatus.NOT_DOWNLOADED,
    val downloadProgress: Int = 0,
    val downloadedAtMillis: Long? = null,
    val lastAccessedAtMillis: Long? = null,
    val lastSynchronizedAtMillis: Long? = null,
    val eTag: String? = null,
    val cTag: String? = null,
    val cacheExpiresAtMillis: Long? = null,
    val integritySha256: String? = null,
)

enum class CloudDownloadStatus { NOT_DOWNLOADED, QUEUED, DOWNLOADING, COMPLETE, FAILED, CANCELLED }
