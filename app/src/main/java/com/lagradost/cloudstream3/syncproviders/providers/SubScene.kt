package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.debugPrint
import com.lagradost.cloudstream3.subtitles.AbstractSubProvider
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities
import com.lagradost.cloudstream3.subtitles.SubtitleResource
import com.lagradost.cloudstream3.syncproviders.providers.IndexSubtitleApi.Companion.getOrdinal
import com.lagradost.cloudstream3.utils.SubtitleHelper

class SubScene : AbstractSubProvider {
    val mainUrl = "https://subscene.com"
    val name = "Subscene"
    override val idPrefix = "subscene"

    override suspend fun search(query: AbstractSubtitleEntities.SubtitleSearch): List<AbstractSubtitleEntities.SubtitleEntity>? {
        val seasonName =
            query.seasonNumber?.let { number ->
                // Need to translate "7" to "Seventh Season"
                getOrdinal(number)?.let { words -> " - $words Season" }
            } ?: ""

        val fullQuery = query.query + seasonName

        val doc = app.post(
            "$mainUrl/subtitles/searchbytitle",
            data = mapOf("query" to fullQuery, "l" to "")
        ).document

        return doc.select("div.title a").map { element ->
            val href = "$mainUrl${element.attr("href")}"
            val title = element.text()

            AbstractSubtitleEntities.SubtitleEntity(
                idPrefix = idPrefix,
                name = title,
                source = name,
                data = href,
                lang = query.lang ?: "en",
                epNumber = query.epNumber
            )
        }.distinctBy { it.data }
    }

    override suspend fun SubtitleResource.getResources(data: AbstractSubtitleEntities.SubtitleEntity) {
        val resultDoc = app.get(data.data).document
        val queryLanguage = SubtitleHelper.fromTwoLettersToLanguage(data.lang) ?: "English"

        val results = resultDoc.select("table tbody tr").mapNotNull { element ->
            val anchor = element.select("a")
            val href = anchor.attr("href") ?: return@mapNotNull null
            val fixedHref = "$mainUrl${href}"
            val spans = anchor.select("span")
            val language = spans.firstOrNull()?.text()
            val title = spans.getOrNull(1)?.text()
            val isPositive = anchor.select("span.positive-icon").isNotEmpty()

            TableElement(title, language, fixedHref, isPositive)
        }.sortedBy {
            it.getScore(queryLanguage, data.epNumber)
        }

        debugPrint { "$name found subtitles: ${results.takeLast(3)}" }
        // Last = highest score
        val selectedResult = results.lastOrNull() ?: return

        val subtitleDocument = app.get(selectedResult.href).document
        val subtitleDownloadUrl =
            "$mainUrl${subtitleDocument.select("div.download a").attr("href")}"

        this.addZipUrl(subtitleDownloadUrl) { name, _ ->
            name
        }
    }

    /**
     * Class to manage the various different subtitle results and rank them.
     */
    data class TableElement(
        val title: String?,
        val language: String?,
        val href: String,
        val isPositive: Boolean
    ) {
        private fun matchesLanguage(other: String): Boolean {
            return language != null && (language.contains(other, ignoreCase = true) ||
                    other.contains(language, ignoreCase = true))
        }

        /**
         * Scores in this order:
         * Preferred Language > Episode number > Positive rating > English Language
         */
        fun getScore(queryLanguage: String, episodeNum: Int?): Int {
            var score = 0
            if (this.matchesLanguage(queryLanguage)) {
                score += 8
            }
            // Matches Episode 7 using "E07" with any number of leading zeroes
            if (episodeNum != null && title != null && title.contains(
                    Regex(
                        """E0*${episodeNum}""",
                        RegexOption.IGNORE_CASE
                    )
                )
            ) {
                score += 4
            }
            if (isPositive) {
                score += 2
            }
            if (this.matchesLanguage("English")) {
                score += 1
            }
            return score
        }
    }
}