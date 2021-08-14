package com.lagradost.cloudstream3.ui.result

import android.content.Context
import androidx.lifecycle.*
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getApiFromName
import com.lagradost.cloudstream3.APIHolder.getId
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.getResultSeason
import com.lagradost.cloudstream3.utils.DataStoreHelper.getResultWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.DataStoreHelper.setBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.setResultSeason
import com.lagradost.cloudstream3.utils.DataStoreHelper.setResultWatchState
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val EPISODE_RANGE_SIZE = 50
const val EPISODE_RANGE_OVERLOAD = 60

class ResultViewModel : ViewModel() {
    var repo: APIRepository? = null

    private val _resultResponse: MutableLiveData<Resource<Any?>> = MutableLiveData()
    private val _episodes: MutableLiveData<List<ResultEpisode>> = MutableLiveData()
    private val _publicEpisodes: MutableLiveData<List<ResultEpisode>> = MutableLiveData()
    private val _publicEpisodesCount: MutableLiveData<Int> = MutableLiveData() // before the sorting
    private val _rangeOptions: MutableLiveData<List<String>> = MutableLiveData()
    val selectedRange: MutableLiveData<String> = MutableLiveData()
    private val selectedRangeInt: MutableLiveData<Int> = MutableLiveData()
    val rangeOptions: LiveData<List<String>> = _rangeOptions

    val resultResponse: LiveData<Resource<Any?>> get() = _resultResponse
    val episodes: LiveData<List<ResultEpisode>> get() = _episodes
    val publicEpisodes: LiveData<List<ResultEpisode>> get() = _publicEpisodes
    val publicEpisodesCount: LiveData<Int> get() = _publicEpisodesCount

    private val dubStatus: MutableLiveData<DubStatus> = MutableLiveData()

    private val page: MutableLiveData<LoadResponse> = MutableLiveData()
    val id: MutableLiveData<Int> = MutableLiveData()
    val selectedSeason: MutableLiveData<Int> = MutableLiveData(-2)
    val seasonSelections: MutableLiveData<List<Int?>> = MutableLiveData()

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
        val seasons = seasonTypes.toList().map { it.first }
        seasonSelections.postValue(seasons)
        if(seasons.isEmpty()) { // WHAT THE FUCK DID YOU DO????? HOW DID YOU DO THIS
            _publicEpisodes.postValue(ArrayList())
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
            val allRange ="1-${currentList.size}"
            _rangeOptions.postValue(listOf(allRange))
            selectedRangeInt.postValue(0)
            selectedRange.postValue(allRange)
        }

        _publicEpisodes.postValue(currentList)
    }

    fun changeSeason(context: Context, selection: Int?) {
        filterEpisodes(context, _episodes.value, selection, null)
    }

    fun changeRange(context: Context, range: Int?) {
        filterEpisodes(context, _episodes.value, null, range)
    }

    private fun updateEpisodes(context: Context, localId: Int?, list: List<ResultEpisode>, selection: Int?) {
        _episodes.postValue(list)
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

    fun load(context: Context, url: String, apiName: String) = viewModelScope.launch {
        _resultResponse.postValue(Resource.Loading(url))

        _apiName.postValue(apiName)
        val api = getApiFromName(apiName)
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

                when (d) {
                    is AnimeLoadResponse -> {
                        val isDub = d.dubEpisodes != null && d.dubEpisodes.size > 0
                        dubStatus.postValue(if (isDub) DubStatus.Dubbed else DubStatus.Subbed)

                        val dataList = (if (isDub) d.dubEpisodes else d.subEpisodes)

                        if (dataList != null) {
                            val episodes = ArrayList<ResultEpisode>()
                            for ((index, i) in dataList.withIndex()) {
                                episodes.add(
                                    context.buildResultEpisode(
                                        i.name,
                                        i.posterUrl,
                                        index + 1, //TODO MAKE ABLE TO NOT HAVE SOME EPISODE
                                        null, // TODO FIX SEASON
                                        i.url,
                                        apiName,
                                        (mainId + index + 1),
                                        index,
                                        i.rating,
                                        i.descript,
                                    )
                                )
                            }
                            updateEpisodes(context, mainId, episodes, -1)
                        }
                    }

                    is TvSeriesLoadResponse -> {
                        val episodes = ArrayList<ResultEpisode>()
                        for ((index, i) in d.episodes.withIndex()) {
                            episodes.add(
                                context.buildResultEpisode(
                                    i.name,
                                    i.posterUrl,
                                    i.episode ?: (index + 1),
                                    i.season,
                                    i.data,
                                    apiName,
                                    (mainId + index + 1).hashCode(),
                                    index,
                                    i.rating,
                                    i.descript
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
                                )
                            ), -1
                        )
                    }
                }
            }
            else -> {

            }
        }
    }

    private val _allEpisodes: MutableLiveData<HashMap<Int, ArrayList<ExtractorLink>>> =
        MutableLiveData(HashMap()) // LOOKUP BY ID
    private val _allEpisodesSubs: MutableLiveData<HashMap<Int, ArrayList<SubtitleFile>>> =
        MutableLiveData(HashMap()) // LOOKUP BY ID

    val allEpisodes: LiveData<HashMap<Int, ArrayList<ExtractorLink>>> get() = _allEpisodes
    val allEpisodesSubs: LiveData<HashMap<Int, ArrayList<SubtitleFile>>> get() = _allEpisodesSubs

    private var _apiName: MutableLiveData<String> = MutableLiveData()
    val apiName: LiveData<String> get() = _apiName

    data class EpisodeData(val links: ArrayList<ExtractorLink>, val subs: ArrayList<SubtitleFile>)

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
        val subs = ArrayList<SubtitleFile>()
        return safeApiCall {
            repo?.loadLinks(data, isCasting, { subtitleFile ->
                if (!subs.any { it.url == subtitleFile.url }) {
                    subs.add(subtitleFile)
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