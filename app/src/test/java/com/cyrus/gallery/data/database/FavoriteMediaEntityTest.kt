package com.cyrus.gallery.data.database

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteMediaEntityTest {

    @Test
    fun `favorite entity retains media id and timestamp`() {
        val now = 1715000000000L
        val fav = FavoriteMediaEntity(mediaId = "local:999", addedAtMillis = now)
        assertEquals("local:999", fav.mediaId)
        assertEquals(now, fav.addedAtMillis)
    }
}
