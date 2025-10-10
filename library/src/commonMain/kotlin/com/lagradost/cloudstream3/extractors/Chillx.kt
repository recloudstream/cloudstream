package com.lagradost.cloudstream3.extractors


import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.security.MessageDigest


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
        val keysData = RowdyAvocadoKeys.getKeys()
        val fetchkey = keysData.chillx.firstOrNull() ?: throw ErrorLoadingException("No Chillx key found")
        val decoded = decodeWithKey(encodedString, fetchkey)
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
            newExtractorLink(
                source = name,
                name = name,
                url = m3u8,
            ) {
                this.referer = mainUrl
                this.quality = Qualities.P1080.value
                this.headers = header
            }
        )

        val subtitles = extractSrtSubtitles(decoded)

        subtitles.forEachIndexed { _, (language, url) ->
            subtitleCallback.invoke(
                newSubtitleFile(
                    language,
                    url
                )
            )
        }

    }

    private fun reverseString(input: String): String {
        return input.reversed()
    }

    private fun decodeWithKey(input: String, key: String): String {
        // Hashing the key using SHA1 and encoding it in hex
        val sha1 = MessageDigest.getInstance("SHA-1")
        val keyHash = sha1.digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        println(keyHash)

        val inputLength = input.length
        val keyHashLength = keyHash.length
        var keyIndex = 0
        var decodedString = ""

        for (index in 0 until inputLength step 2) {
            // Extracting two characters from input, applying reverseString, converting to base 36, then back to hex
            val reversedPair = reverseString(input.substring(index, index + 2))
            val base36Value = Integer.parseInt(reversedPair, 36)
            val hexValue = base36Value.toString(16)

            // Reset keyIndex when it exceeds keyHashLength
            if (keyIndex == keyHashLength) {
                keyIndex = 0
            }

            // Get the char code of the current character in keyHash
            val keyCharCode = keyHash[keyIndex].code
            keyIndex++

            // Subtracting keyCharCode from the hex value and appending it to the result string
            decodedString += (Integer.parseInt(hexValue, 16) - keyCharCode).toChar()
        }

        // Return the decoded string
        return String(decodedString.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
    }


    private fun extractSrtSubtitles(subtitle: String): List<Pair<String, String>> {
        // Regex to match the language and associated .srt URL properly, and stop at the next [Language] section
        val regex = """\[([^]]+)](https?://[^\s,]+\.srt)""".toRegex()

        // Process each match and return language-URL pairs
        return regex.findAll(subtitle).map { match ->
            val (language, url) = match.destructured
            language.trim() to url.trim()
        }.toList()
    }
}