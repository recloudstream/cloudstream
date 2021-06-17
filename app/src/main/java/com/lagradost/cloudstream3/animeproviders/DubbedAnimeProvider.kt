package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.mapper

class DubbedAnimeProvider : MainAPI() {
    override val mainUrl: String
        get() = "https://bestdubbedanime.com"
    override val name: String
        get() = "DubbedAnime"
    override val hasQuickSearch: Boolean
        get() = true

    override fun quickSearch(query: String): ArrayList<Any>? {
        val url = "$mainUrl/xz/searchgrid.php?p=1&limit=12&s=$query&_=${unixTime}"
        val response = khttp.get(url)

        return super.quickSearch(query)
    }

    /*
    override fun search(query: String): ArrayList<Any>? {
        val url = "$mainUrl/search/$query"

        mapper.readValue<>()

        return super.search(query)
    }*/
}