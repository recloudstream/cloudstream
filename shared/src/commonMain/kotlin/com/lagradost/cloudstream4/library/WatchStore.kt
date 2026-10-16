package com.lagradost.cloudstream4.library

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The Android app's library lists, with the same ids it stores */
enum class WatchType(val internalId: Int, val label: String) {
    WATCHING(0, "Watching"),
    COMPLETED(1, "Completed"),
    ONHOLD(2, "On hold"),
    DROPPED(3, "Dropped"),
    PLANTOWATCH(4, "Plan to watch"),
    NONE(5, "None");

    companion object {
        /** The lists in the order the library shows them */
        val lists = listOf(WATCHING, PLANTOWATCH, ONHOLD, COMPLETED, DROPPED)

        fun fromInternalId(id: Int?) = entries.find { it.internalId == id } ?: NONE
    }
}

/** What is needed to show a title again without loading it: in the library, and in Continue watching */
data class TitleHeader(
    val id: Int,
    val apiName: String,
    val url: String,
    val name: String,
    val type: TvType?,
    val posterUrl: String?,
    val posterHeaders: Map<String, String>? = null,
    val year: Int? = null,
    val plot: String? = null,
)

data class Bookmark(
    val header: TitleHeader,
    val status: WatchType,
    val bookmarkedTime: Long,
    val latestUpdatedTime: Long,
)

data class PlaybackPosition(val positionMs: Long, val durationMs: Long) {
    val fraction: Float get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    /** Like the Android app, past 90% counts as watched */
    val watched: Boolean get() = durationMs > 0 && positionMs * 100 / durationMs >= NEXT_EPISODE_PERCENTAGE
}

/** Where to pick up a title: the episode last watched, or the next one once that was finished */
data class ResumeEntry(
    val header: TitleHeader,
    val episodeId: Int?,
    val episode: Int?,
    val season: Int?,
    val updateTime: Long,
)

/** The source a title was last watched from, picked first when it plays again: its name and quality */
data class SavedSource(val name: String, val quality: Int)

/** The library, playback positions and Continue watching, stored the way the Android app stores them */
interface WatchStore {
    /** Changes whenever anything is saved, so screens know to read again */
    val version: StateFlow<Int>

    fun bookmark(id: Int): Bookmark?
    fun bookmarks(): List<Bookmark>
    fun setBookmark(header: TitleHeader, status: WatchType)

    fun position(id: Int): PlaybackPosition?
    fun setPosition(id: Int, positionMs: Long, durationMs: Long)

    fun resume(parentId: Int): ResumeEntry?
    fun resumeEntries(): List<ResumeEntry>
    fun setResume(entry: ResumeEntry)
    fun removeResume(parentId: Int)

    /** The source the title, by its id, last played from */
    fun lastSource(parentId: Int): SavedSource?
    fun setLastSource(parentId: Int, source: SavedSource)
}

/** Kept in memory only, for tests and previews */
class InMemoryWatchStore(private val clock: () -> Long = { System.currentTimeMillis() }) : WatchStore {
    private val _version = MutableStateFlow(0)
    override val version: StateFlow<Int> = _version.asStateFlow()
    private val bookmarks = LinkedHashMap<Int, Bookmark>()
    private val positions = HashMap<Int, PlaybackPosition>()
    private val resumes = LinkedHashMap<Int, ResumeEntry>()
    private val sources = HashMap<Int, SavedSource>()

    private fun changed() = _version.update { it + 1 }

    override fun bookmark(id: Int) = bookmarks[id]
    override fun bookmarks() = bookmarks.values.toList()
    override fun setBookmark(header: TitleHeader, status: WatchType) {
        val now = clock()
        if (status == WatchType.NONE) bookmarks.remove(header.id)
        else bookmarks[header.id] = Bookmark(header, status, bookmarks[header.id]?.bookmarkedTime ?: now, now)
        changed()
    }

    override fun position(id: Int) = positions[id]
    override fun setPosition(id: Int, positionMs: Long, durationMs: Long) {
        if (durationMs < MIN_DURATION_MS) return
        positions[id] = PlaybackPosition(positionMs, durationMs)
        changed()
    }

    override fun resume(parentId: Int) = resumes[parentId]
    override fun resumeEntries() = resumes.values.toList()
    override fun setResume(entry: ResumeEntry) {
        resumes[entry.header.id] = entry
        changed()
    }

    override fun removeResume(parentId: Int) {
        resumes.remove(parentId)
        changed()
    }

    override fun lastSource(parentId: Int) = sources[parentId]
    override fun setLastSource(parentId: Int, source: SavedSource) {
        sources[parentId] = source
    }
}

/** Shorter videos are not remembered, like on Android */
const val MIN_DURATION_MS = 30_000L

/** From this far into an episode, Continue watching moves on to the next one */
const val NEXT_EPISODE_PERCENTAGE = 90

/** The ids the Android app gives titles and episodes, so saved positions and lists mean the same on both */
object WatchIds {
    fun titleId(response: LoadResponse, api: MainAPI): Int =
        response.uniqueUrl.replace(api.mainUrl, "").replace("/", "").hashCode()

    /**
     * Every episode's id, worked out like ResultViewModel2: anime by dub, number and season, series by
     * season and number. A movie has the title's own id.
     */
    fun episodeIds(response: LoadResponse, titleId: Int): Map<Episode, Int> = when (response) {
        is AnimeLoadResponse -> buildMap {
            for ((dub, episodes) in response.episodes) for ((index, episode) in episodes.withIndex()) {
                val number = episode.episode ?: (index + 1)
                putIfAbsent(episode, titleId + number + dubIndex(dub) * 1_000_000 + (episode.season?.times(10_000) ?: 0))
            }
        }
        is TvSeriesLoadResponse -> buildMap {
            val sorted = response.episodes.sortedBy { (it.season?.times(10_000) ?: 0) + (it.episode ?: 0) }
            for ((index, episode) in sorted.withIndex()) {
                val number = episode.episode ?: (index + 1)
                putIfAbsent(episode, titleId + (episode.season?.times(100_000) ?: 0) + number + 1)
            }
        }
        else -> emptyMap()
    }

    private fun dubIndex(dub: DubStatus) = dub.id
}

/** What the player needs to save progress for one movie or episode */
data class PlaybackTracking(
    val header: TitleHeader,
    /** The episode's id, or the title's for a movie */
    val id: Int,
    val episode: Int? = null,
    val season: Int? = null,
    /** Continue watching moves here when this one is finished, null for a movie or the last episode */
    val next: NextEpisode? = null,
    val startPositionMs: Long = 0,
) {
    data class NextEpisode(val id: Int, val episode: Int?, val season: Int?)
}

/**
 * Saves how far the user got, like the Android app's setViewPosAndResume: the position of what is
 * playing, and Continue watching pointing at it, or at the next episode once it is nearly done.
 * Finishing the last episode or a movie takes the title out of Continue watching.
 */
fun WatchStore.saveProgress(tracking: PlaybackTracking, positionMs: Long, durationMs: Long, now: Long = System.currentTimeMillis()) {
    if (durationMs < MIN_DURATION_MS || positionMs <= 0) return
    setPosition(tracking.id, positionMs, durationMs)
    val finished = positionMs * 100 / durationMs >= NEXT_EPISODE_PERCENTAGE
    val parentId = tracking.header.id
    when {
        !finished -> setResume(ResumeEntry(tracking.header, tracking.id, tracking.episode, tracking.season, now))
        tracking.next != null -> setResume(ResumeEntry(tracking.header, tracking.next.id, tracking.next.episode, tracking.next.season, now))
        else -> removeResume(parentId)
    }
}
