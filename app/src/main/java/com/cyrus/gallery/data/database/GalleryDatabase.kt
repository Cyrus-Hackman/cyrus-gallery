package com.cyrus.gallery.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [CloudMediaCacheEntity::class, FavoriteMediaEntity::class], version = 2, exportSchema = false)
@TypeConverters(CloudCacheConverters::class)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun cloudMediaCacheDao(): CloudMediaCacheDao
    abstract fun favoriteMediaDao(): FavoriteMediaDao
}
