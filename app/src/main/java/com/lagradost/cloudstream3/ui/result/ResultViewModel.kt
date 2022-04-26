package com.lagradost.cloudstream3.ui.result

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.APIHolder.getApiFromUrlNull
import com.lagradost.cloudstream3.APIHolder.getId
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.animeproviders.GogoanimeProvider
import com.lagradost.cloudstream3.animeproviders.NineAnimeProvider
import com.lagradost.cloudstream3.metaproviders.SyncRedirector
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.player.IGenerator
import com.lagradost.cloudstream3.ui.player.RepoLinkGenerator
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.getDub
import com.lagradost.cloudstream3.utils.DataStoreHelper.getResultSeason
import com.lagradost.cloudstream3.utils.DataStoreHelper.getResultWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.DataStoreHelper.setBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.setDub
import com.lagradost.cloudstream3.utils.DataStoreHelper.setResultSeason
import com.lagradost.cloudstream3.utils.DataStoreHelper.setResultWatchState
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.FillerEpisodeCheck.getFillerEpisodes
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.set

const val EPISODE_RANGE_SIZE = 50
const val EPISODE_RANGE_OVERLOAD = 60

class ResultViewModel : ViewModel() {
    private var repo: APIRepository? = null
    private var generator: IGenerator? = null

    private val _resultResponse: MutableLiveData<Resource<LoadResponse>> = MutableLiveData()
    private val _episodes: MutableLiveData<List<ResultEpisode>> = MutableLiveData()
    private val episodeById: MutableLiveData<HashMap<Int, Int>> =
        MutableLiveData() // lookup by ID to get Index

    private val _publicEpisodes: MutableLiveData<Resource<List<ResultEpisode>>> = MutableLiveData()
    private val _publicEpisodesCount: MutableLiveData<Int> = MutableLiveData() // before the sorting
    private val _rangeOptions: MutableLiveData<List<String>> = MutableLiveData()
    val selectedRange: MutableLiveData<String> = MutableLiveData()
    private val selectedRangeInt: MutableLiveData<Int> = MutableLiveData()
    val rangeOptions: LiveData<List<String>> = _rangeOptions

    val result: LiveData<Resource<LoadResponse>> get() = _resultResponse

    val episodes: LiveData<List<ResultEpisode>> get() = _episodes
    val publicEpisodes: LiveData<Resource<List<ResultEpisode>>> get() = _publicEpisodes
    val publicEpisodesCount: LiveData<Int> get() = _publicEpisodesCount

    val dubStatus: LiveData<DubStatus> get() = _dubStatus
    private val _dubStatus: MutableLiveData<DubStatus> = MutableLiveData()

    private val page: MutableLiveData<LoadResponse> = MutableLiveData()
    val id: MutableLiveData<Int> = MutableLiveData()
    val selectedSeason: MutableLiveData<Int> = MutableLiveData(-2)
    val seasonSelections: MutableLiveData<List<Int?>> = MutableLiveData()

    val dubSubSelections: LiveData<Set<DubStatus>> get() = _dubSubSelections
    private val _dubSubSelections: MutableLiveData<Set<DubStatus>> = MutableLiveData()

    val dubSubEpisodes: LiveData<Map<DubStatus, List<ResultEpisode>>?> get() = _dubSubEpisodes
    private val _dubSubEpisodes: MutableLiveData<Map<DubStatus, List<ResultEpisode>>?> =
        MutableLiveData()

    private val _watchStatus: MutableLiveData<WatchType> = MutableLiveData()
    val watchStatus: LiveData<WatchType> get() = _watchStatus

    fun updateWatchStatus(status: WatchType) = viewModelScope.launch {
        val currentId = id.value ?: return@launch
        _watchStatus.postValue(status)
        val resultPage = page.value

        withContext(Dispatchers.IO) {
            setResultWatchState(currentId, status.internalId)
            if (resultPage != null) {
                val current = getBookmarkedData(currentId)
                val currentTime = System.currentTimeMillis()
                setBookmarkedData(
                    currentId,
                    DataStoreHelper.BookmarkedData(
                        currentId,
                        current?.bookmarkedTime ?: currentTime,
                        currentTime,
                        resultPage.name,
                        resultPage.url,
                        resultPage.apiName,
                        resultPage.type,
                        resultPage.posterUrl,
                        resultPage.year
                    )
                )
            }
        }
    }

