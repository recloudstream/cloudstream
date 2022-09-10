package com.lagradost.cloudstream3.subtitles

import com.lagradost.cloudstream3.TvType

class AbstractSubtitleEntities {
    data class SubtitleEntity(
        var idPrefix : String,
        var name: String = "", //Title of movie/series. This is the one to be displayed when choosing.
        var lang: String = "en",
        var data: String = "", //Id or link, depends on provider how to process
        var type: TvType = TvType.Movie, //Movie, TV series, etc..
        var source: String,
        var epNumber: Int? = null,
        var seasonNumber: Int? = null,
        var year: Int? = null,
        var isHearingImpaired: Boolean = false,
        var headers: Map<String, String> = emptyMap()
    )

    data class SubtitleSearch(
        var query: String = "",
        var imdb: Long? = null,
        var lang: String? = null,
        var epNumber: Int? = null,
        var seasonNumber: Int? = null,
        var year: Int? = null
    )
}