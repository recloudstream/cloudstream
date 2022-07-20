package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.TvType

/** Needs to inherit from MainAPI() to
 * make the app know what functions to call
 */
class VidEmbedProvider : VidstreamProviderTemplate() {
    // mainUrl is good to have as a holder for the url to make future changes easier.
    override var mainUrl = "https://membed.net"

    // name is for how the provider will be named which is visible in the UI, no real rules for this.
    override var name = "VidEmbed"

    override val homePageUrlList: List<String> = listOf(
        mainUrl,
        "$mainUrl/movies",
        "$mainUrl/series",
        "$mainUrl/recommended-series",
        "$mainUrl/cinema-movies"
    )

    override val iv = "9225679083961858"
    override val secretKey = "25742532592138496744665879883281"
    override val secretDecryptKey = secretKey

    // This is just extra metadata about what type of movies the provider has.
    // Needed for search functionality.
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
}
