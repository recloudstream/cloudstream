package com.lagradost.cloudstream3.utils.downloader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDownloadPolicyTest {
    @Test fun acceptsCompletedLegacyFile() {
        assertTrue(LocalDownloadPolicy.isComplete(1024, 1024, null, false))
    }
    @Test fun acceptsRecordedCompletionWithUnknownContentLength() {
        assertTrue(LocalDownloadPolicy.isComplete(1024, 0, 1024, false))
    }
    @Test fun rejectsPartialEmptyAndActiveFiles() {
        assertFalse(LocalDownloadPolicy.isComplete(512, 1024, null, false))
        assertFalse(LocalDownloadPolicy.isComplete(0, 0, 0, false))
        assertFalse(LocalDownloadPolicy.isComplete(1024, 1024, 1024, true))
    }
    @Test fun rejectsChangedLengthAndUnconfirmedUnknownLength() {
        assertFalse(LocalDownloadPolicy.isComplete(512, 0, 1024, false))
        assertFalse(LocalDownloadPolicy.isComplete(1024, 0, null, false))
    }
}
