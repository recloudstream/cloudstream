package com.lagradost.cloudstream3.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

class AppProxySelectorTest {
    @Test
    fun `parses host and port`() {
        assertEquals("192.168.1.10" to 8080, AppProxySelector.parseAddress("192.168.1.10:8080"))
        assertEquals("proxy.lan" to 1080, AppProxySelector.parseAddress(" proxy.lan:1080 "))
        assertEquals("fd00::1" to 1080, AppProxySelector.parseAddress("[fd00::1]:1080"))
    }

    @Test
    fun `rejects anything but host and port`() {
        listOf(
            "",
            "192.168.1.10",
            "192.168.1.10:",
            "192.168.1.10:0",
            "192.168.1.10:65536",
            "192.168.1.10:port",
            ":8080",
            "fd00::1:1080",
            "http://192.168.1.10:8080",
            "user:password@192.168.1.10:8080",
            "192.168.1.10:8080/path",
            "192.168.1.10:8080?query",
            "proxy lan:8080",
        ).forEach { address ->
            assertNull(address, AppProxySelector.parseAddress(address))
        }
    }

    @Test
    fun `detects local addresses`() {
        listOf(
            "localhost", "127.0.0.1", "[::1]", "10.0.0.2", "172.16.5.4",
            "192.168.1.10", "169.254.1.1", "[fe80::1]",
        ).forEach { host -> assertTrue(host, AppProxySelector.isLocalAddress(host)) }

        listOf(
            "example.com", "8.8.8.8", "172.32.0.1", "[2606:4700::1111]", "192.168.1.10.example.com",
        ).forEach { host -> assertFalse(host, AppProxySelector.isLocalAddress(host)) }
    }

    @Test
    fun `proxies remote requests only`() {
        val selector = AppProxySelector(Proxy.Type.SOCKS, "192.168.1.10", 1080)
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("192.168.1.10", 1080))

        assertEquals(listOf(proxy), selector.select(URI("https://example.com/video.m3u8")))
        assertEquals(listOf(Proxy.NO_PROXY), selector.select(URI("http://127.0.0.1:8090/echo")))
        assertEquals(listOf(Proxy.NO_PROXY), selector.select(URI("http://192.168.1.20:8096/")))
    }
}
