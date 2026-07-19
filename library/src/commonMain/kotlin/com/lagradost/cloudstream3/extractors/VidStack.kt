package com.lagradost.cloudstream3.extractors

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.helper.AesHelper.hexToByteArray
import com.lagradost.cloudstream3.extractors.helper.AesHelper.rawAesCbc
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.cloudstream3.utils.newExtractorLink
import io.ktor.http.Url

class Server1uns : VidStack() {
    override var name = "Vidstack"
    override var mainUrl = "https://server1.uns.bio"
    override var requiresReferer = true
}

open class VidStack : ExtractorApi() {
    override var name = "Vidstack"
    override var mainUrl = "https://vidstack.io"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0")
        val hash = url.substringAfterLast("#").substringAfter("/")
        val baseurl = getBaseUrl(url)

        val encoded = app.get("$baseurl/api/v1/video?id=$hash", headers = headers).text.trim()

        val key = "kiemtienmua911ca"
        val ivList = listOf("1234567890oiuytr", "0123456789abcdef")

        val decryptedText = ivList.firstNotNullOfOrNull { iv ->
            try {
                rawAesCbc(
                    input = encoded.hexToByteArray(),
                    key = key.encodeToByteArray(),
                    iv = iv.encodeToByteArray(),
                    encrypt = false,
                    padding = true,
                ).decodeToString()
            } catch (_: Exception) {
                null
            }
        } ?: throw Exception("Failed to decrypt with all IVs")

        val m3u8 = Regex("\"source\":\"(.*?)\"").find(decryptedText)
            ?.groupValues?.get(1)
            ?.replace("\\/", "/") ?: ""
        val subtitlePattern = Regex("\"([^\"]+)\":\\s*\"([^\"]+)\"")
        val subtitleSection = Regex("\"subtitle\":\\{(.*?)\\}").find(decryptedText)?.groupValues?.get(1)

        subtitleSection?.let { section ->
            subtitlePattern.findAll(section).forEach { match ->
                val lang = match.groupValues[1]
                val rawPath = match.groupValues[2].split("#")[0]
                if (rawPath.isNotEmpty()) {
                    val path = rawPath.replace("\\/", "/")
                    val subUrl = "$mainUrl$path"
                    subtitleCallback(newSubtitleFile(lang, fixUrl(subUrl)))
                }
            }
        }

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = m3u8,
                type = ExtractorLinkType.M3U8,
            ) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }

    private fun getBaseUrl(url: String): String {
        return try {
            Url(url).let { "${it.protocol.name}://${it.host}" }
        } catch (e: Exception) {
            Log.e("Vidstack", "getBaseUrl fallback: ${e.message}")
            mainUrl
        }
    }
}
