package android.net

import java.net.URI
import java.net.URLDecoder

/** The parts of android.net.Uri extensions use, backed by java.net.URI */
open class Uri private constructor(private val raw: String) {
    private val uri: URI? = runCatching { URI(raw) }.getOrNull()

    open fun getScheme(): String? = uri?.scheme
    open fun getHost(): String? = uri?.host
    open fun getPort(): Int = uri?.port ?: -1
    open fun getAuthority(): String? = uri?.authority
    open fun getPath(): String? = uri?.path
    open fun getEncodedPath(): String? = uri?.rawPath
    open fun getQuery(): String? = uri?.query
    open fun getEncodedQuery(): String? = uri?.rawQuery
    open fun getFragment(): String? = uri?.fragment
    open fun getPathSegments(): List<String> = getPath()?.split('/')?.filter { it.isNotEmpty() } ?: emptyList()
    open fun getLastPathSegment(): String? = getPathSegments().lastOrNull()

    private fun queryPairs(): List<Pair<String, String>> =
        getEncodedQuery()?.split('&')?.filter { it.isNotEmpty() }?.map {
            val key = it.substringBefore('=')
            val value = if ('=' in it) it.substringAfter('=') else ""
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        } ?: emptyList()

    open fun getQueryParameter(key: String): String? = queryPairs().firstOrNull { it.first == key }?.second
    open fun getQueryParameters(key: String): List<String> = queryPairs().filter { it.first == key }.map { it.second }
    open fun getQueryParameterNames(): Set<String> = queryPairs().mapTo(LinkedHashSet()) { it.first }

    override fun toString(): String = raw
    override fun equals(other: Any?): Boolean = other is Uri && other.raw == raw
    override fun hashCode(): Int = raw.hashCode()

    companion object {
        @JvmField
        val EMPTY = Uri("")

        @JvmStatic
        fun parse(uriString: String): Uri = Uri(uriString)

        @JvmStatic
        fun encode(s: String?): String? = s?.let { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

        @JvmStatic
        fun decode(s: String?): String? = s?.let { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }
    }
}
