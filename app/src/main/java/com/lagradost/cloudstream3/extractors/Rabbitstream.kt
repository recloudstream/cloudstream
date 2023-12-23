package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// Code found in https://github.com/Claudemirovsky/keys
// special credits to @Claudemirovsky for providing key
class Megacloud : Rabbitstream() {
    override val name = "Megacloud"
    override val mainUrl = "https://megacloud.tv"
    override val embed = "embed-2/ajax/e-1"
    override val key = "https://raw.githubusercontent.com/Claudemirovsky/keys/e1/key"
}

class Dokicloud : Rabbitstream() {
    override val name = "Dokicloud"
    override val mainUrl = "https://dokicloud.one"
}

open class Rabbitstream : ExtractorApi() {
    override val name = "Rabbitstream"
    override val mainUrl = "https://rabbitstream.net"
    override val requiresReferer = false
    open val embed = "ajax/embed-4"
    open val key = "https://raw.githubusercontent.com/Claudemirovsky/keys/e4/key"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfterLast("/").substringBefore("?")

        val response = app.get(
            "$mainUrl/$embed/getSources?id=$id",
            referer = mainUrl,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        )

        val encryptedMap = response.parsedSafe<SourcesEncrypted>()
        val sources = encryptedMap?.sources
        val decryptedSources = if (sources == null || encryptedMap.encrypted == false) {
            response.parsedSafe()
        } else {
            val (key, encData) = extractRealKey(sources, getRawKey())
            val decrypted = decryptMapped<List<Sources>>(encData, key)
            SourcesResponses(
                sources = decrypted,
                tracks = encryptedMap.tracks
            )
        }

        decryptedSources?.sources?.map { source ->
            M3u8Helper.generateM3u8(
                name,
                source?.file ?: return@map,
                "$mainUrl/",
            ).forEach(callback)
        }

        decryptedSources?.tracks?.map { track ->
            subtitleCallback.invoke(
                SubtitleFile(
                    track?.label ?: "",
                    track?.file ?: return@map
                )
            )
        }


    }

    private suspend fun getRawKey(): String = app.get(key).text

    private fun extractRealKey(sources: String, stops: String): Pair<String, String> {
        val decryptKey = parseJson<List<List<Int>>>(stops)
        val sourcesArray = sources.toCharArray()

        var extractedKey = ""
        var currentIndex = 0
        for (index in decryptKey) {
            val start = index[0] + currentIndex
            val end = start + index[1]
            for (i in start until end) {
                extractedKey += sourcesArray[i].toString()
                sourcesArray[i] = ' '
            }
            currentIndex += index[1]
        }

        return extractedKey to sourcesArray.joinToString("")
    }

    private inline fun <reified T> decryptMapped(input: String, key: String): T? {
        val decrypt = decrypt(input, key)
        return AppUtils.tryParseJson(decrypt)
    }

    private fun decrypt(input: String, key: String): String {
        return decryptSourceUrl(
            generateKey(
                base64DecodeArray(input).copyOfRange(8, 16),
                key.toByteArray()
            ), input
        )
    }

    private fun generateKey(salt: ByteArray, secret: ByteArray): ByteArray {
        var key = md5(secret + salt)
        var currentKey = key
        while (currentKey.size < 48) {
            key = md5(key + secret + salt)
            currentKey += key
        }
        return currentKey
    }

    private fun md5(input: ByteArray): ByteArray {
        return MessageDigest.getInstance("MD5").digest(input)
    }

    private fun decryptSourceUrl(decryptionKey: ByteArray, sourceUrl: String): String {
        val cipherData = base64DecodeArray(sourceUrl)
        val encrypted = cipherData.copyOfRange(16, cipherData.size)
        val aesCBC = Cipher.getInstance("AES/CBC/PKCS5Padding")
        aesCBC.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(decryptionKey.copyOfRange(0, 32), "AES"),
            IvParameterSpec(decryptionKey.copyOfRange(32, decryptionKey.size))
        )
        val decryptedData = aesCBC?.doFinal(encrypted) ?: throw ErrorLoadingException("Cipher not found")
        return String(decryptedData, StandardCharsets.UTF_8)
    }

    data class Tracks(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("kind") val kind: String? = null,
    )

    data class Sources(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

    data class SourcesResponses(
        @JsonProperty("sources") val sources: List<Sources?>? = emptyList(),
        @JsonProperty("tracks") val tracks: List<Tracks?>? = emptyList(),
    )

    data class SourcesEncrypted(
        @JsonProperty("sources") val sources: String? = null,
        @JsonProperty("encrypted") val encrypted: Boolean? = null,
        @JsonProperty("tracks") val tracks: List<Tracks?>? = emptyList(),
    )

}