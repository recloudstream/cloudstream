package com.lagradost.cloudstream3.syncproviders.providers

import android.util.Log
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.imdbUrlToIdNullable
import com.lagradost.cloudstream3.subtitles.AbstractSubApi
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities
import com.lagradost.cloudstream3.utils.SubtitleHelper

class IndexSubtitleApi : AbstractSubApi {
    override val name = "IndexSubtitle"
    override val idPrefix = "indexsubtitle"
    override val requiresLogin = false
    override val icon: Nothing? = null
    override val createAccountUrl: Nothing? = null

    override fun loginInfo(): Nothing? = null

    override fun logOut() {}

    companion object {
        const val host = "https://indexsubtitle.com"
        const val TAG = "INDEXSUBS"
    }

    private fun fixUrl(url: String): String {
        if (url.startsWith("http")) {
            return url
        }
        if (url.isEmpty()) {
            return ""
        }

        val startsWithNoHttp = url.startsWith("//")
        if (startsWithNoHttp) {
            return "https:$url"
        } else {
            if (url.startsWith('/')) {
                return host + url
            }
            return "$host/$url"
        }
    }

    private fun getOrdinal(num: Int?): String? {
        return when (num) {
            1 -> "First"
            2 -> "Second"
            3 -> "Third"
            4 -> "Fourth"
            5 -> "Fifth"
            6 -> "Sixth"
            7 -> "Seventh"
            8 -> "Eighth"
            9 -> "Ninth"
            10 -> "Tenth"
            11 -> "Eleventh"
            12 -> "Twelfth"
            13 -> "Thirteenth"
            14 -> "Fourteenth"
            15 -> "Fifteenth"
            16 -> "Sixteenth"
            17 -> "Seventeenth"
            18 -> "Eighteenth"
            19 -> "Nineteenth"
            20 -> "Twentieth"
            21 -> "Twenty-First"
            22 -> "Twenty-Second"
            23 -> "Twenty-Third"
            24 -> "Twenty-Fourth"
            25 -> "Twenty-Fifth"
            26 -> "Twenty-Sixth"
            27 -> "Twenty-Seventh"
            28 -> "Twenty-Eighth"
            29 -> "Twenty-Ninth"
            30 -> "Thirtieth"
            31 -> "Thirty-First"
            32 -> "Thirty-Second"
            33 -> "Thirty-Third"
            34 -> "Thirty-Fourth"
            35 -> "Thirty-Fifth"
            else -> null
        }
    }

    private fun isRightEps(text: String, seasonNum: Int?, epNum: Int?): Boolean {
        val FILTER_EPS_REGEX =
            Regex("(?i)((Chapter\\s?0?${epNum})|((Season)?\\s?0?${seasonNum}?\\s?(Episode)\\s?0?${epNum}[^0-9]))|(?i)((S?0?${seasonNum}?E0?${epNum}[^0-9])|(0?${seasonNum}[a-z]0?${epNum}[^0-9]))")
        return text.contains(FILTER_EPS_REGEX)
    }

    private fun haveEps(text: String): Boolean {
        val HAVE_EPS_REGEX =
            Regex("(?i)((Chapter\\s?0?\\d)|((Season)?\\s?0?\\d?\\s?(Episode)\\s?0?\\d))|(?i)((S?0?\\d?E0?\\d)|(0?\\d[a-z]0?\\d))")
        return text.contains(HAVE_EPS_REGEX)
    }

