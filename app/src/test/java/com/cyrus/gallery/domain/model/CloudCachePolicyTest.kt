package com.cyrus.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudCachePolicyTest {

    @Test
    fun `default cloud cache policy enforces wifi only and 5GB limit`() {
        val policy = CloudCachePolicy()
        assertEquals(SyncNetworkMode.WIFI_ONLY, policy.networkMode)
        assertEquals(AutomaticDownloadMode.THUMBNAILS_ONLY, policy.automaticDownload)
        assertEquals(5L * 1024 * 1024 * 1024, policy.maxCacheBytes)
        assertEquals(30, policy.evictCachedAfterDays)
    }

    @Test
    fun `custom cloud cache policy can be configured`() {
        val policy = CloudCachePolicy(
            networkMode = SyncNetworkMode.WIFI_AND_MOBILE,
            automaticDownload = AutomaticDownloadMode.ON_DEMAND,
            maxCacheBytes = 2L * 1024 * 1024 * 1024,
            evictCachedAfterDays = 14,
        )
        assertEquals(SyncNetworkMode.WIFI_AND_MOBILE, policy.networkMode)
        assertEquals(AutomaticDownloadMode.ON_DEMAND, policy.automaticDownload)
        assertEquals(2L * 1024 * 1024 * 1024, policy.maxCacheBytes)
        assertEquals(14, policy.evictCachedAfterDays)
    }
}
