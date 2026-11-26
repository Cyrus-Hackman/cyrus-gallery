package com.cyrus.gallery.domain.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemTest {
    @Test
    fun `cloud item retains its stable OneDrive identity without a local URI`() {
        val item = MediaItem(
            id = "onedrive:01ABC",
            displayName = "Iceland.jpg",
            type = MediaType.IMAGE,
            source = MediaSource.ONEDRIVE,
            mimeType = "image/jpeg",
            createdAt = Instant.EPOCH,
            modifiedAt = Instant.EPOCH,
            oneDriveItemId = "01ABC",
            cloudStorageState = CloudStorageState.REMOTE_ONLY,
        )

        assertEquals(MediaSource.ONEDRIVE, item.source)
        assertEquals("01ABC", item.oneDriveItemId)
        assertNull(item.localContentUri)
        assertEquals(CloudStorageState.REMOTE_ONLY, item.cloudStorageState)
    }

    @Test
    fun `matchesQuery filters across displayName albumName and mimeType`() {
        val item = MediaItem(
            id = "onedrive:01XYZ",
            displayName = "Sunset_Beach.png",
            type = MediaType.IMAGE,
            source = MediaSource.ONEDRIVE,
            mimeType = "image/png",
            createdAt = Instant.EPOCH,
            modifiedAt = Instant.EPOCH,
            albumName = "Vacations",
        )

        assertTrue(item.matchesQuery("beach"))
        assertTrue(item.matchesQuery("vacation"))
        assertTrue(item.matchesQuery("png"))
        assertFalse(item.matchesQuery("snow"))
    }
}
