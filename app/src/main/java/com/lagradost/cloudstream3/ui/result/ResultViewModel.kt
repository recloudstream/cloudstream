package com.lagradost.cloudstream3.ui.result

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.APIHolder.getId
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper.getBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.getResultSeason
import com.lagradost.cloudstream3.utils.DataStoreHelper.getResultWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.DataStoreHelper.removeLastWatched
import com.lagradost.cloudstream3.utils.DataStoreHelper.setBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.setLastWatched
import com.lagradost.cloudstream3.utils.DataStoreHelper.setResultSeason
import com.lagradost.cloudstream3.utils.DataStoreHelper.setResultWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.setViewPos
import com.lagradost.cloudstream3.utils.FillerEpisodeCheck.getFillerEpisodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.set

const val EPISODE_RANGE_SIZE = 50
const val EPISODE_RANGE_OVERLOAD = 60

class ResultViewModel : ViewModel() {
    fun clear() {
        repo = null
        _resultResponse.value = null
        _episodes.value = null
        episodeById.value = null
        _publicEpisodes.value = null
        _publicEpisodesCount.value = null
        _rangeOptions.value = null
        selectedRange.value = null
        selectedRangeInt.value = null
        _dubStatus.value = null
        id.value = null
        selectedSeason.value = -2
        _dubSubEpisodes.value = null
    }

    private var repo: APIRepository? = null

    private val _resultResponse: MutableLiveData<Resource<Any?>> = MutableLiveData()
    private val _episodes: MutableLiveData<List<ResultEpisode>> = MutableLiveData()
    private val episodeById: MutableLiveData<HashMap<Int, Int>> = MutableLiveData() // lookup by ID to get Index

    private val _publicEpisodes: MutableLiveData<Resource<List<ResultEpisode>>> = MutableLiveData()
    private val _publicEpisodesCount: MutableLiveData<Int> = MutableLiveData() // before the sorting
    private val _rangeOptions: MutableLiveData<List<String>> = MutableLiveData()
    val selectedRange: MutableLiveData<String> = MutableLiveData()
    private val selectedRangeInt: MutableLiveData<Int> = MutableLiveData()
    val rangeOptions: LiveData<List<String>> = _rangeOptions

    val resultResponse: LiveData<Resource<Any?>> get() = _resultResponse
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
    private val _dubSubEpisodes: MutableLiveData<Map<DubStatus, List<ResultEpisode>>?> = MutableLiveData()

    private val _watchStatus: MutableLiveData<WatchType> = MutableLiveData()
    val watchStatus: LiveData<WatchType> get() = _watchStatus

