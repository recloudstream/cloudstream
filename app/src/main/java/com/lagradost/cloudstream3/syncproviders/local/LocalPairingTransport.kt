package com.lagradost.cloudstream3.syncproviders.local

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object LocalPairingTransport {
    private const val MAGIC = 0x43533331
    private const val MAX_PAYLOAD = 65_536
    private val random = SecureRandom()
    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private fun keys(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"), random)
    }.generateKeyPair()

    private fun key(own: KeyPair, remote: ByteArray, code: String, salt: ByteArray, receiver: ByteArray, sender: ByteArray): SecretKeySpec {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(own.private)
        agreement.doPhase(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(remote)), true)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(agreement.generateSecret()); digest.update(code.toByteArray(Charsets.UTF_8))
        digest.update(salt); digest.update(receiver); digest.update(sender)
        return SecretKeySpec(digest.digest(), "AES")
    }

    private fun DataInputStream.frame(max: Int): ByteArray {
        val length = readInt()
        if (length !in 1..max) throw IOException("Invalid local transfer frame")
        return ByteArray(length).also { readFully(it) }
    }
    private fun DataOutputStream.frame(bytes: ByteArray) { writeInt(bytes.size); write(bytes); flush() }
    fun isLocal(address: InetAddress) = address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        (address.address.size == 16 && address.address[0].toInt().and(0xfe) == 0xfc)

    class Receiver(private val receive: (ByteArray) -> Boolean, private val ended: () -> Unit = {}) : Closeable {
        private val socket = ServerSocket(0).apply { soTimeout = 1000 }
        private val pair = keys()
        private val salt = ByteArray(32).also(random::nextBytes)
        private val open = AtomicBoolean(true)
        private val activePeer = AtomicReference<Socket?>(null)
        val port: Int get() = socket.localPort
        val code = (1..12).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
        private val deadline = System.nanoTime() + 120_000_000_000L
        fun start() {
            Thread({
                var attempts = 0
                try {
                    while (open.get() && attempts < 5 && System.nanoTime() < deadline) {
                        val peer = try { socket.accept() } catch (_: SocketTimeoutException) { continue }
                        activePeer.set(peer)
                        peer.use {
                            if (!isLocal(peer.inetAddress)) return@use
                            attempts++
                            peer.soTimeout = 4000
                            try {
                                val input = DataInputStream(peer.getInputStream())
                                val output = DataOutputStream(peer.getOutputStream())
                                output.writeInt(MAGIC); output.frame(pair.public.encoded); output.frame(salt)
                                val sender = input.frame(256)
                                val iv = input.frame(12)
                                if (iv.size != 12) throw IOException("Invalid local transfer nonce")
                                val encrypted = input.frame(MAX_PAYLOAD + 16)
                                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                                cipher.init(Cipher.DECRYPT_MODE, key(pair, sender, code, salt, pair.public.encoded, sender), GCMParameterSpec(128, iv))
                                val plain = cipher.doFinal(encrypted)
                                val accepted = plain.size <= MAX_PAYLOAD && receive(plain)
                                output.writeBoolean(accepted); output.flush()
                                if (accepted) close()
                            } catch (_: Exception) { }
                        }
                    }
                } catch (_: IOException) { }
                finally { close(); ended() }
            }, "cloudstream-local-receive").apply { isDaemon = true }.start()
        }
        override fun close() {
            if (open.getAndSet(false)) socket.close()
            activePeer.getAndSet(null)?.let { runCatching { it.close() } }
        }
    }

    fun send(address: InetAddress, port: Int, code: String, payload: ByteArray): Boolean {
        val normalizedCode = code.uppercase(java.util.Locale.ROOT).filterNot { it.isWhitespace() || it == '-' }
        require(isLocal(address)) { "Only local network addresses are supported" }
        require(port in 1..65535 && normalizedCode.length == 12 && normalizedCode.all { it in CODE_ALPHABET } && payload.size in 1..MAX_PAYLOAD)
        Socket().use { socket ->
            socket.connect(InetSocketAddress(address, port), 4000); socket.soTimeout = 5000
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())
            if (input.readInt() != MAGIC) throw IOException("Unsupported local transfer version")
            val receiver = input.frame(256)
            val salt = input.frame(32)
            require(salt.size == 32)
            val pair = keys()
            val iv = ByteArray(12).also(random::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(pair, receiver, normalizedCode, salt, receiver, pair.public.encoded), GCMParameterSpec(128, iv))
            output.frame(pair.public.encoded); output.frame(iv); output.frame(cipher.doFinal(payload))
            return input.readBoolean()
        }
    }
}
