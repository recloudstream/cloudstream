package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.TvType

/** Needs to inherit from MainAPI() to
 * make the app know what functions to call
 */
class AsianLoadProvider : VidstreamProviderTemplate() {
    override val name = "AsianLoad"
    override val mainUrl = "https://asianembed.io"
    override val homePageUrlList = listOf(
        mainUrl,
        "$mainUrl/recently-added-raw",
        "$mainUrl/movies",
        "$mainUrl/kshow",
        "$mainUrl/popular",
        "$mainUrl/ongoing-series"
    )

    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
}
