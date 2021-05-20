package com.lagradost.cloudstream3.ui.result

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.getApiFromName
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.launch

class ResultViewModel : ViewModel() {
    private val _resultResponse: MutableLiveData<Resource<Any?>> = MutableLiveData()
    val resultResponse: LiveData<Resource<Any?>> get() = _resultResponse

    fun load(url: String, apiName: String) = viewModelScope.launch {
        val data = safeApiCall {
            getApiFromName(apiName).load(url)
        }

        _resultResponse.postValue(data)
    }

    private val _allEpisodes: MutableLiveData<HashMap<Int, ArrayList<ExtractorLink>>> = MutableLiveData(HashMap())
    val allEpisodes: LiveData<HashMap<Int, ArrayList<ExtractorLink>>> get() = _allEpisodes

    fun loadEpisode(episode: ResultEpisode, callback: (Resource<Boolean>) -> Unit) = viewModelScope.launch {
        if (_allEpisodes.value?.contains(episode.id) == true) {
            _allEpisodes.value?.remove(episode.id)
        }
        val links = ArrayList<ExtractorLink>()
        val data = safeApiCall {
            getApiFromName(episode.apiName).loadLinks(episode.data, true) { //TODO IMPLEMENT CASTING
                links.add(it)
                _allEpisodes.value?.set(episode.id, links)
                // _allEpisodes.value?.get(episode.id)?.add(it)
            }
        }
        callback.invoke(data)
    }
}