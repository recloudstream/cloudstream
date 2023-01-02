package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import android.util.Log
import java.net.URLDecoder

open class Cda: ExtractorApi() {
    override var mainUrl = "https://ebd.cda.pl"
    override var name = "Cda"
    override val requiresReferer = false


    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val mediaId = url
            .split("/").last()
            .split("?").first()
        val doc = app.get("https://ebd.cda.pl/647x500/$mediaId", headers=mapOf(
            "Referer" to "https://ebd.cda.pl/647x500/$mediaId",
            "User-Agent" to USER_AGENT,
            "Cookie" to "cda.player=html5"
        )).document
        val dataRaw = doc.selectFirst("[player_data]")?.attr("player_data") ?: return null
        val playerData = tryParseJson<PlayerData>(dataRaw) ?: return null
        return listOf(ExtractorLink(
            name,
            name,
            getFile(playerData.video.file),
            referer = "https://ebd.cda.pl/647x500/$mediaId",
            quality = Qualities.Unknown.value
        ))
    }

    private fun rot13(a: String): String {
        return a.map {
            when {
                it in 'A'..'M' || it in 'a'..'m' -> it + 13
                it in 'N'..'Z' || it in 'n'..'z' -> it - 13
                else -> it
            }
        }.joinToString("")
    }

    private fun cdaUggc(a: String): String {
        val decoded = rot13(a)
        return if (decoded.endsWith("adc.mp4")) decoded.replace("adc.mp4",".mp4")
        else decoded
    }

    private fun cdaDecrypt(b: String): String {
        var a = b
            .replace("_XDDD", "")
            .replace("_CDA", "")
            .replace("_ADC", "")
            .replace("_CXD", "")
            .replace("_QWE", "")
            .replace("_Q5", "")
            .replace("_IKSDE", "")
        a = URLDecoder.decode(a, "UTF-8")
        a = a.map { char ->
            if (32 < char.toInt() && char.toInt() < 127) {
                return@map String.format("%c", 33 + (char.toInt() + 14) % 94)
            } else {
                return@map char
            }
        }.joinToString("")
        a = a
            .replace(".cda.mp4", "")
            .replace(".2cda.pl", ".cda.pl")
            .replace(".3cda.pl", ".cda.pl")
        return if (a.contains("/upstream")) "https://" + a.replace("/upstream", ".mp4/upstream")
            else "https://${a}.mp4"
    }

    private fun getFile(a: String) = when {
        a.startsWith("uggc") -> cdaUggc(a)
        !a.startsWith("http") -> cdaDecrypt(a)
        else -> a
    }

    data class VideoPlayerData(
        val file: String,
        val qualities: Map<String, String> = mapOf(),
        val quality: String?,
        val ts: Int?,
        val hash2: String?
    )

    data class PlayerData(
        val video: VideoPlayerData
    )
}