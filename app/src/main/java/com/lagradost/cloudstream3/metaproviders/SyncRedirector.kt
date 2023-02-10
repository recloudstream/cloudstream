package com.lagradost.cloudstream3.metaproviders

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.SyncApis
import com.lagradost.cloudstream3.syncproviders.SyncIdName

object SyncRedirector {
    val syncApis = SyncApis
    private val syncIds =
        listOf(
            SyncIdName.MyAnimeList to Regex("""myanimelist\.net\/anime\/(\d+)"""),
            SyncIdName.Anilist to Regex("""anilist\.co\/anime\/(\d+)""")
        )

    suspend fun redirect(
        url: String,
        providerApi: MainAPI
    ): String {
        // Deprecated since providers should do this instead!

        // Tries built in ID -> ProviderUrl
        /*
        for (api in syncApis) {
            if (url.contains(api.mainUrl)) {
                val otherApi = when (api.name) {
                    aniListApi.name -> "anilist"
                    malApi.name -> "myanimelist"
                    else -> return url
                }

                SyncUtil.getUrlsFromId(api.getIdFromUrl(url), otherApi).firstOrNull { realUrl ->
                    realUrl.contains(providerApi.mainUrl)
                }?.let {
                    return it
                }
//                ?: run {
//                    throw ErrorLoadingException("Page does not exist on $preferredUrl")
//                }
            }
        }
        */

        // Tries provider solution
        // This goes through all sync ids and finds supported id by said provider
        return syncIds.firstNotNullOfOrNull { (syncName, syncRegex) ->
            if (providerApi.supportedSyncNames.contains(syncName)) {
                syncRegex.find(url)?.value?.let {
                    suspendSafeApiCall {
                        providerApi.getLoadUrl(syncName, it)
                    }
                }
            } else null
        } ?: url
    }
}