    companion object {
        const val TAG = "RVM"
    }

    var lastMeta: SyncAPI.SyncResult? = null
    private fun applyMeta(resp: LoadResponse, meta: SyncAPI.SyncResult?): LoadResponse {
        if (meta == null) return resp
        lastMeta = meta
        return resp.apply {
            Log.i(TAG, "applyMeta")

            duration = duration ?: meta.duration
            rating = rating ?: meta.publicScore
            tags = tags ?: meta.genres
            plot = if (plot.isNullOrBlank()) meta.synopsis else plot
            addTrailer(meta.trailerUrl)
            posterUrl = posterUrl ?: meta.posterUrl ?: meta.backgroundPosterUrl
            actors = actors ?: meta.actors

            val realRecommendations = ArrayList<SearchResponse>()
            val apiNames = listOf(GogoanimeProvider().name, NineAnimeProvider().name)
            meta.recommendations?.forEach { rec ->
                apiNames.forEach { name ->
                    realRecommendations.add(rec.copy(apiName = name))
                }
            }

            recommendations = recommendations?.union(realRecommendations)?.toList()
                ?: realRecommendations
        }
    }

    fun setMeta(meta: SyncAPI.SyncResult) {
        Log.i(TAG, "setMeta")
        (result.value as? Resource.Success<LoadResponse>?)?.value?.let { resp ->
            _resultResponse.postValue(Resource.Success(applyMeta(resp, meta)))
        }
    }

    private fun loadWatchStatus(localId: Int? = null) {
        val currentId = localId ?: id.value ?: return
        val currentWatch = getResultWatchState(currentId)
        _watchStatus.postValue(currentWatch)
    }

    private fun filterEpisodes(list: List<ResultEpisode>?, selection: Int?, range: Int?) {
        if (list == null) return
        val seasonTypes = HashMap<Int?, Boolean>()
        for (i in list) {
            if (!seasonTypes.containsKey(i.season)) {
                seasonTypes[i.season] = true
            }
        }
        val seasons = seasonTypes.toList().map { it.first }.sortedBy { it }
        seasonSelections.postValue(seasons)
        if (seasons.isEmpty()) { // WHAT THE FUCK DID YOU DO????? HOW DID YOU DO THIS
            _publicEpisodes.postValue(Resource.Success(emptyList()))
            return
        }

        val realSelection = if (!seasonTypes.containsKey(selection)) seasons.first() else selection
        val internalId = id.value

        if (internalId != null) setResultSeason(internalId, realSelection)

        selectedSeason.postValue(realSelection ?: -2)

        var currentList = list.filter { it.season == realSelection }
        _publicEpisodesCount.postValue(currentList.size)

        val rangeList = ArrayList<String>()
        for (i in currentList.indices step EPISODE_RANGE_SIZE) {
            if (i + EPISODE_RANGE_SIZE < currentList.size) {
                rangeList.add("${i + 1}-${i + EPISODE_RANGE_SIZE}")
            } else {
                rangeList.add("${i + 1}-${currentList.size}")
            }
        }

        val cRange = range ?: if (selection != null) {
            0
        } else {
            selectedRangeInt.value ?: 0
        }

        val realRange = if (cRange * EPISODE_RANGE_SIZE > currentList.size) {
            currentList.size / EPISODE_RANGE_SIZE
        } else {
            cRange
        }

        if (currentList.size > EPISODE_RANGE_OVERLOAD) {
            currentList = currentList.subList(
                realRange * EPISODE_RANGE_SIZE,
                minOf(currentList.size, (realRange + 1) * EPISODE_RANGE_SIZE)
            )
            _rangeOptions.postValue(rangeList)
            selectedRangeInt.postValue(realRange)
            selectedRange.postValue(rangeList[realRange])
        } else {
            val allRange = "1-${currentList.size}"
            _rangeOptions.postValue(listOf(allRange))
            selectedRangeInt.postValue(0)
            selectedRange.postValue(allRange)
        }

        _publicEpisodes.postValue(Resource.Success(currentList))
    }

