package com.lagradost.cloudstream3

import android.app.Activity
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.cloudstream3.animeproviders.ShiroProvider
import com.lagradost.cloudstream3.movieproviders.MeloMovieProvider
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.util.*
import kotlin.collections.ArrayList

const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:68.0) Gecko/20100101 Firefox/68.0"
val baseHeader = mapOf("User-Agent" to USER_AGENT)
val mapper = JsonMapper.builder().addModule(KotlinModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()!!

object APIHolder {
    val allApi = AllProvider()

    private const val defProvider = 0

    val apis = arrayListOf(
        ShiroProvider(),
        MeloMovieProvider(),
    )

    fun getApiFromName(apiName: String?): MainAPI {
        for (api in apis) {
            if (apiName == api.name)
                return api
        }
        return apis[defProvider]
    }

    fun Activity.getApiSettings(): HashSet<String> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

        return settingsManager.getStringSet(this.getString(R.string.search_providers_list_key),
            setOf(apis[defProvider].name))?.toHashSet() ?: hashSetOf(apis[defProvider].name)
    }
}


abstract class MainAPI {
    open val name = "NONE"
    open val mainUrl = "NONE"
    open val instantLinkLoading = false // THIS IS IF THE LINK IS STORED IN THE "DATA"
    open fun search(query: String): ArrayList<Any>? { // SearchResponse
        return null
    }

    open fun load(slug: String): Any? { //LoadResponse
        return null
    }

    // callback is fired once a link is found, will return true if method is executed successfully
    open fun loadLinks(data: String, isCasting: Boolean, callback: (ExtractorLink) -> Unit): Boolean {
        return false
    }
}

fun MainAPI.fixUrl(url: String): String {
    if (url.startsWith('/')) {
        return mainUrl + url
    } else if (!url.startsWith("http") && !url.startsWith("//")) {
        return "$mainUrl/$url"
    }
    return url
}

fun sortUrls(urls: List<ExtractorLink>): List<ExtractorLink> {
    return urls.sortedBy { t -> -t.quality }
}

data class Link(
    val name: String,
    val url: String,
    val quality: Int?,
    val referer: String?,
)

enum class ShowStatus {
    Completed,
    Ongoing,
}

enum class DubStatus {
    Dubbed,
    Subbed,
}

enum class TvType {
    Movie,
    TvSeries,
    Anime,
    ONA,
}

interface SearchResponse {
    val name: String
    val url: String // PUBLIC URL FOR OPEN IN APP
    val slug: String // USED FOR INTERNAL DATA
    val apiName: String
    val type: TvType
    val posterUrl: String?
    val year: Int?
}

data class AnimeSearchResponse(
    override val name: String,
    override val url: String,
    override val slug: String,
    override val apiName: String,
    override val type: TvType,

    override val posterUrl: String?,
    override val year: Int?,

    val otherName: String?,
    val dubStatus: EnumSet<DubStatus>?,
    val dubEpisodes: Int?,
    val subEpisodes: Int?,
) : SearchResponse

data class MovieSearchResponse(
    override val name: String,
    override val url: String,
    override val slug: String,
    override val apiName: String,
    override val type: TvType,

    override val posterUrl: String?,
    override val year: Int?,
) : SearchResponse

data class TvSeriesSearchResponse(
    override val name: String,
    override val url: String,
    override val slug: String,
    override val apiName: String,
    override val type: TvType,

    override val posterUrl: String?,
    override val year: Int?,
    val episodes: Int?,
) : SearchResponse

interface LoadResponse {
    val name: String
    val url: String
    val apiName: String
    val type: TvType
    val posterUrl: String?
    val year: Int?
    val plot: String?
}

fun LoadResponse.isEpisodeBased(): Boolean {
    return this is AnimeLoadResponse || this is TvSeriesLoadResponse
}

data class AnimeLoadResponse(
    val engName: String?,
    val japName: String?,
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType,

    override val posterUrl: String?,
    override val year: Int?,

    val dubEpisodes: ArrayList<String>?,
    val subEpisodes: ArrayList<String>?,
    val showStatus: ShowStatus?,

    override val plot: String?,
    val tags: ArrayList<String>?,
    val synonyms: ArrayList<String>?,

    val malId: Int?,
    val anilistId: Int?,
) : LoadResponse

data class MovieLoadResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType,
    val movieUrl: String,

    override val posterUrl: String?,
    override val year: Int?,
    override val plot: String?,

    val imdbId: Int?,
) : LoadResponse

data class TvSeriesEpisode(val name: String?, val season : Int?, val episode: Int?, val data : String)

data class TvSeriesLoadResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType,
    val episodes: ArrayList<TvSeriesEpisode>,

    override val posterUrl: String?,
    override val year: Int?,
    override val plot: String?,

    val showStatus: ShowStatus?,
    val imdbId: Int?,
) : LoadResponse