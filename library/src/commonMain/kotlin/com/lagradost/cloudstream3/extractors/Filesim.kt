package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.api.Log
import com.lagradost.cloudstream3.network.WebViewResolver

class Multimoviesshg : Filesim() {
    override var mainUrl = "https://multimoviesshg.com"
}

class Guccihide : Filesim() {
    override val name = "Guccihide"
    override var mainUrl = "https://guccihide.com"
}

class Ahvsh : Filesim() {
    override val name = "Ahvsh"
    override var mainUrl = "https://ahvsh.com"
}

class Moviesm4u : Filesim() {
    override val mainUrl = "https://moviesm4u.com"
    override val name = "Moviesm4u"
}

class StreamhideTo : Filesim() {
    override val mainUrl = "https://streamhide.to"
    override val name = "Streamhide"
}

class StreamhideCom : Filesim() {
    override var name: String = "Streamhide"
    override var mainUrl: String = "https://streamhide.com"
}

class Movhide : Filesim() {
    override var name: String = "Movhide"
    override var mainUrl: String = "https://movhide.pro"
}

class Ztreamhub : Filesim() {
    override val mainUrl: String = "https://ztreamhub.com" //Here 'cause works
    override val name = "Zstreamhub"
}

open class Filesim : ExtractorApi() {
    override val name = "Filesim"
    override val mainUrl = "https://files.im"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = url.replace("/download/", "/e/")
        var pageResponse = app.get(embedUrl, referer = referer)

        val iframeElement = pageResponse.document.selectFirst("iframe")
        if (iframeElement != null) {
            val iframeUrl = iframeElement.attr("src")
            pageResponse = app.get(
                iframeUrl,
                headers = mapOf(
                    "Accept-Language" to "en-US,en;q=0.5",
                    "Sec-Fetch-Dest" to "iframe"
                ),
                referer = pageResponse.url
            )
        }

        val scriptData = if (!getPacked(pageResponse.text).isNullOrEmpty()) {
            getAndUnpack(pageResponse.text)
        } else {
            pageResponse.document.selectFirst("script:containsData(sources:)")?.data()
        }

        val m3u8Url = scriptData?.let {
            Regex("""file:\s*"(.*?m3u8.*?)"""").find(it)?.groupValues?.getOrNull(1)
        }

        if (!m3u8Url.isNullOrEmpty()) {
            M3u8Helper.generateM3u8(
                name,
                m3u8Url,
                mainUrl
            ).forEach(callback)
        } else {
            // Fallback using WebViewResolver
            val resolver = WebViewResolver(
                interceptUrl = Regex("""(m3u8|master\.txt)"""),
                additionalUrls = listOf(Regex("""(m3u8|master\.txt)""")),
                useOkhttp = false,
                timeout = 15_000L
            )

            val interceptedUrl = app.get(
                url = pageResponse.url,
                referer = referer,
                interceptor = resolver
            ).url

            if (interceptedUrl.isNotEmpty()) {
                M3u8Helper.generateM3u8(
                    name,
                    interceptedUrl,
                    mainUrl
                ).forEach(callback)
            } else {
                Log.d("Filesim", "No m3u8 found via script or WebView fallback.")
            }
        }
    }
}
