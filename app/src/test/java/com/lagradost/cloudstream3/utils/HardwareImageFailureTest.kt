package com.lagradost.cloudstream3.utils

import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareImageFailureTest {
    @Test fun recoversAllocationFailureWithoutModelDetection() {
        assertTrue(HardwareImageFailure.isRecoverable(IllegalStateException("failed to allocate hardware Bitmap!")))
    }

    @Test fun recoversWrappedSoftwareCanvasFailure() {
        assertTrue(HardwareImageFailure.isRecoverable(IOException("decode failed",
            IllegalArgumentException("Software rendering does not support hardware bitmaps"))))
    }

    @Test fun doesNotRetryHttpFailuresAsDecodeFailures() {
        assertFalse(HardwareImageFailure.isRecoverable(IOException("HTTP 403")))
    }

    @Test fun doesNotRetryOutOfMemoryEvenWhenWrappedByHardwareError() {
        assertFalse(HardwareImageFailure.isRecoverable(IllegalStateException("hardware bitmap",
            OutOfMemoryError("heap exhausted"))))
    }

    @Test fun doesNotRetryCancellation() {
        assertFalse(HardwareImageFailure.isRecoverable(CancellationException("hardware bitmap request cancelled")))
    }

    @Test fun recoversGraphicBufferAllocationFailure() {
        assertTrue(HardwareImageFailure.isRecoverable(IllegalStateException("GraphicBuffer allocation failed")))
    }

    @Test fun recoversAHardwareBufferFailure() {
        assertTrue(HardwareImageFailure.isRecoverable(IllegalStateException("AHardwareBuffer allocation failed")))
    }

    @Test fun rejectsUnrelatedDecodeFailure() {
        assertFalse(HardwareImageFailure.isRecoverable(IllegalArgumentException("Invalid image data")))
    }

    @Test fun doesNotRetryFatalErrors() {
        assertFalse(HardwareImageFailure.isRecoverable(StackOverflowError("hardware bitmap")))
    }

    @Test fun terminatesForCyclicCauseChains() {
        val first = IllegalStateException("hardware bitmap allocation failed")
        val second = IllegalArgumentException("decode failed")
        first.initCause(second)
        second.initCause(first)
        assertTrue(HardwareImageFailure.isRecoverable(first))
    }
}
