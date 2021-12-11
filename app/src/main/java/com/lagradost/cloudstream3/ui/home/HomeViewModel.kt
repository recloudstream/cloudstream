package com.lagradost.cloudstream3.ui.home

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.AcraApplication.Companion.context
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.APIRepository.Companion.noneApi
import com.lagradost.cloudstream3.ui.APIRepository.Companion.randomApi
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllResumeStateIds
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllWatchStateIds
import com.lagradost.cloudstream3.utils.DataStoreHelper.getBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.getLastWatched
import com.lagradost.cloudstream3.utils.DataStoreHelper.getResultWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class HomeViewModel : ViewModel() {
    private var repo: APIRepository? = null

    private val _apiName = MutableLiveData<String>()
    val apiName: LiveData<String> = _apiName

    private val _page = MutableLiveData<Resource<HomePageResponse?>>()
    val page: LiveData<Resource<HomePageResponse?>> = _page

    private val _randomItems = MutableLiveData<List<SearchResponse>?>(null)
    val randomItems: LiveData<List<SearchResponse>?> = _randomItems

    private fun autoloadRepo(): APIRepository {
        return APIRepository(apis.first { it.hasMainPage })
    }

    private val _availableWatchStatusTypes = MutableLiveData<Pair<EnumSet<WatchType>, EnumSet<WatchType>>>()
    val availableWatchStatusTypes: LiveData<Pair<EnumSet<WatchType>, EnumSet<WatchType>>> = _availableWatchStatusTypes
    private val _bookmarks = MutableLiveData<Pair<Boolean, List<SearchResponse>>>()
    val bookmarks: LiveData<Pair<Boolean, List<SearchResponse>>> = _bookmarks

    private val _resumeWatching = MutableLiveData<List<SearchResponse>>()
    val resumeWatching: LiveData<List<SearchResponse>> = _resumeWatching

    fun loadResumeWatching(context: Context) = viewModelScope.launch {
        val resumeWatching = withContext(Dispatchers.IO) {
            context.getAllResumeStateIds().mapNotNull { id ->
                context.getLastWatched(id)
            }.sortedBy { -it.updateTime }
        }

        // val resumeWatchingResult = ArrayList<DataStoreHelper.ResumeWatchingResult>()

        val resumeWatchingResult = withContext(Dispatchers.IO) {
            resumeWatching.map { resume ->
                val data = context.getKey<VideoDownloadHelper.DownloadHeaderCached>(
                    DOWNLOAD_HEADER_CACHE,
                    resume.parentId.toString()
                ) ?: return@map null
                val watchPos = context.getViewPos(resume.episodeId)
                DataStoreHelper.ResumeWatchingResult(
                    data.name,
                    data.url,
                    data.apiName,
                    data.type,
                    data.poster,
                    watchPos,
                    resume.episodeId,
                    resume.parentId,
                    resume.episode,
                    resume.season,
                    resume.isFromDownload
                )
            }.filterNotNull()
        }

        _resumeWatching.postValue(resumeWatchingResult)
    }

    fun loadStoredData(context: Context, preferredWatchStatus: EnumSet<WatchType>?) = viewModelScope.launch {
        val watchStatusIds = withContext(Dispatchers.IO) {
            context.getAllWatchStateIds().map { id ->
                Pair(id, context.getResultWatchState(id))
            }
        }.distinctBy { it.first }
        val length = WatchType.values().size
        val currentWatchTypes = EnumSet.noneOf(WatchType::class.java)

        for (watch in watchStatusIds) {
            currentWatchTypes.add(watch.second)
            if (currentWatchTypes.size >= length) {
                break
            }
        }

        currentWatchTypes.remove(WatchType.NONE)

        if (currentWatchTypes.size <= 0) {
            _bookmarks.postValue(Pair(false, ArrayList()))
            return@launch
        }

        val watchPrefNotNull = preferredWatchStatus ?: EnumSet.of(currentWatchTypes.first())
        //if (currentWatchTypes.any { watchPrefNotNull.contains(it) }) watchPrefNotNull else listOf(currentWatchTypes.first())

        _availableWatchStatusTypes.postValue(
            Pair(
                watchPrefNotNull,
                currentWatchTypes,
            )
        )

        val list = withContext(Dispatchers.IO) {
            watchStatusIds.filter { watchPrefNotNull.contains(it.second) }
                .mapNotNull { context.getBookmarkedData(it.first) }
                .sortedBy { -it.latestUpdatedTime }
        }
        _bookmarks.postValue(Pair(true,list))
    }

    private var onGoingLoad: Job? = null
    private fun loadAndCancel(api: MainAPI?) {
        onGoingLoad?.cancel()
        onGoingLoad = load(api)
    }

    private fun load(api: MainAPI?) = viewModelScope.launch {
        repo = if (api != null) {
            APIRepository(api)
        } else {
            autoloadRepo()
        }

        _apiName.postValue(repo?.name)
        _randomItems.postValue(listOf())

        if (repo?.hasMainPage == true) {
            _page.postValue(Resource.Loading())

            val data = repo?.getMainPage()
            when (data) {
                is Resource.Success -> {
                    val home = data.value
                    if (home?.items?.isNullOrEmpty() == false) {
                        val currentList =
                            home.items.shuffled().filter { !it.list.isNullOrEmpty() }.flatMap { it.list }
                                .distinctBy { it.url }
                                .toList()

                        if (!currentList.isNullOrEmpty()) {
                            val randomItems = currentList.shuffled()

                            _randomItems.postValue(randomItems)
                        }
                    }
                }
                else -> {
                }
            }
            _page.postValue(data)
        } else {
            _page.postValue(Resource.Success(HomePageResponse(emptyList())))
        }
    }

    fun loadAndCancel(preferredApiName: String?, currentPrefMedia: Int) = viewModelScope.launch {
        val api = getApiFromNameNull(preferredApiName)
        if (preferredApiName == noneApi.name)
            loadAndCancel(noneApi)
        else if (preferredApiName == randomApi.name || api == null) {
            val validAPIs = AppUtils.filterProviderByPreferredMedia(apis, currentPrefMedia)
            val apiRandom = validAPIs.random()
            loadAndCancel(apiRandom)
            context?.setKey(HOMEPAGE_API, apiRandom.name)
        } else {
            loadAndCancel(api)
        }
    }
}