package com.lagradost.cloudstream3.ui.result

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getId
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.animeproviders.GogoanimeProvider
import com.lagradost.cloudstream3.animeproviders.NineAnimeProvider
import com.lagradost.cloudstream3.metaproviders.SyncRedirector
import com.lagradost.cloudstream3.mvvm.*
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.player.IGenerator
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.FillerEpisodeCheck
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit


/** This starts at 1 */
data class EpisodeRange(
    // used to index data
    val startIndex: Int,
    val length: Int,
    // used to display data
    val startEpisode: Int,
    val endEpisode: Int,
)

data class ResultData(
    val url: String,
    val tags: List<String>,
    val actors: List<ActorData>?,
    val actorsText: UiText?,

    val comingSoon: Boolean,
    val backgroundPosterUrl: String?,
    val title: String,

    val posterImage: UiImage?,
    val plotText: UiText,
    val apiName: UiText,
    val ratingText: UiText?,
    val vpnText: UiText?,
    val metaText: UiText?,
    val durationText: UiText?,
    val onGoingText: UiText?,
    val noEpisodesFoundText: UiText?,
    val titleText: UiText,
    val typeText: UiText,
    val yearText: UiText?,
    val nextAiringDate: UiText?,
    val nextAiringEpisode: UiText?,
)

fun LoadResponse.toResultData(repo: APIRepository): ResultData {
    debugAssert({ repo.name == apiName }) {
        "Api returned wrong apiName"
    }

    val hasActorImages = actors?.firstOrNull()?.actor?.image?.isNotBlank() == true

    var nextAiringEpisode: UiText? = null
    var nextAiringDate: UiText? = null

    if (this is EpisodeResponse) {
        val airing = this.nextAiring
        if (airing != null && airing.unixTime > unixTime) {
            val seconds = airing.unixTime - unixTime
            val days = TimeUnit.SECONDS.toDays(seconds)
            val hours: Long = TimeUnit.SECONDS.toHours(seconds) - days * 24
            val minute =
                TimeUnit.SECONDS.toMinutes(seconds) - TimeUnit.SECONDS.toHours(seconds) * 60
            nextAiringEpisode = when {
                days > 0 -> {
                    txt(
                        R.string.next_episode_time_day_format,
                        days,
                        hours,
                        minute
                    )
                }
                hours > 0 -> txt(
                    R.string.next_episode_time_hour_format,
                    hours,
                    minute
                )
                minute > 0 -> txt(
                    R.string.next_episode_time_min_format,
                    minute
                )
                else -> null
            }?.also {
                nextAiringDate = txt(R.string.next_episode_format, airing.episode)
            }
        }
    }

    return ResultData(
        nextAiringDate = nextAiringDate,
        nextAiringEpisode = nextAiringEpisode,
        posterImage = img(
            posterUrl, posterHeaders
        ) ?: img(R.drawable.default_cover),
        titleText = txt(name),
        url = url,
        tags = tags ?: emptyList(),
        comingSoon = comingSoon,
        actors = if (hasActorImages) actors else null,
        actorsText = if (hasActorImages) null else txt(
            R.string.cast_format,
            actors?.joinToString { it.actor.name }),
        plotText =
        if (plot.isNullOrBlank()) txt(if (this is TorrentLoadResponse) R.string.torrent_no_plot else R.string.normal_no_plot) else txt(
            plot!!
        ),
        backgroundPosterUrl = backgroundPosterUrl,
        title = name,
        typeText = txt(
            when (type) {
                TvType.TvSeries -> R.string.tv_series_singular
                TvType.Anime -> R.string.anime_singular
                TvType.OVA -> R.string.ova_singular
                TvType.AnimeMovie -> R.string.movies_singular
                TvType.Cartoon -> R.string.cartoons_singular
                TvType.Documentary -> R.string.documentaries_singular
                TvType.Movie -> R.string.movies_singular
                TvType.Torrent -> R.string.torrent_singular
                TvType.AsianDrama -> R.string.asian_drama_singular
                TvType.Live -> R.string.live_singular
            }
        ),
        yearText = txt(year),
        apiName = txt(apiName),
        ratingText = rating?.div(1000f)?.let { UiText.StringResource(R.string.rating_format, it) },
        vpnText = txt(
            when (repo.vpnStatus) {
                VPNStatus.None -> null
                VPNStatus.Torrent -> R.string.vpn_torrent
                VPNStatus.MightBeNeeded -> R.string.vpn_might_be_needed
            }
        ),
        metaText =
        if (repo.providerType == ProviderType.MetaProvider) txt(R.string.provider_info_meta) else null,
        durationText = txt(R.string.duration_format, duration),
        onGoingText = if (this is EpisodeResponse) {
            txt(
                when (showStatus) {
                    ShowStatus.Ongoing -> R.string.status_ongoing
                    ShowStatus.Completed -> R.string.status_completed
                    else -> null
                }
            )
        } else null,
        noEpisodesFoundText =
        if ((this is TvSeriesLoadResponse && this.episodes.isEmpty()) || (this is AnimeLoadResponse && !this.episodes.any { it.value.isNotEmpty() })) txt(
            R.string.no_episodes_found
        ) else null
    )
}

