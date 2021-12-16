package com.lagradost.cloudstream3.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.SyncApis
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.APIRepository.Companion.providersActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OnGoingSearch(
    val apiName: String,
    val data: Resource<List<SearchResponse>>
)

class SearchViewModel : ViewModel() {
    private val _searchResponse: MutableLiveData<Resource<ArrayList<SearchResponse>>> = MutableLiveData()
    val searchResponse: LiveData<Resource<ArrayList<SearchResponse>>> get() = _searchResponse

    private val _currentSearch: MutableLiveData<ArrayList<OnGoingSearch>> = MutableLiveData()
    val currentSearch: LiveData<ArrayList<OnGoingSearch>> get() = _currentSearch

    private val repos = apis.map { APIRepository(it) }
    private val syncApis = SyncApis

    private fun clearSearch() {
        _searchResponse.postValue(Resource.Success(ArrayList()))
    }

    var onGoingSearch: Job? = null
    fun searchAndCancel(query: String, isMainApis: Boolean = true, ignoreSettings: Boolean = false) {
        onGoingSearch?.cancel()
        onGoingSearch = search(query, isMainApis, ignoreSettings)
    }

    data class SyncSearchResultSearchResponse(
        override val name: String,
        override val url: String,
        override val apiName: String,
        override val type: TvType?,
        override val posterUrl: String?,
        override val id: Int?,
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

    private fun search(query: String, isMainApis: Boolean = true, ignoreSettings: Boolean = false) =
        viewModelScope.launch {
            if (query.length <= 1) {
                clearSearch()
                return@launch
            }

            _searchResponse.postValue(Resource.Loading())

            val currentList = ArrayList<OnGoingSearch>()

            _currentSearch.postValue(ArrayList())

            withContext(Dispatchers.IO) { // This interrupts UI otherwise
                if (isMainApis) {
                    repos.filter { a ->
                        ignoreSettings || (providersActive.size == 0 || providersActive.contains(a.name))
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
                currentList.map { it.data }.filterIsInstance<Resource.Success<List<SearchResponse>>>().map { it.value }

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