    override suspend fun search(query: AbstractSubtitleEntities.SubtitleSearch): List<AbstractSubtitleEntities.SubtitleEntity> {
        val imdbId = query.imdb ?: 0
        val lang = query.lang
        val queryLang = SubtitleHelper.fromTwoLettersToLanguage(lang.toString())
        val queryText = query.query
        val epNum = query.epNumber ?: 0
        val seasonNum = query.seasonNumber ?: 0
        val yearNum = query.year ?: 0

        val urlItems = ArrayList<String>()

        fun cleanResources(
            results: MutableList<AbstractSubtitleEntities.SubtitleEntity>,
            name: String,
            link: String
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
                    year = yearNum
                )
            )
        }

        val document = app.get("$host/?search=$queryText").document

        document.select("div.my-3.p-3 div.media").map { block ->
            if (seasonNum > 0) {
                val name = block.select("strong.text-primary").text().trim()
                val season = getOrdinal(seasonNum)
                if ((block.selectFirst("a")?.attr("href")
                        ?.contains(
                            "$season",
                            ignoreCase = true
                        )!! || name.contains(
                        "$season",
                        ignoreCase = true
                    )) && name.contains(queryText, ignoreCase = true)
                ) {
                    block.select("div.media").mapNotNull {
                        urlItems.add(
                            fixUrl(
                                it.selectFirst("a")!!.attr("href")
                            )
                        )
                    }
                }
            } else {
                if (block.selectFirst("strong")!!.text().trim()
                        .matches(Regex("(?i)^$queryText\$"))
                ) {
                    if (block.select("span[title=Release]").isNullOrEmpty()) {
                        block.select("div.media").mapNotNull {
                            val urlItem = fixUrl(
                                it.selectFirst("a")!!.attr("href")
                            )
                            val itemDoc = app.get(urlItem).document
                            val id = imdbUrlToIdNullable(
                                itemDoc.selectFirst("div.d-flex span.badge.badge-primary")?.parent()
                                    ?.attr("href")
                            )?.toLongOrNull()
                            val year = itemDoc.selectFirst("div.d-flex span.badge.badge-success")
                                ?.ownText()
                                ?.trim().toString()
                            Log.i(TAG, "id => $id \nyear => $year||$yearNum")
                            if (imdbId > 0) {
                                if (id == imdbId) {
                                    urlItems.add(urlItem)
                                }
                            } else {
                                if (year.contains("$yearNum")) {
                                    urlItems.add(urlItem)
                                }
                            }
                        }
                    } else {
                        if (block.select("span[title=Release]").text().trim()
                                .contains("$yearNum")
                        ) {
                            block.select("div.media").mapNotNull {
                                urlItems.add(
                                    fixUrl(
                                        it.selectFirst("a")!!.attr("href")
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        Log.i(TAG, "urlItems => $urlItems")
        val results = mutableListOf<AbstractSubtitleEntities.SubtitleEntity>()

        urlItems.forEach { url ->
            val request = app.get(url)
            if (request.isSuccessful) {
                request.document.select("div.my-3.p-3 div.media").map { block ->
                    if (block.select("span.d-block span[data-original-title=Language]").text()
                            .trim()
                            .contains("$queryLang")
                    ) {
                        var name = block.select("strong.text-primary").text().trim()
                        val link = fixUrl(block.selectFirst("a")!!.attr("href"))
                        if (seasonNum > 0) {
                            when {
                                isRightEps(name, seasonNum, epNum) -> {
                                    cleanResources(results, name, link)
                                }
                                !(haveEps(name)) -> {
                                    name = "$name (S${seasonNum}:E${epNum})"
                                    cleanResources(results, name, link)
                                }
                            }
                        } else {
                            cleanResources(results, name, link)
                        }
                    }
                }
            }
        }
        return results
    }

    override suspend fun load(data: AbstractSubtitleEntities.SubtitleEntity): String? {
        val seasonNum = data.seasonNumber
        val epNum = data.epNumber

        val req = app.get(data.data)

        if (req.isSuccessful) {
            val document = req.document
            val link = if (document.select("div.my-3.p-3 div.media").size == 1) {
                fixUrl(
                    document.selectFirst("div.my-3.p-3 div.media a")!!.attr("href")
                )
            } else {
                document.select("div.my-3.p-3 div.media").mapNotNull { block ->
                    val name =
                        block.selectFirst("strong.d-block.text-primary")?.text()?.trim().toString()
                    if (seasonNum!! > 0) {
                        if (isRightEps(name, seasonNum, epNum)) {
                            fixUrl(block.selectFirst("a")!!.attr("href"))
                        } else {
                            null
                        }
                    } else {
                        fixUrl(block.selectFirst("a")!!.attr("href"))
                    }
                }.first()
            }
            return link
        }

        return null

    }

}