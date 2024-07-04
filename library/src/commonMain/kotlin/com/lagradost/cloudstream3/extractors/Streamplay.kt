package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.getCaptchaToken
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import java.net.URI

open class Streamplay : ExtractorApi() {
    override val name = "Streamplay"
    override val mainUrl = "https://streamplay.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val request = app.get(url, referer = referer)
        val redirectUrl = request.url
        val mainServer = URI(redirectUrl).let {
            "${it.scheme}://${it.host}"
        }
        val key = redirectUrl.substringAfter("embed-").substringBefore(".html")
        val token =
            request.document.select("script").find { it.data().contains("sitekey:") }?.data()
                ?.substringAfterLast("sitekey: '")?.substringBefore("',")?.let { captchaKey ->
                    getCaptchaToken(
                        redirectUrl,
                        captchaKey,
                        referer = "$mainServer/"
                    )
                } ?: throw ErrorLoadingException("can't bypass captcha")
        app.post(
            "$mainServer/player-$key-488x286.html", data = mapOf(
                "op" to "embed",
                "token" to token
            ),
            referer = redirectUrl,
            headers = mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                "Content-Type" to "application/x-www-form-urlencoded"
            )
        ).document.select("script").find { script ->
            script.data().contains("eval(function(p,a,c,k,e,d)")
        }?.let {
            val data = getAndUnpack(it.data()).substringAfter("sources=[").substringBefore(",desc")
                .replace("file", "\"file\"")
                .replace("label", "\"label\"")
            tryParseJson<List<Source>>("[$data}]")?.map { res ->
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name,
                        res.file ?: return@map null,
                        "$mainServer/",
                        when (res.label) {
                            "HD" -> Qualities.P720.value
                            "SD" -> Qualities.P480.value
                            else -> Qualities.Unknown.value
                        },
                        headers = mapOf(
                            "Range" to "bytes=0-"
                        )
                    )
                )
            }
        }

    }

    data class Source(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

}