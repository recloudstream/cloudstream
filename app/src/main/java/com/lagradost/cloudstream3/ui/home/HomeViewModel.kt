package com.lagradost.cloudstream3.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    var repo: APIRepository? = null

    private val _apiName = MutableLiveData<String>()
    val apiName: LiveData<String> = _apiName

    private val _page = MutableLiveData<Resource<HomePageResponse>>()
    val page: LiveData<Resource<HomePageResponse>> = _page

    private fun autoloadRepo(): APIRepository {
        return APIRepository(apis.first { it.hasMainPage })
    }

    fun load(api : MainAPI?)  = viewModelScope.launch {
        repo = if (api?.hasMainPage == true) {
            APIRepository(api)
        } else {
            autoloadRepo()
        }
        _page.postValue(Resource.Loading())
        _page.postValue(repo?.getMainPage())
    }

    fun load(preferredApiName: String?) = viewModelScope.launch {
        val api = getApiFromNameNull(preferredApiName)
        load(api)
    }
}