package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.helper.*
import com.lagradost.cloudstream3.extractors.helper.AesHelper.cryptoAESHandler
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

class Moviesapi : Chillx() {
    override val name = "Moviesapi"
    override val mainUrl = "https://w1.moviesapi.club"
}

class Bestx : Chillx() {
    override val name = "Bestx"
    override val mainUrl = "https://bestx.stream"
}

class Watchx : Chillx() {
    override val name = "Watchx"
    override val mainUrl = "https://watchx.top"
}

open class Chillx : ExtractorApi() {
    override val name = "Chillx"
    override val mainUrl = "https://chillx.top"
    override val requiresReferer = true
    private var key: String? = null

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val master = Regex("\\s*=\\s*'([^']+)").find(
            app.get(
                url,
                referer = referer ?: "",
                headers = mapOf(
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                    "Accept-Language" to "en-US,en;q=0.5",
                )
            ).text
        )?.groupValues?.get(1)
        val decrypt = cryptoAESHandler(master ?: return, getKey().toByteArray(), false)?.replace("\\", "") ?: throw ErrorLoadingException("failed to decrypt")

        val source = Regex(""""?file"?:\s*"([^"]+)""").find(decrypt)?.groupValues?.get(1)
        val tracks = Regex("""tracks:\s*\[(.+)]""").find(decrypt)?.groupValues?.get(1)

        // required
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to mainUrl,
        )

        M3u8Helper.generateM3u8(
            name,
            source ?: return,
            "$mainUrl/",
            headers = headers
        ).forEach(callback)

        AppUtils.tryParseJson<List<Tracks>>("[$tracks]")
            ?.filter { it.kind == "captions" }?.map { track ->
                subtitleCallback.invoke(
                    SubtitleFile(
                        track.label ?: "",
                        track.file ?: return@map null
                    )
                )
            }
    }

    suspend fun getKey() = key ?: fetchKey().also { key = it }

    private suspend fun fetchKey(): String {
        return app.get("https://raw.githubusercontent.com/Sofie99/Resources/main/chillix_key.json").parsed()
    }

    data class Tracks(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("kind") val kind: String? = null,
    )
}
