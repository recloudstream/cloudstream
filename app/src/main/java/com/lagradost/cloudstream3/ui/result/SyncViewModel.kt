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

    private val _statusResponse: MutableLiveData<Resource<SyncAPI.SyncStatus>?> =
        MutableLiveData(null)

    val status: LiveData<Resource<SyncAPI.SyncStatus>?> get() = _statusResponse

    // prefix, id
    private val syncIds = hashMapOf<String, String>()

    fun setMalId(id: String) {
        syncIds[malApi.idPrefix] = id
    }

    fun setAniListId(id: String) {
        syncIds[aniListApi.idPrefix] = id
    }

    fun setScore(status: SyncAPI.SyncStatus) = viewModelScope.launch {
        for ((prefix, id) in syncIds) {
            repos.firstOrNull { it.idPrefix == prefix }?.score(id, status)
        }

        updateStatus()
    }

    fun updateStatus() = viewModelScope.launch {
        _statusResponse.postValue(Resource.Loading())
        var lastError: Resource<SyncAPI.SyncStatus> = Resource.Failure(false, null, null, "No data")
        for ((prefix, id) in syncIds) {
            repos.firstOrNull { it.idPrefix == prefix }?.let {
                val result = it.getStatus(id)
                if (result is Resource.Success) {
                    _statusResponse.postValue(result)
                    return@launch
                } else if (result is Resource.Failure) {
                    lastError = result
                }
            }
        }
        _statusResponse.postValue(lastError)
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
    }
}