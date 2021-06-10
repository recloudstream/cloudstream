package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.extractors.Vidstream
import java.net.URLEncoder
import java.util.*
import kotlin.collections.ArrayList


class ShiroProvider : MainAPI() {
    companion object {
        var token: String? = null

        fun getType(t: String): TvType {
            return when (t) {
                "TV" -> TvType.Anime
                "OVA" -> TvType.ONA
                "movie" -> TvType.Movie
                else -> TvType.Anime
            }
        }
    }

    private fun autoLoadToken(): Boolean {
        if (token != null) return true
        return loadToken()
    }

    private fun loadToken(): Boolean {
        return try {
            val response = khttp.get(mainUrl, headers = baseHeader)

            val jsMatch = Regex("""src="(/static/js/main.*?)"""").find(response.text)
            val (destructed) = jsMatch!!.destructured
            val jsLocation = "$mainUrl$destructed"
            val js = khttp.get(jsLocation, headers = baseHeader)
            val tokenMatch = Regex("""token:"(.*?)"""").find(js.text)
            token = (tokenMatch!!.destructured).component1()

            token != null
        } catch (e: Exception) {
            false
        }
    }

    override val mainUrl: String
        get() = "https://shiro.is"

    override val name: String
        get() = "Shiro"

    data class ShiroSearchResponseShow(
        @JsonProperty("image") val image: String,
        @JsonProperty("_id") val _id: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("episodeCount") val episodeCount: String,
        @JsonProperty("language") val language: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("year") val year: String?,
        @JsonProperty("canonicalTitle") val canonicalTitle: String,
        @JsonProperty("english") val english: String?,
    )

    data class ShiroSearchResponse(
        @JsonProperty("data") val data: List<ShiroSearchResponseShow>,
        @JsonProperty("status") val status: String,
    )

    data class ShiroFullSearchResponseCurrentPage(
        @JsonProperty("items") val items: List<ShiroSearchResponseShow>,
    )

    data class ShiroFullSearchResponseNavItems(
        @JsonProperty("currentPage") val currentPage: ShiroFullSearchResponseCurrentPage,
    )

    data class ShiroFullSearchResponseNav(
        @JsonProperty("nav") val nav: ShiroFullSearchResponseNavItems,
    )

    data class ShiroFullSearchResponse(
        @JsonProperty("data") val data: ShiroFullSearchResponseNav,
        @JsonProperty("status") val status: String,
    )

    data class ShiroVideo(
        @JsonProperty("video_id") val video_id: String,
        @JsonProperty("host") val host: String,
    )

    data class ShiroEpisodes(
        @JsonProperty("anime") val anime: AnimePageData?,
        @JsonProperty("anime_slug") val anime_slug: String,
        @JsonProperty("create") val create: String,
        @JsonProperty("dayOfTheWeek") val dayOfTheWeek: String,
        @JsonProperty("episode_number") val episode_number: Int,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("update") val update: String,
        @JsonProperty("_id") val _id: String,
        @JsonProperty("videos") val videos: List<ShiroVideo>,
    )

    data class AnimePageData(
        @JsonProperty("banner") val banner: String?,
        @JsonProperty("canonicalTitle") val canonicalTitle: String?,
        @JsonProperty("episodeCount") val episodeCount: String,
        @JsonProperty("genres") val genres: List<String>?,
        @JsonProperty("image") val image: String,
        @JsonProperty("japanese") val japanese: String?,
        @JsonProperty("english") val english: String?,
        @JsonProperty("language") val language: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("synopsis") val synopsis: String,
        @JsonProperty("type") val type: String?,
        @JsonProperty("views") val views: Int?,
        @JsonProperty("year") val year: String?,
        @JsonProperty("_id") val _id: String,
        @JsonProperty("episodes") var episodes: List<ShiroEpisodes>?,
        @JsonProperty("synonyms") var synonyms: List<String>?,
        @JsonProperty("status") val status: String?,
        @JsonProperty("schedule") val schedule: String?,
    )

    data class AnimePage(
        @JsonProperty("data") val data: AnimePageData,
        @JsonProperty("status") val status: String,
    )

    override fun search(query: String): ArrayList<Any>? {
        if (!autoLoadToken()) return null
        val returnValue: ArrayList<Any> = ArrayList()
        val response = khttp.get("https://tapi.shiro.is/advanced?search=${
            URLEncoder.encode(
                query,
                "UTF-8"
            )
        }&token=$token".replace("+", "%20"))
        if (response.text == "{\"status\":\"Found\",\"data\":[]}") return returnValue // OR ELSE WILL CAUSE WEIRD ERROR

        val mapped = response.let { mapper.readValue<ShiroFullSearchResponse>(it.text) }
        for (i in mapped.data.nav.currentPage.items) {
            val type = getType(i.type)
            val isDubbed = i.language == "dubbed"
            val set: EnumSet<DubStatus> = EnumSet.noneOf(DubStatus::class.java)

            if (isDubbed)
                set.add(DubStatus.Dubbed)
            else
                set.add(DubStatus.Subbed)
            val episodeCount = i.episodeCount.toInt()

            returnValue.add(AnimeSearchResponse(
                i.name.replace("Dubbed", ""), // i.english ?: i.canonicalTitle,
                "$mainUrl/${i.slug}",
                i.slug,
                this.name,
                type,
                "https://cdn.shiro.is/${i.image}",
                i.year?.toIntOrNull(),
                i.canonicalTitle,
                set,
                if (isDubbed) episodeCount else null,
                if (!isDubbed) episodeCount else null,
            ))
        }
        return returnValue
    }

    override fun load(slug: String): Any? {
        if (!autoLoadToken()) return null
        val rurl = "https://tapi.shiro.is/anime/slug/${slug}?token=${token}"
        val response = khttp.get(rurl, timeout = 120.0)
        val mapped = response.let { mapper.readValue<AnimePage>(it.text) }
        val data = mapped.data
        val isDubbed = data.language == "dubbed"
        val episodes = ArrayList<Any>(data.episodes ?: ArrayList())
        val status = when (data.status) {
            "current" -> ShowStatus.Ongoing
            "finished" -> ShowStatus.Completed
            else -> null
        }

        return AnimeLoadResponse(
            data.english,
            data.japanese,
            data.name.replace("Dubbed", ""),//data.canonicalTitle ?: data.name.replace("Dubbed", ""),
            "$mainUrl/anime/${slug}",
            this.name,
            getType(data.type ?: ""),
            "https://cdn.shiro.is/${data.image}",
            data.year?.toIntOrNull(),
            if (isDubbed) episodes else null,
            if (!isDubbed) episodes else null,
            status,
            data.synopsis,
            ArrayList(data.genres ?: ArrayList()),
            ArrayList(data.synonyms ?: ArrayList()),
            null,
            null,
        )
    }

    override fun loadLinks(data: Any, isCasting: Boolean, callback: (ExtractorLink) -> Unit): Boolean {
        if (data is ShiroEpisodes) {
            return Vidstream().getUrl(data.videos[0].video_id, isCasting) {
                callback.invoke(it)
            }
        }
        return false
    }
}