package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup

/**
 * overrideMainUrl is necessary for for other vidstream clones like vidembed.cc
 * If they diverge it'd be better to make them separate.
 * */
class Vidstream(val mainUrl: String) {
    val name: String = "Vidstream"

    private fun getExtractorUrl(id: String): String {
        return "$mainUrl/streaming.php?id=$id"
    }

    private fun getDownloadUrl(id: String): String {
        return "$mainUrl/download?id=$id"
    }

    private val normalApis = arrayListOf(MultiQuality())

    // https://gogo-stream.com/streaming.php?id=MTE3NDg5
    suspend fun getUrl(
        id: String,
        isCasting: Boolean = false,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val extractorUrl = getExtractorUrl(id)
        runAllAsync(
            {
                normalApis.amap { api ->
                    val url = api.getExtractorUrl(id)
                    api.getSafeUrl(
                        url,
                        callback = callback,
                        subtitleCallback = subtitleCallback
                    )
                }
            }, {
                /** Stolen from GogoanimeProvider.kt extractor */
                val link = getDownloadUrl(id)
                println("Generated vidstream download link: $link")
                val page = app.get(link, referer = extractorUrl)

                val pageDoc = Jsoup.parse(page.text)
                val qualityRegex = Regex("(\\d+)P")

                //a[download]
                pageDoc.select(".dowload > a")?.amap { element ->
                    val href = element.attr("href") ?: return@amap
                    val qual = if (element.text()
                            .contains("HDP")
                    ) "1080" else qualityRegex.find(element.text())?.destructured?.component1()
                        .toString()

                    if (!loadExtractor(href, link, subtitleCallback, callback)) {
                        callback.invoke(
                            newExtractorLink(
                                this.name,
                                name = this.name,
                                href,
                                type = INFER_TYPE
                            ) {
                                this.referer = page.url
                                this.quality = getQualityFromName(qual)
                            }
                        )
                    }
                }
            }, {
                with(app.get(extractorUrl)) {
                    val document = Jsoup.parse(this.text)
                    val primaryLinks = document.select("ul.list-server-items > li.linkserver")
                    //val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()

                    // All vidstream links passed to extractors
                    primaryLinks.distinctBy { it.attr("data-video") }.forEach { element ->
                        val link = element.attr("data-video")
                        //val name = element.text()

                        // Matches vidstream links with extractors
                        extractorApis.filter { !it.requiresReferer || !isCasting }.amap { api ->
                            if (link.startsWith(api.mainUrl)) {
                                api.getSafeUrl(link, extractorUrl, subtitleCallback, callback)
                            }
                        }
                    }
                }
            }
        )
        return true
    }
}