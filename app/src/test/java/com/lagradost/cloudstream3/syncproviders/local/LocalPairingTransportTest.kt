package com.lagradost.cloudstream3.syncproviders.local

import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class LocalPairingTransportTest {
    private val loopback = InetAddress.getByName("127.0.0.1")

    @Test fun deliversExactlyOneAuthenticatedPayload() {
        val values = mutableListOf<String>()
        val ended = CountDownLatch(1)
        LocalPairingTransport.Receiver({ values.add(String(it)); true }, { ended.countDown() }).use { receiver ->
            val port = receiver.port
            receiver.start()
            assertTrue(LocalPairingTransport.send(loopback, port, receiver.code.chunked(4).joinToString("-"), "title".toByteArray()))
            assertTrue(ended.await(2, TimeUnit.SECONDS))
            assertEquals(listOf("title"), values)
            assertTrue(runCatching { Socket(loopback, port).close() }.isFailure)
        }
    }

    @Test fun incorrectCodeDoesNotDeliverAndCanBeRetried() {
        var received = 0
        LocalPairingTransport.Receiver({ received++; true }).use { receiver ->
            receiver.start()
            val wrong = if (receiver.code.startsWith("A")) "B" + receiver.code.drop(1) else "A" + receiver.code.drop(1)
            assertFalse(runCatching { LocalPairingTransport.send(loopback, receiver.port, wrong, byteArrayOf(1)) }.getOrDefault(false))
            assertEquals(0, received)
            assertTrue(LocalPairingTransport.send(loopback, receiver.port, receiver.code, byteArrayOf(2)))
            assertEquals(1, received)
        }
    }

    @Test fun receiverMayRejectInvalidPayloadWithoutEndingSession() {
        LocalPairingTransport.Receiver({ String(it) == "valid" }).use { receiver ->
            receiver.start()
            assertFalse(LocalPairingTransport.send(loopback, receiver.port, receiver.code, "invalid".toByteArray()))
            assertTrue(LocalPairingTransport.send(loopback, receiver.port, receiver.code, "valid".toByteArray()))
        }
    }

    @Test fun closeInterruptsIncompleteConnection() {
        val ended = CountDownLatch(1)
        val receiver = LocalPairingTransport.Receiver({ fail("Unexpected delivery"); false }, { ended.countDown() })
        receiver.start()
        Socket(loopback, receiver.port).use { socket ->
            socket.soTimeout = 1000
            val input = java.io.DataInputStream(socket.getInputStream())
            input.readInt()
            repeat(2) { input.readFully(ByteArray(input.readInt())) }
            receiver.close()
            assertEquals(-1, input.read())
        }
        assertTrue(ended.await(2, TimeUnit.SECONDS))
    }

    @Test fun publicDestinationIsRejectedBeforeConnection() {
        assertFalse(LocalPairingTransport.isLocal(InetAddress.getByName("8.8.8.8")))
        assertTrue(runCatching { LocalPairingTransport.send(InetAddress.getByName("8.8.8.8"),80,"A".repeat(12), byteArrayOf(1)) }.exceptionOrNull() is IllegalArgumentException)
    }
}