    fun updateWatchStatus(context: Context, status: WatchType) = viewModelScope.launch {
        val currentId = id.value ?: return@launch
        _watchStatus.postValue(status)
        val resultPage = page.value

        withContext(Dispatchers.IO) {
            context.setResultWatchState(currentId, status.internalId)
            if (resultPage != null) {
                val current = context.getBookmarkedData(currentId)
                val currentTime = System.currentTimeMillis()
                context.setBookmarkedData(
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

    private fun loadWatchStatus(context: Context, localId: Int? = null) {
        val currentId = localId ?: id.value ?: return
        val currentWatch = context.getResultWatchState(currentId)
        _watchStatus.postValue(currentWatch)
    }

    private fun filterEpisodes(context: Context, list: List<ResultEpisode>?, selection: Int?, range: Int?) {
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

        if (internalId != null) context.setResultSeason(internalId, realSelection)

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

    fun changeSeason(context: Context, selection: Int?) {
        filterEpisodes(context, _episodes.value, selection, null)
    }

    fun changeRange(context: Context, range: Int?) {
        filterEpisodes(context, _episodes.value, null, range)
    }

    fun changeDubStatus(context: Context, status: DubStatus?) {
        dubSubEpisodes.value?.get(status)?.let { episodes ->
            _dubStatus.postValue(status)
            updateEpisodes(context, null, episodes, null)
        }
    }

    private fun updateEpisodes(context: Context, localId: Int?, list: List<ResultEpisode>, selection: Int?) {
        _episodes.postValue(list)
        val set = HashMap<Int, Int>()

        list.withIndex().forEach { set[it.value.id] = it.index }
        episodeById.postValue(set)

        filterEpisodes(
            context,
            list,
            if (selection == -1) context.getResultSeason(localId ?: id.value ?: return) else selection, null
        )
    }

    fun reloadEpisodes(context: Context) {
        val current = _episodes.value ?: return
        val copy = current.map {
            val posDur = context.getViewPos(it.id)
            it.copy(position = posDur?.position ?: 0, duration = posDur?.duration ?: 0)
        }
        updateEpisodes(context, null, copy, selectedSeason.value)
    }

    fun setViewPos(context: Context?, episodeId: Int?, pos: Long, dur: Long) {
        try {
            if (context == null || episodeId == null) return
            context.setViewPos(episodeId, pos, dur)
            var index = episodeById.value?.get(episodeId) ?: return

            var startPos = pos
            var startDur = dur
            val episodeList = (episodes.value ?: return)
            var episode = episodeList[index]
            val parentId = id.value ?: return
            while (true) {
                if (startDur > 0L && (startPos * 100 / startDur) > 95) {
                    index++
                    if (episodeList.size <= index) { // last episode
                        context.removeLastWatched(parentId)
                        return
                    }
                    episode = episodeList[index]

                    startPos = episode.position
                    startDur = episode.duration

                    continue
                } else {
                    context.setLastWatched(parentId, episode.id, episode.episode, episode.season)
                    return
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun filterName(name: String?): String? {
        if (name == null) return null
        Regex("[eE]pisode [0-9]*(.*)").find(name)?.groupValues?.get(1)?.let {
            if (it.isEmpty())
                return null
        }
        return name
    }

    fun load(context: Context, url: String, apiName: String, showFillers: Boolean) = viewModelScope.launch {
        _resultResponse.postValue(Resource.Loading(url))
        _publicEpisodes.postValue(Resource.Loading())

        _apiName.postValue(apiName)
        val api = getApiFromNameNull(apiName)
        if (api == null) {
            _resultResponse.postValue(Resource.Failure(false, null, null, "This provider does not exist"))
            return@launch
        }
        repo = APIRepository(api)

        val data = repo?.load(url)

        _resultResponse.postValue(data)

        when (data) {
            is Resource.Success -> {
                val d = data.value
                page.postValue(d)
                val mainId = d.getId()
                id.postValue(mainId)
                loadWatchStatus(context, mainId)

                context.setKey(
                    DOWNLOAD_HEADER_CACHE,
                    mainId.toString(),
                    VideoDownloadHelper.DownloadHeaderCached(
                        apiName,
                        url,
                        d.type,
                        d.name,
                        d.posterUrl,
                        mainId,
                        System.currentTimeMillis(),
                    )
                )

                when (d) {
                    is AnimeLoadResponse -> {
                        //TODO context.getKey<>() isdub

                        val isDub =
                            d.episodes.containsKey(DubStatus.Dubbed) && !d.episodes[DubStatus.Dubbed].isNullOrEmpty()
                        val dubStatus = if (isDub) DubStatus.Dubbed else DubStatus.Subbed
                        _dubStatus.postValue(dubStatus)

                        _dubSubSelections.postValue(d.episodes.keys)
                        val fillerEpisodes = if (showFillers) safeApiCall { getFillerEpisodes(d.name) } else null

                        var idIndex = 0
                        val res = d.episodes.map { ep ->
                            val episodes = ArrayList<ResultEpisode>()
                            for ((index, i) in ep.value.withIndex()) {

                                val episode = i.episode ?: (index + 1)
                                episodes.add(
                                    context.buildResultEpisode(
                                        filterName(i.name),
                                        i.posterUrl,
                                        episode,
                                        null, // TODO FIX SEASON
                                        i.url,
                                        apiName,
                                        mainId + index + 1 + idIndex * 100000,
                                        index,
                                        i.rating,
                                        i.description,
                                        if (fillerEpisodes is Resource.Success) fillerEpisodes.value?.let {
                                            it.contains(episode) && it[episode] == true
                                        } ?: false else false,
                                    )
                                )
                            }
                            idIndex++

                            Pair(ep.key, episodes)
                        }.toMap()

                        _dubSubEpisodes.postValue(res)
                        res[dubStatus]?.let { episodes ->
                            updateEpisodes(context, mainId, episodes, -1)
                        }
                    }

                    is TvSeriesLoadResponse -> {
                        val episodes = ArrayList<ResultEpisode>()
                        for ((index, i) in d.episodes.withIndex()) {
                            episodes.add(
                                context.buildResultEpisode(
                                    filterName(i.name),
                                    i.posterUrl,
                                    i.episode ?: (index + 1),
                                    i.season,
                                    i.data,
                                    apiName,
                                    (mainId + index + 1).hashCode(),
                                    index,
                                    i.rating,
                                    i.descript,
                                    null,
                                )
                            )
                        }
                        updateEpisodes(context, mainId, episodes, -1)
                    }
                    is MovieLoadResponse -> {
                        updateEpisodes(
                            context, mainId, arrayListOf(
                                context.buildResultEpisode(
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
                                )
                            ), -1
                        )
                    }
                    is TorrentLoadResponse -> {
                        updateEpisodes(
                            context, mainId, arrayListOf(
                                context.buildResultEpisode(
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
                                )
                            ), -1
                        )
                    }
                }
            }
            else -> {
                // nothing
            }
        }
    }

    private val _allEpisodes: MutableLiveData<HashMap<Int, List<ExtractorLink>>> =
        MutableLiveData(HashMap()) // LOOKUP BY ID
    private val _allEpisodesSubs: MutableLiveData<HashMap<Int, HashMap<String, SubtitleFile>>> =
        MutableLiveData(HashMap()) // LOOKUP BY ID

    val allEpisodes: LiveData<HashMap<Int, List<ExtractorLink>>> get() = _allEpisodes
    val allEpisodesSubs: LiveData<HashMap<Int, HashMap<String, SubtitleFile>>> get() = _allEpisodesSubs

    private var _apiName: MutableLiveData<String> = MutableLiveData()
    val apiName: LiveData<String> get() = _apiName

    data class EpisodeData(val links: List<ExtractorLink>, val subs: HashMap<String, SubtitleFile>)

    fun loadEpisode(
        episode: ResultEpisode,
        isCasting: Boolean,
        callback: (Resource<EpisodeData>) -> Unit,
    ) {
        loadEpisode(episode.id, episode.data, isCasting, callback)
    }

    suspend fun loadEpisode(
        episode: ResultEpisode,
        isCasting: Boolean,
    ): Resource<EpisodeData> {
        return loadEpisode(episode.id, episode.data, isCasting)
    }

    private suspend fun loadEpisode(
        id: Int,
        data: String,
        isCasting: Boolean,
    ): Resource<EpisodeData> {
        if (_allEpisodes.value?.contains(id) == true) {
            _allEpisodes.value?.remove(id)
        }
        val links = ArrayList<ExtractorLink>()
        val subs = HashMap<String, SubtitleFile>()
        return safeApiCall {
            repo?.loadLinks(data, isCasting, { subtitleFile ->
                if (!subs.values.any { it.url == subtitleFile.url }) {
                    val langTrimmed = subtitleFile.lang.trimEnd()

                    val langId = if (langTrimmed.length == 2) {
                        SubtitleHelper.fromTwoLettersToLanguage(langTrimmed) ?: langTrimmed
                    } else {
                        langTrimmed
                    }

                    var title: String
                    var count = 0
                    while (true) {
                        title = "$langId${if (count == 0) "" else " ${count + 1}"}"
                        count++
                        if (!subs.containsKey(title)) {
                            break
                        }
                    }

                    val file =
                        subtitleFile.copy(
                            lang = title
                        )

                    subs[title] = file

                    _allEpisodesSubs.value?.set(id, subs)
                    _allEpisodesSubs.postValue(_allEpisodesSubs.value)
                }
            }) { link ->
                if (!links.any { it.url == link.url }) {
                    links.add(link)
                    _allEpisodes.value?.set(id, links)
                    _allEpisodes.postValue(_allEpisodes.value)
                }
            }
            EpisodeData(links, subs)
        }
    }

    private fun loadEpisode(
        id: Int,
        data: String,
        isCasting: Boolean,
        callback: (Resource<EpisodeData>) -> Unit,
    ) =
        viewModelScope.launch {
            val localData = loadEpisode(id, data, isCasting)
            callback.invoke(localData)
        }
}