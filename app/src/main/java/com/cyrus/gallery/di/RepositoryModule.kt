package com.cyrus.gallery.di

import com.cyrus.gallery.data.repository.LocalGalleryRepository
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
    abstract fun bindGalleryRepository(impl: LocalGalleryRepository): GalleryRepository
}