    fun changeSeason(selection: Int?) {
        filterEpisodes(_episodes.value, selection, null)
    }

    fun changeRange(range: Int?) {
        filterEpisodes(_episodes.value, null, range)
    }

    fun changeDubStatus(status: DubStatus?) {
        if (status == null) return
        dubSubEpisodes.value?.get(status)?.let { episodes ->
            id.value?.let {
                setDub(it, status)
            }
            _dubStatus.postValue(status!!)
            updateEpisodes(null, episodes, null)
        }
    }

    suspend fun loadEpisode(
        episode: ResultEpisode,
        isCasting: Boolean,
        clearCache: Boolean = false
    ): Resource<Pair<Set<ExtractorLink>, Set<SubtitleData>>> {
        return safeApiCall {
            val index = _episodes.value?.indexOf(episode) ?: episode.index

            val currentLinks = mutableSetOf<ExtractorLink>()
            val currentSubs = mutableSetOf<SubtitleData>()

            generator?.goto(index)
            generator?.generateLinks(clearCache, isCasting, {
                it.first?.let { link ->
                    currentLinks.add(link)
                }
            }, { sub ->
                currentSubs.add(sub)
            })

            return@safeApiCall Pair(
                currentLinks.toSet(),
                currentSubs.toSet()
            )
        }
    }

    fun getGenerator(episode: ResultEpisode): IGenerator? {
        val index = _episodes.value?.indexOf(episode) ?: episode.index

        generator?.goto(index)
        return generator
    }

    private fun updateEpisodes(localId: Int?, list: List<ResultEpisode>, selection: Int?) {
        _episodes.postValue(list)
        generator = RepoLinkGenerator(list)

        val set = HashMap<Int, Int>()
        val range = selectedRangeInt.value

        list.withIndex().forEach { set[it.value.id] = it.index }
        episodeById.postValue(set)

        filterEpisodes(
            list,
            if (selection == -1) getResultSeason(localId ?: id.value ?: return) else selection,
            range
        )
    }

    fun reloadEpisodes() {
        val current = _episodes.value ?: return
        val copy = current.map {
            val posDur = getViewPos(it.id)
            it.copy(position = posDur?.position ?: 0, duration = posDur?.duration ?: 0)
        }
        updateEpisodes(null, copy, selectedSeason.value)
    }

    private fun filterName(name: String?): String? {
        if (name == null) return null
        Regex("[eE]pisode [0-9]*(.*)").find(name)?.groupValues?.get(1)?.let {
            if (it.isEmpty())
                return null
        }
        return name
    }

