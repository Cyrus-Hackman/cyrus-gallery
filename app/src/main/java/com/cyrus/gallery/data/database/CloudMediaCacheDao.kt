package com.cyrus.gallery.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cyrus.gallery.domain.model.CloudStorageState
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudMediaCacheDao {
    @Query("SELECT * FROM cloud_media_cache ORDER BY lastAccessedAtMillis ASC")
    fun observeAll(): Flow<List<CloudMediaCacheEntity>>

    @Query("SELECT * FROM cloud_media_cache WHERE driveId = :driveId AND oneDriveItemId = :itemId LIMIT 1")
    suspend fun find(driveId: String, itemId: String): CloudMediaCacheEntity?

    @Query("SELECT * FROM cloud_media_cache WHERE oneDriveItemId = :itemId LIMIT 1")
    suspend fun findByItemId(itemId: String): CloudMediaCacheEntity?

    @Query("SELECT * FROM cloud_media_cache WHERE storageState = :state ORDER BY lastAccessedAtMillis ASC")
    suspend fun getItemsByStorageState(state: CloudStorageState): List<CloudMediaCacheEntity>

    @Query("SELECT * FROM cloud_media_cache WHERE storageState = 'CACHED' ORDER BY lastAccessedAtMillis ASC")
    suspend fun getEvictableItems(): List<CloudMediaCacheEntity>

    @Query("SELECT * FROM cloud_media_cache")
    suspend fun getAll(): List<CloudMediaCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CloudMediaCacheEntity)

    @Query("UPDATE cloud_media_cache SET storageState = :state, localCachedPath = :path, lastAccessedAtMillis = :accessedTime WHERE cacheId = :cacheId")
    suspend fun updateStorageState(cacheId: String, state: CloudStorageState, path: String?, accessedTime: Long)

    @Query("UPDATE cloud_media_cache SET downloadStatus = :status, downloadProgress = :progress, localCachedPath = :path, downloadedAtMillis = :downloadedTime WHERE cacheId = :cacheId")
    suspend fun updateDownloadStatus(cacheId: String, status: CloudDownloadStatus, progress: Int, path: String?, downloadedTime: Long?)

    @Query("UPDATE cloud_media_cache SET lastAccessedAtMillis = :accessedTime WHERE oneDriveItemId = :itemId")
    suspend fun updateLastAccessed(itemId: String, accessedTime: Long)

    @Query("DELETE FROM cloud_media_cache WHERE cacheId = :cacheId")
    suspend fun removeRecord(cacheId: String)
}
