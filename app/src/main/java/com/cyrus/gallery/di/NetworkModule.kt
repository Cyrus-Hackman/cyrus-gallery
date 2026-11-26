package com.cyrus.gallery.di

import android.content.Context
import androidx.room.Room
import com.cyrus.gallery.data.auth.OneDriveAuthManager
import com.cyrus.gallery.data.database.CloudMediaCacheDao
import com.cyrus.gallery.data.database.GalleryDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GraphMediaClient

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
    @GraphMediaClient
    fun provideGraphMediaHttpClient(
        baseClient: OkHttpClient,
        authManager: OneDriveAuthManager,
    ): OkHttpClient = baseClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host.contains("graph.microsoft.com")) {
                val token = authManager.cachedAccessToken
                if (!token.isNullOrEmpty()) {
                    return@addInterceptor chain.proceed(
                        request.newBuilder()
                            .header("Authorization", "Bearer $token")
                            .build(),
                    )
                }
            }
            chain.proceed(request)
        }
        .authenticator { _, response ->
            // Synchronously refresh expired token once on 401 without blocking fast normal image scrolls
            if (!response.request.url.host.contains("graph.microsoft.com") || response.priorResponse != null) {
                return@authenticator null
            }
            val freshToken = runBlocking { authManager.getAccessToken() } ?: return@authenticator null
            response.request.newBuilder()
                .header("Authorization", "Bearer $freshToken")
                .build()
        }
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

    @Provides
    fun provideFavoriteMediaDao(database: GalleryDatabase): com.cyrus.gallery.data.database.FavoriteMediaDao =
        database.favoriteMediaDao()
}
