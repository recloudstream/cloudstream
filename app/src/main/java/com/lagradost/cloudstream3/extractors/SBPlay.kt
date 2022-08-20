package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getPostForm
import org.jsoup.Jsoup

//class SBPlay1 : SBPlay() {
//    override var mainUrl = "https://sbplay1.com"
//}

//class SBPlay2 : SBPlay() {
//    override var mainUrl = "https://sbplay2.com"
//}

open class SBPlay : ExtractorApi() {
    override var mainUrl = "https://sbplay.one"
    override var name = "SBPlay"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val response = app.get(url, referer = referer).text
        val document = Jsoup.parse(response)

        val links = ArrayList<ExtractorLink>()

        val tree = document.select("table > tbody > tr > td > a")
        for (item in tree) {
            val onDownload = item.attr("onclick")
            val name = "${this.name} - ${item.text()}"
            try {
                Regex("download_video\\('(.*?)','(.*?)','(.*?)'\\)").matchEntire(onDownload)?.let {
                    val id = it.groupValues[1]
                    val mode = it.groupValues[2]
                    val hash = it.groupValues[3]
                    val href = "https://sbplay.one/dl?op=download_orig&id=$id&mode=$mode&hash=$hash"
                    val hrefResponse = app.get(href).text
                    app.post("https://sbplay.one/?op=notifications&open=&_=$unixTimeMS", referer = href)
                    val hrefDocument = Jsoup.parse(hrefResponse)
                    val hrefSpan = hrefDocument.selectFirst("span > a")
                    if (hrefSpan == null) {
                        getPostForm(href, hrefResponse)?.let { form ->
                            val postDocument = Jsoup.parse(form)
                            val downloadBtn = postDocument.selectFirst("a.downloadbtn")?.attr("href")
                            if (downloadBtn.isNullOrEmpty()) {
                                val hrefSpan2 = postDocument.selectFirst("span > a")?.attr("href")
                                if (hrefSpan2?.startsWith("https://") == true) {
                                    links.add(
                                        ExtractorLink(
                                            this.name, name,
                                            hrefSpan2, "", Qualities.Unknown.value, false
                                        )
                                    )
                                } else {
                                    // no link found!!!
                                }
                            } else {
                                links.add(
                                    ExtractorLink(
                                        this.name,
                                        name,
                                        downloadBtn,
                                        "",
                                        Qualities.Unknown.value,
                                        false
                                    )
                                )
                            }
                        }
                    } else {
                        val link = hrefSpan.attr("href")
                        links.add(ExtractorLink(this.name, name, link, "", Qualities.Unknown.value, false))
                    }
                }
            } catch (e: Exception) {
                logError(e)
            }
        }

        return links
    }
}