package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.*

class Acefile : ExtractorApi() {
    override val name = "Acefile"
    override val mainUrl = "https://acefile.co"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        app.get(url).document.select("script").map { script ->
            if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                val data = getAndUnpack(script.data())
                val id = data.substringAfter("{\"id\":\"").substringBefore("\",")
                val key = data.substringAfter("var nfck=\"").substringBefore("\";")
                app.get("https://acefile.co/local/$id?key=$key").text.let {
                    base64Decode(
                        it.substringAfter("JSON.parse(atob(\"").substringBefore("\"))")
                    ).let { res ->
                        sources.add(
                            ExtractorLink(
                                name,
                                name,
                                res.substringAfter("\"file\":\"").substringBefore("\","),
                                "$mainUrl/",
                                Qualities.Unknown.value,
                                headers = mapOf("range" to "bytes=0-")
                            )
                        )
                    }
                }
            }
        }
        return sources
    }

}