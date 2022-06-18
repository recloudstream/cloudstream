package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.mvvm.safeApiCall

class SyncRepo(private val repo: SyncAPI) {
    val idPrefix = repo.idPrefix
    val name = repo.name
    val icon = repo.icon
    val mainUrl = repo.mainUrl
    val requiresLogin = repo.requiresLogin

    suspend fun score(id: String, status: SyncAPI.SyncStatus): Resource<Boolean> {
        return safeApiCall { repo.score(id, status) }
    }

    suspend fun getStatus(id : String) : Resource<SyncAPI.SyncStatus>  {
        return safeApiCall { repo.getStatus(id) ?: throw ErrorLoadingException("No data") }
    }

    suspend fun getResult(id : String) : Resource<SyncAPI.SyncResult>  {
        return safeApiCall { repo.getResult(id) ?: throw ErrorLoadingException("No data") }
    }

    suspend fun search(query : String) : Resource<List<SyncAPI.SyncSearchResult>> {
        return safeApiCall { repo.search(query) ?: throw ErrorLoadingException() }
    }

    fun hasAccount() : Boolean {
        return normalSafeApiCall { repo.loginInfo() != null } ?: false
    }

    fun getIdFromUrl(url : String) : String = repo.getIdFromUrl(url)
}