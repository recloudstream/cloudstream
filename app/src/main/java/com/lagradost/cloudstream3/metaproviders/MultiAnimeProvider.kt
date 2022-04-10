package com.lagradost.cloudstream3.metaproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.syncproviders.OAuth2API

class MultiAnimeProvider : MainAPI() {
    override var name = "MultiAnime"
    override val lang = "en"
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.Anime)
    private val syncApi = OAuth2API.aniListApi

    private val validApis by lazy {
        APIHolder.apis.filter {
            it.lang == this.lang && it::class.java != this::class.java && it.supportedTypes.contains(
                TvType.Anime
            )
        }
    }

    private fun filterName(name: String): String {
        return Regex("""[^a-zA-Z0-9-]""").replace(name, "")
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        return syncApi.search(query)?.map {
            AnimeSearchResponse(it.name, it.url, this.name, TvType.Anime, it.posterUrl)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        return syncApi.getResult(url)?.let { res ->
            newAnimeLoadResponse(res.title!!, url, TvType.Anime) {
                posterUrl = res.posterUrl
                plot = res.synopsis
                tags = res.genres
                rating = res.publicScore
                addTrailer(res.trailerUrl)
                addAniListId(res.id.toIntOrNull())
            }
        }
    }
}