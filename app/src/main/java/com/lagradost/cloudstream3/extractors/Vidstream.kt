package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.pmap
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.extractorApis
import org.jsoup.Jsoup

class Vidstream {
    val name: String = "Vidstream"
    private val mainUrl: String = "https://gogo-stream.com"

    private fun getExtractorUrl(id: String): String {
        return "$mainUrl/streaming.php?id=$id"
    }

    private val normalApis = arrayListOf(Shiro(), MultiQuality())

    // https://gogo-stream.com/streaming.php?id=MTE3NDg5
    fun getUrl(id: String, isCasting: Boolean = false, callback: (ExtractorLink) -> Unit): Boolean {
        try {
            normalApis.pmap { api ->
                val url = api.getExtractorUrl(id)
                val source = api.getSafeUrl(url)
                source?.forEach { callback.invoke(it) }
            }

            val url = getExtractorUrl(id)
            with(khttp.get(url)) {
                val document = Jsoup.parse(this.text)
                val primaryLinks = document.select("ul.list-server-items > li.linkserver")
                //val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()

                // All vidstream links passed to extractors
                primaryLinks.forEach { element ->
                    val link = element.attr("data-video")
                    //val name = element.text()

                    // Matches vidstream links with extractors
                    extractorApis.filter { !it.requiresReferer || !isCasting }.pmap { api ->
                        if (link.startsWith(api.mainUrl)) {
                            val extractedLinks = api.getSafeUrl(link, url)
                            if (extractedLinks?.isNotEmpty() == true) {
                                extractedLinks.forEach {
                                    callback.invoke(it)
                                }
                            }
                        }
                    }
                }
                return true
            }
        } catch (e: Exception) {
            return false
        }
    }
}