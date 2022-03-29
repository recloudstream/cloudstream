package com.lagradost.cloudstream3.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.getKeys
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.SyncApis
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OnGoingSearch(
    val apiName: String,
    val data: Resource<List<SearchResponse>>
)

const val SEARCH_HISTORY_KEY = "search_history"

class SearchViewModel : ViewModel() {
    private val _searchResponse: MutableLiveData<Resource<List<SearchResponse>>> =
        MutableLiveData()
    val searchResponse: LiveData<Resource<List<SearchResponse>>> get() = _searchResponse

    private val _currentSearch: MutableLiveData<List<OnGoingSearch>> = MutableLiveData()
    val currentSearch: LiveData<List<OnGoingSearch>> get() = _currentSearch

    private val _currentHistory: MutableLiveData<List<SearchHistoryItem>> = MutableLiveData()
    val currentHistory: LiveData<List<SearchHistoryItem>> get() = _currentHistory

    private val repos = apis.map { APIRepository(it) }
    private val syncApis = SyncApis

    fun clearSearch() {
        _searchResponse.postValue(Resource.Success(ArrayList()))
        _currentSearch.postValue(emptyList())
    }

    private var onGoingSearch: Job? = null
    fun searchAndCancel(
        query: String,
        isMainApis: Boolean = true,
        providersActive: Set<String> = setOf(),
        ignoreSettings: Boolean = false
    ) {
        onGoingSearch?.cancel()
        onGoingSearch = search(query, isMainApis, providersActive, ignoreSettings)
    }

    data class SyncSearchResultSearchResponse(
        override val name: String,
        override val url: String,
        override val apiName: String,
        override var type: TvType?,
        override var posterUrl: String?,
        override var id: Int?,
        override var quality: SearchQuality? = null
    ) : SearchResponse

    private fun SyncAPI.SyncSearchResult.toSearchResponse(): SyncSearchResultSearchResponse {
        return SyncSearchResultSearchResponse(
            this.name,
            this.url,
            this.syncApiName,
            null,
            this.posterUrl,
            null, //this.id.hashCode()
        )
    }

    fun updateHistory() = viewModelScope.launch {
        ioSafe {
            val items = getKeys(SEARCH_HISTORY_KEY)?.mapNotNull {
                getKey<SearchHistoryItem>(it)
            }?.sortedByDescending { it.searchedAt } ?: emptyList()
            _currentHistory.postValue(items)
        }
    }

    private fun search(
        query: String,
        isMainApis: Boolean = true,
        providersActive: Set<String>,
        ignoreSettings: Boolean = false
    ) =
        viewModelScope.launch {
            if (query.length <= 1) {
                clearSearch()
                return@launch
            }

            val key = query.hashCode().toString()
            setKey(
                SEARCH_HISTORY_KEY,
                key,
                SearchHistoryItem(
                    searchedAt = System.currentTimeMillis(),
                    searchText = query,
                    type = emptyList(), // TODO implement tv type
                    key = key,
                )
            )

            _searchResponse.postValue(Resource.Loading())

            val currentList = ArrayList<OnGoingSearch>()

            _currentSearch.postValue(ArrayList())

            withContext(Dispatchers.IO) { // This interrupts UI otherwise
                if (isMainApis) {
                    repos.filter { a ->
                        ignoreSettings || (providersActive.isEmpty() || providersActive.contains(a.name))
                    }.apmap { a -> // Parallel
                        val search = a.search(query)
                        currentList.add(OnGoingSearch(a.name, search))
                        _currentSearch.postValue(currentList)
                    }
                } else {
                    syncApis.apmap { a ->
                        val search = safeApiCall {
                            a.search(query)?.map { it.toSearchResponse() }
                                ?: throw ErrorLoadingException()
                        }

                        currentList.add(OnGoingSearch(a.name, search))
                    }
                }
            }
            _currentSearch.postValue(currentList)

            val list = ArrayList<SearchResponse>()
            val nestedList =
                currentList.map { it.data }
                    .filterIsInstance<Resource.Success<List<SearchResponse>>>().map { it.value }

            // I do it this way to move the relevant search results to the top
            var index = 0
            while (true) {
                var added = 0
                for (sublist in nestedList) {
                    if (sublist.size > index) {
                        list.add(sublist[index])
                        added++
                    }
                }
                if (added == 0) break
                index++
            }

            _searchResponse.postValue(Resource.Success(list))
        }
}