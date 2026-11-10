package com.lagradost.cloudstream3.lab

import com.lagradost.cloudstream4.viewmodels.DigestPair
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.DigestException
import java.security.MessageDigest

class ExtensionTransferTest {
    private val bytes = "extension payload".toByteArray()
    private fun digest() = DigestPair("SHA-256", MessageDigest.getInstance("SHA-256").digest(bytes))

    @Test fun transfersExactBytesAndReportsProgress() = runBlocking {
        val output = ByteArrayOutputStream()
        var transferred = 0L
        ExtensionTransfer.transfer(output, ByteArrayInputStream(bytes), bytes.size.toLong(),
            { count, length -> transferred = count; assertEquals(bytes.size.toLong(), length) }, digest())
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(bytes.size.toLong(), transferred)
    }

    @Test fun rejectsMismatchedDigest() = runBlocking {
        try {
            ExtensionTransfer.transfer(ByteArrayOutputStream(), ByteArrayInputStream(bytes), null,
                { _, _ -> }, DigestPair("SHA-256", ByteArray(32)))
            fail("Corrupt extension must not be accepted")
        } catch (_: DigestException) { }
    }

    @Test fun comparesAnExistingFileDigest() = runBlocking {
        val file = File.createTempFile("extension-transfer-", ".cs3")
        try {
            file.writeBytes(bytes)
            assertTrue(ExtensionTransfer.isFileDigest(file, digest()))
            assertFalse(ExtensionTransfer.isFileDigest(file, DigestPair("SHA-256", ByteArray(32))))
            assertFalse(ExtensionTransfer.isFileDigest(file, null))
        } finally { file.delete() }
    }
}