    fun load(url: String, apiName: String, showFillers: Boolean) = viewModelScope.launch {
        _publicEpisodes.postValue(Resource.Loading())
        _resultResponse.postValue(Resource.Loading(url))

        val api = getApiFromNameNull(apiName) ?: getApiFromUrlNull(url)
        if (api == null) {
            _resultResponse.postValue(
                Resource.Failure(
                    false,
                    null,
                    null,
                    "This provider does not exist"
                )
            )
            return@launch
        }

        val validUrlResource = safeApiCall {
            SyncRedirector.redirect(
                url,
                api.mainUrl.replace(NineAnimeProvider().mainUrl, "9anime")
                    .replace(GogoanimeProvider().mainUrl, "gogoanime")
            )
        }

        if (validUrlResource !is Resource.Success) {
            if (validUrlResource is Resource.Failure) {
                _resultResponse.postValue(validUrlResource)
            }

            return@launch
        }
        val validUrl = validUrlResource.value

        _resultResponse.postValue(Resource.Loading(validUrl))

        _apiName.postValue(apiName)

        repo = APIRepository(api)

        val data = repo?.load(validUrl) ?: return@launch

        _resultResponse.postValue(data)

        when (data) {
            is Resource.Success -> {
                val d = applyMeta(data.value, lastMeta)
                page.postValue(d)
                val mainId = d.getId()
                id.postValue(mainId)
                loadWatchStatus(mainId)

                setKey(
                    DOWNLOAD_HEADER_CACHE,
                    mainId.toString(),
                    VideoDownloadHelper.DownloadHeaderCached(
                        apiName,
                        validUrl,
                        d.type,
                        d.name,
                        d.posterUrl,
                        mainId,
                        System.currentTimeMillis(),
                    )
                )

                when (d) {
                    is AnimeLoadResponse -> {
                        if (d.episodes.isEmpty()) {
                            _dubSubEpisodes.postValue(emptyMap())
                            return@launch
                        }

                        val status = getDub(mainId)
                        val statuses = d.episodes.map { it.key }
                        val dubStatus = if (statuses.contains(status)) status else statuses.first()

                        val fillerEpisodes =
                            if (showFillers) safeApiCall { getFillerEpisodes(d.name) } else null

                        val existingEpisodes = HashSet<Int>()
                        val res = d.episodes.map { ep ->
                            val episodes = ArrayList<ResultEpisode>()
                            val idIndex = ep.key.id
                            for ((index, i) in ep.value.withIndex()) {
                                val episode = i.episode ?: (index + 1)
                                val id = mainId + episode + idIndex * 1000000
                                if (!existingEpisodes.contains(episode)) {
                                    existingEpisodes.add(id)
                                    episodes.add(buildResultEpisode(
                                        d.name,
                                        filterName(i.name),
                                        i.posterUrl,
                                        episode,
                                        i.season,
                                        i.data,
                                        apiName,
                                        id,
                                        index,
                                        i.rating,
                                        i.description,
                                        if (fillerEpisodes is Resource.Success) fillerEpisodes.value?.let {
                                            it.contains(episode) && it[episode] == true
                                        } ?: false else false,
                                        d.type,
                                        mainId
                                    ))
                                }
                            }

                            Pair(ep.key, episodes)
                        }.toMap()

                        // These posts needs to be in this order as to make the preferDub in ResultFragment work
                        _dubSubEpisodes.postValue(res)
                        res[dubStatus]?.let { episodes ->
                            updateEpisodes(mainId, episodes, -1)
                        }
                        _dubStatus.postValue(dubStatus)
                        _dubSubSelections.postValue(d.episodes.keys)
                    }

                    is TvSeriesLoadResponse -> {
                        val episodes = ArrayList<ResultEpisode>()
                        val existingEpisodes = HashSet<Int>()
                        for ((index, i) in d.episodes.sortedBy {
                            (it.season?.times(10000) ?: 0) + (it.episode ?: 0)
                        }.withIndex()) {
                            val episode = i.episode ?: (index + 1)
                            val id = mainId + (i.season?.times(100000) ?: 0) + episode + 1
                            if (!existingEpisodes.contains(id)) {
                                existingEpisodes.add(id)
                                episodes.add(
                                    buildResultEpisode(
                                        d.name,
                                        filterName(i.name),
                                        i.posterUrl,
                                        episode,
                                        i.season,
                                        i.data,
                                        apiName,
                                        id,
                                        index,
                                        i.rating,
                                        i.description,
                                        null,
                                        d.type,
                                        mainId
                                    )
                                )
                            }
                        }
                        updateEpisodes(mainId, episodes, -1)
                    }
                    is MovieLoadResponse -> {
                        buildResultEpisode(
                            d.name,
                            d.name,
                            null,
                            0,
                            null,
                            d.dataUrl,
                            d.apiName,
                            (mainId), // HAS SAME ID
                            0,
                            null,
                            null,
                            null,
                            d.type,
                            mainId
                        ).let {
                            updateEpisodes(mainId, listOf(it), -1)
                        }
                    }
                    is TorrentLoadResponse -> {
                        updateEpisodes(
                            mainId, listOf(
                                buildResultEpisode(
                                    d.name,
                                    d.name,
                                    null,
                                    0,
                                    null,
                                    d.torrent ?: d.magnet ?: "",
                                    d.apiName,
                                    (mainId), // HAS SAME ID
                                    0,
                                    null,
                                    null,
                                    null,
                                    d.type,
                                    mainId
                                )
                            ), -1
                        )
                    }
                }
            }
            else -> Unit
        }
    }

    private var _apiName: MutableLiveData<String> = MutableLiveData()
    val apiName: LiveData<String> get() = _apiName

}