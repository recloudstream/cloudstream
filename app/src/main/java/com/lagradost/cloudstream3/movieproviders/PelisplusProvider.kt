package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.TvType

/** Needs to inherit from MainAPI() to
 * make the app know what functions to call
 */ 
class PelisplusProvider : PelisplusProviderTemplate() {
    // mainUrl is good to have as a holder for the url to make future changes easier.
    override val mainUrl: String
        get() = "https://pelisplus.icu"

    // name is for how the provider will be named which is visible in the UI, no real rules for this.
    override val name: String
        get() = "Pelisplus"

    override val homePageUrlList: List<String> = listOf(
        mainUrl,
        "$mainUrl/movies",
        "$mainUrl/series",
        "$mainUrl/new-season",
        "$mainUrl/popular"
    )

    // This is just extra metadata about what type of movies the provider has.
    // Needed for search functionality.
    override val supportedTypes: Set<TvType>
        get() = setOf(TvType.TvSeries, TvType.Movie)
}
