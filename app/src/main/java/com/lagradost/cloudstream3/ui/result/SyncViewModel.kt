package com.lagradost.cloudstream3.ui.result

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.SyncApis
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.aniListApi
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.malApi
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import kotlinx.coroutines.launch


class SyncViewModel : ViewModel() {
    private val repos = SyncApis

    private val _metaResponse: MutableLiveData<Resource<SyncAPI.SyncResult>> =
        MutableLiveData()

    val metadata: LiveData<Resource<SyncAPI.SyncResult>> get() = _metaResponse

    private val _userDataResponse: MutableLiveData<Resource<SyncAPI.SyncStatus>?> =
        MutableLiveData(null)

    val userData: LiveData<Resource<SyncAPI.SyncStatus>?> get() = _userDataResponse

    // prefix, id
    private val syncIds = hashMapOf<String, String>()

    fun setMalId(id: String) {
        syncIds[malApi.idPrefix] = id
    }

    fun setAniListId(id: String) {
        syncIds[aniListApi.idPrefix] = id
    }

    fun setEpisodesDelta(delta: Int) {
        val user = userData.value
        if (user is Resource.Success) {
            user.value.watchedEpisodes?.plus(
                delta
            )?.let { episode ->
                setEpisodes(episode)
            }
        }
    }

    fun setEpisodes(episodes: Int) {
        if (episodes < 0) return
        val meta = metadata.value
        if (meta is Resource.Success) {
            meta.value.totalEpisodes?.let { max ->
                if (episodes > max) {
                    setEpisodes(max)
                    return
                }
            }
        }

        val user = userData.value
        if (user is Resource.Success) {
            _userDataResponse.postValue(Resource.Success(user.value.copy(watchedEpisodes = episodes)))
        }
    }

    fun setScore(score: Int) {
        val user = userData.value
        if (user is Resource.Success) {
            _userDataResponse.postValue(Resource.Success(user.value.copy(score = score)))
        }
    }

    fun setStatus(which: Int) {
        if (which < -1 || which > 5) return // validate input
        val user = userData.value
        if (user is Resource.Success) {
            _userDataResponse.postValue(Resource.Success(user.value.copy(status = which)))
        }
    }

    fun publishUserData() = viewModelScope.launch {
        val user = userData.value
        if (user is Resource.Success) {
            for ((prefix, id) in syncIds) {
                repos.firstOrNull { it.idPrefix == prefix }?.score(id, user.value)
            }
        }
        updateUserData()
    }

    fun updateUserData() = viewModelScope.launch {
        _userDataResponse.postValue(Resource.Loading())
        var lastError: Resource<SyncAPI.SyncStatus> = Resource.Failure(false, null, null, "No data")
        for ((prefix, id) in syncIds) {
            repos.firstOrNull { it.idPrefix == prefix }?.let {
                val result = it.getStatus(id)
                if (result is Resource.Success) {
                    _userDataResponse.postValue(result)
                    return@launch
                } else if (result is Resource.Failure) {
                    lastError = result
                }
            }
        }
        _userDataResponse.postValue(lastError)
    }

    fun updateMetadata() = viewModelScope.launch {
        _metaResponse.postValue(Resource.Loading())
        var lastError: Resource<SyncAPI.SyncResult> = Resource.Failure(false, null, null, "No data")
        for ((prefix, id) in syncIds) {
            repos.firstOrNull { it.idPrefix == prefix }?.let {
                val result = it.getResult(id)
                if (result is Resource.Success) {
                    _metaResponse.postValue(result)
                    return@launch
                } else if (result is Resource.Failure) {
                    lastError = result
                }
            }
        }
        _metaResponse.postValue(lastError)
        setEpisodesDelta(0)
    }
}