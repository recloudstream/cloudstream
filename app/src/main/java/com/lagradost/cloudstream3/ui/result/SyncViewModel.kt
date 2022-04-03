package com.lagradost.cloudstream3.ui.result

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.SyncApis
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.aniListApi
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.malApi
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.utils.SyncUtil
import kotlinx.coroutines.launch


data class CurrentSynced(
    val name: String,
    val idPrefix: String,
    val isSynced: Boolean,
    val hasAccount: Boolean,
    val icon: Int,
)

class SyncViewModel : ViewModel() {
    companion object {
        const val TAG = "SYNCVM"
    }

    private val repos = SyncApis

    private val _metaResponse: MutableLiveData<Resource<SyncAPI.SyncResult>> =
        MutableLiveData()

    val metadata: LiveData<Resource<SyncAPI.SyncResult>> get() = _metaResponse

    private val _userDataResponse: MutableLiveData<Resource<SyncAPI.SyncStatus>?> =
        MutableLiveData(null)

    val userData: LiveData<Resource<SyncAPI.SyncStatus>?> get() = _userDataResponse

    // prefix, id
    private val syncIds = hashMapOf<String, String>()

    private val _currentSynced: MutableLiveData<List<CurrentSynced>> =
        MutableLiveData(getMissing())

    // pair of name idPrefix isSynced
    val synced: LiveData<List<CurrentSynced>> get() = _currentSynced

    private fun getMissing(): List<CurrentSynced> {
        return repos.map {
            CurrentSynced(
                it.name,
                it.idPrefix,
                syncIds.containsKey(it.idPrefix),
                it.hasAccount(),
                it.icon,
            )
        }
    }

    fun updateSynced() {
        Log.i(TAG, "updateSynced")
        _currentSynced.postValue(getMissing())
    }

    fun setMalId(id: String?) : Boolean {
        if(syncIds[malApi.idPrefix] == id ?: return false) return false
        syncIds[malApi.idPrefix] = id
        Log.i(TAG, "setMalId = $id")
        return true
    }

    fun setAniListId(id: String?) : Boolean {
        if(syncIds[aniListApi.idPrefix] == id ?: return false) return false
        syncIds[aniListApi.idPrefix] = id
        Log.i(TAG, "setAniListId = $id")
        return true
    }

    var hasAddedFromUrl: HashSet<String> = hashSetOf()

    fun addFromUrl(url: String?) = viewModelScope.launch {
        Log.i(TAG, "addFromUrl = $url")

        if (url == null || hasAddedFromUrl.contains(url)) return@launch
        SyncUtil.getIdsFromUrl(url)?.let { (malId, aniListId) ->
            hasAddedFromUrl.add(url)

            setMalId(malId)
            setAniListId(aniListId)
            updateSynced()
            if (malId != null || aniListId != null) {
                Log.i(TAG, "addFromUrl->updateMetaAndUser $malId $aniListId")
                updateMetaAndUser()
            }
        }
    }

    fun setEpisodesDelta(delta: Int) {
        Log.i(TAG, "setEpisodesDelta = $delta")

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
        Log.i(TAG, "setEpisodes = $episodes")

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
        Log.i(TAG, "setScore = $score")
        val user = userData.value
        if (user is Resource.Success) {
            _userDataResponse.postValue(Resource.Success(user.value.copy(score = score)))
        }
    }

    fun setStatus(which: Int) {
        Log.i(TAG, "setStatus = $which")
        if (which < -1 || which > 5) return // validate input
        val user = userData.value
        if (user is Resource.Success) {
            _userDataResponse.postValue(Resource.Success(user.value.copy(status = which)))
        }
    }

    fun publishUserData() = viewModelScope.launch {
        Log.i(TAG, "publishUserData")
        val user = userData.value
        if (user is Resource.Success) {
            for ((prefix, id) in syncIds) {
                repos.firstOrNull { it.idPrefix == prefix }?.score(id, user.value)
            }
        }
        updateUserData()
    }

    private fun updateUserData() = viewModelScope.launch {
        Log.i(TAG, "updateUserData")
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

    private fun updateMetadata() = viewModelScope.launch {
        Log.i(TAG, "updateMetadata")

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

    fun updateMetaAndUser() {
        Log.i(TAG, "updateMetaAndUser")
        updateMetadata()
        updateUserData()
    }
}