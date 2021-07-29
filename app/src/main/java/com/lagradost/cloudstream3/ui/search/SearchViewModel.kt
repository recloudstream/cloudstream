package com.lagradost.cloudstream3.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder.allApi
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.ui.APIRepository
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private val _searchResponse: MutableLiveData<Resource<ArrayList<SearchResponse>>> = MutableLiveData()
    val searchResponse: LiveData<Resource<ArrayList<SearchResponse>>> get() = _searchResponse
    var searchCounter = 0
    private val repo = APIRepository(allApi)

    private fun clearSearch() {
        _searchResponse.postValue(Resource.Success(ArrayList()))
    }

    fun search(query: String) = viewModelScope.launch {
        searchCounter++
        if(query.length <= 1) {
            clearSearch()
            return@launch
        }
        val localSearchCounter = searchCounter
        _searchResponse.postValue(Resource.Loading())
        val data = repo.search(query)

        if(localSearchCounter != searchCounter) return@launch
        _searchResponse.postValue(data)
    }

    fun quickSearch(query: String) = viewModelScope.launch {
        searchCounter++
        if(query.length <= 1) {
            clearSearch()
            return@launch
        }
        val localSearchCounter = searchCounter
        _searchResponse.postValue(Resource.Loading())
        val data = repo.quickSearch(query)

        if(localSearchCounter != searchCounter) return@launch
        _searchResponse.postValue(data)
    }
}