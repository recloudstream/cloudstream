package com.lagradost.cloudstream3.extractors

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked
import com.lagradost.cloudstream3.network.WebViewResolver



class Mwish : StreamWishExtractor() {
    override val name = "Mwish"
    override val mainUrl = "https://mwish.pro"
}

class Dwish : StreamWishExtractor() {
    override val name = "Dwish"
    override val mainUrl = "https://dwish.pro"
}

class Ewish : StreamWishExtractor() {
    override val name = "Embedwish"
    override val mainUrl = "https://embedwish.com"
}

class WishembedPro : StreamWishExtractor() {
    override val name = "Wishembed"
    override val mainUrl = "https://wishembed.pro"
}

class Kswplayer : StreamWishExtractor() {
    override val name = "Kswplayer"
    override val mainUrl = "https://kswplayer.info"
}

class Wishfast : StreamWishExtractor() {
    override val name = "Wishfast"
    override val mainUrl = "https://wishfast.top"
}

class Streamwish2 : StreamWishExtractor() {
    override val mainUrl = "https://streamwish.site"
}

class SfastwishCom : StreamWishExtractor() {
    override val name = "Sfastwish"
    override val mainUrl = "https://sfastwish.com"
}

class Strwish : StreamWishExtractor() {
    override val name = "Strwish"
    override val mainUrl = "https://strwish.xyz"
}

class Strwish2 : StreamWishExtractor() {
    override val name = "Strwish"
    override val mainUrl = "https://strwish.com"
}

class FlaswishCom : StreamWishExtractor() {
    override val name = "Flaswish"
    override val mainUrl = "https://flaswish.com"
}

class Awish : StreamWishExtractor() {
    override val name = "Awish"
    override val mainUrl = "https://awish.pro"
}

class Obeywish : StreamWishExtractor() {
    override val name = "Obeywish"
    override val mainUrl = "https://obeywish.com"
}

class Jodwish : StreamWishExtractor() {
    override val name = "Jodwish"
    override val mainUrl = "https://jodwish.com"
}

class Swhoi : StreamWishExtractor() {
    override val name = "Swhoi"
    override val mainUrl = "https://swhoi.com"
}

class Multimovies : StreamWishExtractor() {
    override val name = "Multimovies"
    override val mainUrl = "https://multimovies.cloud"
}

class UqloadsXyz : StreamWishExtractor() {
    override val name = "Uqloads"
    override val mainUrl = "https://uqloads.xyz"
}

class Doodporn : StreamWishExtractor() {
    override val name = "Doodporn"
    override val mainUrl = "https://doodporn.xyz"
}

class CdnwishCom : StreamWishExtractor() {
    override val name = "Cdnwish"
    override val mainUrl = "https://cdnwish.com"
}

class Asnwish : StreamWishExtractor() {
    override val name = "Asnwish"
    override val mainUrl = "https://asnwish.com"
}

class Nekowish : StreamWishExtractor() {
    override val name = "Nekowish"
    override val mainUrl = "https://nekowish.my.id"
}

class Nekostream : StreamWishExtractor() {
    override val name = "Nekostream"
    override val mainUrl = "https://neko-stream.click"
}

class Swdyu : StreamWishExtractor() {
    override val name = "Swdyu"
    override val mainUrl = "https://swdyu.com"
}

class Wishonly : StreamWishExtractor() {
    override val name = "Wishonly"
    override val mainUrl = "https://wishonly.site"
}

class Playerwish : StreamWishExtractor() {
    override val name = "Playerwish"
    override val mainUrl = "https://playerwish.com"
}

open class StreamWishExtractor : ExtractorApi() {
    override val name = "Streamwish"
    override val mainUrl = "https://streamwish.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Referer" to "$mainUrl/",
            "Origin" to "$mainUrl/",
            "User-Agent" to USER_AGENT
        )

        val pageResponse = app.get(resolveEmbedUrl(url), referer = referer)

        val playerScriptData = when {
            !getPacked(pageResponse.text).isNullOrEmpty() -> getAndUnpack(pageResponse.text)
            pageResponse.document.select("script").any { it.html().contains("jwplayer(\"vplayer\").setup(") } ->
                pageResponse.document.select("script").firstOrNull {
                    it.html().contains("jwplayer(\"vplayer\").setup(")
                }?.html()
            else -> pageResponse.document.selectFirst("script:containsData(sources:)")?.data()
        }

        val directStreamUrl = playerScriptData?.let {
            Regex("""file:\s*"(.*?m3u8.*?)"""").find(it)?.groupValues?.getOrNull(1)
        }

        if (!directStreamUrl.isNullOrEmpty()) {
            M3u8Helper.generateM3u8(
                name,
                directStreamUrl,
                mainUrl,
                headers = headers
            ).forEach(callback)
        } else {
            val webViewM3u8Resolver = WebViewResolver(
                interceptUrl = Regex("""txt|m3u8"""),
                additionalUrls = listOf(Regex("""txt|m3u8""")),
                useOkhttp = false,
                timeout = 15_000L
            )

            val interceptedStreamUrl = app.get(
                url,
                referer = referer,
                interceptor = webViewM3u8Resolver
            ).url

            if (interceptedStreamUrl.isNotEmpty()) {
                M3u8Helper.generateM3u8(
                    name,
                    interceptedStreamUrl,
                    mainUrl,
                    headers = headers
                ).forEach(callback)
            } else {
                Log.d("StreamwishExtractor", "No m3u8 found in fallback either.")
            }
        }
    }

    private fun resolveEmbedUrl(inputUrl: String): String {
        return if (inputUrl.contains("/f/")) {
            val videoId = inputUrl.substringAfter("/f/")
            "$mainUrl/$videoId"
        } else {
            inputUrl
        }
    }
}

