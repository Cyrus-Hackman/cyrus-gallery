package com.cyrus.gallery.di

import android.content.Context
import androidx.room.Room
import com.cyrus.gallery.data.database.CloudMediaCacheDao
import com.cyrus.gallery.data.database.GalleryDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideGalleryDatabase(@ApplicationContext context: Context): GalleryDatabase =
        Room.databaseBuilder(
            context,
            GalleryDatabase::class.java,
            "gallery_database.db",
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCloudMediaCacheDao(database: GalleryDatabase): CloudMediaCacheDao =
        database.cloudMediaCacheDao()
}
