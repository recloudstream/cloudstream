package com.lagradost.cloudstream3.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.filterHomePageListByFilmQuality
import com.lagradost.cloudstream3.APIHolder.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.APIHolder.filterSearchResultByFilmQuality
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.AcraApplication.Companion.context
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.debugAssert
import com.lagradost.cloudstream3.mvvm.debugWarning
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.APIRepository.Companion.noneApi
import com.lagradost.cloudstream3.ui.APIRepository.Companion.randomApi
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllResumeStateIds
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllWatchStateIds
import com.lagradost.cloudstream3.utils.DataStoreHelper.getBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.getLastWatched
import com.lagradost.cloudstream3.utils.DataStoreHelper.getResultWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.HOMEPAGE_API
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.collections.set

class HomeViewModel : ViewModel() {
    private var repo: APIRepository? = null

    private val _apiName = MutableLiveData<String>()
    val apiName: LiveData<String> = _apiName

    private val _randomItems = MutableLiveData<List<SearchResponse>?>(null)
    val randomItems: LiveData<List<SearchResponse>?> = _randomItems

    private fun autoloadRepo(): APIRepository {
        return APIRepository(apis.first { it.hasMainPage })
    }

    private val _availableWatchStatusTypes =
        MutableLiveData<Pair<EnumSet<WatchType>, EnumSet<WatchType>>>()
    val availableWatchStatusTypes: LiveData<Pair<EnumSet<WatchType>, EnumSet<WatchType>>> =
        _availableWatchStatusTypes
    private val _bookmarks = MutableLiveData<Pair<Boolean, List<SearchResponse>>>()
    val bookmarks: LiveData<Pair<Boolean, List<SearchResponse>>> = _bookmarks

    private val _resumeWatching = MutableLiveData<List<SearchResponse>>()
    val resumeWatching: LiveData<List<SearchResponse>> = _resumeWatching

    fun loadResumeWatching() = viewModelScope.launch {
        val resumeWatching = withContext(Dispatchers.IO) {
            getAllResumeStateIds()?.mapNotNull { id ->
                getLastWatched(id)
            }?.sortedBy { -it.updateTime }
        }

        // val resumeWatchingResult = ArrayList<DataStoreHelper.ResumeWatchingResult>()

        val resumeWatchingResult = withContext(Dispatchers.IO) {
            resumeWatching?.map { resume ->
                val data = getKey<VideoDownloadHelper.DownloadHeaderCached>(
                    DOWNLOAD_HEADER_CACHE,
                    resume.parentId.toString()
                ) ?: return@map null
                val watchPos = getViewPos(resume.episodeId)
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
            }?.filterNotNull()
        }
        resumeWatchingResult?.let {
            _resumeWatching.postValue(it)
        }
    }

    fun loadStoredData(preferredWatchStatus: EnumSet<WatchType>?) = viewModelScope.launch {
        val watchStatusIds = withContext(Dispatchers.IO) {
            getAllWatchStateIds()?.map { id ->
                Pair(id, getResultWatchState(id))
            }
        }?.distinctBy { it.first } ?: return@launch

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
                .mapNotNull { getBookmarkedData(it.first) }
                .sortedBy { -it.latestUpdatedTime }
        }
        _bookmarks.postValue(Pair(true, list))
    }

    private var onGoingLoad: Job? = null
    private fun loadAndCancel(api: MainAPI?) {
        onGoingLoad?.cancel()
        onGoingLoad = load(api)
    }

    data class ExpandableHomepageList(
        var list: HomePageList,
        var currentPage: Int,
        var hasNext: Boolean,
    )

    private val expandable: MutableMap<String, ExpandableHomepageList> = mutableMapOf()
    private val _page =
        MutableLiveData<Resource<Map<String, ExpandableHomepageList>>>(Resource.Loading())
    val page: LiveData<Resource<Map<String, ExpandableHomepageList>>> = _page

    val lock: MutableSet<String> = mutableSetOf()

    suspend fun expandAndReturn(name: String): ExpandableHomepageList? {
        if (lock.contains(name)) return null
        lock += name

        repo?.apply {
            expandable[name]?.let { current ->
                debugAssert({ !current.hasNext }) {
                    "Expand called when not needed"
                }

                val nextPage = current.currentPage + 1
                val next = getMainPage(nextPage, mainPage.indexOfFirst { it.name == name })
                if (next is Resource.Success) {
                    next.value.filterNotNull().forEach { main ->
                        main.items.forEach { newList ->
                            val key = newList.name
                            expandable[key]?.apply {
                                hasNext = main.hasNext
                                currentPage = nextPage

                                debugWarning({ newList.list.any { outer -> this.list.list.any { it.url == outer.url } } }) {
                                    "Expanded contained an item that was previously already in the list\n${list.name} = ${this.list.list}\n${newList.name} = ${newList.list}"
                                }

                                this.list.list += newList.list
                                this.list.list.distinctBy { it.url } // just to be sure we are not adding the same shit for some reason
                            } ?: debugWarning {
                                "Expanded an item not in main load named $key, current list is ${expandable.keys}"
                            }
                        }
                    }
                } else {
                    current.hasNext = false
                }
            }
            _page.postValue(Resource.Success(expandable))
        }

        lock -= name

        return expandable[name]
    }

    // this is soo over engineered, but idk how I can make it clean without making the main api harder to use :pensive:
    fun expand(name: String) = viewModelScope.launch {
        expandAndReturn(name)
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

            when (val data = repo?.getMainPage(1, null)) {
                is Resource.Success -> {
                    try {
                        expandable.clear()
                        data.value.forEach { home ->
                            home?.items?.forEach { list ->
                                val filteredList = context?.filterHomePageListByFilmQuality(list) ?: list
                                expandable[list.name] =
                                    ExpandableHomepageList(filteredList, 1, home.hasNext)
                            }
                        }
                        _page.postValue(Resource.Success(expandable))
                        val items = data.value.mapNotNull { it?.items }.flatten()

                        //val home = data.value
                        if (items.isNotEmpty()) {
                            val currentList =
                                items.shuffled().filter { it.list.isNotEmpty() }
                                    .flatMap { it.list }
                                    .distinctBy { it.url }
                                    .toList()

                            if (currentList.isNotEmpty()) {
                                val randomItems = context?.filterSearchResultByFilmQuality(currentList.shuffled()) ?: currentList.shuffled()

                                _randomItems.postValue(randomItems)
                            }
                        }
                    } catch (e: Exception) {
                        _randomItems.postValue(emptyList())
                        logError(e)
                    }
                }
                is Resource.Failure -> {
                    _page.postValue(data!!)
                }
                else -> Unit
            }
        } else {
            _page.postValue(Resource.Success(emptyMap()))
        }
    }

    fun loadAndCancel(preferredApiName: String?) = viewModelScope.launch {
        val api = getApiFromNameNull(preferredApiName)
        if (preferredApiName == noneApi.name)
            loadAndCancel(noneApi)
        else if (preferredApiName == randomApi.name || api == null) {
            val validAPIs = context?.filterProviderByPreferredMedia()
            if (validAPIs.isNullOrEmpty()) {
                loadAndCancel(noneApi)
            } else {
                val apiRandom = validAPIs.random()
                loadAndCancel(apiRandom)
                setKey(HOMEPAGE_API, apiRandom.name)
            }
        } else {
            loadAndCancel(api)
        }
    }
}