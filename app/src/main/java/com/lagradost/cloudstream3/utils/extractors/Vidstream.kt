package com.lagradost.cloudstream3.utils.extractors

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.APIS
import com.lagradost.cloudstream3.utils.extractors.MultiQuality
import com.lagradost.cloudstream3.utils.extractors.Shiro
import org.jsoup.Jsoup

class Vidstream {
    val name: String = "Vidstream"
    private val mainUrl: String = "https://gogo-stream.com"

    private fun getExtractorUrl(id: String): String {
        return "$mainUrl/streaming.php?id=$id"
    }

    // https://gogo-stream.com/streaming.php?id=MTE3NDg5
    fun getUrl(id: String, isCasting: Boolean = false): List<ExtractorLink> {
        try {
            val url = getExtractorUrl(id)
            with(khttp.get(url)) {
                val document = Jsoup.parse(this.text)
                val primaryLinks = document.select("ul.list-server-items > li.linkserver")
                val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()

                // --- Shiro ---
                val shiroUrl = Shiro().getExtractorUrl(id)
                val shiroSource = Shiro().getUrl(shiroUrl)
                shiroSource?.forEach { extractedLinksList.add(it) }
                // --- MultiQuality ---
                val multiQualityUrl = MultiQuality().getExtractorUrl(id)
                val multiQualitySource = MultiQuality().getUrl(multiQualityUrl)
                multiQualitySource?.forEach { extractedLinksList.add(it) }
                // --------------------

                // All vidstream links passed to extractors
                primaryLinks.forEach { element ->
                    val link = element.attr("data-video")
                    //val name = element.text()

                    // Matches vidstream links with extractors
                    APIS.filter { !it.requiresReferer || !isCasting}.forEach { api ->
                        if (link.startsWith(api.mainUrl)) {
                            val extractedLinks = api.getUrl(link, url)
                            if (extractedLinks?.isNotEmpty() == true) {
                                extractedLinks.forEach {
                                    extractedLinksList.add(it)
                                }
                            }
                        }
                    }
                }
                return extractedLinksList
            }
        } catch (e: Exception) {
            return listOf()
        }
    }
}