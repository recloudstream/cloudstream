package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import java.net.URLDecoder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

// Code found in https://github.com/KillerDogeEmpire/vidplay-keys
// special credits to @KillerDogeEmpire for providing key

class AnyVidplay(hostUrl: String) : Vidplay() {
    override val mainUrl = hostUrl
}

class MyCloud : Vidplay() {
    override val name = "MyCloud"
    override val mainUrl = "https://mcloud.bz"
}

class MegaF : Vidplay() {
    override val name = "MegaF"
    override val mainUrl = "https://megaf.cc"
}

class VidplayOnline : Vidplay() {
    override val mainUrl = "https://vidplay.online"
}

object RowdyAvocadoKeys {
    data class KeysData(
        @JsonProperty("vidplay") val vidplay: List<Step> = emptyList(),
        @JsonProperty("chillx") val chillx: List<String> = emptyList(),
        @JsonProperty("vidsrcto") val vidsrcto: List<Step> = emptyList(),
    )

    data class Step(
        @JsonProperty("sequence") val sequence: Int,
        @JsonProperty("method") val method: String,
        @JsonProperty("keys") val keys: List<String>? = null
    )

    private const val SOURCE = "https://rowdy-avocado.github.io/multi-keys/"

    private var keys: KeysData? = null

    suspend fun getKeys(): KeysData {
        return keys
            ?: run {
                keys = app.get(SOURCE).parsedSafe<KeysData>()
                    ?: throw ErrorLoadingException("Unable to get keys")
                keys!!
            }
    }
}

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
open class Vidplay : ExtractorApi() {
    override val name = "Vidplay"
    override val mainUrl = "https://vidplay.site"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val myKeys = RowdyAvocadoKeys.getKeys()
        val domain = url.substringBefore("/e/")
        val id = url.substringBefore("?").substringAfterLast("/")
        val encodedId = vrfEncrypt(myKeys, id)
        val t = url.substringAfter("t=").substringBefore("&")
        val h = rc4Encryption(myKeys.vidplay.find { it.method == "h" }?.keys?.get(0) ?: return, id)
        val mediaUrl = "$domain/mediainfo/$encodedId?t=$t&h=$h"
        val encodedRes = app.get("$mediaUrl").parsedSafe<Response>()?.result
            ?: throw Exception("Unable to fetch link")
        val decodedRes = vrfDecrypt(myKeys, encodedRes)
        val res = tryParseJson<Result>(decodedRes)
        res?.sources?.map {
            M3u8Helper.generateM3u8(this.name, it.file ?: return@map, "$mainUrl/").forEach(callback)
        }

        res?.tracks?.filter { it.kind == "captions" }?.map {
            subtitleCallback.invoke(
                newSubtitleFile(
                    it.label ?: return@map,
                    it.file ?: return@map
                )
            )
        }
    }

    private fun vrfEncrypt(keys: RowdyAvocadoKeys.KeysData, input: String): String? {
        var vrf = input
        keys.vidplay.sortedBy { it.sequence }.forEach { step ->
            when (step.method) {
                "exchange" -> vrf = exchange(
                    vrf,
                    step.keys?.get(0) ?: return@forEach,
                    step.keys?.get(1) ?: return@forEach
                )

                "rc4" -> vrf = rc4Encryption(step.keys?.get(0) ?: return@forEach, vrf)
                "reverse" -> vrf = vrf.reversed()
                "base64" -> vrf = Base64.UrlSafe.encode(vrf.toByteArray())
                "else" -> {}
            }
        }
        // vrf = java.net.URLEncoder.encode(vrf, "UTF-8")
        return vrf
    }

    private fun vrfDecrypt(keys: RowdyAvocadoKeys.KeysData, input: String): String {
        var vrf = input
        keys.vidplay.sortedByDescending { it.sequence }.forEach { step ->
            when (step.method) {
                "exchange" -> vrf = exchange(
                    vrf,
                    step.keys?.get(1) ?: return@forEach,
                    step.keys?.get(0) ?: return@forEach
                )

                "rc4" -> vrf = rc4Decryption(step.keys?.get(0) ?: return@forEach, vrf)
                "reverse" -> vrf = vrf.reversed()
                "base64" -> vrf = Base64.UrlSafe.decode(vrf).toString(Charsets.UTF_8)
                "else" -> {}
            }
        }
        return URLDecoder.decode(vrf, "utf-8")
    }

    private fun rc4Encryption(key: String, input: String): String {
        val rc4Key = SecretKeySpec(key.toByteArray(), "RC4")
        val cipher = Cipher.getInstance("RC4")
        cipher.init(Cipher.DECRYPT_MODE, rc4Key, cipher.parameters)
        var output = cipher.doFinal(input.toByteArray())
        output = Base64.UrlSafe.encode(output).toByteArray()
        return output.toString(Charsets.UTF_8)
    }

    private fun rc4Decryption(key: String, input: String): String {
        var vrf = input.toByteArray()
        vrf = Base64.UrlSafe.decode(vrf)
        val rc4Key = SecretKeySpec(key.toByteArray(), "RC4")
        val cipher = Cipher.getInstance("RC4")
        cipher.init(Cipher.DECRYPT_MODE, rc4Key, cipher.parameters)
        vrf = cipher.doFinal(vrf)

        return vrf.toString(Charsets.UTF_8)
    }

    private fun exchange(input: String, key1: String, key2: String): String {
        return input.map { i ->
            val index = key1.indexOf(i)
            if (index != -1) {
                key2[index]
            } else {
                i
            }
        }.joinToString("")
    }

    data class Tracks(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("kind") val kind: String? = null,
    )

    data class Sources(
        @JsonProperty("file") val file: String? = null,
    )

    data class Result(
        @JsonProperty("sources") val sources: ArrayList<Sources>? = arrayListOf(),
        @JsonProperty("tracks") val tracks: ArrayList<Tracks>? = arrayListOf(),
    )

    data class Response(@JsonProperty("result") val result: String? = null)

}
