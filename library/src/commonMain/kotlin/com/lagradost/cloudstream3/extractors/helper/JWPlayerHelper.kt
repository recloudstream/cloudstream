package com.lagradost.cloudstream3.extractors.helper

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Prerelease
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.collections.orEmpty

object JwPlayerHelper {
    private val sourceRegex = Regex(""""?sources"?:\s*(\[.*?\])""")
    private val tracksRegex = Regex(""""?tracks"?:\s*(\[.*?\])""")
    private val m3u8Regex = Regex("""[:=]\s*\"([^\"\s]+(\.m3u8|master\.txt)[^\"\s]*)""")

    /**
     * Get stream links the "sources" attribute inside a JWPlayer script, e.g.
     *
     * ```js
     * <script>
     * jwplayer("vplayer").setup({
     *     sources: [{
     *         file: "https://example.com/master.m3u8",
     *     }],
     *     tracks: [{
     *         file: "https://example.com/subtitles.vtt",
     *         kind: "captions",
     *         label: "en",
     *     }],
     * }
     * ```
     *
     *  @param script The content of a HTML <script> tag containing the jwplayer code.
     *  @return whether any extractor or subtitle link was found
     */
    suspend fun extractStreamLinks(
        script: String,
        sourceName: String,
        mainUrl: String,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit,
        headers: Map<String, String> = mapOf(),
    ): Boolean {
        val sourceMatches = sourceRegex.findAll(script).flatMap { sourceMatch ->
            val match = sourceMatch.groupValues[1].addMarks("file", "label", "type")
            tryParseJson<List<Source>>(match).orEmpty()
        }.toList()

        var extractedLinks = sourceMatches.flatMap { link ->
            val cleanUrl = link.file.replace("\\/", "/")
            if (cleanUrl.contains(".m3u8") || cleanUrl.contains(".txt")) {
                try {
                    M3u8Helper.generateM3u8(
                        source = sourceName,
                        streamUrl = cleanUrl,
                        referer = mainUrl,
                        headers = headers,
                    )
                } catch (e: Exception) {
                    Log.d("JW_PLAYER_HELPER", "Error generating M3U8 links: ${e.message}")
                    emptyList()
                }
            } else {
                listOf(
                    newExtractorLink(
                        source = sourceName,
                        name = sourceName,
                        url = fixUrl(cleanUrl, mainUrl),
                    ) {
                        this.referer = url
                        this.headers = headers
                    }
                )
            }
        }

        /**
         * Fallback to searching for HLS streams, e.g.
         *
         * ```js
         * var links = {
         *     "hls2": "https://mmmmmmmmmm.qqqqqqqqqqqq.com/hls2/01/00000/ggggggggg_l/master.m3u8?t=##################&s=123456",
         *     "hls3": "https://mmmmmmmmmm.qqqqqqqqqqqq.space/#########/hls3/01/00000/ggggggggg_l/master.txt",
         *     "hls4": "/stream/zzzzzzzzzzzzzzz/hhhhhhhhhhh/123456789/123456/master.m3u8",
         * };
         *
         * jwplayer("vplayer").setup({
         *     sources: [{
         *         file: links.hls4 || links.hls3 || links.hls2,
         *         type: "hls",
         *     }],
         * });
         * ```
         */
        if (extractedLinks.isEmpty()) {
            extractedLinks = m3u8Regex.findAll(script).toList().map { match ->
                val cleanUrl = match.groupValues[1].replace("\\/", "/")
                val isM3u8 = cleanUrl.contains(".m3u8") || cleanUrl.contains(".txt")
                newExtractorLink(
                    source = sourceName,
                    name = sourceName,
                    url = fixUrl(cleanUrl, mainUrl),
                    type = if (isM3u8) ExtractorLinkType.M3U8 else INFER_TYPE,
                ) {
                    this.referer = url
                    this.headers = headers
                }
            }
        }

        val tracksMatches = tracksRegex.findAll(script).flatMap { trackMatch ->
            val match = trackMatch.groupValues[1].addMarks("file", "kind", "label")
            tryParseJson<List<Track>>(match).orEmpty()
        }.toList()

        val subtitleFiles = tracksMatches.filter {
            (it.kind.orEmpty().contains("caption") || it.kind.orEmpty()
                .contains("subtitle")) && it.file != null && it.label != null
        }.map {
            newSubtitleFile(
                lang = it.label!!,
                url = fixUrl(it.file!!, mainUrl),
            )
        }

        extractedLinks.forEach { callback.invoke(it) }
        subtitleFiles.forEach { subtitleCallback.invoke(it) }
        return sourceMatches.isNotEmpty() || subtitleFiles.isNotEmpty()
    }

    fun canParseJwScript(script: String): Boolean {
        return sourceRegex.containsMatchIn(script)
    }

    private fun fixUrl(url: String, mainUrl: String): String {
        return when {
            url.startsWith("/") -> mainUrl + url
            url.startsWith("http") -> url
            else -> "$mainUrl/$url"
        }
    }

    private fun String.addMarks(vararg strings: String): String {
        return strings.fold(this) { accumulator, str ->
            accumulator.replace(Regex("\"?$str\"?"), "\"$str\"")
        }
    }

    @Serializable
    private data class Source(
        @JsonProperty("file") @SerialName("file") val file: String,
        @JsonProperty("label") @SerialName("label") val label: String?,
        @JsonProperty("type") @SerialName("type") val type: String?,
    )

    @Serializable
    data class Track(
        @JsonProperty("file") @SerialName("file") val file: String? = null,
        @JsonProperty("label") @SerialName("label") val label: String? = null,
        @JsonProperty("kind") @SerialName("kind") val kind: String? = null,
    )
}
