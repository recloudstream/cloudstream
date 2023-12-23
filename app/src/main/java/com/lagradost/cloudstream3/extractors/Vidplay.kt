package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

// Code found in https://github.com/Claudemirovsky/worstsource-keys
// special credits to @Claudemirovsky for providing key
open class Vidplay : ExtractorApi() {
    override val name = "Vidplay"
    override val mainUrl = "https://vidplay.site"
    override val requiresReferer = true
    open val key = "https://raw.githubusercontent.com/Claudemirovsky/worstsource-keys/keys/keys.json"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringBefore("?").substringAfterLast("/")
        val encodeId = encodeId(id, getKeys())
        val mediaUrl = callFutoken(encodeId, url)
        val res = app.get(
            "$mediaUrl", headers = mapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest",
            ), referer = url
        ).parsedSafe<Response>()?.result?.sources

        res?.map {
            M3u8Helper.generateM3u8(
                this.name,
                it.file ?: return@map,
                "$mainUrl/"
            ).forEach(callback)
        }

    }

    private suspend fun getKeys(): List<String> {
        return app.get(key).parsed()
    }

    private suspend fun callFutoken(id: String, url: String): String? {
        val script = app.get("$mainUrl/futoken").text
        val k = "k='(\\S+)'".toRegex().find(script)?.groupValues?.get(1) ?: return null
        val a = mutableListOf(k)
        for (i in id.indices) {
            a.add((k[i % k.length].code + id[i].code).toString())
        }
        return "$mainUrl/mediainfo/${a.joinToString(",")}?${url.substringAfter("?")}"
    }

    private fun encodeId(id: String, keyList: List<String>): String {
        val cipher1 = Cipher.getInstance("RC4")
        val cipher2 = Cipher.getInstance("RC4")
        cipher1.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyList[0].toByteArray(), "RC4"),
            cipher1.parameters
        )
        cipher2.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyList[1].toByteArray(), "RC4"),
            cipher2.parameters
        )
        var input = id.toByteArray()
        input = cipher1.doFinal(input)
        input = cipher2.doFinal(input)
        return base64Encode(input).replace("/", "_")
    }

    data class Sources(
        @JsonProperty("file") val file: String? = null,
    )

    data class Result(
        @JsonProperty("sources") val sources: ArrayList<Sources>? = arrayListOf(),
    )

    data class Response(
        @JsonProperty("result") val result: Result? = null,
    )

}
