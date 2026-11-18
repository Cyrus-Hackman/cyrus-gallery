package com.cyrus.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumTest {

    @Test
    fun `album model retains name item count and source`() {
        val album = Album(
            id = "bucket_123",
            name = "Camera",
            coverUri = null,
            itemCount = 42,
            source = MediaSource.LOCAL,
        )

        assertEquals("bucket_123", album.id)
        assertEquals("Camera", album.name)
        assertEquals(42, album.itemCount)
        assertEquals(MediaSource.LOCAL, album.source)
    }
}
