package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.StringUtils.decodeUri
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Document

open class InternetArchive : ExtractorApi() {
    override val mainUrl = "https://archive.org"
    override val requiresReferer = false
    override val name = "Internet Archive"

    companion object {
        private var archivedItems: MutableMap<String, Document> = mutableMapOf()
    }

    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/details/$id"
    }

    // https://archive.org/details/the-the-infected
    // https://archive.org/details/TheEdgeOfTheEarth
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = archivedItems[url] ?: run {
            try {
                val doc = app.get(url).document
                archivedItems[url] = doc
                doc
            } catch (e: Exception) {
                logError(e)
                return
            }
        }

        val subtitleLinks = document.select("a[href*=\"/download/\"]").filter { element ->
            val subtitleUrl = element.attr("href")
            subtitleUrl.endsWith(".vtt", true) ||
                    subtitleUrl.endsWith(".srt", true)
        }

        subtitleLinks.forEach {
            val subtitleUrl = mainUrl + it.attr("href")
            val fileName = subtitleUrl.substringAfterLast('/')
            val subtitleFile = newSubtitleFile(
                lang = fileName.substringBeforeLast(".")
                    .substringAfterLast("."),
                url = subtitleUrl
            )
            subtitleCallback(subtitleFile)
        }

        val fileLinks = document.select("a[href*=\"/download/\"]").filter { element ->
            val mediaUrl = element.attr("href")

            mediaUrl.endsWith(".mp4", true) ||
                    mediaUrl.endsWith(".mpg", true) ||
                    mediaUrl.endsWith(".mkv", true) ||
                    mediaUrl.endsWith(".avi", true) ||
                    mediaUrl.endsWith(".ogv", true) ||
                    mediaUrl.endsWith(".ogg", true) ||
                    mediaUrl.endsWith(".mp3", true) ||
                    mediaUrl.endsWith(".wav", true) ||
                    mediaUrl.endsWith(".flac", true)
        }

        val select = fileLinks.ifEmpty {
            document.head().select("meta[property=\"og:video\"]")
        }

        select.forEach {
            val mediaUrl = when {
                it.hasAttr("href") -> mainUrl + it.attr("href")
                it.hasAttr("content") -> it.attr("content")
                else -> return@forEach
            }

            val fileName = mediaUrl.substringAfterLast('/')
            val quality = when {
                fileName.contains("1080", true) -> Qualities.P1080.value
                fileName.contains("720", true) -> Qualities.P720.value
                fileName.contains("480", true) -> Qualities.P480.value
                else -> Qualities.Unknown.value
            }

            if (mediaUrl.isNotEmpty()) {
                val name = if (mediaUrl.count() > 1) {
                    val fileExtension = mediaUrl.substringAfterLast(".")
                    val fileNameCleaned = fileName.decodeUri().substringBeforeLast('.')
                    "$fileNameCleaned ($fileExtension)"
                } else this.name
                callback(
                    newExtractorLink(
                        this.name,
                        name,
                        mediaUrl
                    ) {
                        this.quality = quality
                    }
                )
            }
        }
    }
}