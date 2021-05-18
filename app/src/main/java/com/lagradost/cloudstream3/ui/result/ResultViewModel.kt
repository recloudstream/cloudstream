package com.lagradost.cloudstream3.ui.result

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.getApiFromName
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.safeApiCall
import kotlinx.coroutines.launch

class ResultViewModel : ViewModel() {
    private val _resultResponse: MutableLiveData<Resource<Any?>> = MutableLiveData()
    val resultResponse: LiveData<Resource<Any?>> get() = _resultResponse

    fun load(url: String, apiName:String) = viewModelScope.launch {
        val data = safeApiCall {
            getApiFromName(apiName).load(url)
        }

        _resultResponse.postValue(data)
    }
}