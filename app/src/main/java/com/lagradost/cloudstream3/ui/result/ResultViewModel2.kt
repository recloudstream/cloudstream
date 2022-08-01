package com.lagradost.cloudstream3.ui.result

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getId
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.LoadResponse.Companion.getAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.getMalId
import com.lagradost.cloudstream3.animeproviders.GogoanimeProvider
import com.lagradost.cloudstream3.animeproviders.NineAnimeProvider
import com.lagradost.cloudstream3.metaproviders.SyncRedirector
import com.lagradost.cloudstream3.mvvm.*
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.providers.Kitsu
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.player.IGenerator
import com.lagradost.cloudstream3.utils.*
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
    val playMovieText: UiText?,
    val plotHeaderText: UiText,
)

fun txt(status: DubStatus?): UiText? {
    return txt(
        when (status) {
            DubStatus.Dubbed -> R.string.app_dubbed_text
            DubStatus.Subbed -> R.string.app_subbed_text
            else -> null
        }
    )
}

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
        plotHeaderText = txt(
            when (this.type) {
                TvType.Torrent -> R.string.torrent_plot
                else -> R.string.result_plot
            }
        ),
        playMovieText = txt(
            when (this.type) {
                TvType.Live -> R.string.play_livestream_button
                TvType.Torrent -> R.string.play_torrent_button
                TvType.Movie, TvType.AnimeMovie -> R.string.play_movie_button
                else -> null
            }
        ),
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
    private var currentMeta: SyncAPI.SyncResult? = null
    private var currentSync: Map<String, String>? = null
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

    private val _episodesCountText: MutableLiveData<UiText?> =
        MutableLiveData(null)
    val episodesCountText: LiveData<UiText?> = _episodesCountText

    private val _trailers: MutableLiveData<List<TrailerData>> = MutableLiveData(mutableListOf())
    val trailers: LiveData<List<TrailerData>> = _trailers


    private val _dubSubSelections: MutableLiveData<List<Pair<UiText?, DubStatus>>> =
        MutableLiveData(emptyList())
    val dubSubSelections: LiveData<List<Pair<UiText?, DubStatus>>> = _dubSubSelections

    private val _rangeSelections: MutableLiveData<List<Pair<UiText?, EpisodeRange>>> = MutableLiveData(emptyList())
    val rangeSelections: LiveData<List<Pair<UiText?, EpisodeRange>>> = _rangeSelections

    private val _seasonSelections: MutableLiveData<List<Pair<UiText?, Int>>> = MutableLiveData(emptyList())
    val seasonSelections: LiveData<List<Pair<UiText?, Int>>> = _seasonSelections


    private val _recommendations: MutableLiveData<List<SearchResponse>> =
        MutableLiveData(emptyList())
    val recommendations: LiveData<List<SearchResponse>> = _recommendations

    private val _selectedRange: MutableLiveData<UiText?> =
        MutableLiveData(null)
    val selectedRange: LiveData<UiText?> = _selectedRange

    private val _selectedSeason: MutableLiveData<UiText?> =
        MutableLiveData(null)
    val selectedSeason: LiveData<UiText?> = _selectedSeason

    private val _selectedDubStatus: MutableLiveData<UiText?> = MutableLiveData(null)
    val selectedDubStatus: LiveData<UiText?> = _selectedDubStatus

    companion object {
        const val TAG = "RVM2"
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

    private suspend fun applyMeta(
        resp: LoadResponse,
        meta: SyncAPI.SyncResult?,
        syncs: Map<String, String>? = null
    ): Pair<LoadResponse, Boolean> {
        if (meta == null) return resp to false
        var updateEpisodes = false
        val out = resp.apply {
            Log.i(ResultViewModel.TAG, "applyMeta")

            duration = duration ?: meta.duration
            rating = rating ?: meta.publicScore
            tags = tags ?: meta.genres
            plot = if (plot.isNullOrBlank()) meta.synopsis else plot
            posterUrl = posterUrl ?: meta.posterUrl ?: meta.backgroundPosterUrl
            actors = actors ?: meta.actors

            if (this is EpisodeResponse) {
                nextAiring = nextAiring ?: meta.nextAiring
            }

            for ((k, v) in syncs ?: emptyMap()) {
                syncData[k] = v
            }

            val realRecommendations = ArrayList<SearchResponse>()
            val apiNames = listOf(GogoanimeProvider().name, NineAnimeProvider().name)
            meta.recommendations?.forEach { rec ->
                apiNames.forEach { name ->
                    realRecommendations.add(rec.copy(apiName = name))
                }
            }

            recommendations = recommendations?.union(realRecommendations)?.toList()
                ?: realRecommendations

            argamap({
                addTrailer(meta.trailers)
            }, {
                if (this !is AnimeLoadResponse) return@argamap
                val map =
                    Kitsu.getEpisodesDetails(getMalId(), getAniListId(), isResponseRequired = false)
                if (map.isNullOrEmpty()) return@argamap
                updateEpisodes = DubStatus.values().map { dubStatus ->
                    val current =
                        this.episodes[dubStatus]?.mapIndexed { index, episode ->
                            episode.apply {
                                this.episode = this.episode ?: (index + 1)
                            }
                        }?.sortedBy { it.episode ?: 0 }?.toMutableList()
                    if (current.isNullOrEmpty()) return@map false
                    val episodeNumbers = current.map { ep -> ep.episode!! }
                    var updateCount = 0
                    map.forEach { (episode, node) ->
                        episodeNumbers.binarySearch(episode).let { index ->
                            current.getOrNull(index)?.let { currentEp ->
                                current[index] = currentEp.apply {
                                    updateCount++
                                    val currentBack = this
                                    this.description = this.description ?: node.description?.en
                                    this.name = this.name ?: node.titles?.canonical
                                    this.episode = this.episode ?: node.num ?: episodeNumbers[index]
                                    this.posterUrl = this.posterUrl ?: node.thumbnail?.original?.url
                                }
                            }
                        }
                    }
                    this.episodes[dubStatus] = current
                    updateCount > 0
                }.any { it }
            })
        }
        return out to updateEpisodes
    }

    fun setMeta(meta: SyncAPI.SyncResult, syncs: Map<String, String>?) =
        viewModelScope.launch {
            Log.i(TAG, "setMeta")
            currentMeta = meta
            currentSync = syncs
            val (value, updateEpisodes) = Coroutines.ioWork {
                currentResponse?.let { resp ->
                    return@ioWork applyMeta(resp, meta, syncs)
                }
                return@ioWork null to null
            }

            postSuccessful(
                value ?: return@launch,
                currentRepo ?: return@launch,
                updateEpisodes ?: return@launch,
                false
            )
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

    fun changeSeason(season: Int) {
        postEpisodeRange(currentIndex?.copy(season = season), currentRange)
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

        val size = currentEpisodes[indexer]?.size

        _episodesCountText.postValue(
            txt(
                R.string.episode_format,
                if (size == 1) R.string.episode else R.string.episodes,
                size
            )
        )

        currentIndex = indexer
        currentRange = range

        _selectedSeason.postValue(
            when (indexer.season) {
                0 -> txt(R.string.no_season)
                else -> txt(R.string.season_format, R.string.season, indexer.season) //TODO FIX
            }
        )
        _selectedRange.postValue(
            if ((currentRanges[indexer]?.size ?: 0) > 1) {
                txt(R.string.episodes_range, range.startEpisode, range.endEpisode)
            } else {
                null
            }
        )
        _selectedDubStatus.postValue(txt(indexer.dubStatus))

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
        _recommendations.postValue(loadResponse.recommendations ?: emptyList())
        _page.postValue(Resource.Success(loadResponse.toResultData(apiRepository)))
        _trailers.postValue(loadResponse.trailers)
    }

    fun hasLoaded() = currentResponse != null

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
                    val loadResponse = Coroutines.ioWork {
                        applyMeta(data.value, currentMeta, currentSync).first
                    }
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