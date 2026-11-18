package com.cyrus.gallery.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteTargetTest {

    @Test
    fun `delete targets distinguish between device cache and permanent cloud deletion`() {
        val targets = DeleteTarget.entries.toList()
        assertEquals(3, targets.size)
        assertEquals(DeleteTarget.LOCAL_DEVICE, targets[0])
        assertEquals(DeleteTarget.CLOUD_CACHE_ONLY, targets[1])
        assertEquals(DeleteTarget.ONEDRIVE_PERMANENT, targets[2])
    }
}
