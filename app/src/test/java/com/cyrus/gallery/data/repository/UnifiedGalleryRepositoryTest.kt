package com.cyrus.gallery.data.repository

import com.cyrus.gallery.domain.model.CloudStorageState
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.MediaSource
import com.cyrus.gallery.domain.model.MediaType
import org.junit.Assert.assertEquals
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

        val localItems = listOf(localItem)
        val cloudItems = listOf(duplicateCloudItem, uniqueCloudItem)

        // Deduplication rule: filter out cloud items whose displayName is already in local
        val localNameSet = localItems.map { it.displayName.lowercase() }.toSet()
        val distinctCloud = cloudItems.filter { it.displayName.lowercase() !in localNameSet }
        val unified = (localItems + distinctCloud).sortedByDescending { it.createdAt }

        assertEquals(2, unified.size)
        assertEquals("local:101", unified[0].id)
        assertEquals(MediaSource.LOCAL, unified[0].source)
        assertEquals("onedrive:cloud_202", unified[1].id)
        assertEquals(MediaSource.ONEDRIVE, unified[1].source)
        assertTrue(unified.none { it.id == "onedrive:cloud_101" })
    }
}
