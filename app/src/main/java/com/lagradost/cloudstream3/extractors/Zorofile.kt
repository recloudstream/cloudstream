package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.getCaptchaToken
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

open class Zorofile : ExtractorApi() {
    override val name = "Zorofile"
    override val mainUrl = "https://zorofile.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.split("?").first().split("/").last()
        val token = app.get(
            url,
            referer = referer
        ).document.select("button.g-recaptcha").attr("data-sitekey").let { captchaKey ->
            getCaptchaToken(
                url,
                captchaKey,
                referer = referer
            )
        } ?: throw ErrorLoadingException("can't bypass captcha")

        val data = app.post(
            "$mainUrl/dl",
            data = mapOf(
                "op" to "embed",
                "file_code" to id,
                "auto" to "1",
                "referer" to "$referer/",
                "g-recaptcha-response" to token
            ),
            referer = url,
            headers = mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                "Content-Type" to "application/x-www-form-urlencoded",
                "Origin" to mainUrl,
                "Sec-Fetch-Dest" to "iframe",
                "Sec-Fetch-Mode" to "navigate",
                "Sec-Fetch-Site" to "same-origin",
                "Sec-Fetch-User" to "?1",
                "Upgrade-Insecure-Requests" to "1",
            )
        ).document.select("script").find { it.data().contains("var holaplayer;") }?.data()
            ?.substringAfter("sources: [")?.substringBefore("],")?.replace("src", "\"src\"")
            ?.replace("type", "\"type\"")

        tryParseJson<Sources>("$data")?.let { res ->
            return M3u8Helper.generateM3u8(
                name,
                res.src ?: return@let,
                "$mainUrl/",
                headers = mapOf(
                    "Origin" to mainUrl,
                )
            ).forEach(callback)
        }
    }

    private data class Sources(
        @JsonProperty("src") val src: String? = null,
        @JsonProperty("type") val type: String? = null,
    )
}