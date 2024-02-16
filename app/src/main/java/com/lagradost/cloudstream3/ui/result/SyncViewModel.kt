package com.lagradost.cloudstream3.ui.result

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.SyncApis
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.aniListApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.malApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.simklApi
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.SyncUtil
import java.util.*


data class CurrentSynced(
    val name: String,
    val idPrefix: String,
    val isSynced: Boolean,
    val hasAccount: Boolean,
    val icon: Int?,
)

class SyncViewModel : ViewModel() {
    companion object {
        const val TAG = "SYNCVM"
    }

    private val repos = SyncApis

    private val _metaResponse: MutableLiveData<Resource<SyncAPI.SyncResult>?> =
        MutableLiveData(null)

    val metadata: LiveData<Resource<SyncAPI.SyncResult>?> = _metaResponse

    private val _userDataResponse: MutableLiveData<Resource<SyncAPI.AbstractSyncStatus>?> =
        MutableLiveData(null)

    val userData: LiveData<Resource<SyncAPI.AbstractSyncStatus>?> = _userDataResponse

    // prefix, id
    private val syncs = mutableMapOf<String, String>()
    //private val _syncIds: MutableLiveData<MutableMap<String, String>> =
    //    MutableLiveData(mutableMapOf())
    //val syncIds: LiveData<MutableMap<String, String>> get() = _syncIds

    fun getSyncs(): Map<String, String> {
        return syncs
    }

    private val _currentSynced: MutableLiveData<List<CurrentSynced>> =
        MutableLiveData(getMissing())

    // pair of name idPrefix isSynced
    val synced: LiveData<List<CurrentSynced>> = _currentSynced

    private fun getMissing(): List<CurrentSynced> {
        return repos.map {
            CurrentSynced(
                it.name,
                it.idPrefix,
                syncs.containsKey(it.idPrefix),
                it.hasAccount(),
                it.icon,
            )
        }
    }

    fun updateSynced() {
        Log.i(TAG, "updateSynced")
        _currentSynced.postValue(getMissing())
    }

    private fun addSync(idPrefix: String, id: String): Boolean {
        if (syncs[idPrefix] == id) return false
        Log.i(TAG, "addSync $idPrefix = $id")

        syncs[idPrefix] = id
        //_syncIds.postValue(syncs)
        return true
    }

    fun addSyncs(map: Map<String, String>?): Boolean {
        var isValid = false

        map?.forEach { (prefix, id) ->
            isValid = addSync(prefix, id) || isValid
        }
        return isValid
    }

    private fun setMalId(id: String?): Boolean {
        return addSync(malApi.idPrefix, id ?: return false)
    }

    private fun setAniListId(id: String?): Boolean {
        return addSync(aniListApi.idPrefix, id ?: return false)
    }

    var hasAddedFromUrl: HashSet<String> = hashSetOf()

