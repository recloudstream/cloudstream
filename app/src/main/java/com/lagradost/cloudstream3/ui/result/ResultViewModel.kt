package com.lagradost.cloudstream3.ui.result

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getApiFromName
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.launch

class ResultViewModel : ViewModel() {
    private val _resultResponse: MutableLiveData<Resource<Any?>> = MutableLiveData()
    private val _episodes: MutableLiveData<ArrayList<ResultEpisode>> = MutableLiveData()
    val resultResponse: LiveData<Resource<Any?>> get() = _resultResponse
    val episodes: LiveData<ArrayList<ResultEpisode>> get() = _episodes
    private val dubStatus: MutableLiveData<DubStatus> = MutableLiveData()

    fun load(url: String, apiName: String) = viewModelScope.launch {
        _apiName.postValue(apiName)
        val data = safeApiCall {
            getApiFromName(apiName).load(url)
        }
        _resultResponse.postValue(data)

        when (data) {
            is Resource.Success -> {
                val d = data.value
                if (d is LoadResponse) {
                    when (d) {
                        is AnimeLoadResponse -> {
                            val isDub = d.dubEpisodes != null && d.dubEpisodes.size > 0
                            dubStatus.postValue(if (isDub) DubStatus.Dubbed else DubStatus.Subbed)

                            val dataList = (if (isDub) d.dubEpisodes else d.subEpisodes)

                            if (dataList != null) {
                                val episodes = ArrayList<ResultEpisode>()
                                for ((index, i) in dataList.withIndex()) {
                                    episodes.add(ResultEpisode(
                                        null, // TODO ADD NAMES
                                        index + 1, //TODO MAKE ABLE TO NOT HAVE SOME EPISODE
                                        null, // TODO FIX SEASON
                                        i,
                                        apiName,
                                        (d.url + index).hashCode(),
                                        index,
                                        0f,//(index * 0.1f),//TODO TEST; REMOVE
                                    ))
                                }
                                _episodes.postValue(episodes)
                            }

                        }
                        is TvSeriesLoadResponse -> {
                            val episodes = ArrayList<ResultEpisode>()
                            for ((index, i) in d.episodes.withIndex()) {
                                episodes.add(ResultEpisode(
                                    null, // TODO ADD NAMES
                                    index + 1, //TODO MAKE ABLE TO NOT HAVE SOME EPISODE
                                    null, // TODO FIX SEASON
                                    i,
                                    apiName,
                                    (d.url + index).hashCode(),
                                    index,
                                    0f,//(index * 0.1f),//TODO TEST; REMOVE
                                ))
                            }
                            _episodes.postValue(episodes)
                        }
                        is MovieLoadResponse -> {
                            _episodes.postValue(arrayListOf(ResultEpisode(null,
                                0, null,
                                d.movieUrl,
                                d.apiName,
                                (d.url).hashCode(),
                                0,
                                0f)))
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

    fun loadEpisode(episode: ResultEpisode, isCasting : Boolean, callback: (Resource<Boolean>) -> Unit) {
        loadEpisode(episode.id, episode.data, isCasting, callback)
    }

    fun loadEpisode(id: Int, data: Any, isCasting : Boolean, callback: (Resource<Boolean>) -> Unit) =
        viewModelScope.launch {
            if (_allEpisodes.value?.contains(id) == true) {
                _allEpisodes.value?.remove(id)
            }
            val links = ArrayList<ExtractorLink>()
            val data = safeApiCall {
                getApiFromName(_apiName.value).loadLinks(data, isCasting) { //TODO IMPLEMENT CASTING
                    for (i in links) {
                        if (i.url == it.url) return@loadLinks
                    }

                    links.add(it)
                    _allEpisodes.value?.set(id, links)

                    // _allEpisodes.value?.get(episode.id)?.add(it)
                }
            }
            callback.invoke(data)
        }

    fun loadIndex(index: Int): ResultEpisode? {
        return episodes.value?.get(index)
    }

}