package com.lagradost.cloudstream3.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadResponseCachePolicyTest {
    @Test fun transientUnavailableResultsDoNotStickForTenMinutes() {
        assertTrue(LoadResponseCachePolicy.isFresh(100, 120, true))
        assertFalse(LoadResponseCachePolicy.isFresh(100, 131, true))
        assertTrue(LoadResponseCachePolicy.isFresh(100, 131, false))
    }
    @Test fun normalEntriesExpireAndClockRollbackInvalidates() {
        assertFalse(LoadResponseCachePolicy.isFresh(100, 700, false))
        assertFalse(LoadResponseCachePolicy.isFresh(100, 99, false))
    }
}
