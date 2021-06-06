package com.lagradost.cloudstream3.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.safeApiCall
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    val api: MainAPI = apis[0] //TODO MULTI API

    private val _searchResponse: MutableLiveData<Resource<ArrayList<Any>>> = MutableLiveData()
    val searchResponse: LiveData<Resource<ArrayList<Any>>> get() = _searchResponse

    fun search(query: String) = viewModelScope.launch {
        val data = safeApiCall {
            api.search(query)
        }

        _searchResponse.postValue(data as Resource<ArrayList<Any>>?)
    }
}