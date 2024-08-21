package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

class Tubeless : Voe() {
    override val name = "Tubeless"
    override val mainUrl = "https://tubelessceliolymph.com"
}

class Simpulumlamerop : Voe() {
    override val name = "Simplum"
    override var mainUrl = "https://simpulumlamerop.com"
}

class Urochsunloath : Voe() {
    override val name = "Uroch"
    override var mainUrl = "https://urochsunloath.com"
}

class Yipsu : Voe() {
    override val name = "Yipsu"
    override var mainUrl = "https://yip.su"
}

class MetaGnathTuggers : Voe() {
    override val name = "Metagnath"
    override val mainUrl = "https://metagnathtuggers.com"
}

class Voe1 : Voe() {
    override val mainUrl = "https://donaldlineelse.com"
}

open class Voe : ExtractorApi() {
    override val name = "Voe"
    override val mainUrl = "https://voe.sx"
    override val requiresReferer = true

    private val linkRegex =
        "(http|https)://([\\w_-]+(?:\\.[\\w_-]+)+)([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])".toRegex()
    private val base64Regex = Regex("'.*'")
    private val redirectRegex = Regex("""window.location.href = '([^']+)';""")

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url, referer = referer).document

        val script =
            if (!res.select("script").firstOrNull() { it.data().contains("sources =") }?.data()
                    .isNullOrEmpty()
            ) {
                res.select("script").find { it.data().contains("sources =") }?.data()
            } else {
                redirectRegex.find(res.data())?.groupValues?.get(1)?.let { redirectUrl ->
                    app.get(
                        redirectUrl,
                        referer = referer
                    ).document.select("script").find { it.data().contains("sources =") }?.data()
                }
            }

        val link =
            Regex("[\"']hls[\"']:\\s*[\"'](.*)[\"']").find(script ?: return)?.groupValues?.get(1)

        val videoLinks = mutableListOf<String>()

        if (!link.isNullOrBlank()) {
            videoLinks.add(
                when {
                    linkRegex.matches(link) -> link
                    else -> base64Decode(link)
                }
            )
        } else {
            val link2 = base64Regex.find(script)?.value ?: return
            val decoded = base64Decode(link2)
            val videoLinkDTO = AppUtils.parseJson<WcoSources>(decoded)
            videoLinkDTO.let { videoLinks.add(it.toString()) }
        }

        videoLinks.forEach { videoLink ->
            M3u8Helper.generateM3u8(
                name,
                videoLink,
                "$mainUrl/",
                headers = mapOf("Origin" to "$mainUrl/")
            ).forEach(callback)
        }
    }

    data class WcoSources(
        @JsonProperty("VideoLinkDTO") val VideoLinkDTO: String,
    )
}
