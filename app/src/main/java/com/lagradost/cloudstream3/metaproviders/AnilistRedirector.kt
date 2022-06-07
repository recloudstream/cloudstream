package com.lagradost.cloudstream3.metaproviders

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.SyncApis
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.aniListApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.malApi
import com.lagradost.cloudstream3.utils.SyncUtil

object SyncRedirector {
    val syncApis = SyncApis

    suspend fun redirect(url: String, preferredUrl: String): String {
        for (api in syncApis) {
            if (url.contains(api.mainUrl)) {
                val otherApi = when (api.name) {
                    aniListApi.name -> "anilist"
                    malApi.name -> "myanimelist"
                    else -> return url
                }

                return SyncUtil.getUrlsFromId(api.getIdFromUrl(url), otherApi).firstOrNull { realUrl ->
                    realUrl.contains(preferredUrl)
                } ?: run {
                    throw ErrorLoadingException("Page does not exist on $preferredUrl")
                }
            }
        }
        return url
    }
}