    fun addFromUrl(url: String?) = ioSafe {
        Log.i(TAG, "addFromUrl = $url")

        if (url == null || hasAddedFromUrl.contains(url)) return@ioSafe
        if (!url.startsWith("http")) return@ioSafe

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
            user.value.watchedEpisodes = episodes
            _userDataResponse.postValue(Resource.Success(user.value))
        }
    }

    fun setScore(score: Int) {
        Log.i(TAG, "setScore = $score")
        val user = userData.value
        if (user is Resource.Success) {
            user.value.score = score
            _userDataResponse.postValue(Resource.Success(user.value))
        }
    }

    fun setStatus(which: Int) {
        Log.i(TAG, "setStatus = $which")
        if (which < -1 || which > 5) return // validate input
        val user = userData.value
        if (user is Resource.Success) {
            user.value.status = SyncWatchType.fromInternalId(which)
            _userDataResponse.postValue(Resource.Success(user.value))
        }
    }

    fun publishUserData() = ioSafe {
        Log.i(TAG, "publishUserData")
        val user = userData.value
        if (user is Resource.Success) {
            syncs.forEach { (prefix, id) ->
                repos.firstOrNull { it.idPrefix == prefix }?.score(id, user.value)
            }
        }
        updateUserData()
    }

    fun modifyMaxEpisode(episodeNum: Int) {
        Log.i(TAG, "modifyMaxEpisode = $episodeNum")
        modifyData { status ->
            status.watchedEpisodes = maxOf(
                episodeNum,
                status.watchedEpisodes ?: return@modifyData null
            )
            status
        }
    }

    /// modifies the current sync data, return null if you don't want to change it
    private fun modifyData(update: ((SyncAPI.AbstractSyncStatus) -> (SyncAPI.AbstractSyncStatus?))) =
        ioSafe {
            syncs.amap { (prefix, id) ->
                repos.firstOrNull { it.idPrefix == prefix }?.let { repo ->
                    if (repo.hasAccount()) {
                        val result = repo.getStatus(id)
                        if (result is Resource.Success) {
                            update(result.value)?.let { newData ->
                                Log.i(TAG, "modifyData ${repo.name} => $newData")
                                repo.score(id, newData)
                            }
                        } else if (result is Resource.Failure) {
                            Log.e(TAG, "modifyData getStatus error ${result.errorString}")
                        }
                    }
                }
            }
        }

    fun updateUserData() = ioSafe {
        Log.i(TAG, "updateUserData")
        _userDataResponse.postValue(Resource.Loading())
        var lastError: Resource<SyncAPI.SyncStatus> = Resource.Failure(false, null, null, "No data")
        syncs.forEach { (prefix, id) ->
            repos.firstOrNull { it.idPrefix == prefix }?.let { repo ->
                if (repo.hasAccount()) {
                    val result = repo.getStatus(id)
                    if (result is Resource.Success) {
                        _userDataResponse.postValue(result)
                        return@ioSafe
                    } else if (result is Resource.Failure) {
                        Log.e(TAG, "updateUserData error ${result.errorString}")
                        lastError = result
                    }
                }
            }
        }
        _userDataResponse.postValue(lastError)
    }

    private fun updateMetadata() = ioSafe {
        Log.i(TAG, "updateMetadata")

        _metaResponse.postValue(Resource.Loading())
        var lastError: Resource<SyncAPI.SyncResult> = Resource.Failure(false, null, null, "No data")
        val current = ArrayList(syncs.toList())

        // shitty way to sort anilist first, as it has trailers while mal does not
        if (syncs.containsKey(aniListApi.idPrefix)) {
            try { // swap can throw error
                Collections.swap(
                    current,
                    current.indexOfFirst { it.first == aniListApi.idPrefix },
                    0
                )
            } catch (t: Throwable) {
                logError(t)
            }
        }

        current.forEach { (prefix, id) ->
            repos.firstOrNull { it.idPrefix == prefix }?.let { repo ->
                if (!repo.requiresLogin || repo.hasAccount()) {
                    Log.i(TAG, "updateMetadata loading ${repo.idPrefix}")
                    val result = repo.getResult(id)
                    if (result is Resource.Success) {
                        _metaResponse.postValue(result)
                        return@ioSafe
                    } else if (result is Resource.Failure) {
                        Log.e(
                            TAG,
                            "updateMetadata error $id at ${repo.idPrefix} ${result.errorString}"
                        )
                        lastError = result
                    }
                }
            }
        }
        _metaResponse.postValue(lastError)
        setEpisodesDelta(0)
    }

    fun syncName(syncName: String) : String? {
        // fix because of bad old data :pensive:
        val realName = when(syncName) {
            "MAL" -> malApi.idPrefix
            "Simkl" -> simklApi.idPrefix
            "AniList" -> aniListApi.idPrefix
            else -> syncName
        }
        return repos.firstOrNull { it.idPrefix == realName }?.idPrefix
    }

    fun setSync(syncName : String, syncId : String) {
        syncs.clear()
        syncs[syncName] = syncId
    }

    fun clear() {
        syncs.clear()
        _metaResponse.postValue(null)
        _currentSynced.postValue(getMissing())
        _userDataResponse.postValue(null)
    }

    fun updateMetaAndUser() {
        _userDataResponse.postValue(Resource.Loading())
        _metaResponse.postValue(Resource.Loading())

        Log.i(TAG, "updateMetaAndUser")
        updateMetadata()
        updateUserData()
    }
}