package com.cyrus.gallery.domain.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaGroupTest {

    @Test
    fun `groupByMonth groups items into correct month buckets`() {
        // April 2024 and May 2024 timestamps (seconds since epoch)
        val april = Instant.parse("2024-04-15T10:00:00Z")
        val may   = Instant.parse("2024-05-20T10:00:00Z")

        fun item(id: String, ts: Instant) = MediaItem(
            id = id, displayName = "$id.jpg", type = MediaType.IMAGE,
            source = MediaSource.LOCAL, mimeType = "image/jpeg",
            createdAt = ts, modifiedAt = ts,
        )

        val items = listOf(item("a", may), item("b", april), item("c", april))
        // Items are assumed pre-sorted (most recent first) by repository
        val groups = items.groupByMonth(java.time.ZoneId.of("UTC"))

        assertEquals(2, groups.size)
        assertEquals(1, groups[0].items.size) // May
        assertEquals(2, groups[1].items.size) // April
    }

    @Test
    fun `groupByMonth puts items without date into Unknown date bucket`() {
        val item = MediaItem(
            id = "x", displayName = "x.jpg", type = MediaType.IMAGE,
            source = MediaSource.LOCAL, mimeType = "image/jpeg",
            createdAt = null, modifiedAt = null,
        )
        val groups = listOf(item).groupByMonth()
        assertEquals("Unknown date", groups.first().label)
    }
}
