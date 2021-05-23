package com.lagradost.cloudstream3.mvvm

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun <T> LifecycleOwner.observe(liveData: LiveData<T>, action: (t: T) -> Unit) {
    liveData.observe(this, Observer { it?.let { t -> action(t) } })
}

fun <T> LifecycleOwner.observeDirectly(liveData: LiveData<T>, action: (t: T) -> Unit) {
    liveData.observe(this, Observer { it?.let { t -> action(t) } })
    val currentValue = liveData.value
    if (currentValue != null)
        action(currentValue)
}

sealed class Resource<out T> {
    data class Success<out T>(val value: T) : Resource<T>()
    data class Failure(
        val isNetworkError: Boolean,
        val errorCode: Int?,
        val errorResponse: Any?, //ResponseBody
        val errorString: String,
    ) : Resource<Nothing>()
}

suspend fun <T> safeApiCall(
    apiCall: suspend () -> T,
): Resource<T> {
    return withContext(Dispatchers.IO) {
        try {
            Resource.Success(apiCall.invoke())
        } catch (throwable: Throwable) {
            Log.d("ApiError", "-------------------------------------------------------------------")
            Log.d("ApiError", "safeApiCall: " + throwable.localizedMessage)
            Log.d("ApiError", "safeApiCall: " + throwable.message)
            Log.d("ApiError", "-------------------------------------------------------------------")
            when (throwable) {
                /*is HttpException -> {
                    Resource.Failure(false, throwable.code(), throwable.response()?.errorBody(), throwable.localizedMessage)
                }
                is SocketTimeoutException -> {
                    Resource.Failure(true,null,null,"Please try again later.")
                }
                is UnknownHostException ->{
                    Resource.Failure(true,null,null,"Cannot connect to server, try again later.")
                }*/
                else -> {
                    Resource.Failure(true, null, null, throwable.localizedMessage)
                }
            }
        }
    }
}