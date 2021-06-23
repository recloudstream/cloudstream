package com.lagradost.cloudstream3.ui.result

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getApiFromName
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.utils.DataStoreHelper.getResultWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.DataStoreHelper.setResultWatchState
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.launch

class ResultViewModel : ViewModel() {
    private val _resultResponse: MutableLiveData<Resource<Any?>> = MutableLiveData()
    private val _episodes: MutableLiveData<List<ResultEpisode>> = MutableLiveData()
    val resultResponse: LiveData<Resource<Any?>> get() = _resultResponse
    val episodes: LiveData<List<ResultEpisode>> get() = _episodes
    private val dubStatus: MutableLiveData<DubStatus> = MutableLiveData()

    private val page: MutableLiveData<LoadResponse> = MutableLiveData()
    private val id: MutableLiveData<Int> = MutableLiveData()

    private val _watchStatus: MutableLiveData<WatchType> = MutableLiveData()
    val watchStatus: LiveData<WatchType> get() = _watchStatus

    fun updateWatchStatus(context: Context, status: WatchType) {
        val currentId = id.value ?: return
        _watchStatus.postValue(status)
        context.setResultWatchState(currentId, status.internalId)
    }

    private fun loadWatchStatus(context: Context, localId: Int? = null) {
        val currentId = localId ?: id.value ?: return
        val currentWatch = context.getResultWatchState(currentId)
        _watchStatus.postValue(currentWatch)
    }

    fun reloadEpisodes(context: Context) {
        val current = _episodes.value ?: return
        val copy = current.map {
            val posDur = context.getViewPos(it.id)
            it.copy(position = posDur?.position ?: 0, duration = posDur?.duration ?: 0)
        }
        _episodes.postValue(copy)
    }

    // THIS SHOULD AT LEAST CLEAN IT UP, SO APIS CAN SWITCH DOMAIN
    private fun getId(url: String, api: MainAPI): Int {
        return url.replace(api.mainUrl, "").hashCode()
    }

    fun load(context: Context, url: String, apiName: String) = viewModelScope.launch {
        _resultResponse.postValue(Resource.Loading(url))

        _apiName.postValue(apiName)
        val api = getApiFromName(apiName)
        val data = safeApiCall {
            api.load(url)
        }

        _resultResponse.postValue(data)

        when (data) {
            is Resource.Success -> {
                val d = data.value
                if (d is LoadResponse) {
                    page.postValue(d)
                    val mainId = getId(d.url, api)
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
                                    episodes.add(context.buildResultEpisode(
                                        i.name,
                                        null,
                                        index + 1, //TODO MAKE ABLE TO NOT HAVE SOME EPISODE
                                        null, // TODO FIX SEASON
                                        i.url,
                                        apiName,
                                        (mainId + index + 1),
                                        index,
                                    ))
                                }
                                _episodes.postValue(episodes)
                            }
                        }

                        is TvSeriesLoadResponse -> {
                            val episodes = ArrayList<ResultEpisode>()
                            for ((index, i) in d.episodes.withIndex()) {
                                episodes.add(context.buildResultEpisode(
                                    (i.name
                                        ?: (if (i.season != null && i.episode != null) "S${i.season}:E${i.episode}" else null)), // TODO ADD NAMES
                                    null,
                                    i.episode ?: (index + 1),
                                    i.season,
                                    i.data,
                                    apiName,
                                    (mainId + index + 1).hashCode(),
                                    index,
                                ))
                            }
                            _episodes.postValue(episodes)
                        }
                        is MovieLoadResponse -> {
                            _episodes.postValue(arrayListOf(context.buildResultEpisode(
                                null,
                                null,
                                0, null,
                                d.dataUrl,
                                d.apiName,
                                (mainId + 1),
                                0,
                            )))
                        }
                    }
                }
            }
            else -> {

            }
        }
    }

    private val _allEpisodes: MutableLiveData<HashMap<Int, ArrayList<ExtractorLink>>> =
        MutableLiveData(HashMap()) // LOOKUP BY ID

    val allEpisodes: LiveData<HashMap<Int, ArrayList<ExtractorLink>>> get() = _allEpisodes

    private var _apiName: MutableLiveData<String> = MutableLiveData()
    val apiName: LiveData<String> get() = _apiName


    fun loadEpisode(
        episode: ResultEpisode,
        isCasting: Boolean,
        callback: (Resource<ArrayList<ExtractorLink>>) -> Unit,
    ) {
        loadEpisode(episode.id, episode.data, isCasting, callback)
    }

    private fun loadEpisode(
        id: Int,
        data: String,
        isCasting: Boolean,
        callback: (Resource<ArrayList<ExtractorLink>>) -> Unit,
    ) =
        viewModelScope.launch {
            if (_allEpisodes.value?.contains(id) == true) {
                _allEpisodes.value?.remove(id)
            }
            val links = ArrayList<ExtractorLink>()
            val localData = safeApiCall {
                getApiFromName(_apiName.value).loadLinks(data, isCasting) {
                    for (i in links) {
                        if (i.url == it.url) return@loadLinks
                    }

                    links.add(it)
                    _allEpisodes.value?.set(id, links)
                    _allEpisodes.postValue(_allEpisodes.value)
                    // _allEpisodes.value?.get(episode.id)?.add(it)
                }
                links
            }
            callback.invoke(localData)
        }

    fun loadIndex(index: Int): ResultEpisode? {
        return episodes.value?.get(index)
    }
}