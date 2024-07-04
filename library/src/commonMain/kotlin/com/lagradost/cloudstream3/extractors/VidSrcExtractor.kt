package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.delay
import java.net.URI

class VidSrcExtractor2 : VidSrcExtractor() {
    override val mainUrl = "https://vidsrc.me/embed"
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val newUrl = url.lowercase().replace(mainUrl, super.mainUrl)
        super.getUrl(newUrl, referer, subtitleCallback, callback)
    }
}

open class VidSrcExtractor : ExtractorApi() {
    override val name = "VidSrc"
    private val absoluteUrl = "https://v2.vidsrc.me"
    override val mainUrl = "$absoluteUrl/embed"
    override val requiresReferer = false

    companion object {
        /** Infinite function to validate the vidSrc pass */
        suspend fun validatePass(url: String) {
            val uri = URI(url)
            val host = uri.host

            // Basically turn https://tm3p.vidsrc.stream/ -> https://vidsrc.stream/
            val referer = host.split(".").let {
                val size = it.size
                "https://" + it.subList(maxOf(0, size - 2), size).joinToString(".") + "/"
            }

            while (true) {
                app.get(url, referer = referer)
                delay(60_000)
            }
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val iframedoc = app.get(url).document

        val serverslist =
            iframedoc.select("div#sources.button_content div#content div#list div").map {
                val datahash = it.attr("data-hash")
                if (datahash.isNotBlank()) {
                    val links = try {
                        app.get(
                            "$absoluteUrl/srcrcp/$datahash",
                            referer = "https://rcp.vidsrc.me/"
                        ).url
                    } catch (e: Exception) {
                        ""
                    }
                    links
                } else ""
            }

        serverslist.amap { server ->
            val linkfixed = server.replace("https://vidsrc.xyz/", "https://embedsito.com/")
            if (linkfixed.contains("/prorcp")) {
                val srcresponse = app.get(server, referer = absoluteUrl).text
                val m3u8Regex = Regex("((https:|http:)//.*\\.m3u8)")
                val srcm3u8 = m3u8Regex.find(srcresponse)?.value ?: return@amap
                val passRegex = Regex("""['"](.*set_pass[^"']*)""")
                val pass = passRegex.find(srcresponse)?.groupValues?.get(1)?.replace(
                    Regex("""^//"""), "https://"
                )

                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name,
                        srcm3u8,
                        "https://vidsrc.stream/",
                        Qualities.Unknown.value,
                        extractorData = pass,
                        isM3u8 = true
                    )
                )
            } else {
                loadExtractor(linkfixed, url, subtitleCallback, callback)
            }
        }
    }

}