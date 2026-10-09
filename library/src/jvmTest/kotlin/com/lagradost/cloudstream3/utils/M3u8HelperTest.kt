package com.lagradost.cloudstream3.utils

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class M3u8HelperTest {
    private lateinit var server: HttpServer
    private lateinit var host: String
    private val requestedPaths: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val segment = ByteArray(256) { (it or 0x80).toByte() }

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        host = "127.0.0.1:${server.address.port}"
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            requestedPaths += path
            val body = when (path) {
                "/a/b/index.m3u8" -> playlist(key = null).encodeToByteArray()
                "/a/b/encrypted.m3u8" -> playlist(key = "/keys/k.bin").encodeToByteArray()
                "/a/b/seg0.ts", "/root/seg1.ts", "/proto/seg2.ts" -> segment
                "/keys/k.bin" -> ByteArray(16)
                else -> null
            }
            val bytes = body ?: "404 not found".encodeToByteArray()
            exchange.sendResponseHeaders(if (body == null) 404 else 200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
    }

    private fun playlist(key: String?): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-TARGETDURATION:10")
        if (key != null) appendLine("#EXT-X-KEY:METHOD=AES-128,URI=\"$key\"")
        appendLine("#EXTINF:10.0,")
        appendLine("seg0.ts")
        appendLine("#EXTINF:10.0,")
        appendLine("/root/seg1.ts")
        appendLine("#EXTINF:10.0,")
        appendLine("//$host/proto/seg2.ts")
        appendLine("#EXT-X-ENDLIST")
    }

    @Test
    fun hslLazyResolvesSegmentUrlsAgainstPlaylistUrl() = runBlocking {
        val data = M3u8Helper2.hslLazy(
            M3u8Helper.M3u8Stream("http://$host/a/b/index.m3u8?token=abc"),
            requireAudio = false,
        )

        assertEquals(
            listOf(
                "http://$host/a/b/seg0.ts",
                "http://$host/root/seg1.ts",
                "http://$host/proto/seg2.ts",
            ),
            data.allTsLinks.map { it.url },
        )
        for (index in 0 until data.size) {
            assertContentEquals(segment, data.resolveLink(index))
        }
    }

    @Test
    fun hslLazyResolvesKeyUrlAgainstPlaylistUrl() = runBlocking {
        M3u8Helper2.hslLazy(
            M3u8Helper.M3u8Stream("http://$host/a/b/encrypted.m3u8"),
            requireAudio = false,
        )

        assertTrue("/keys/k.bin" in requestedPaths, "requested: $requestedPaths")
    }
}
