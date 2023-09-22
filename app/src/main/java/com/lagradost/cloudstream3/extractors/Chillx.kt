package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.helper.*
import com.lagradost.cloudstream3.extractors.helper.AesHelper.cryptoAESHandler
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

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

    companion object {
        private const val KEY = "m4H6D9%0\$N&F6rQ&"
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val master = Regex("MasterJS\\s*=\\s*'([^']+)").find(
            app.get(
                url,
                referer = referer
            ).text
        )?.groupValues?.get(1)
        val decrypt = cryptoAESHandler(master ?: return, KEY.toByteArray(), false)?.replace("\\", "") ?: throw ErrorLoadingException("failed to decrypt")

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

        callback.invoke(
            ExtractorLink(
                name,
                name,
                source ?: return,
                "$mainUrl/",
                Qualities.P1080.value,
                headers = headers,
                isM3u8 = true
            )
        )

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

    data class Tracks(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("kind") val kind: String? = null,
    )
}
