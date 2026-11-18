package com.cyrus.gallery.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudMediaCacheDao {
    @Query("SELECT * FROM cloud_media_cache ORDER BY lastAccessedAtMillis ASC")
    fun observeAll(): Flow<List<CloudMediaCacheEntity>>

    @Query("SELECT * FROM cloud_media_cache WHERE driveId = :driveId AND oneDriveItemId = :itemId LIMIT 1")
    suspend fun find(driveId: String, itemId: String): CloudMediaCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CloudMediaCacheEntity)

    @Query("DELETE FROM cloud_media_cache WHERE cacheId = :cacheId")
    suspend fun removeRecord(cacheId: String)
}
