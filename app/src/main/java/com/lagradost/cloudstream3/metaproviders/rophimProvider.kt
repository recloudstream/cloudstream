package com.lagradost.cloudstream3.metaproviders

class rophimProvider package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app

class RoPhimProvider : MainAPI() {
    override var mainUrl = "https://rophim.net"
    override var name = "RoPhim"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    // Trang chủ
    override suspend fun getMainPage(page: Int, request: MainPageRequest, loadLinks: Boolean): HomePageResponse {
        val document = app.get(mainUrl).document
        val lists = ArrayList<HomePageList>()

        // Phim mới cập nhật
        document.select("div.ml-item").forEach { block ->
            val header = "Phim Mới Cập Nhật"
            val items = block.select("div.item").mapNotNull { item ->
                try {
                    val title = item.selectFirst("h2")?.text() ?: return@mapNotNull null
                    val href = item.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                    val posterUrl = item.selectFirst("img")?.attr("src")
                    val quality = item.selectFirst("span.mli-quality")?.text()

                    MovieSearchResponse(
                        name = title,
                        url = fixUrl(href),
                        apiName = this.name,
                        type = TvType.Movie,
                        posterUrl = fixUrl(posterUrl ?: ""),
                        quality = getQualityFromString(quality)
                    )
                } catch (e: Exception) {
                    null
                }
            }
            if (items.isNotEmpty()) {
                lists.add(HomePageList(header, items))
            }
        }

        // Phim chiếu rạp
        document.select("div.movies-list-full").forEach { block ->
            val header = "Phim Chiếu Rạp"
            val items = block.select("div.ml-item").mapNotNull { item ->
                try {
                    val title = item.selectFirst("h2")?.text() ?: return@mapNotNull null
                    val href = item.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                    val posterUrl = item.selectFirst("img")?.attr("src")

                    MovieSearchResponse(
                        name = title,
                        url = fixUrl(href),
                        apiName = this.name,
                        type = TvType.Movie,
                        posterUrl = fixUrl(posterUrl ?: "")
                    )
                } catch (e: Exception) {
                    null
                }
            }
            if (items.isNotEmpty()) {
                lists.add(HomePageList(header, items))
            }
        }

        return HomePageResponse(lists)
    }

    // Tìm kiếm
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/tim-kiem/${query.replace(" ", "-")}"
        val document = app.get(url).document

        return document.select("div.ml-item").mapNotNull { item ->
            try {
                val title = item.selectFirst("h2")?.text() ?: return@mapNotNull null
                val href = item.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val posterUrl = item.selectFirst("img")?.attr("src")

                MovieSearchResponse(
                    name = title,
                    url = fixUrl(href),
                    apiName = this.name,
                    type = TvType.Movie,
                    posterUrl = fixUrl(posterUrl ?: "")
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    // Load thông tin phim
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text() ?: ""
        val poster = document.selectFirst("div.film-poster img")?.attr("src")
        val description = document.selectFirst("div.desc")?.text()
        val year = document.selectFirst("div.mvici-right p:contains(Năm)")?.text()?.substringAfter(":")?.trim()?.toIntOrNull()

        val tags = document.select("div.mvici-right p:contains(Thể loại) a").map { it.text() }

        val episodes = document.select("div.ep-item").map { ep ->
            Episode(
                data = fixUrl(ep.attr("href")),
                name = ep.text(),
                episode = ep.text().replace("Tập ", "").toIntOrNull()
            )
        }

        return if (episodes.size > 1) {
            TvSeriesLoadResponse(
                name = title,
                url = url,
                apiName = this.name,
                type = TvType.TvSeries,
                episodes = episodes,
                posterUrl = fixUrl(poster ?: ""),
                year = year,
                plot = description,
                tags = tags
            )
        } else {
            MovieLoadResponse(
                name = title,
                url = url,
                apiName = this.name,
                type = TvType.Movie,
                dataUrl = url,
                posterUrl = fixUrl(poster ?: ""),
                year = year,
                plot = description,
                tags = tags
            )
        }
    }

    // Load links phim và phụ đề
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // Server phim
        document.select("div.list-server").forEach { server ->
            val serverName = server.selectFirst("span.server-name")?.text() ?: ""
            server.select("a.ep-item").forEach { link ->
                val href = link.attr("href")
                if (href.isNotEmpty()) {
                    loadExtractor(href, data, subtitleCallback, callback)
                }
            }
        }

        // Xử lý phụ đề
        document.select("track[kind=subtitles]").forEach { track ->
            val subUrl = track.attr("src")
            val label = track.attr("label")
            if (subUrl.isNotEmpty()) {
                subtitleCallback.invoke(
                    SubtitleFile(
                        lang = label,
                        url = fixUrl(subUrl)
                    )
                )
            }
        }

        return true
    }

    // Các hàm tiện ích
    private fun fixUrl(url: String): String {
        if (url.startsWith("http")) return url
        if (url.startsWith("//")) return "https:$url"
        if (url.isEmpty()) return ""
        if (url.startsWith("/")) return mainUrl + url
        return "$mainUrl/$url"
    }

    private fun getQualityFromString(str: String?): SearchQuality {
        return when (str?.uppercase()) {
            "HD" -> SearchQuality.HD
            "SD" -> SearchQuality.SD
            "CAM" -> SearchQuality.Cam
            "FULL HD" -> SearchQuality.HD
            else -> SearchQuality.HD
        }
    }

    // Xử lý phân trang (nếu cần)
    private fun getPagingResult(url: String, page: Int): Document {
        return if (page == 1) {
            app.get(url).document
        } else {
            app.get("$url/trang-$page").document
        }
    }
}
```
{
}