package com.lagradost.cloudstream3.utils

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.network.text
import java.util.concurrent.TimeUnit

object SyncUtil {
    /** first. Mal, second. Anilist,
     * valid sites are: Gogoanime, Twistmoe and 9anime*/
    fun getIdsFromSlug(slug: String, site : String = "Gogoanime"): Pair<String?, String?>? {
        try {
            //Gogoanime, Twistmoe and 9anime
            val url =
                "https://raw.githubusercontent.com/MALSync/MAL-Sync-Backup/master/data/pages/$site/$slug.json"
            val response = app.get(url, cacheTime = 1, cacheUnit = TimeUnit.DAYS).text
            val mapped = mapper.readValue<MalSyncPage?>(response)

            val overrideMal = mapped?.malId ?: mapped?.Mal?.id ?: mapped?.Anilist?.malId
            val overrideAnilist = mapped?.aniId ?: mapped?.Anilist?.id

            if (overrideMal != null) {
                return overrideMal.toString() to overrideAnilist?.toString()
            }
            return null
        } catch (e: Exception) {
            logError(e)
        }
        return null
    }

    data class MalSyncPage(
        @JsonProperty("identifier") val identifier: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("page") val page: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("url") val url: String?,
        @JsonProperty("image") val image: String?,
        @JsonProperty("hentai") val hentai: Boolean?,
        @JsonProperty("sticky") val sticky: Boolean?,
        @JsonProperty("active") val active: Boolean?,
        @JsonProperty("actor") val actor: String?,
        @JsonProperty("malId") val malId: Int?,
        @JsonProperty("aniId") val aniId: Int?,
        @JsonProperty("createdAt") val createdAt: String?,
        @JsonProperty("updatedAt") val updatedAt: String?,
        @JsonProperty("deletedAt") val deletedAt: String?,
        @JsonProperty("Mal") val Mal: Mal?,
        @JsonProperty("Anilist") val Anilist: Anilist?,
        @JsonProperty("malUrl") val malUrl: String?
    )

    data class Anilist(
//            @JsonProperty("altTitle") val altTitle: List<String>?,
//            @JsonProperty("externalLinks") val externalLinks: List<String>?,
        @JsonProperty("id") val id: Int?,
        @JsonProperty("malId") val malId: Int?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("url") val url: String?,
        @JsonProperty("image") val image: String?,
        @JsonProperty("category") val category: String?,
        @JsonProperty("hentai") val hentai: Boolean?,
        @JsonProperty("createdAt") val createdAt: String?,
        @JsonProperty("updatedAt") val updatedAt: String?,
        @JsonProperty("deletedAt") val deletedAt: String?
    )

    data class Mal(
//            @JsonProperty("altTitle") val altTitle: List<String>?,
        @JsonProperty("id") val id: Int?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("url") val url: String?,
        @JsonProperty("image") val image: String?,
        @JsonProperty("category") val category: String?,
        @JsonProperty("hentai") val hentai: Boolean?,
        @JsonProperty("createdAt") val createdAt: String?,
        @JsonProperty("updatedAt") val updatedAt: String?,
        @JsonProperty("deletedAt") val deletedAt: String?
    )

}