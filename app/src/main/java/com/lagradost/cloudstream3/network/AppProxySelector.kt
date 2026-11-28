package com.lagradost.cloudstream3.network

import android.content.Context
import com.lagradost.cloudstream4.AppSettings
import com.lagradost.cloudstream4.ProxyType
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.UnknownHostException

/**
 * Routes requests through the proxy set in the settings, except requests to local addresses,
 * as those (like the torrent server on localhost) can not be reached through the proxy.
 */
class AppProxySelector(
    private val type: Proxy.Type,
    private val host: String,
    private val port: Int,
) : ProxySelector() {
    override fun select(uri: URI): List<Proxy> {
        val target = uri.host
        if (target == null || isLocalAddress(target)) return listOf(Proxy.NO_PROXY)
        // Resolved here with the system DNS, as DoH can not resolve local hostnames
        return listOf(Proxy(type, InetSocketAddress(host, port)))
    }

    override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) = Unit

    companion object {
        private val ipv4Regex = Regex("""\d{1,3}(\.\d{1,3}){3}""")

        /** Returns the proxy set in the settings, or null if none is set */
        fun fromSettings(context: Context): AppProxySelector? {
            val settings = AppSettings(context).general
            val type = when (settings.proxyType.get()) {
                ProxyType.None -> return null
                ProxyType.Http -> Proxy.Type.HTTP
                ProxyType.Socks -> Proxy.Type.SOCKS
            }
            val (host, port) = parseAddress(settings.proxyAddress.get()) ?: return null
            return AppProxySelector(type, host, port)
        }

        /** Parses "host:port", returns null if the address is invalid */
        fun parseAddress(address: String): Pair<String, Int>? {
            val uri = try {
                URI("proxy://${address.trim()}")
            } catch (_: URISyntaxException) {
                return null
            }
            val host = uri.host ?: return null
            val isHostAndPort = uri.port in 1..65535 && uri.rawUserInfo == null &&
                    uri.rawPath.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment == null
            return if (isHostAndPort) host.removeSurrounding("[", "]") to uri.port else null
        }

        /** True for localhost and loopback, LAN or link-local IPs. Hostnames are not resolved. */
        fun isLocalAddress(host: String): Boolean {
            if (host.equals("localhost", ignoreCase = true)) return true
            // URIs always wrap IPv6 addresses in brackets
            val isIp = (host.startsWith('[') && host.endsWith(']')) || ipv4Regex.matches(host)
            if (!isIp) return false
            val address = try {
                InetAddress.getByName(host.removeSurrounding("[", "]"))
            } catch (_: UnknownHostException) {
                return false
            }
            return address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress
        }
    }
}
