package com.lagradost.cloudstream3.network

import android.util.Log
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import com.lagradost.nicehttp.ignoreAllSSLErrors
import android.content.Context
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.mapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.lagradost.cloudstream3.R

private const val TAG = "VpnProviders"

// ─────────────────────────────────────────────────────────────────────────────
// Data model
// ─────────────────────────────────────────────────────────────────────────────

enum class VpnSource { NONE, PROXIFLY }

data class VpnServer(
    val name: String,
    val host: String,
    val port: Int,
    val source: VpnSource,
    /** Confirmed working proxy port (set after testProxyWithOkHttp passes). */
    val proxyPort: Int = port,
    /** Confirmed working proxy protocol (set after testProxyWithOkHttp passes). */
    val proxyType: Proxy.Type = Proxy.Type.SOCKS,
    val username: String? = null,
    val password: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// Proxifly SOCKS5 data source
//
// Raw JSON URL (updated every ~10 min by Proxifly CI):
//   https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/protocols/socks5/data.json
//
// JSON structure per entry:
//   { "ip":"1.2.3.4", "port":1080, "score":5, "geolocation":{"country":"US"} }
//
// Country filtering is done client-side (no per-country URL available).
// ─────────────────────────────────────────────────────────────────────────────

private const val PROXIFLY_SOCKS5_URL =
    "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/protocols/socks5/data.json"

/** Cache TTL: re-fetch the list after 1 hour. */
private const val PROXY_LIST_TTL_MS = 60 * 60 * 1_000L

private var cachedProxyList: List<Triple<String, Int, String>>? = null // host, port, countryCode
private var proxyCachedAt: Long = 0L

/**
 * Fetches and caches the full Proxifly SOCKS5 list.
 * Each entry is (ip, port, countryCode).
 * Results are sorted descending by score so high-quality proxies are tried first.
 */
private suspend fun fetchProxiflyList(): List<Triple<String, Int, String>> =
    withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedProxyList
        if (cached != null && (now - proxyCachedAt) < PROXY_LIST_TTL_MS) {
            return@withContext cached
        }
        try {
            val conn = java.net.URL(PROXIFLY_SOCKS5_URL).openConnection()
                    as java.net.HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout    = 10_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            val json = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(json)

            data class Entry(val ip: String, val port: Int, val country: String, val score: Int)

            val entries = mutableListOf<Entry>()
            for (i in 0 until arr.length()) {
                val obj     = arr.getJSONObject(i)
                val ip      = obj.optString("ip", "")
                val port    = obj.optInt("port", 0)
                val score   = obj.optInt("score", 0)
                val country = obj.optJSONObject("geolocation")?.optString("country", "") ?: ""
                if (ip.isNotEmpty() && port > 0) {
                    entries += Entry(ip, port, country.uppercase(), score)
                }
            }

            val sorted = entries.sortedByDescending { it.score }
                .map { Triple(it.ip, it.port, it.country) }

            Log.d(TAG, "Fetched ${sorted.size} Proxifly SOCKS5 entries")
            cachedProxyList = sorted
            proxyCachedAt   = now
            sorted
        } catch (e: Exception) {
            logError(e)
            Log.w(TAG, "Failed to fetch Proxifly list: ${e.message}")
            cachedProxyList ?: emptyList()
        }
    }

/**
 * Returns all SOCKS5 server candidates from Proxifly,
 * optionally filtered by [countryCode] (ISO 3166-1 alpha-2, e.g. "US").
 * Pass null to get the best proxies from any country.
 */
