package com.lagradost.cloudstream3

import android.app.Activity
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.cloudstream3.animeproviders.ShiroProvider
import java.util.*
import kotlin.collections.ArrayList

const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:68.0) Gecko/20100101 Firefox/68.0"
val baseHeader = mapOf("User-Agent" to USER_AGENT)
val mapper = JsonMapper.builder().addModule(KotlinModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()!!

object APIHolder {
    val allApi = AllProvider()

    private const val defProvider = 0

    val apis = arrayListOf<MainAPI>(
        ShiroProvider()
    )

    fun getApiFromName(apiName: String): MainAPI {
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
    open fun search(query: String): ArrayList<Any>? { // SearchResponse
        return null
    }

    open fun load(slug: String): Any? { //LoadResponse
        return null
    }

    open fun loadLinks(data: Any, id: Int): Boolean {
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


data class Link(
    val name: String,
    val url: String,
    val quality: Int?,
    val referer: String?,
)

interface LinkExtractor {
    val linkStart: String // THIS IS USED TO AUTO-EXTRACT LINKS FROM URL
    fun extract(link: String, referer: String): ArrayList<Link>
}

enum class ShowStatus {
    Completed,
    Ongoing,
}

enum class DubStatus {
    HasDub,
    HasSub,
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

    val dubEpisodes: ArrayList<Any>?,
    val subEpisodes: ArrayList<Any>?,
    val showStatus: ShowStatus?,

    val tags: ArrayList<String>?,
    val plot: String?,
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

    val imdbId: Int?,
) : LoadResponse

data class TvSeriesLoadResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType,
    val episodes: ArrayList<String>,

    override val posterUrl: String?,
    override val year: Int?,

    val showStatus: ShowStatus?,
    val imdbId: Int?,
) : LoadResponse