class ResultViewModel2 : ViewModel() {
    private var currentResponse: LoadResponse? = null

    data class EpisodeIndexer(
        val dubStatus: DubStatus,
        val season: Int,
    )

    /** map<dub, map<season, List<episode>>> */
    private var currentEpisodes: Map<EpisodeIndexer, List<ResultEpisode>> = mapOf()
    private var currentRanges: Map<EpisodeIndexer, List<EpisodeRange>> = mapOf()
    private var currentIndex: EpisodeIndexer? = null
    private var currentRange: EpisodeRange? = null
    private var currentShowFillers: Boolean = false
    private var currentRepo: APIRepository? = null
    private var currentId: Int? = null
    private var fillers: Map<Int, Boolean> = emptyMap()
    private var generator: IGenerator? = null
    private var preferDubStatus: DubStatus? = null
    private var preferStartEpisode: Int? = null
    private var preferStartSeason: Int? = null

    private val _page: MutableLiveData<Resource<ResultData>> =
        MutableLiveData(Resource.Loading())
    val page: LiveData<Resource<ResultData>> = _page

    private val _episodes: MutableLiveData<Resource<List<ResultEpisode>>> =
        MutableLiveData(Resource.Loading())
    val episodes: LiveData<Resource<List<ResultEpisode>>> = _episodes

    private val _episodesCount: MutableLiveData<Int> =
        MutableLiveData(0)
    val episodesCount: LiveData<Int> = _episodesCount

    private val _trailers: MutableLiveData<List<TrailerData>> = MutableLiveData(mutableListOf())
    val trailers: LiveData<List<TrailerData>> = _trailers

    private val _dubStatus: MutableLiveData<DubStatus?> = MutableLiveData(null)
    val dubStatus: LiveData<DubStatus?> = _dubStatus

    private val _dubSubSelections: MutableLiveData<List<DubStatus>> = MutableLiveData(emptyList())
    val dubSubSelections: LiveData<List<DubStatus>> = _dubSubSelections

