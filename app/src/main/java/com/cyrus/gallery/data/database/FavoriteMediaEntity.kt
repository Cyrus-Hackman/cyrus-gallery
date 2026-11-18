package com.cyrus.gallery.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_media")
data class FavoriteMediaEntity(
    @PrimaryKey val mediaId: String,
    val addedAtMillis: Long = System.currentTimeMillis(),
)
