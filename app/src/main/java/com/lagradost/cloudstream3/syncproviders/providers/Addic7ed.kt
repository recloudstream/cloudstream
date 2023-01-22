package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.subtitles.AbstractSubApi
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities
import com.lagradost.cloudstream3.utils.SubtitleHelper

class Addic7ed : AbstractSubApi {
    override val name = "Addic7ed"
    override val idPrefix = "addic7ed"
    override val requiresLogin = false
    override val icon: Nothing? = null
    override val createAccountUrl: Nothing? = null

    override fun loginInfo(): Nothing? = null

    override fun logOut() {}

    companion object {
        const val host = "https://www.addic7ed.com"
        const val TAG = "ADDIC7ED"
    }

    private fun fixUrl(url: String): String {
        return if (url.startsWith("/")) host + url
        else if (!url.startsWith("http")) "$host/$url"
        else url

    }

    override suspend fun search(query: AbstractSubtitleEntities.SubtitleSearch): List<AbstractSubtitleEntities.SubtitleEntity> {
        val lang = query.lang
        val queryLang = SubtitleHelper.fromTwoLettersToLanguage(lang.toString())
        val queryText = query.query.trim()
        val epNum = query.epNumber ?: 0
        val seasonNum = query.seasonNumber ?: 0
        val yearNum = query.year ?: 0

        fun cleanResources(
            results: MutableList<AbstractSubtitleEntities.SubtitleEntity>,
            name: String,
            link: String,
            headers: Map<String, String>,
            isHearingImpaired: Boolean
        ) {
            results.add(
                AbstractSubtitleEntities.SubtitleEntity(
                    idPrefix = idPrefix,
                    name = name,
                    lang = queryLang.toString(),
                    data = link,
                    source = this.name,
                    type = if (seasonNum > 0) TvType.TvSeries else TvType.Movie,
                    epNumber = epNum,
                    seasonNumber = seasonNum,
                    year = yearNum,
                    headers = headers,
                    isHearingImpaired = isHearingImpaired
                )
            )
        }

        val title = queryText.substringBefore("(").trim()
        val url = "$host/search.php?search=${title}&Submit=Search"
        val hostDocument = app.get(url).document
        var searchResult = ""
        if (!hostDocument.select("span:contains($title)").isNullOrEmpty()) searchResult = url
        else if (!hostDocument.select("table.tabel")
                .isNullOrEmpty()
        ) searchResult = hostDocument.select("a:contains($title)").attr("href").toString()
        else {
            val show =
                hostDocument.selectFirst("#sl button")?.attr("onmouseup")?.substringAfter("(")
                    ?.substringBefore(",")
            val doc = app.get(
                "$host/ajax_loadShow.php?show=$show&season=$seasonNum&langs=&hd=undefined&hi=undefined",
                referer = "$host/"
            ).document
            doc.select("#season tr:contains($queryLang)").mapNotNull { node ->
                if (node.selectFirst("td")?.text()
                        ?.toIntOrNull() == seasonNum && node.select("td:eq(1)")
                        .text()
                        .toIntOrNull() == epNum
                ) searchResult = fixUrl(node.select("a").attr("href"))
            }
        }
        val results = mutableListOf<AbstractSubtitleEntities.SubtitleEntity>()
        val document = app.get(
            url = fixUrl(searchResult),
        ).document

        document.select(".tabel95 .tabel95 tr:contains($queryLang)").mapNotNull { node ->
            val name = if (seasonNum > 0) "${document.select(".titulo").text().replace("Subtitle","").trim()}${
                node.parent()!!.select(".NewsTitle").text().substringAfter("Version").substringBefore(", Duration")
            }" else "${document.select(".titulo").text().replace("Subtitle","").trim()}${node.parent()!!.select(".NewsTitle").text().substringAfter("Version").substringBefore(", Duration")}"
            val link = fixUrl(node.select("a.buttonDownload").attr("href"))
            val isHearingImpaired =
                !node.parent()!!.select("tr:last-child [title=\"Hearing Impaired\"]").isNullOrEmpty()
            cleanResources(results, name, link, mapOf("referer" to "$host/"), isHearingImpaired)
        }
        return results
    }

    override suspend fun load(data: AbstractSubtitleEntities.SubtitleEntity): String {
        return data.data
    }
}