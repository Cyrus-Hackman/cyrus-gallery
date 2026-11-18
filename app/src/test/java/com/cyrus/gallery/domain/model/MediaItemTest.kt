package com.cyrus.gallery.domain.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
