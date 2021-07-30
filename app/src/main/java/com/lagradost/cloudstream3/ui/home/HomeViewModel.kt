package com.lagradost.cloudstream3.ui.home

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllWatchStateIds
import com.lagradost.cloudstream3.utils.DataStoreHelper.getBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.getResultWatchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel : ViewModel() {
    var repo: APIRepository? = null

    private val _apiName = MutableLiveData<String>()
    val apiName: LiveData<String> = _apiName

    private val _page = MutableLiveData<Resource<HomePageResponse>>()
    val page: LiveData<Resource<HomePageResponse>> = _page

    private fun autoloadRepo(): APIRepository {
        return APIRepository(apis.first { it.hasMainPage })
    }

    private val availableWatchStatusTypes = MutableLiveData<Pair<WatchType, List<WatchType>>>()
    private val bookmarks = MutableLiveData<List<SearchResponse>>()

    fun loadStoredData(context: Context, preferredWatchStatus: WatchType?) = viewModelScope.launch {
        val watchStatusIds = withContext(Dispatchers.IO) {
            context.getAllWatchStateIds().map { id ->
                Pair(id, context.getResultWatchState(id))
            }
        }
        val length = WatchType.values().size
        val currentWatchTypes = HashSet<WatchType>()

        for (watch in watchStatusIds) {
            currentWatchTypes.add(watch.second)
            if (currentWatchTypes.size >= length) {
                break
            }
        }

        if (currentWatchTypes.size <= 0) {
            bookmarks.postValue(ArrayList())
            return@launch
        }

        val watchStatus = preferredWatchStatus ?: currentWatchTypes.first()
        availableWatchStatusTypes.postValue(
            Pair(
                watchStatus,
                currentWatchTypes.sortedBy { it.internalId }.toList()
            )
        )
        val list = withContext(Dispatchers.IO) {
            watchStatusIds.map { context.getBookmarkedData(it.first) }
        }
    }

    fun load(api: MainAPI?) = viewModelScope.launch {
        repo = if (api?.hasMainPage == true) {
            APIRepository(api)
        } else {
            autoloadRepo()
        }
        _apiName.postValue(repo?.name)
        _page.postValue(Resource.Loading())
        _page.postValue(repo?.getMainPage())
    }

    fun load(preferredApiName: String?) = viewModelScope.launch {
        val api = getApiFromNameNull(preferredApiName)
        load(api)
    }
}