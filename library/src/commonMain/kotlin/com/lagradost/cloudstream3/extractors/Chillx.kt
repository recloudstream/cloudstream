package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import java.security.MessageDigest
import java.util.Base64


class Watchx : Chillx() {
    override val name = "Watchx"
    override val mainUrl = "https://watchx.top"
}

class Beastx : Chillx() {
    override val name = "Beastx"
    override val mainUrl = "https://beastx.top"
}

class Bestx : Chillx() {
    override val name = "Bestx"
    override val mainUrl = "https://bestx.stream"
}

class Playerx : Chillx() {
    override val name = "Playerx"
    override val mainUrl = "https://playerx.stream"
}

class Moviesapi : Chillx() {
    override val name = "Moviesapi"
    override val mainUrl = "https://w1.moviesapi.club"
}

class AnimesagaStream : Chillx() {
    override val name = "Animesaga"
    override val mainUrl = "https://stream.animesaga.in"
}

class Anplay : Chillx() {
    override val name = "Anplay"
    override val mainUrl = "https://stream.anplay.in"
}

class Kinogeru : Chillx() {
    override val name = "Kinoger"
    override val mainUrl = "https://kinoger.ru"
}

class Vidxstream : Chillx() {
    override val name = "Vidxstream"
    override val mainUrl = "https://vidxstream.xyz"
}

class Boltx : Chillx() {
    override val name = "Boltx"
    override val mainUrl = "https://boltx.stream"
}

class Vectorx : Chillx() {
    override val name = "Vectorx"
    override val mainUrl = "https://vectorx.top"
}

class Boosterx : Chillx() {
    override val name = "Boosterx"
    override val mainUrl = "https://boosterx.stream"
}


open class Chillx : ExtractorApi() {
    override val name = "Chillx"
    override val mainUrl = "https://chillx.top"
    override val requiresReferer = true

    companion object {
        private val keySource = "https://rowdy-avocado.github.io/multi-keys/"
        private var keys: KeysData? = null

        private suspend fun getKeys(): KeysData {
            return keys ?: run {
                keys = app.get(keySource).parsedSafe<KeysData>()
                    ?: throw ErrorLoadingException("Unable to get keys")
                keys!!
            }
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url,referer=referer).toString()
        val encodedString =
            Regex("Encrypted\\s*=\\s*'(.*?)';").find(res)?.groupValues?.get(1)?.replace("_", "/")
                ?.replace("-", "+")
                ?: ""
        val keysData = getKeys()
        val fetchkey = keysData.chillx.firstOrNull() ?: throw ErrorLoadingException("No Chillx key found")
        Log.d("Phisher",fetchkey)
        val key = logSha256Checksum(fetchkey)
        val decodedBytes: ByteArray = decodeBase64WithPadding(encodedString)
        val byteList: List<Int> = decodedBytes.map { it.toInt() and 0xFF }
        val processedResult = decryptWithXor(byteList, key)
        val decoded= base64Decode(processedResult)
        val m3u8 =Regex("""file:\s*"(.*?)"""").find(decoded)?.groupValues?.get(1) ?:""
        val header =
            mapOf(
                "accept" to "*/*",
                "accept-language" to "en-US,en;q=0.5",
                "Origin" to mainUrl,
                "Accept-Encoding" to "gzip, deflate, br",
                "Connection" to "keep-alive",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "cross-site",
                "user-agent" to USER_AGENT,
            )
        callback.invoke(
            ExtractorLink(
                name,
                name,
                m3u8,
                mainUrl,
                Qualities.P1080.value,
                INFER_TYPE,
                headers = header
            )
        )
    }

    private fun logSha256Checksum(input: String): List<Int> {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val sha256Hash = messageDigest.digest(input.toByteArray())
        val unsignedIntArray = sha256Hash.map { it.toInt() and 0xFF }
        return unsignedIntArray
    }

    private fun decodeBase64WithPadding(xIdJ2lG: String): ByteArray {
        // Ensure padding for Base64 encoding (if necessary)
        var paddedString = xIdJ2lG
        while (paddedString.length % 4 != 0) {
            paddedString += '=' // Add necessary padding
        }

        // Decode using standard Base64 (RFC4648)
        return Base64.getDecoder().decode(paddedString)
    }

    private fun decryptWithXor(byteList: List<Int>, xorKey: List<Int>): String {
        val result = StringBuilder()
        val length = byteList.size

        for (i in 0 until length) {
            val byteValue = byteList[i]
            val keyValue = xorKey[i % xorKey.size]
            val xorResult = byteValue xor keyValue
            result.append(xorResult.toChar())
        }

        return result.toString()
    }

    data class KeysData(
        val chillx: List<String>
    )

}