    companion object {
        private const val EPISODE_RANGE_SIZE = 50
        private const val EPISODE_RANGE_OVERLOAD = 60

        private fun filterName(name: String?): String? {
            if (name == null) return null
            Regex("[eE]pisode [0-9]*(.*)").find(name)?.groupValues?.get(1)?.let {
                if (it.isEmpty())
                    return null
            }
            return name
        }

        fun singleMap(ep: ResultEpisode): Map<EpisodeIndexer, List<ResultEpisode>> =
            mapOf(
                EpisodeIndexer(DubStatus.None, 0) to listOf(
                    ep
                )
            )

        private fun getRanges(allEpisodes: Map<EpisodeIndexer, List<ResultEpisode>>): Map<EpisodeIndexer, List<EpisodeRange>> {
            return allEpisodes.keys.mapNotNull { index ->
                val episodes =
                    allEpisodes[index] ?: return@mapNotNull null // this should never happened

                // fast case
                if (episodes.size <= EPISODE_RANGE_OVERLOAD) {
                    return@mapNotNull index to listOf(
                        EpisodeRange(
                            0,
                            episodes.size,
                            episodes.minOf { it.episode },
                            episodes.maxOf { it.episode })
                    )
                }

                if (episodes.isEmpty()) {
                    return@mapNotNull null
                }

                val list = mutableListOf<EpisodeRange>()

                val currentEpisode = episodes.first()
                var currentIndex = 0
                val maxIndex = episodes.size
                var targetEpisode = 0
                var currentMin = currentEpisode.episode
                var currentMax = currentEpisode.episode

                while (currentIndex < maxIndex) {
                    val startIndex = currentIndex
                    targetEpisode += EPISODE_RANGE_SIZE
                    while (currentIndex < maxIndex && episodes[currentIndex].episode <= targetEpisode) {
                        val episodeNumber = episodes[currentIndex].episode
                        if (episodeNumber < currentMin) {
                            currentMin = episodeNumber
                        } else if (episodeNumber > currentMax) {
                            currentMax = episodeNumber
                        }
                        ++currentIndex
                    }

                    val length = currentIndex - startIndex
                    if (length <= 0) continue

                    list.add(
                        EpisodeRange(
                            startIndex,
                            length,
                            currentMin,
                            currentMax
                        )
                    )
                }

                /*var currentMin = Int.MAX_VALUE
                var currentMax = Int.MIN_VALUE
                var currentStartIndex = 0
                var currentLength = 0
                for (ep in episodes) {
                    val episodeNumber = ep.episode
                    if (episodeNumber < currentMin) {
                        currentMin = episodeNumber
                    } else if (episodeNumber > currentMax) {
                        currentMax = episodeNumber
                    }

                    if (++currentLength >= EPISODE_RANGE_SIZE) {
                        list.add(
                            EpisodeRange(
                                currentStartIndex,
                                currentLength,
                                currentMin,
                                currentMax
                            )
                        )
                        currentMin = Int.MAX_VALUE
                        currentMax = Int.MIN_VALUE
                        currentStartIndex += currentLength
                        currentLength = 0
                    }
                }
                if (currentLength > 0) {
                    list.add(
                        EpisodeRange(
                            currentStartIndex,
                            currentLength,
                            currentMin,
                            currentMax
                        )
                    )
                }*/

                index to list
            }.toMap()
        }
    }

    private suspend fun updateFillers(name: String) {
        fillers =
            try {
                FillerEpisodeCheck.getFillerEpisodes(name)
            } catch (e: Exception) {
                logError(e)
                null
            } ?: emptyMap()

    }

    fun changeDubStatus(status: DubStatus) {
        postEpisodeRange(currentIndex?.copy(dubStatus = status), currentRange)
    }

    fun changeRange(range: EpisodeRange) {
        postEpisodeRange(currentIndex, range)
    }

    private fun getEpisodes(indexer: EpisodeIndexer, range: EpisodeRange): List<ResultEpisode> {
        //TODO ADD GENERATOR

        val startIndex = range.startIndex
        val length = range.length

        return currentEpisodes[indexer]
            ?.let { list ->
                val start = minOf(list.size, startIndex)
                val end = minOf(list.size, start + length)
                list.subList(start, end).map {
                    val posDur = DataStoreHelper.getViewPos(it.id)
                    it.copy(position = posDur?.position ?: 0, duration = posDur?.duration ?: 0)
                }
            }
            ?: emptyList()
    }

    fun reloadEpisodes() {
        _episodes.postValue(
            Resource.Success(
                getEpisodes(
                    currentIndex ?: return,
                    currentRange ?: return
                )
            )
        )
    }

    private fun postEpisodeRange(indexer: EpisodeIndexer?, range: EpisodeRange?) {
        if (range == null || indexer == null) {
            return
        }

        currentIndex = indexer
        currentRange = range

        //TODO SET KEYS
        preferStartEpisode = range.startEpisode
        preferStartSeason = indexer.season
        preferDubStatus = indexer.dubStatus

        val ret = getEpisodes(indexer, range)
        _episodes.postValue(Resource.Success(ret))
    }

