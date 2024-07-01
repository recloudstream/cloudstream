package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody


class Streamlare : Slmaxed() {
    override val mainUrl = "https://streamlare.com/"
}

open class Slmaxed : ExtractorApi() {
    override val name = "Streamlare"
    override val mainUrl = "https://slmaxed.com/"
    override val requiresReferer = true

    // https://slmaxed.com/e/oLvgezw3LjPzbp8E -> oLvgezw3LjPzbp8E
    val embedRegex = Regex("""/e/([^/]*)""")


    data class JsonResponse(
        @JsonProperty val status: String? = null,
        @JsonProperty val message: String? = null,
        @JsonProperty val type: String? = null,
        @JsonProperty val token: String? = null,
        @JsonProperty val result: Map<String, Result>? = null
    )

    data class Result(
        @JsonProperty val label: String? = null,
        @JsonProperty val file: String? = null,
        @JsonProperty val type: String? = null
    )

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val id = embedRegex.find(url)!!.groupValues[1]
        val json = app.post(
            "${mainUrl}api/video/stream/get",
            requestBody = """{"id":"$id"}""".toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        ).parsed<JsonResponse>()
        return json.result?.mapNotNull {
            it.value.let { result ->
                ExtractorLink(
                    this.name,
                    this.name,
                    result.file ?: return@mapNotNull null,
                    url,
                    result.label?.replace("p", "", ignoreCase = true)?.trim()?.toIntOrNull()
                        ?: Qualities.Unknown.value,
                    isM3u8 = result.type?.contains("hls", ignoreCase = true) == true
                )
            }
        }
    }
}