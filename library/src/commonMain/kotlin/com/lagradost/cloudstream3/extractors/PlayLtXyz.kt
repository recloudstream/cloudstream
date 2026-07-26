package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

open class PlayLtXyz : ExtractorApi() {
    override val name = "PlayLt"
    override val mainUrl = "https://play.playlt.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val extractedLinksList = mutableListOf<ExtractorLink>()
        var idUser = ""
        var idFile = ""
        var bodyText = ""
        val doc = app.get(url, referer = referer).document
        bodyText = doc.select("script").firstOrNull {
            val text = it.toString()
            text.contains("var idUser")
        }?.toString() ?: ""
        if (bodyText.isNotBlank()) {
            idUser = "(?<=var idUser = \")(.*)(?=\";)".toRegex().find(bodyText)
                ?.groupValues?.get(0) ?: ""

            idFile = "(?<=var idfile = \")(.*)(?=\";)".toRegex().find(bodyText)
                ?.groupValues?.get(0) ?: ""
        }

        if (idUser.isNotBlank() && idFile.isNotBlank()) {
            val ajaxHead = mapOf(
                Pair("Origin", mainUrl),
                Pair("Referer", mainUrl),
                Pair("Sec-Fetch-Site", "same-site"),
                Pair("Sec-Fetch-Mode", "cors"),
                Pair("Sec-Fetch-Dest", "empty"),
            )

            val ajaxData = mapOf(
                Pair("referrer", referer ?: mainUrl),
                Pair("typeend", "html"),
            )

            val postUrl = "https://api-plhq.playlt.xyz/apiv5/$idUser/$idFile"
            val data = app.post(postUrl, headers = ajaxHead, data = ajaxData)
            if (data.isSuccessful) {
                val itemStr = data.text
                Log.i(this.name, "Result => (data) $itemStr")
                tryParseJson<ResponseData>(itemStr)?.let { item ->
                    val linkUrl = item.data ?: ""
                    if (linkUrl.isNotBlank()) {
                        extractedLinksList.add(
                            newExtractorLink(
                                source = name,
                                name = name,
                                url = linkUrl,
                                type = ExtractorLinkType.M3U8,
                            ) {
                                this.referer = url
                                this.quality = Qualities.Unknown.value
                            }
                        )
                    }
                }
            }
        }

        return extractedLinksList
    }

    @Serializable
    private data class ResponseData(
        @JsonProperty("data") @SerialName("data") val data: String? = null,
    )
}
