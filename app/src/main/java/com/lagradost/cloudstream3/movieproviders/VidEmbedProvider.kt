package com.lagradost.cloudstream3.movieproviders

import org.jsoup.Jsoup
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.Vidstream
import com.lagradost.cloudstream3.network.get
import com.lagradost.cloudstream3.network.text
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import java.util.*
import kotlin.collections.ArrayList

/** Needs to inherit from MainAPI() to
 * make the app know what functions to call
 */
class VidEmbedProvider : VidstreamProviderTemplate() {
    // mainUrl is good to have as a holder for the url to make future changes easier.
    override val mainUrl: String
        get() = "https://vidembed.cc"

    // name is for how the provider will be named which is visible in the UI, no real rules for this.
    override val name: String
        get() = "VidEmbed"

    override val homePageUrlList: List<String> = listOf(
        mainUrl,
        "$mainUrl/movies",
        "$mainUrl/series",
        "$mainUrl/recommended-series",
        "$mainUrl/cinema-movies"
    )

    // This is just extra metadata about what type of movies the provider has.
    // Needed for search functionality.
    override val supportedTypes: Set<TvType>
        get() = setOf(TvType.TvSeries, TvType.Movie)
}
