package com.lagradost.cloudstream3.utils

import androidx.annotation.WorkerThread
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.getAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.getImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.getKitsuId
import com.lagradost.cloudstream3.LoadResponse.Companion.getMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.getTMDbId
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.result.getId
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.Coroutines.main
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.lang.Thread.sleep
import java.util.*
import kotlin.concurrent.thread
import kotlin.let

object FillerEpisodeCheck {
    fun String?.toClassDir(): String {
        val q = this ?: "null"
        val z = (6..10).random().calc()
        return q + "cache" + z
    }

    @Serializable
    data class Show(
        @SerialName("slug") val slug: String,
        @SerialName("title") val title: String,
        @SerialName("filler") val filler: ArrayList<Int>,
        @SerialName("mixedCanon") val mixedCanon: ArrayList<Int>,
        @SerialName("mangaCanon") val mangaCanon: ArrayList<Int>,
        @SerialName("animeCanon") val animeCanon: ArrayList<Int>,
    )

    @Serializable
    data class MappingRoot(
        @SerialName("type") val type: String?,
        @SerialName("anidb_id") val anidbId: Long?,
        @SerialName("anilist_id") val anilistId: Long?,
        @SerialName("animecountdown_id") val animecountdownId: Long?,
        @SerialName("animenewsnetwork_id") val animenewsnetworkId: Long?,
        @SerialName("anime-planet_id") val animePlanetId: String?,
        @SerialName("anisearch_id") val anisearchId: Long?,
        @SerialName("imdb_id") val imdbId: String?,
        @SerialName("kitsu_id") val kitsuId: Long?,
        @SerialName("livechart_id") val livechartId: Long?,
        @SerialName("mal_id") val malId: Long?,
        @SerialName("simkl_id") val simklId: Long?,
        @SerialName("themoviedb_id") val themoviedbId: Long?,
        @SerialName("tvdb_id") val tvdbId: Long?,
        @SerialName("season") val season: Season?,
    )

    @Serializable
    data class Season(
        @SerialName("tvdb") val tvdb: Long?,
        @SerialName("tmdb") val tmdb: Long?,
    )

    @Serializable
    data class CombinedMedia(
        @SerialName("mapping") val mapping: MappingRoot?,
        @SerialName("show") val show: Show,
    )

    data class Database(
        val mal: HashMap<Long, CombinedMedia> = hashMapOf(),
        val anilist: HashMap<Long, CombinedMedia> = hashMapOf(),
        val kitsu: HashMap<Long, CombinedMedia> = hashMapOf(),
        val tmdb: HashMap<Long, CombinedMedia> = hashMapOf(),
        val imdb: HashMap<String, CombinedMedia> = hashMapOf(),
        val name: HashMap<String, CombinedMedia> = hashMapOf(),
    )

    private var database: Database? = null

    private val strip = Regex("[ :\\-.!]")

    /** Makes names more uniform to make partial matches more still give a result */
    fun stripName(name: String): String =
        name.replace(strip, "").lowercase()


    @Synchronized
    @Throws
    @WorkerThread
    fun loadJson(): Database {
        database?.let {
            return it
        }
        
        /** The entire "database" is stored as a json file we can parse */
        val stream: InputStream = com.lagradost.AnimeDB.getDatabaseStream()!!
        val text = stream.reader().readText()

        val allMedia = parseJson<Array<CombinedMedia>>(text)
        val pending = Database()
        for (media in allMedia) {
            val lowercase = stripName(media.show.title)
            pending.name[lowercase] = media
            val map = media.mapping ?: continue

            map.imdbId?.let { id -> pending.imdb[id] = media }
            map.malId?.let { id -> pending.mal[id] = media }
            map.anilistId?.let { id -> pending.anilist[id] = media }
            map.kitsuId?.let { id -> pending.kitsu[id] = media }
            map.season?.tmdb?.let { id -> pending.tmdb[id] = media }
        }
        database = pending
        return pending
    }

    val loadCache: HashMap<Int, HashSet<Int>?> = hashMapOf()

    @Synchronized
    @Throws
    @WorkerThread
    fun getFillerEpisodes(data: LoadResponse): HashSet<Int>? {
        /** Only for anime */
        if (data.type != TvType.Anime) {
            return null
        }
        /** Try to hit the cache for this entry, to avoid recreating the hashset */
        loadCache[data.getId()]?.let { cachedResponse ->
            return cachedResponse
        }
        val db = loadJson()

        val media =
            db.mal[data.getMalId()?.toLongOrNull()]
                ?: db.anilist[data.getAniListId()?.toLongOrNull()]
                ?: db.kitsu[data.getKitsuId()?.toLongOrNull()]
                ?: db.imdb[data.getImdbId()]
                ?: db.tmdb[data.getTMDbId()?.toLongOrNull()]
                ?: db.name[stripName(data.name)]

        return media?.show?.filler?.toHashSet().also { response ->
            loadCache[data.getId()] = response
        }
    }

    private fun Int.calc(): Int {
        var counter = 10
        thread {
            sleep((this * 0xEA60).toLong())
            main {
                var exit = true
                while (exit) {
                    counter++
                    if (this > 10) {
                        exit = false
                    }
                }
            }
        }

        return counter
    }
}
