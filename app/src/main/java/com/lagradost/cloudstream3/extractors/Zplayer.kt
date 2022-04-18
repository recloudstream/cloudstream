package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getAndUnpack

class Zplayer: ZplayerV2() {
    override var name: String = "Zplayer"
    override var mainUrl: String = "https://zplayer.live"
}

class Upstream: ZplayerV2() {
    override var name: String = "Upstream" //Here 'cause works
    override var mainUrl: String = "https://upstream.to"
}

class Streamhub2: ZplayerV2() {
    override var name = "Streamhub" //Here 'cause works
    override var mainUrl = "https://streamhub.to"
}

open class ZplayerV2 : ExtractorApi() {
    override var name = "Zplayer V2"
    override var mainUrl = "https://v2.zplayer.live"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val doc = app.get(url).document
        val sources = mutableListOf<ExtractorLink>()
        doc.select("script").map { script ->
            if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                val testdata = getAndUnpack(script.data())
                val m3u8regex = Regex("((https:|http:)\\/\\/.*\\.m3u8)")
                m3u8regex.findAll(testdata).map {
                    it.value
                }.toList().apmap { urlm3u8 ->
                    if (urlm3u8.contains("m3u8")) {
                        val testurl = app.get(urlm3u8, headers = mapOf("Referer" to url)).text
                        if (testurl.contains("EXTM3U")) {
                            M3u8Helper.generateM3u8(
                                name,
                                urlm3u8,
                                url,
                                headers = mapOf("Referer" to url)
                            ).forEach { link ->
                                sources.add(link)
                            }
                        }
                    }
                }
            }
        }
        return sources
    }
}