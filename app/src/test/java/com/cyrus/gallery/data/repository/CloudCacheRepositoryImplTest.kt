package com.cyrus.gallery.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudCacheRepositoryImplTest {

    @Test
    fun `resolveEvictedBytes prefers actual file size on disk over DB originalSizeBytes`() {
        assertEquals(42_500L, CloudCacheRepositoryImpl.resolveEvictedBytes(actualFileBytes = 42_500L, fallbackOriginalBytes = 0L))
        assertEquals(42_500L, CloudCacheRepositoryImpl.resolveEvictedBytes(actualFileBytes = 42_500L, fallbackOriginalBytes = 100_000L))
        assertEquals(1_024L, CloudCacheRepositoryImpl.resolveEvictedBytes(actualFileBytes = 0L, fallbackOriginalBytes = 1_024L))
        assertEquals(0L, CloudCacheRepositoryImpl.resolveEvictedBytes(actualFileBytes = 0L, fallbackOriginalBytes = -50L))
    }
}
