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

    @Test
    fun `collectDescendantFolderIds gathers nested cloud subfolders and guards cycles`() {
        val root = Album(id = "f_root", name = "Pictures", source = MediaSource.ONEDRIVE)
        val child = Album(id = "f_2025", name = "2025", source = MediaSource.ONEDRIVE, parentFolderId = "f_root")
        val grandChild = Album(id = "f_trip", name = "Trip", source = MediaSource.ONEDRIVE, parentFolderId = "f_2025")
        val other = Album(id = "f_other", name = "Docs", source = MediaSource.ONEDRIVE)

        val ids = collectDescendantFolderIds("f_root", listOf(root, child, grandChild, other))

        assertEquals(setOf("f_root", "f_2025", "f_trip"), ids)
    }
}