    private suspend fun postSuccessful(
        loadResponse: LoadResponse,
        apiRepository: APIRepository,
        updateEpisodes: Boolean,
        updateFillers: Boolean,
    ) {
        currentResponse = loadResponse
        postPage(loadResponse, apiRepository)
        if (updateEpisodes)
            postEpisodes(loadResponse, updateFillers)
    }

    private suspend fun postEpisodes(loadResponse: LoadResponse, updateFillers: Boolean) {
        _episodes.postValue(Resource.Loading())

        val mainId = loadResponse.getId()
        currentId = mainId

        if (updateFillers && loadResponse is AnimeLoadResponse) {
            updateFillers(loadResponse.name)
        }

        val allEpisodes = when (loadResponse) {
            is AnimeLoadResponse -> {
                val existingEpisodes = HashSet<Int>()
                val episodes: MutableMap<EpisodeIndexer, MutableList<ResultEpisode>> =
                    mutableMapOf()
                loadResponse.episodes.map { ep ->
                    val idIndex = ep.key.id
                    for ((index, i) in ep.value.withIndex()) {
                        val episode = i.episode ?: (index + 1)
                        val id = mainId + episode + idIndex * 1000000
                        if (!existingEpisodes.contains(episode)) {
                            existingEpisodes.add(id)
                            val eps =
                                buildResultEpisode(
                                    loadResponse.name,
                                    filterName(i.name),
                                    i.posterUrl,
                                    episode,
                                    null,
                                    i.season,
                                    i.data,
                                    loadResponse.apiName,
                                    id,
                                    index,
                                    i.rating,
                                    i.description,
                                    fillers.getOrDefault(episode, false),
                                    loadResponse.type,
                                    mainId
                                )

                            val season = eps.season ?: 0
                            val indexer = EpisodeIndexer(ep.key, season)
                            episodes[indexer]?.add(eps) ?: run {
                                episodes[indexer] = mutableListOf(eps)
                            }
                        }
                    }
                }
                episodes
            }
            is TvSeriesLoadResponse -> {
                val episodes: MutableMap<EpisodeIndexer, MutableList<ResultEpisode>> =
                    mutableMapOf()
                val existingEpisodes = HashSet<Int>()
                for ((index, episode) in loadResponse.episodes.sortedBy {
                    (it.season?.times(10000) ?: 0) + (it.episode ?: 0)
                }.withIndex()) {
                    val episodeIndex = episode.episode ?: (index + 1)
                    val id =
                        mainId + (episode.season?.times(100000) ?: 0) + episodeIndex + 1
                    if (!existingEpisodes.contains(id)) {
                        existingEpisodes.add(id)
                        val seasonIndex = episode.season?.minus(1)
                        val currentSeason =
                            loadResponse.seasonNames?.getOrNull(seasonIndex ?: -1)

                        val ep =
                            buildResultEpisode(
                                loadResponse.name,
                                filterName(episode.name),
                                episode.posterUrl,
                                episodeIndex,
                                seasonIndex,
                                currentSeason?.season ?: episode.season,
                                episode.data,
                                loadResponse.apiName,
                                id,
                                index,
                                episode.rating,
                                episode.description,
                                null,
                                loadResponse.type,
                                mainId
                            )

                        val season = episode.season ?: 0
                        val indexer = EpisodeIndexer(DubStatus.None, season)

                        episodes[indexer]?.add(ep) ?: kotlin.run {
                            episodes[indexer] = mutableListOf(ep)
                        }
                    }
                }
                episodes
            }
            is MovieLoadResponse -> {
                singleMap(
                    buildResultEpisode(
                        loadResponse.name,
                        loadResponse.name,
                        null,
                        0,
                        null,
                        null,
                        loadResponse.dataUrl,
                        loadResponse.apiName,
                        (mainId), // HAS SAME ID
                        0,
                        null,
                        null,
                        null,
                        loadResponse.type,
                        mainId
                    )
                )
            }
            is LiveStreamLoadResponse -> {
                singleMap(
                    buildResultEpisode(
                        loadResponse.name,
                        loadResponse.name,
                        null,
                        0,
                        null,
                        null,
                        loadResponse.dataUrl,
                        loadResponse.apiName,
                        (mainId), // HAS SAME ID
                        0,
                        null,
                        null,
                        null,
                        loadResponse.type,
                        mainId
                    )
                )
            }
            is TorrentLoadResponse -> {
                singleMap(
                    buildResultEpisode(
                        loadResponse.name,
                        loadResponse.name,
                        null,
                        0,
                        null,
                        null,
                        loadResponse.torrent ?: loadResponse.magnet ?: "",
                        loadResponse.apiName,
                        (mainId), // HAS SAME ID
                        0,
                        null,
                        null,
                        null,
                        loadResponse.type,
                        mainId
                    )
                )
            }
            else -> {
                mapOf()
            }
        }

        currentEpisodes = allEpisodes
        val ranges = getRanges(allEpisodes)
        currentRanges = ranges

        // this takes the indexer most preferable by the user given the current sorting
        val min = ranges.keys.minByOrNull { index ->
            kotlin.math.abs(
                index.season - (preferStartSeason ?: 0)
            ) + if (index.dubStatus == preferDubStatus) 0 else 100000
        }

        // this takes the range most preferable by the user given the current sorting
        val ranger = ranges[min]
        val range = ranger?.firstOrNull {
            it.startEpisode >= (preferStartEpisode ?: 0)
        } ?: ranger?.lastOrNull()

        postEpisodeRange(min, range)
    }

