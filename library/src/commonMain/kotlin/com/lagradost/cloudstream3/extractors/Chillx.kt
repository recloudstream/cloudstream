package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.helper.AesHelper.cryptoAESHandler
import com.lagradost.cloudstream3.fixTitle
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URI

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
	private var key: String? = null

    @Suppress("NAME_SHADOWING")
	override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {		
		val master = Regex("\\s*=\\s*'([^']+)").find(
            app.get(
                url,
                referer = referer ?: "",
                headers = mapOf(
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                    "Accept-Language" to "en-US,en;q=0.5",
                )
            ).text
        )?.groupValues?.get(1)
		
		val key = fetchKey() ?: throw ErrorLoadingException("Unable to get key")
		
        val decrypt = cryptoAESHandler(master ?: return, key.toByteArray(), false)
            ?.replace("\\", "")
            ?: throw ErrorLoadingException("failed to decrypt")

        val source = Regex(""""?file"?:\s*"([^"]+)""").find(decrypt)?.groupValues?.get(1)
        val name = url.getHost()

        val subtitles = Regex("""subtitle"?:\s*"([^"]+)""").find(decrypt)?.groupValues?.get(1)
        val subtitlePattern = """\[(.*?)\](https?://[^\s,]+)""".toRegex()
        val matches = subtitlePattern.findAll(subtitles ?: "")
        val languageUrlPairs = matches.map { matchResult ->
            val (language, url) = matchResult.destructured
            decodeUnicodeEscape(language) to url
        }.toList()

        languageUrlPairs.forEach{ (name, file) ->
            subtitleCallback.invoke(
                SubtitleFile(
                    name,
                    file
                )
            )
        }
		
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
                url = source ?: return,
                referer = "$mainUrl/",
                quality = Qualities.Unknown.value,
				INFER_TYPE,
                headers = header,
            )
        )
    }	

    private suspend fun fetchKey(): String? {
        return app.get("https://raw.githubusercontent.com/Rowdy-Avocado/multi-keys/keys/index.html")
            .parsedSafe<Keys>()?.key?.get(0)?.also { key = it }
    }
	
	private fun decodeUnicodeEscape(input: String): String {
        val regex = Regex("u([0-9a-fA-F]{4})")
        return regex.replace(input) {
            it.groupValues[1].toInt(16).toChar().toString()
        }
    }
	
	private fun String.getHost(): String {
		return fixTitle(URI(this).host.substringBeforeLast(".").substringAfterLast("."))
	}
	
	data class Keys(
        @JsonProperty("chillx") val key: List<String>
    )
    
}
