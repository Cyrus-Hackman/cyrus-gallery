package com.cyrus.gallery.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [CloudMediaCacheEntity::class], version = 1, exportSchema = false)
@TypeConverters(CloudCacheConverters::class)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun cloudMediaCacheDao(): CloudMediaCacheDao
}
