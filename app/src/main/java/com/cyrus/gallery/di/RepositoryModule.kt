package com.cyrus.gallery.di

import com.cyrus.gallery.data.auth.OneDriveAuthManager
import com.cyrus.gallery.data.auth.OneDriveAuthManagerImpl
import com.cyrus.gallery.data.remote.graph.OneDriveGraphClient
import com.cyrus.gallery.data.remote.graph.OneDriveGraphClientImpl
import com.cyrus.gallery.data.repository.CloudCacheRepositoryImpl
import com.cyrus.gallery.data.repository.UnifiedGalleryRepository
import com.cyrus.gallery.domain.repository.CloudCacheRepository
import com.cyrus.gallery.domain.repository.GalleryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGalleryRepository(impl: UnifiedGalleryRepository): GalleryRepository

    @Binds
    @Singleton
    abstract fun bindCloudCacheRepository(impl: CloudCacheRepositoryImpl): CloudCacheRepository

    @Binds
    @Singleton
    abstract fun bindOneDriveAuthManager(impl: OneDriveAuthManagerImpl): OneDriveAuthManager

    @Binds
    @Singleton
    abstract fun bindOneDriveGraphClient(impl: OneDriveGraphClientImpl): OneDriveGraphClient
}
