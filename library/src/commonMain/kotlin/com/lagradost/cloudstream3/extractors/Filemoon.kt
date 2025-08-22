package com.lagradost.cloudstream3.extractors

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.M3u8Helper


class FileMoon : FilemoonV2() {
    override var mainUrl = "https://filemoon.to"
    override var name = "FileMoon"
}

class FileMoonIn : FilemoonV2() {
    override var mainUrl = "https://filemoon.in"
    override var name = "FileMoon"
}

class FileMoonSx : FilemoonV2() {
    override var mainUrl = "https://filemoon.sx"
    override var name = "FileMoonSx"
}


open class FilemoonV2 : ExtractorApi() {
    override var name = "Filemoon"
    override var mainUrl = "https://filemoon.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val defaultHeaders = mapOf(
            "Referer" to url,
            "Sec-Fetch-Dest" to "iframe",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "cross-site",
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:137.0) Gecko/20100101 Firefox/137.0"
        )

        val initialResponse = app.get(url, defaultHeaders)
        val iframeSrcUrl = initialResponse.document.selectFirst("iframe")?.attr("src")

        if (iframeSrcUrl.isNullOrEmpty()) {
            val fallbackScriptData = initialResponse.document
                .selectFirst("script:containsData(function(p,a,c,k,e,d))")
                ?.data().orEmpty()
            val unpackedScript = JsUnpacker(fallbackScriptData).unpack()

            val videoUrl = unpackedScript?.let {
                Regex("""sources:\[\{file:"(.*?)"""").find(it)?.groupValues?.get(1)
            }

            if (!videoUrl.isNullOrEmpty()) {
                M3u8Helper.generateM3u8(
                    name,
                    videoUrl,
                    mainUrl,
                    headers = defaultHeaders
                ).forEach(callback)
            } else {
                Log.d("FilemoonV2", "No iframe and no video URL found in script fallback.")
            }
            return
        }

        // If iframe was found, continue processing
        val iframeHeaders = defaultHeaders + ("Accept-Language" to "en-US,en;q=0.5")
        val iframeResponse = app.get(iframeSrcUrl, headers = iframeHeaders)

        val iframeScriptData = iframeResponse.document
            .selectFirst("script:containsData(function(p,a,c,k,e,d))")
            ?.data().orEmpty()

        val unpackedScript = JsUnpacker(iframeScriptData).unpack()

        val videoUrl = unpackedScript?.let {
            Regex("""sources:\[\{file:"(.*?)"""").find(it)?.groupValues?.get(1)
        }

        if (!videoUrl.isNullOrEmpty()) {
            M3u8Helper.generateM3u8(
                name,
                videoUrl,
                mainUrl,
                headers = defaultHeaders
            ).forEach(callback)
        } else {
            // Last-resort fallback using WebView interception
            val resolver = WebViewResolver(
                interceptUrl = Regex("""(m3u8|master\.txt)"""),
                additionalUrls = listOf(Regex("""(m3u8|master\.txt)""")),
                useOkhttp = false,
                timeout = 15_000L
            )

            val interceptedUrl = app.get(
                iframeSrcUrl,
                referer = referer,
                interceptor = resolver
            ).url

            if (interceptedUrl.isNotEmpty()) {
                M3u8Helper.generateM3u8(
                    name,
                    interceptedUrl,
                    mainUrl,
                    headers = defaultHeaders
                ).forEach(callback)
            } else {
                Log.d("FilemoonV2", "No video URL intercepted in WebView fallback.")
            }
        }
    }
}
