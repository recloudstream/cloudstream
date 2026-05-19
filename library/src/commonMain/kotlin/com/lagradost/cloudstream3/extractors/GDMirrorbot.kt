package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URI

class Techinmind : GDMirrorbot() {
    override var name = "Techinmind Cloud AIO"
    override var mainUrl = "https://stream.techinmind.space"
    override var requiresReferer = true
}

open class GDMirrorbot : ExtractorApi() {
    override var name = "GDMirrorbot"
    override var mainUrl = "https://gdmirrorbot.nl"
    override val requiresReferer = true

    private data class EmbedData(
        @JsonProperty("data") val data: List<FileSlug>? = null,
    )

    private data class FileSlug(
        @JsonProperty("fileslug") val fileslug: String? = null,
    )

    private data class EmbedHelper(
        @JsonProperty("siteUrls") val siteUrls: Map<String, String>? = null,
        @JsonProperty("siteFriendlyNames") val siteFriendlyNames: Map<String, String>? = null,
        @JsonProperty("mresult") val mresult: Any? = null,
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val (sid, host) = if (!url.contains("key=")) {
            Pair(url.substringAfterLast("embed/"), getBaseUrl(app.get(url).url))
        } else {
            var pageText = app.get(url).text
            val finalId = Regex("""FinalID\s*=\s*"([^"]+)"""").find(pageText)?.groupValues?.get(1)
            val myKey = Regex("""myKey\s*=\s*"([^"]+)"""").find(pageText)?.groupValues?.get(1)
            val idType = Regex("""idType\s*=\s*"([^"]+)"""").find(pageText)?.groupValues?.get(1) ?: "imdbid"
            val baseUrl = Regex("""let\s+baseUrl\s*=\s*"([^"]+)"""").find(pageText)?.groupValues?.get(1)
            val hostUrl = baseUrl?.let { getBaseUrl(it) }

            if (finalId != null && myKey != null) {
                val apiUrl = if (url.contains("/tv/")) {
                    val season = Regex("""/tv/\d+/(\d+)/""").find(url)?.groupValues?.get(1) ?: "1"
                    val episode = Regex("""/tv/\d+/\d+/(\d+)""").find(url)?.groupValues?.get(1) ?: "1"
                    "$mainUrl/myseriesapi?tmdbid=$finalId&season=$season&epname=$episode&key=$myKey"
                } else {
                    "$mainUrl/mymovieapi?$idType=$finalId&key=$myKey"
                }
                pageText = app.get(apiUrl).text
            }

            val embedData = tryParseJson<EmbedData>(pageText)
            val embedId = url.substringAfterLast("/")
            val sidValue = embedData?.data?.firstOrNull()?.fileslug
                ?.takeIf { it.isNotBlank() } ?: embedId

            Pair(sidValue, hostUrl)
        }

        val postData = mapOf("sid" to sid)
        val responseText = app.post("$host/embedhelper.php", data = postData).text

        val root = tryParseJson<EmbedHelper>(responseText) ?: return
        val siteUrls = root.siteUrls ?: return
        val siteFriendlyNames = root.siteFriendlyNames

        // mresult can arrive as a JSON object or a base64-encoded string
        val mresult: Map<String, String>? = run {
            val raw = responseText
                .substringAfter("\"mresult\":")
                .trimStart()
            when {
                raw.startsWith("\"") -> {
                    // base64-encoded string
                    tryParseJson<Map<String, String>>(
                        try { base64Decode(raw.trim('"')) } catch (_: Exception) { return }
                    )
                }
                raw.startsWith("{") -> tryParseJson<Map<String, String>>(
                    raw.substringBefore("\n}").substringBefore(",\n\"").let { "{$it}" }
                        .let { responseText.substringAfter("\"mresult\":").trimStart() }
                )
                else -> null
            }
        }
        if (mresult == null) return

        siteUrls.keys.intersect(mresult.keys).forEach { key ->
            val base = siteUrls[key]?.trimEnd('/') ?: return@forEach
            val path = mresult[key]?.trimStart('/') ?: return@forEach
            val fullUrl = "$base/$path"
            val friendlyName = siteFriendlyNames?.get(key) ?: key

            try {
                when (friendlyName) {
                    "StreamHG", "EarnVids" -> VidHidePro().getUrl(fullUrl, referer, subtitleCallback, callback)
                    "RpmShare", "UpnShare", "StreamP2p" -> VidStack().getUrl(fullUrl, referer, subtitleCallback, callback)
                    else -> loadExtractor(fullUrl, referer ?: mainUrl, subtitleCallback, callback)
                }
            } catch (e: Exception) {
                Log.e("GDMirrorbot", "Failed to extract from $friendlyName at $fullUrl: $e")
            }
        }
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}
