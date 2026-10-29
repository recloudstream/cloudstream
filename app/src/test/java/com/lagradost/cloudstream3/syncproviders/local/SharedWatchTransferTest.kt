package com.lagradost.cloudstream3.syncproviders.local

import org.junit.Assert.*
import org.junit.Test

class SharedWatchTransferTest {
    private val valid = """{"version":1,"title":"Film","apiName":"Source","url":"https://example.com/film","position":25000,"duration":100000}"""
    @Test fun readsTitleAndProgress() {
        val transfer = SharedWatchTransfer.decode(valid.toByteArray())
        assertNotNull(transfer)
        assertEquals(25000L, transfer!!.position)
    }
    @Test fun rejectsNestedOrUnknownFields() {
        assertNull(SharedWatchTransfer.decode(valid.replace("25000", "{\"@type\":\"danger\"}").toByteArray()))
        assertNull(SharedWatchTransfer.decode(valid.replace("\"version\":1", "\"@class\":\"example\",\"version\":1").toByteArray()))
    }
    @Test fun rejectsAmbiguousOrUnsupportedVersion() {
        assertNull(SharedWatchTransfer.decode(valid.replace("\"version\":1", "\"version\":1,\"version\":1").toByteArray()))
        assertNull(SharedWatchTransfer.decode(valid.replace("\"version\":1", "\"version\":2").toByteArray()))
        assertNull(SharedWatchTransfer.decode((valid+"{}").toByteArray()))
    }
    @Test fun rejectsInvalidPositionsOrUrls() {
        assertNull(SharedWatchTransfer.decode(valid.replace("25000", "-1").toByteArray()))
        assertNull(SharedWatchTransfer.decode(valid.replace("25000", "100001").toByteArray()))
        assertNull(SharedWatchTransfer.decode(valid.replace("https://example.com/film", "file:///secret").toByteArray()))
        assertNull(SharedWatchTransfer.decode(valid.replace("https://example.com/film", "https://user:password@example.com").toByteArray()))
    }
    @Test fun boundsInputBeforeParsing() {
        assertNull(SharedWatchTransfer.decode(ByteArray(16385)))
    }
}
