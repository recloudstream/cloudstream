package com.lagradost.cloudstream4.desktop.player

import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A localhost-only HTTP proxy that attaches the headers an extension asks for (referer, cookies, origin...)
 * to every request of a stream. VLC and external players cannot send arbitrary headers, so streams are
 * played through this instead. HLS playlists are rewritten so segments and keys also go through the proxy.
 */
object StreamProxy {
    private val sessions = ConcurrentHashMap<String, Map<String, String>>()

    private val client: OkHttpClient by lazy {
        app.baseClient.newBuilder()
            .followRedirects(true)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val server: HttpServer by lazy {
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/stream") { exchange ->
                try {
                    handle(exchange)
                } catch (t: Throwable) {
                    logError(t)
                    runCatching { exchange.sendResponseHeaders(502, -1) }
                } finally {
                    exchange.close()
                }
            }
            executor = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "StreamProxy").apply { isDaemon = true }
            }
            start()
        }
    }

    private val port: Int get() = server.address.port

    /**
     * @return a localhost url that serves [url] with [headers] applied. [referer] is folded into the headers.
     */
    fun proxy(url: String, headers: Map<String, String>, referer: String?): String {
        val all = LinkedHashMap<String, String>()
        all["User-Agent"] = USER_AGENT
        if (!referer.isNullOrBlank()) all["Referer"] = referer
        // Extension headers take priority, regardless of the casing they used
        headers.forEach { (key, value) ->
            all.keys.firstOrNull { it.equals(key, ignoreCase = true) }?.let { all.remove(it) }
            all[key] = value
        }
        val session = UUID.randomUUID().toString()
        sessions[session] = all
        return urlFor(session, url)
    }

    private fun urlFor(session: String, target: String): String =
        "http://127.0.0.1:$port/stream?s=$session&u=${URLEncoder.encode(target, Charsets.UTF_8)}"

    private fun query(exchange: HttpExchange): Map<String, String> =
        exchange.requestURI.rawQuery.orEmpty().split('&').mapNotNull { part ->
            val index = part.indexOf('=')
            if (index == -1) null
            else part.substring(0, index) to URLDecoder.decode(part.substring(index + 1), Charsets.UTF_8)
        }.toMap()

    private fun isPlaylist(url: String, contentType: String?): Boolean {
        val type = contentType?.lowercase().orEmpty()
        return type.contains("mpegurl") || url.substringBefore('?').lowercase().endsWith(".m3u8")
    }

    private fun handle(exchange: HttpExchange) {
        val params = query(exchange)
        val session = params["s"] ?: return exchange.sendResponseHeaders(400, -1)
        val target = params["u"] ?: return exchange.sendResponseHeaders(400, -1)
        val headers = sessions[session] ?: return exchange.sendResponseHeaders(404, -1)

        val request = Request.Builder().url(target).apply {
            headers.forEach { (key, value) -> header(key, value) }
            exchange.requestHeaders.getFirst("Range")?.let { header("Range", it) }
        }.build()

        client.newCall(request).execute().use { response ->
            val body = response.body
            val contentType = response.header("Content-Type")
            val finalUrl = response.request.url.toString()

            // Some hosts serve playlists as octet-stream, so also sniff the first bytes
            val peek = runCatching { response.peekBody(7).string() }.getOrDefault("")
            if (isPlaylist(finalUrl, contentType) || peek.startsWith("#EXTM3U")) {
                val rewritten = rewritePlaylist(session, finalUrl, body.string()).toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
                exchange.sendResponseHeaders(response.code, rewritten.size.toLong())
                exchange.responseBody.use { it.write(rewritten) }
                return
            }

            listOf("Content-Type", "Content-Range", "Accept-Ranges", "Content-Disposition").forEach { name ->
                response.header(name)?.let { exchange.responseHeaders.add(name, it) }
            }
            val length = body.contentLength()
            exchange.sendResponseHeaders(response.code, if (length > 0) length else 0)
            exchange.responseBody.use { out ->
                body.byteStream().use { it.copyTo(out, 64 * 1024) }
            }
        }
    }

    private val uriAttribute = Regex("URI=\"([^\"]+)\"")

    private fun rewritePlaylist(session: String, baseUrl: String, playlist: String): String {
        val base = baseUrl.toHttpUrlOrNull() ?: return playlist
        fun absolute(uri: String) = base.resolve(uri.trim())?.toString() ?: uri

        return playlist.lineSequence().joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                line.startsWith("#") -> uriAttribute.replace(line) { match ->
                    "URI=\"${urlFor(session, absolute(match.groupValues[1]))}\""
                }

                else -> urlFor(session, absolute(line))
            }
        }
    }
}
