package com.lagradost.cloudstream3.extractors

import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.delay

class Up4FunTop : Up4Stream() {
    override var mainUrl: String = "https://up4fun.top"
}

open class Up4Stream : ExtractorApi() {
    override var name = "Up4Stream"
    override var mainUrl = "https://up4stream.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val movieId = url.substringAfterLast("/").substringBefore(".html")

        // redirect from "wait 5 seconds" page to actual movie page
        val redirectResponse = app.get(url, cookies = mapOf("id" to movieId))
        val redirectForm = redirectResponse.document.selectFirst("form[method=POST]") ?: return null
        val redirectUrl = fixUrl(redirectForm.attr("action"))
        val redirectParams = redirectForm.select("input[type=hidden]").associate { input ->
            input.attr("name") to input.attr("value")
        }

        // wait for 5 seconds, otherwise the below md5 hash is invalid
        delay(5000)
        val response = app.post(redirectUrl, data = redirectParams).document

        // starting here, this works similar to many other extractors like StreamWish
        val extractedpack =
            response.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data()
        if (extractedpack == null) {
            Log.e("up4stream", "file not ready: delay too short")
        }

        JsUnpacker(extractedpack).unpack()?.let { unPacked ->
            Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)?.let { link ->
                return listOf(
                    newExtractorLink(
                        this.name,
                        this.name,
                        link,
                    ) {
                        this.referer = referer.orEmpty()
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }
        return null
    }
}