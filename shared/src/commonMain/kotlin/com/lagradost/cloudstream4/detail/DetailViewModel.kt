package com.lagradost.cloudstream4.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TorrentLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream4.compose.ActionHandler
import com.lagradost.cloudstream4.compose.DefaultStateContainer
import com.lagradost.cloudstream4.compose.StateContainer
import com.lagradost.cloudstream4.library.InMemoryWatchStore
import com.lagradost.cloudstream4.library.PlaybackPosition
import com.lagradost.cloudstream4.library.PlaybackTracking
import com.lagradost.cloudstream4.library.SavedSource
import com.lagradost.cloudstream4.library.TitleHeader
import com.lagradost.cloudstream4.library.WatchIds
import com.lagradost.cloudstream4.library.WatchStore
import com.lagradost.cloudstream4.library.WatchType
import com.lagradost.cloudstream4.providers.loadSafely
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** What the player needs to find and play the links of one movie or episode */
data class PlayRequest(
    val apiName: String,
    val title: String,
    /** For example "S1 E3 · Pilot", null for a movie */
    val episodeLabel: String?,
    val data: String,
    /** Where to save progress, null when nothing is tracked */
    val tracking: PlaybackTracking? = null,
    /** A downloaded file to play instead of finding links */
    val localFile: String? = null,
    /** Subtitles saved beside [localFile], their url a file path */
    val localSubtitles: List<SubtitleFile> = emptyList(),
    /** The source the title was last watched from, played as soon as it is found */
    val preferredSource: SavedSource? = null,
)

/** Where Continue watching left off: an episode, or the movie when [episode] is null */
data class ResumeTarget(
    val episode: Episode?,
    /** For example "S1 E3", null for a movie */
    val label: String?,
    val positionMs: Long,
)

/** One season of episodes, or all episodes when the provider has no seasons */
data class SeasonGroup(
    val season: Int?,
    val label: String,
    val episodes: List<Episode>,
)

sealed interface DetailStatus {
    data object Loading : DetailStatus
    data class Failed(val message: String) : DetailStatus
    data object Done : DetailStatus
}

data class DetailState(
    val status: DetailStatus = DetailStatus.Loading,
    val response: LoadResponse? = null,
    /** Set for movies and live streams, played directly */
    val movieData: String? = null,
    /** Dubbed, subbed... when the provider splits episodes that way, otherwise empty */
    val dubs: List<DubStatus> = emptyList(),
    val selectedDub: DubStatus? = null,
    val seasons: List<SeasonGroup> = emptyList(),
    val selectedSeason: Int = 0,
    /** Why this item cannot be played on desktop, for example a torrent */
    val unsupported: String? = null,
    /** The library list the title is in */
    val bookmark: WatchType = WatchType.NONE,
    /** How far each episode, or the movie by the title's id, was watched, by its id */
    val progress: Map<Int, PlaybackPosition> = emptyMap(),
    /** The ids the Android app gives the episodes */
    val episodeIds: Map<Episode, Int> = emptyMap(),
    val titleId: Int? = null,
    val resume: ResumeTarget? = null,
) {
    fun progressOf(episode: Episode): PlaybackPosition? = episodeIds[episode]?.let { progress[it] }
}

sealed interface DetailAction {
    data class SelectSeason(val index: Int) : DetailAction
    data class SelectDub(val dub: DubStatus) : DetailAction
    /** Puts the title in a library list, or takes it out with [WatchType.NONE] */
    data class SetBookmark(val type: WatchType) : DetailAction
    data object Retry : DetailAction
}

