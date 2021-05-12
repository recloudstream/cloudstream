package com.lagradost.cloudstream3

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
    val apis = arrayListOf<MainAPI>(
        ShiroProvider()
    )
}


abstract class MainAPI {
    open val name = "NONE"
    open val mainUrl = "NONE"
    open fun search(query: String): ArrayList<Any>? { // SearchResponse
        return null
    }

    open fun load(url: String): Any? { //LoadResponse
        return null
    }

    open fun loadLinks(url: String, id: Int): Boolean {
        return false
    }
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
    val url: String
    val apiName: String
    val type: TvType
    val posterUrl: String?
    val year: Int?
}

data class AnimeSearchResponse(
    override val name: String,
    override val url: String,
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
    override val apiName: String,
    override val type: TvType,

    override val posterUrl: String?,
    override val year: Int?,
) : SearchResponse

data class TvSeriesSearchResponse(
    override val name: String,
    override val url: String,
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
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType,

    override val posterUrl: String?,
    override val year: Int?,

    val dubEpisodes: ArrayList<String>?,
    val subEpisodes: ArrayList<String>?,
    val otherName: String?,
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