package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlin.math.round

open class Gofile : ExtractorApi() {
    override val name = "Gofile"
    override val mainUrl = "https://gofile.io"
    override val requiresReferer = false
    private val mainApi = "https://api.gofile.io"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = Regex("/(?:\\?c=|d/)([\\da-zA-Z-]+)").find(url)?.groupValues?.get(1) ?: return

        val token = app.post(
            "$mainApi/accounts",
        ).parsedSafe<AccountResponse>()?.data?.token ?: return

        val globalRes = app.get("$mainUrl/dist/js/config.js").text
        val wt = Regex("""appdata\.wt\s*=\s*[\"']([^\"']+)[\"']""").find(globalRes)?.groupValues?.get(1) ?: return

        val headers = mapOf(
            "Authorization" to "Bearer $token",
            "X-Website-Token" to wt
        )

        val parsedResponse = app.get(
            "$mainApi/contents/$id?contentFilter=&page=1&pageSize=1000&sortField=name&sortDirection=1",
            headers = headers
        ).parsedSafe<GofileResponse>()

        val childrenMap = parsedResponse?.data?.children ?: return

        for ((_, file) in childrenMap) {
            if (file.link.isNullOrEmpty() || file.type != "file") continue
            val fileName = file.name ?: ""
            val size = file.size ?: 0L
            val formattedSize = formatBytes(size)

            callback.invoke(
                newExtractorLink(
                    "Gofile",
                    "[Gofile] $fileName [$formattedSize]",
                    file.link,
                    ExtractorLinkType.VIDEO
                ) {
                    this.quality = getQuality(fileName)
                    this.headers = mapOf("Cookie" to "accountToken=$token")
                }
            )
        }
    }

    private fun getQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    private fun roundTo2Decimals(value: Double): String {
        val rounded = round(value * 100) / 100.0
        val intPart = rounded.toLong()
        val decPart = round((rounded - intPart) * 100).toLong()
        return "$intPart.${decPart.toString().padStart(2, '0')}"
    }

    private fun formatBytes(bytes: Long): String {
        val mb = 1024L * 1024
        val gb = mb * 1024
        return when {
            bytes < gb -> "${roundTo2Decimals(bytes.toDouble() / mb)} MB"
            else -> "${roundTo2Decimals(bytes.toDouble() / gb)} GB"
        }
    }

    data class AccountResponse(
        @JsonProperty("data") val data: AccountData? = null
    )

    data class AccountData(
        @JsonProperty("token") val token: String? = null
    )

    data class GofileResponse(
        @JsonProperty("data") val data: GofileData? = null
    )

    data class GofileData(
        @JsonProperty("children") val children: Map<String, GofileFile>? = null
    )

    data class GofileFile(
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("link") val link: String? = null,
        @JsonProperty("size") val size: Long? = 0L
    )
}