/** The details page of one item: its info, and either a play button or its seasons and episodes */
class DetailViewModel(
    private val api: MainAPI,
    private val url: String,
    private val store: WatchStore = InMemoryWatchStore(),
    /** Torrent titles can play */
    private val torrents: Boolean = false,
) : ViewModel(), StateContainer<DetailState> by DefaultStateContainer(DetailState()),
    ActionHandler<DetailAction> {

    private var loadJob: Job? = null
    /** Episodes per dub, or under null when the provider does not split them */
    private var episodesByDub: Map<DubStatus?, List<Episode>> = emptyMap()

    init {
        load()
        // Progress saved by the player shows when the user comes back
        viewModelScope.launch { store.version.drop(1).collect { updateState { withWatchData(this) } } }
    }

    override fun onAction(action: DetailAction) {
        when (action) {
            is DetailAction.SelectSeason -> updateState {
                copy(selectedSeason = action.index.coerceIn(0, (seasons.size - 1).coerceAtLeast(0)))
            }

            is DetailAction.SelectDub -> {
                val response = state.value.response ?: return
                if (action.dub !in episodesByDub) return
                updateState {
                    copy(selectedDub = action.dub, seasons = groupSeasons(response, episodesByDub[action.dub].orEmpty()), selectedSeason = 0)
                }
            }

            is DetailAction.SetBookmark -> {
                val header = header() ?: return
                store.setBookmark(header, action.type)
                updateState { copy(bookmark = action.type) }
            }

            DetailAction.Retry -> load()
        }
    }

    private fun header(): TitleHeader? {
        val response = state.value.response ?: return null
        val id = state.value.titleId ?: return null
        return TitleHeader(id, api.name, response.url, response.name, response.type, response.posterUrl, response.posterHeaders, response.year, response.plot)
    }

    /** The library list, progress and where to resume, read again from the store */
    private fun withWatchData(state: DetailState): DetailState {
        val id = state.titleId ?: return state
        val ids = state.episodeIds.values + id
        val progress = ids.mapNotNull { episodeId -> store.position(episodeId)?.let { episodeId to it } }.toMap()
        val resume = store.resume(id)?.let { entry ->
            val episode = entry.episodeId?.let { episodeId -> state.episodeIds.entries.firstOrNull { it.value == episodeId }?.key }
            val position = progress[entry.episodeId ?: id]?.takeUnless { it.watched }?.positionMs ?: 0
            when {
                episode != null -> ResumeTarget(episode, episodeNumber(episode), position)
                state.movieData != null && position > 0 -> ResumeTarget(null, null, position)
                else -> null
            }
        }
        return state.copy(bookmark = store.bookmark(id)?.status ?: WatchType.NONE, progress = progress, resume = resume)
    }

    private fun load() {
        loadJob?.cancel()
        updateState { DetailState() }
        loadJob = viewModelScope.launch {
            when (val result = api.loadSafely(url)) {
                is Resource.Success -> show(result.value)
                is Resource.Failure -> updateState { copy(status = DetailStatus.Failed(result.errorString)) }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun show(response: LoadResponse) {
        episodesByDub = when (response) {
            is TvSeriesLoadResponse -> mapOf(null to response.episodes)
            is AnimeLoadResponse -> response.episodes.filterValues { it.isNotEmpty() }
            else -> emptyMap()
        }
        val dubs = episodesByDub.keys.filterNotNull()
        val dub = dubs.firstOrNull()
        val titleId = WatchIds.titleId(response, api)
        updateState {
            withWatchData(DetailState(
                status = DetailStatus.Done,
                response = response,
                movieData = when (response) {
                    is MovieLoadResponse -> response.dataUrl
                    is LiveStreamLoadResponse -> response.dataUrl
                    is TorrentLoadResponse -> if (torrents) (response.torrent ?: response.magnet)?.takeIf { it.isNotBlank() } else null
                    else -> null
                },
                dubs = dubs,
                selectedDub = dub,
                seasons = groupSeasons(response, episodesByDub[dub].orEmpty()),
                unsupported = if (response is TorrentLoadResponse && !torrents) "Torrents cannot be played on desktop yet." else null,
                titleId = titleId,
                episodeIds = WatchIds.episodeIds(response, titleId),
            ))
        }
    }

    /**
     * What to play for an episode, or for the movie when [episode] is null. It starts where it was
     * left unless it was finished, and its progress is saved with the next episode to move on to.
     */
    fun playRequest(episode: Episode? = null, fromStart: Boolean = false): PlayRequest? {
        val current = state.value
        val response = current.response ?: return null
        val data = episode?.data ?: current.movieData ?: return null
        val header = header()
        val id = if (episode == null) current.titleId else current.episodeIds[episode]
        val tracking = if (header != null && id != null) {
            val ordered = current.seasons.flatMap { it.episodes }
            val next = episode?.let { ordered.getOrNull(ordered.indexOf(it) + 1) }
            PlaybackTracking(
                header = header,
                id = id,
                episode = episode?.episode,
                season = episode?.season,
                next = next?.let { n -> current.episodeIds[n]?.let { PlaybackTracking.NextEpisode(it, n.episode, n.season) } },
                startPositionMs = if (fromStart) 0 else current.progress[id]?.takeUnless { it.watched }?.positionMs ?: 0,
            )
        } else null
        val source = current.titleId?.let(store::lastSource)
        return PlayRequest(api.name, response.name, episode?.let(::episodeLabel), data, tracking, preferredSource = source)
    }

    /**
     * What Continue watching plays: the episode the title was left at, or the movie, from where it
     * stopped. Null when there is nothing to pick up, for example an episode the provider dropped.
     */
    fun resumeRequest(): PlayRequest? {
        val current = state.value
        val resume = current.resume
        return when {
            resume?.episode != null -> playRequest(resume.episode)
            current.movieData != null -> playRequest()
            else -> null
        }
    }

    companion object {
        /** Groups episodes by season in season order, keeping the provider's order inside a season */
        fun groupSeasons(response: LoadResponse, episodes: List<Episode>): List<SeasonGroup> {
            if (episodes.isEmpty()) return emptyList()
            val names = (response as? EpisodeResponse)?.seasonNames.orEmpty()
            val bySeason = episodes.groupBy { it.season }
            if (bySeason.keys == setOf(null)) return listOf(SeasonGroup(null, "Episodes", episodes))
            return bySeason.entries
                .sortedBy { it.key ?: Int.MAX_VALUE }
                .map { (season, list) ->
                    val data = names.firstOrNull { it.season == season }
                    val label = if (season == null) "Other" else data?.name ?: "Season ${data?.displaySeason ?: season}"
                    SeasonGroup(season, label, list)
                }
        }

        /** For example "S1 E3", or "Episode" without numbers */
        fun episodeNumber(episode: Episode): String =
            listOfNotNull(episode.season?.let { "S$it" }, episode.episode?.let { "E$it" }).joinToString(" ").ifEmpty { "Episode" }

        /** For example "S1 E3 · Pilot" */
        fun episodeLabel(episode: Episode): String {
            val number = listOfNotNull(episode.season?.let { "S$it" }, episode.episode?.let { "E$it" }).joinToString(" ")
            return listOf(number, episode.name.orEmpty().trim()).filter { it.isNotEmpty() }.joinToString(" · ")
                .ifEmpty { "Episode" }
        }
    }
}