    // this instantly updates the metadata on the page
    private fun postPage(loadResponse: LoadResponse, apiRepository: APIRepository) {
        _page.postValue(Resource.Success(loadResponse.toResultData(apiRepository)))
        _trailers.postValue(loadResponse.trailers)
    }

    fun load(
        url: String,
        apiName: String,
        showFillers: Boolean,
        dubStatus: DubStatus,
        startEpisode: Int,
        startSeason: Int
    ) =
        viewModelScope.launch {
            _page.postValue(Resource.Loading(url))
            _episodes.postValue(Resource.Loading(url))

            preferDubStatus = dubStatus
            currentShowFillers = showFillers
            preferStartEpisode = startEpisode
            preferStartSeason = startSeason

            // set api
            val api = APIHolder.getApiFromNameNull(apiName) ?: APIHolder.getApiFromUrlNull(url)
            if (api == null) {
                _page.postValue(
                    Resource.Failure(
                        false,
                        null,
                        null,
                        "This provider does not exist"
                    )
                )
                return@launch
            }


            // validate url
            val validUrlResource = safeApiCall {
                SyncRedirector.redirect(
                    url,
                    api.mainUrl.replace(NineAnimeProvider().mainUrl, "9anime")
                        .replace(GogoanimeProvider().mainUrl, "gogoanime")
                )
            }
            if (validUrlResource !is Resource.Success) {
                if (validUrlResource is Resource.Failure) {
                    _page.postValue(validUrlResource)
                }

                return@launch
            }
            val validUrl = validUrlResource.value
            val repo = APIRepository(api)
            currentRepo = repo

            when (val data = repo.load(validUrl)) {
                is Resource.Failure -> {
                    _page.postValue(data)
                }
                is Resource.Success -> {
                    val loadResponse = data.value
                    val mainId = loadResponse.getId()

                    AcraApplication.setKey(
                        DOWNLOAD_HEADER_CACHE,
                        mainId.toString(),
                        VideoDownloadHelper.DownloadHeaderCached(
                            apiName,
                            validUrl,
                            loadResponse.type,
                            loadResponse.name,
                            loadResponse.posterUrl,
                            mainId,
                            System.currentTimeMillis(),
                        )
                    )

                    postSuccessful(
                        data.value,
                        updateEpisodes = true,
                        updateFillers = showFillers,
                        apiRepository = repo
                    )
                }
                is Resource.Loading -> {
                    debugException { "Invalid load result" }
                }
            }
        }
}