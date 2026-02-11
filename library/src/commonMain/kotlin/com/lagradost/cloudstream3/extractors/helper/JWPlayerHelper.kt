package com.lagradost.cloudstream3.extractors.helper

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.newExtractorLink
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
     *     sources: [{file:"https://example.com/master.m3u8"}],
     *     tracks: [{file: "https://example.com/subtitles.vtt", kind: "captions", label: "en"}],
     * }
     *  ```
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
        headers: Map<String, String> = mapOf()
    ): Boolean {
        val sourceMatches = sourceRegex.findAll(script).flatMap { sourceMatch ->
            val match = sourceMatch.groupValues[1]
                .addMarks("file")
                .addMarks("label")
                .addMarks("type")
            tryParseJson<List<Source>>(match).orEmpty()
        }.toList()

        val extractedLinks = sourceMatches.flatMap { link ->
            if (link.file.contains(".m3u8")) {
                try {
                    M3u8Helper.generateM3u8(
                        source = sourceName,
                        streamUrl = link.file,
                        referer = mainUrl,
                        headers = headers
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
                        url = fixUrl(link.file, mainUrl),
                    ) {
                        this.referer = url
                        this.headers = headers
                    }
                )
            }
        }

        // Fallback to searching for HLS streams, e.g.
        // var links = {
        //  "hls3": "https://mmmmmmmmmm.qqqqqqqqqqqq.space/#########/hls3/01/00000/ggggggggg_l/master.txt",
        //  "hls4": "/stream/zzzzzzzzzzzzzzz/hhhhhhhhhhh/123456789/123456/master.m3u8",
        //  "hls2": "https://mmmmmmmmmm.qqqqqqqqqqqq.com/hls2/01/00000/ggggggggg_l/master.m3u8?t=##################&s=123456"
        // };
        // jwplayer("vplayer").setup({
        //  sources: [{
        //    file: links.hls4 || links.hls3 || links.hls2,
        //    type: "hls"
        //  }],
        if (extractedLinks.isEmpty()) {
            m3u8Regex.findAll(script).toList().map { match ->
                val link = match.groupValues[1]

                newExtractorLink(
                    source = sourceName,
                    name = sourceName,
                    url = fixUrl(link, mainUrl)
                ) {
                    this.referer = url
                    this.headers = headers
                }
            }
        }

        val tracksMatches = tracksRegex.findAll(script).flatMap { trackMatch ->
            val match = trackMatch.groupValues[1]
                .addMarks("file")
                .addMarks("label")
                .addMarks("kind")
            tryParseJson<List<Track>>(match).orEmpty()
        }.toList()

        val subtitleFiles =
            tracksMatches.filter {
                (it.kind.orEmpty().contains("caption") || it.kind.orEmpty()
                    .contains("subtitle")) && it.file != null && it.label != null
            }.map {
                newSubtitleFile(
                    lang = it.label!!,
                    url = fixUrl(it.file!!, mainUrl)
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

    private fun String.addMarks(str: String): String {
        return this.replace(Regex("\"?$str\"?"), "\"$str\"")
    }

    private data class Source(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?,
        @JsonProperty("type") val type: String?,
    )

    data class Track(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("kind") val kind: String? = null,
    )
}