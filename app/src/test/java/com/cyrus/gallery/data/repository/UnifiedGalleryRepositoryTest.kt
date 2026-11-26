package com.cyrus.gallery.data.repository

import com.cyrus.gallery.domain.model.CloudStorageState
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.MediaSource
import com.cyrus.gallery.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UnifiedGalleryRepositoryTest {

    @Test
    fun `deduplication prioritizes local copy when cloud copy has identical filename`() {
        val now = Instant.now()

        val localItem = MediaItem(
            id = "local:101",
            displayName = "Family_Trip.jpg",
            type = MediaType.IMAGE,
            source = MediaSource.LOCAL,
            mimeType = "image/jpeg",
            createdAt = now,
            modifiedAt = now,
        )

        val duplicateCloudItem = MediaItem(
            id = "onedrive:cloud_101",
            displayName = "Family_Trip.jpg",
            type = MediaType.IMAGE,
            source = MediaSource.ONEDRIVE,
            mimeType = "image/jpeg",
            createdAt = now,
            modifiedAt = now,
            oneDriveItemId = "cloud_101",
            cloudStorageState = CloudStorageState.REMOTE_ONLY,
        )

        val uniqueCloudItem = MediaItem(
            id = "onedrive:cloud_202",
            displayName = "Mountains.jpg",
            type = MediaType.IMAGE,
            source = MediaSource.ONEDRIVE,
            mimeType = "image/jpeg",
            createdAt = now.minusSeconds(3600),
            modifiedAt = now.minusSeconds(3600),
            oneDriveItemId = "cloud_202",
            cloudStorageState = CloudStorageState.REMOTE_ONLY,
        )

        val unified = UnifiedGalleryRepository.mergeUnifiedMedia(
            localItems = listOf(localItem),
            enrichedCloudItems = listOf(duplicateCloudItem, uniqueCloudItem),
            favoriteIds = emptySet(),
        )

        assertEquals(2, unified.size)
        assertEquals("local:101", unified[0].id)
        assertEquals(MediaSource.LOCAL, unified[0].source)
        assertEquals("onedrive:cloud_202", unified[1].id)
        assertEquals(MediaSource.ONEDRIVE, unified[1].source)
        assertTrue(unified.none { it.id == "onedrive:cloud_101" })
    }

    @Test
    fun `buildFavoriteMedia retains favorited OneDrive items whose filename matches a local item`() {
        val now = Instant.now()

        val localItem = MediaItem(
            id = "local:101",
            displayName = "Family_Trip.jpg",
            type = MediaType.IMAGE,
            source = MediaSource.LOCAL,
            mimeType = "image/jpeg",
            createdAt = now,
            modifiedAt = now,
        )

        val matchingCloudFavorite = MediaItem(
            id = "onedrive:cloud_101",
            displayName = "family_trip.jpg",
            type = MediaType.IMAGE,
            source = MediaSource.ONEDRIVE,
            mimeType = "image/jpeg",
            createdAt = now.minusSeconds(120),
            modifiedAt = now.minusSeconds(120),
            oneDriveItemId = "cloud_101",
            cloudStorageState = CloudStorageState.REMOTE_ONLY,
            isFavourite = true,
        )

        val nonFavoritedCloudItem = MediaItem(
            id = "onedrive:cloud_202",
            displayName = "Mountains.jpg",
            type = MediaType.IMAGE,
            source = MediaSource.ONEDRIVE,
            mimeType = "image/jpeg",
            createdAt = now.minusSeconds(3600),
            modifiedAt = now.minusSeconds(3600),
            oneDriveItemId = "cloud_202",
            cloudStorageState = CloudStorageState.REMOTE_ONLY,
            isFavourite = false,
        )

        val favSet = setOf("onedrive:cloud_101")

        // Photos stream still deduplicates by filename...
        val unified = UnifiedGalleryRepository.mergeUnifiedMedia(
            localItems = listOf(localItem),
            enrichedCloudItems = listOf(matchingCloudFavorite, nonFavoritedCloudItem),
            favoriteIds = favSet,
        )
        assertTrue(unified.none { it.id == "onedrive:cloud_101" })
        assertFalse(unified.first { it.id == "local:101" }.isFavourite)

        // ...but Favorites stream still includes the favorited cloud item!
        val favorites = UnifiedGalleryRepository.buildFavoriteMedia(
            localItems = listOf(localItem),
            enrichedCloudItems = listOf(matchingCloudFavorite, nonFavoritedCloudItem),
            favoriteIds = favSet,
        )
        assertEquals(1, favorites.size)
        assertEquals("onedrive:cloud_101", favorites[0].id)
        assertTrue(favorites[0].isFavourite)
    }
}
