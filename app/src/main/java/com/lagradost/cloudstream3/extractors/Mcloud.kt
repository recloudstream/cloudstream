package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.WcoStream.Companion.cipher
import com.lagradost.cloudstream3.extractors.WcoStream.Companion.encrypt
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8

open class Mcloud : ExtractorApi() {
    override var name = "Mcloud"
    override var mainUrl = "https://mcloud.to"
    override val requiresReferer = true
    val headers = mapOf(
        "Host" to "mcloud.to",
        "User-Agent" to USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
        "DNT" to "1",
        "Connection" to "keep-alive",
        "Upgrade-Insecure-Requests" to "1",
        "Sec-Fetch-Dest" to "iframe",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "cross-site",
        "Referer" to "https://animekisa.in/", //Referer works for wco and animekisa, probably with others too
        "Pragma" to "no-cache",
        "Cache-Control" to "no-cache",)
    private val key = "LCbu3iYC7ln24K7P" // key credits @Modder4869
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val id = url.substringAfter("e/").substringAfter("embed/").substringBefore("?")
        val encryptedid = encrypt(cipher(key, encrypt(id))).replace("/", "_").replace("=","")
        val link = "$mainUrl/info/$encryptedid"
        val response = app.get(link, headers = headers).text
        if(response.startsWith("<!DOCTYPE html>")) {
            // TODO decrypt html for link
            return emptyList()
        }
        data class SourcesMcloud (
            @JsonProperty("file" ) val file : String
        )

        data class MediaMcloud (
            @JsonProperty("sources" ) val sources : ArrayList<SourcesMcloud> = arrayListOf()
        )

        data class DataMcloud (
            @JsonProperty("media" ) val media : MediaMcloud? = MediaMcloud()
        )

        data class JsonMcloud (
            @JsonProperty("status" ) val status : Int?  = null,
            @JsonProperty("data"   ) val data   : DataMcloud = DataMcloud()
        )

        val mapped = parseJson<JsonMcloud>(response)
        val sources = mutableListOf<ExtractorLink>()
        val checkfile = mapped.status == 200
        if (checkfile)
            mapped.data.media?.sources?.apmap {
                if (it.file.contains("m3u8")) {
                    sources.addAll(
                        generateM3u8(
                            name,
                            it.file,
                            url,
                            headers = mapOf("Referer" to url)
                        )
                    )
                }
            }
        return sources
    }
}