suspend fun fetchProxiflyServers(countryCode: String? = null): List<VpnServer> {
    val list = fetchProxiflyList()
    val filtered = if (countryCode == null) list
    else list.filter { it.third.equals(countryCode, ignoreCase = true) }

    val label = countryCode ?: "Any"
    return filtered.map { (ip, port, cc) ->
        VpnServer(
            name      = "SOCKS5 \u2013 $label ($ip)",
            host      = ip,
            port      = port,
            source    = VpnSource.PROXIFLY,
            proxyPort = port,
            proxyType = Proxy.Type.SOCKS,
        )
    }.also {
        Log.d(TAG, "Candidates for country=$label: ${it.size}")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OkHttp integration
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Applies a SOCKS5 proxy to this [OkHttpClient.Builder] so all CloudStream
 * HTTP traffic is routed through [server].
 */
fun OkHttpClient.Builder.addVpnProxy(server: VpnServer): OkHttpClient.Builder {
    val proxy = Proxy(server.proxyType, InetSocketAddress.createUnresolved(server.host, server.proxyPort))
    proxy(proxy)
    if (server.username != null && server.password != null) {
        proxyAuthenticator { _, response ->
            response.request.newBuilder()
                .header("Proxy-Authorization",
                    okhttp3.Credentials.basic(server.username, server.password))
                .build()
        }
    }
    return this
}

// ─────────────────────────────────────────────────────────────────────────────
// Dynamic Proxy Discovery
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns a list of available proxy countries and their counts.
 * The first item is always ("ANY", totalCount).
 * The rest are specific countries sorted by count (descending).
 */
suspend fun getVpnCountryChoices(): List<Pair<String, Int>> {
    val list = fetchProxiflyList()
    val total = list.size
    val grouped = list.groupingBy { it.third }.eachCount()
    val choices = mutableListOf<Pair<String, Int>>()
    choices.add("ANY" to total)
    choices.addAll(
        grouped.entries
            .filter { it.key.isNotBlank() }
            .map { it.key to it.value }
            .sortedByDescending { it.second }
    )
    return choices
}

// ─────────────────────────────────────────────────────────────────────────────
// Live proxy cache  (read by buildDefaultClient without async calls)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The currently active, tested SOCKS5 proxy.
 * Null = no proxy applied. Set by [resolveAndTestVpnServer].
 *
 * [@Volatile] ensures the write from the IO coroutine is immediately visible
 * to any thread reading it (e.g. CS3IPlayer on the main thread deciding
 * whether to bypass Cronet in favour of OkHttp).
 */
@Volatile
var currentVpnServer: VpnServer? = null

/**
 * Sets JVM-level system proxy properties so ALL Java socket connections
 * (HttpURLConnection, any plain Socket, etc.) route through the SOCKS5 proxy,
 * not just OkHttp clients that have an explicit proxy configured.
 */
fun setGlobalJvmProxy(host: String, port: Int) {
    System.setProperty("socksProxyHost", host)
    System.setProperty("socksProxyPort", port.toString())
    Log.d(TAG, "Global JVM SOCKS5 proxy set: $host:$port")
}

/** Removes the JVM-level SOCKS5 proxy properties. */
fun clearGlobalJvmProxy() {
    System.clearProperty("socksProxyHost")
    System.clearProperty("socksProxyPort")
    Log.d(TAG, "Global JVM SOCKS5 proxy cleared")
}

/**
 * When true, the proxy probe ignores SSL certificate errors (expired/self-signed).
 * This allows MITM-style proxies to pass the test but means HTTPS traffic
 * can be intercepted by the proxy. Controlled by the user via Settings.
 */
@Volatile
var vpnAllowInsecure: Boolean = false

// ─────────────────────────────────────────────────────────────────────────────
// End-to-end proxy test using OkHttp
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tests a SOCKS5 proxy end-to-end:
 *  1. Builds a temporary OkHttpClient with the proxy configured.
 *  2. GETs http://connectivitycheck.gstatic.com/generate_204 through it.
 *  3. Returns true only if the response code is 2xx (typically 204).
 *
 * This tests actual traffic forwarding — no fake handshakes or false positives.
 */
suspend fun testProxyWithOkHttp(
    proxyType: Proxy.Type,
    host: String,
    port: Int,
    timeoutSec: Long = 10L,
    allowInsecure: Boolean = false,
): Boolean = withContext(Dispatchers.IO) {
    try {
        val proxy = Proxy(proxyType, InetSocketAddress.createUnresolved(host, port))
        val client = OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(timeoutSec, TimeUnit.SECONDS)
            .readTimeout(timeoutSec, TimeUnit.SECONDS)
            .writeTimeout(timeoutSec, TimeUnit.SECONDS)
            .apply { if (allowInsecure) ignoreAllSSLErrors() }
            .build()
        val response = client.newCall(
            Request.Builder()
                // Use HTTPS so secure-mode rejects MITM proxies (invalid/expired cert).
                // In allowInsecure mode SSL errors are ignored so MITM proxies pass too.
                .url("https://www.google.com/generate_204")
                .build()
        ).execute()
        val ok = response.code in 200..299
        response.close()
        Log.d(TAG, "OkHttp probe $host:$port [$proxyType] -> HTTP ${response.code} ok=$ok")
        ok
    } catch (e: Exception) {
        Log.v(TAG, "OkHttp probe $host:$port [$proxyType] failed: ${e.message}")
        false
    }
}

/**
 * Resolves and tests the VPN server for [countryCode].
 *
 * Fetches Proxifly SOCKS5 servers (filtered by country),
 * tests each one with a real OkHttp request, and stores the first working server
 * in [currentVpnServer].
 *
 * @return The first working [VpnServer], or null if no proxy succeeded.
 */
suspend fun resolveAndTestVpnServer(countryCode: String): VpnServer? {
    if (countryCode == "NONE" || countryCode.isBlank()) {
        currentVpnServer = null
        clearGlobalJvmProxy()
        return null
    }
    val allowInsecure = vpnAllowInsecure
    val filterCountry = if (countryCode == "ANY") null else countryCode
    val candidates  = fetchProxiflyServers(filterCountry)

    if (candidates.isEmpty()) {
        Log.w(TAG, "No Proxifly candidates for country=$countryCode")
        currentVpnServer = null
        return null
    }

    Log.d(TAG, "Testing ${candidates.size} Proxifly proxies (country=$countryCode allowInsecure=$allowInsecure)…")
    for (server in candidates) {
        if (!testProxyWithOkHttp(Proxy.Type.SOCKS, server.host, server.port, allowInsecure = allowInsecure)) continue

        val verified = server.copy(proxyType = Proxy.Type.SOCKS, proxyPort = server.port)
        currentVpnServer = verified
        setGlobalJvmProxy(server.host, server.port)
        Log.d(TAG, "Proxy confirmed: ${server.host}:${server.port} | insecure=$allowInsecure")
        return verified
    }

    Log.w(TAG, "All ${candidates.size} Proxifly proxies failed for country=$countryCode")
    currentVpnServer = null
    clearGlobalJvmProxy()
    return null
}

/**
 * Called once on app launch to auto-restore the VPN connection.
 * If the user has a VPN enabled, it first tests the last-known working proxy.
 * If that proxy is offline, it searches for a new one in the selected region.
 */
fun initializeVpn(context: Context) {
    val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
    val vpnPref = settingsManager.getString(context.getString(R.string.vpn_country_pref_key), "NONE") ?: "NONE"
    if (vpnPref == "NONE" || vpnPref.isBlank()) return

    vpnAllowInsecure = settingsManager.getBoolean(context.getString(R.string.vpn_ssl_pref_key), false)

    GlobalScope.launch(Dispatchers.IO) {
        val savedJson = settingsManager.getString("vpn_last_server_json", null)
        var restoredServer: VpnServer? = null
        if (savedJson != null) {
            try {
                restoredServer = mapper.readValue<VpnServer>(savedJson)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse saved VPN server", e)
            }
        }

        if (restoredServer != null) {
            Log.d(TAG, "Testing saved VPN server on boot: ${restoredServer.host}:${restoredServer.port} insecure=$vpnAllowInsecure")
            val isAlive = testProxyWithOkHttp(Proxy.Type.SOCKS, restoredServer.host, restoredServer.port, allowInsecure = vpnAllowInsecure)
            if (isAlive) {
                currentVpnServer = restoredServer
                setGlobalJvmProxy(restoredServer.host, restoredServer.port)
                Log.d(TAG, "Saved VPN server restored successfully!")
                // Notify client to rebuild if needed
                com.lagradost.cloudstream3.app.initClient(context)
                return@launch
            }
            Log.w(TAG, "Saved VPN server is offline. Falling back to fresh search...")
        }

        // Saved proxy didn't work (or didn't exist). Find a new one.
        val newServer = resolveAndTestVpnServer(vpnPref)
        if (newServer != null) {
            try {
                settingsManager.edit().putString("vpn_last_server_json", mapper.writeValueAsString(newServer)).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save new VPN server JSON", e)
            }
            com.lagradost.cloudstream3.app.initClient(context)
        } else {
            Log.e(TAG, "Failed to auto-connect to any VPN proxy on boot.")
        }
    }
}
