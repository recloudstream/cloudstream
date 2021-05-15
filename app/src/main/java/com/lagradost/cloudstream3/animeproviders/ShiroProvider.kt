package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import java.net.URLEncoder
import java.util.*


class ShiroProvider : MainAPI() {
    companion object {
        var token: String? = null
    }

    fun autoLoadToken(): Boolean {
        if (token != null) return true
        return loadToken()
    }

    fun loadToken(): Boolean {
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

    data class ShiroSearchResponseShow(
        @JsonProperty("image") val image: String,
        @JsonProperty("_id") val _id: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("episodeCount") val episodeCount: String,
        @JsonProperty("language") val language: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("year") val year: String,
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

    override fun search(query: String): ArrayList<Any>? {
        try {
            if (!autoLoadToken()) return null
            val returnValue: ArrayList<Any> = ArrayList()
            val response = khttp.get("https://tapi.shiro.is/advanced?search=${
                URLEncoder.encode(
                    query,
                    "UTF-8"
                )
            }&token=$token".replace("+", "%20"))
            println(response.text)
            val mapped = response.let { mapper.readValue<ShiroFullSearchResponse>(it.text) }
            for (i in mapped.data.nav.currentPage.items) {

                val type = when (i.type) {
                    "TV" -> TvType.Anime
                    "OVA" -> TvType.ONA
                    "movie" -> TvType.Movie
                    else -> TvType.Anime
                }
                val isDubbed = i.language == "dubbed"
                val set: EnumSet<DubStatus> = EnumSet.noneOf(DubStatus::class.java)

                if (isDubbed)
                    set.add(DubStatus.HasDub)
                else
                    set.add(DubStatus.HasSub)
                val episodeCount = i.episodeCount.toInt()

                returnValue.add(AnimeSearchResponse(
                    i.english ?: i.canonicalTitle,
                    "$mainUrl/${i.slug}",
                    this.name,
                    type,
                    "https://cdn.shiro.is/${i.image}",
                    i.year.toInt(),
                    i.canonicalTitle,
                    set,
                    if (isDubbed) episodeCount else null,
                    if (!isDubbed) episodeCount else null,
                ))
            }
            return returnValue
        } catch (e: Exception) {
            return null
        }
    }
}