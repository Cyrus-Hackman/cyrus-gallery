package com.cyrus.gallery.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteMediaDao {
    @Query("SELECT mediaId FROM favorite_media ORDER BY addedAtMillis DESC")
    fun observeFavoriteIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_media WHERE mediaId = :mediaId)")
    suspend fun isFavorite(mediaId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(entity: FavoriteMediaEntity)

    @Query("DELETE FROM favorite_media WHERE mediaId = :mediaId")
    suspend fun removeFavorite(mediaId: String)
}
