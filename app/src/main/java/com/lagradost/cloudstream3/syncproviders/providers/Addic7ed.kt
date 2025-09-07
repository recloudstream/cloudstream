package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTagToEnglishLanguageName

class Addic7ed : SubtitleAPI() {
    override val name = "Addic7ed"
    override val idPrefix = "addic7ed"

    override val requiresLogin = false

    companion object {
        const val HOST = "https://www.addic7ed.com"
        const val TAG = "ADDIC7ED"
    }

    private fun fixUrl(url: String): String {
        return if (url.startsWith("/")) HOST + url
        else if (!url.startsWith("http")) "$HOST/$url"
        else url
    }

    override suspend fun search(
        auth: AuthData?,
        query: AbstractSubtitleEntities.SubtitleSearch
    ): List<AbstractSubtitleEntities.SubtitleEntity>? {
        val langTagIETF = query.lang.toString()
        val langAddic7edCode = langTagIETF2Addic7ed[langTagIETF]?.first ?: 0
        val queryLang =
            langTagIETF2Addic7ed[langTagIETF]?.second ?:
            fromTagToEnglishLanguageName(langTagIETF) ?: 
            langTagIETF
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
                    lang = langTagIETF,
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
        val url = "$HOST/search.php?search=${title}&Submit=Search"
        val hostDocument = app.get(url).document
        var searchResult = ""
        if (hostDocument.select("span:contains($title)").isNotEmpty()) searchResult = url
        else if (hostDocument.select("table.tabel")
                .isNotEmpty()
        ) searchResult = hostDocument.select("a:contains($title)").attr("href").toString()
        else {
            val show =
                hostDocument.selectFirst("#sl button")?.attr("onmouseup")?.substringAfter("(")
                    ?.substringBefore(",")
            val doc = app.get(
                "$HOST/ajax_loadShow.php?show=$show&season=$seasonNum&langs=$langAddic7edCode&hd=undefined&hi=undefined",
                referer = "$HOST/"
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
                node.parent()!!.select("tr:last-child [title=\"Hearing Impaired\"]").isNotEmpty()
            cleanResources(results, name, link, mapOf("referer" to "$HOST/"), isHearingImpaired)
        }
        return results
    }

    override suspend fun load(
        auth: AuthData?,
        subtitle: AbstractSubtitleEntities.SubtitleEntity
    ): String? {
        return subtitle.data
    }

    // Missing (?_?)
    // Pair("2", ""),
    // Pair("3", ""),
    // Pair("33", ""),
    // Pair("34", ""),
    private val langTagIETF2Addic7ed = mapOf(
        "all"     to Pair("0", "All languages"),
        "ar"      to Pair("38", "Arabic"),
        "az"      to Pair("48", "Azerbaijani"),
        "bg"      to Pair("35", "Bulgarian"),
        "bn"      to Pair("47", "Bengali"),
        "bs"      to Pair("44", "Bosnian"),
        "ca"      to Pair("12", "Català"),
        "cs"      to Pair("14", "Czech"),
        "cy"      to Pair("65", "Welsh"),
        "da"      to Pair("30", "Danish"),
        "de"      to Pair("11", "German"),
        "el"      to Pair("27", "Greek"),
        "en"      to Pair("1", "English"),
        "es-419"  to Pair("6", "Spanish (Latin America)"),
        "es-ar"   to Pair("69", "Spanish (Argentina)"),
        "es-es"   to Pair("5", "Spanish (Spain)"),
        "es"      to Pair("4", "Spanish"),
        "et"      to Pair("54", "Estonian"),
        "eu"      to Pair("13", "Euskera"),
        "fa"      to Pair("43", "Persian"),
        "fi"      to Pair("28", "Finnish"),
        "fr-ca"   to Pair("53", "French (Canadian)"),
        "fr"      to Pair("8", "French"),
        "gl"      to Pair("15", "Galego"),
        "he"      to Pair("23", "Hebrew"),
        "hi"      to Pair("55", "Hindi"),
        "hr"      to Pair("31", "Croatian"),
        "hu"      to Pair("20", "Hungarian"),
        "hy"      to Pair("50", "Armenian"),
        "id"      to Pair("37", "Indonesian"),
        "is"      to Pair("56", "Icelandic"),
        "it"      to Pair("7", "Italian"),
        "ja"      to Pair("32", "Japanese"),
        "kn"      to Pair("66", "Kannada"),
        "ko"      to Pair("42", "Korean"),
        "lt"      to Pair("58", "Lithuanian"),
        "lv"      to Pair("57", "Latvian"),
        "mk"      to Pair("49", "Macedonian"),
        "ml"      to Pair("67", "Malayalam"),
        "mr"      to Pair("62", "Marathi"),
        "ms"      to Pair("40", "Malay"),
        "nl"      to Pair("17", "Dutch"),
        "no"      to Pair("29", "Norwegian"),
        "pl"      to Pair("21", "Polish"),
        "pt-br"   to Pair("10", "Portuguese (Brazilian)"),
        "pt"      to Pair("9", "Portuguese"),
        "ro"      to Pair("26", "Romanian"),
        "ru"      to Pair("19", "Russian"),
        "si"      to Pair("60", "Sinhala"),
        "sk"      to Pair("25", "Slovak"),
        "sl"      to Pair("22", "Slovenian"),
        "sq"      to Pair("52", "Albanian"),
        "sr-latn" to Pair("36", "Serbian (Latin)"),
        "sr"      to Pair("39", "Serbian (Cyrillic)"),
        "sv"      to Pair("18", "Swedish"),
        "ta"      to Pair("59", "Tamil"),
        "te"      to Pair("63", "Telugu"),
        "th"      to Pair("46", "Thai"),
        "tl"      to Pair("68", "Tagalog"),
        "tlh"     to Pair("61", "Klingon"),
        "tr"      to Pair("16", "Turkish"),
        "uk"      to Pair("51", "Ukrainian"),
        "vi"      to Pair("45", "Vietnamese"),
        "yue"     to Pair("64", "Cantonese"),
        "zh-hans" to Pair("41", "Chinese (Simplified)"),
        "zh-hant" to Pair("24", "Chinese (Traditional)"),
    )
}