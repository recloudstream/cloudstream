package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName

open class WatchSB : ExtractorApi() {
    override var name = "WatchSB"
    override var mainUrl = "https://watchsb.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val response = app.get(
            url, interceptor = WebViewResolver(
                Regex("""master\.m3u8""")
            )
        )

        return M3u8Helper().m3u8Generation(
            M3u8Helper.M3u8Stream(
                response.url,
                headers = response.headers.toMap()
            ), true
        )
            .map { stream ->
                ExtractorLink(
                    name,
                    name = name,
                    stream.streamUrl,
                    url,
                    getQualityFromName(stream.quality?.toString()),
                    true
                )
            }
    }
}