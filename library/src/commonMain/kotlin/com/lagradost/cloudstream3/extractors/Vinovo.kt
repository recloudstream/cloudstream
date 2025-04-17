package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class VinovoSi : VinovoTo() {
    override var name = "VinovoSi"
    override var mainUrl = "https://vinovo.si"
}

open class VinovoTo : ExtractorApi() {
    override val mainUrl: String = "https://vinovo.to"
    override val name: String = "VinovoTo"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixedUrl = url.replace("/d/", "/e/")

        val resp = app.get(fixedUrl, referer = referer)
        val doc = resp.document

        val videoBaseUrl = doc.selectFirst("video")?.attr("data-base") ?: return
        val videoToken = doc.selectFirst("meta[name=token]")?.attr("content") ?: return
        val fileCode = doc.selectFirst("meta[name=\"file_code\"]")?.attr("content") ?: return

        val captchaToken = doc.selectFirst("meta[name=recaptcha]")?.attr("content") ?: return
        val captchaSolution =
            APIHolder.getCaptchaToken(fixedUrl, captchaToken, "$mainUrl/") ?: return

        val streamInfo = app.post(
            url = "$mainUrl/api/file/url/$fileCode",
            data = mapOf("token" to videoToken, "recaptcha" to captchaSolution),
            headers = mapOf(
                "Origin" to mainUrl,
                "X-Requested-With" to "XMLHttpRequest"
            ),
            cookies = resp.cookies,
            referer = fixedUrl
        ).parsed<VinovoFileResp>()
        val fileUrl = "$videoBaseUrl/stream/${streamInfo.token}"

        callback.invoke(
            newExtractorLink(source = name, name = name, url = fileUrl) {
                val dataTitle = doc.selectFirst("video")?.attr("data-title").orEmpty()
                quality = Regex("""\.(\d+)p\.""").find(dataTitle)?.groupValues?.getOrNull(1)
                    ?.toIntOrNull() ?: Qualities.Unknown.value
            }
        )
    }

    private data class VinovoFileResp(
        @JsonProperty("status") val status: String,
        @JsonProperty("token") val token: String,
    )
}