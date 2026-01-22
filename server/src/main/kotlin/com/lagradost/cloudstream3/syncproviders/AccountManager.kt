package com.lagradost.cloudstream3.syncproviders

abstract class AccountManager {
    companion object {
        const val NONE_ID: Int = -1
        const val ACCOUNT_TOKEN = "auth_tokens"
        const val ACCOUNT_IDS = "auth_ids"

        const val APP_STRING = "cloudstreamapp"
        const val APP_STRING_REPO = "cloudstreamrepo"
        const val APP_STRING_PLAYER = "cloudstreamplayer"
        const val APP_STRING_SEARCH = "cloudstreamsearch"
        const val APP_STRING_RESUME_WATCHING = "cloudstreamcontinuewatching"
        const val APP_STRING_SHARE = "csshare"

        val malApi = com.lagradost.cloudstream3.syncproviders.providers.MALApi()
        val aniListApi = com.lagradost.cloudstream3.syncproviders.providers.AniListApi()
        val simklApi = com.lagradost.cloudstream3.syncproviders.providers.SimklApi()
        val localListApi = com.lagradost.cloudstream3.syncproviders.providers.LocalList()

        val openSubtitlesApi = com.lagradost.cloudstream3.syncproviders.providers.OpenSubtitlesApi()
        val addic7ed = com.lagradost.cloudstream3.syncproviders.providers.Addic7ed()
        val subDlApi = com.lagradost.cloudstream3.syncproviders.providers.SubDlApi()
        val subSourceApi = com.lagradost.cloudstream3.syncproviders.providers.SubSourceApi()

        var cachedAccounts: MutableMap<String, Array<AuthData>> = mutableMapOf()
        var cachedAccountIds: MutableMap<String, Int> = mutableMapOf()

        val syncApis: Array<SyncRepo> = arrayOf(
            SyncRepo(malApi),
            SyncRepo(aniListApi),
            SyncRepo(simklApi),
            SyncRepo(localListApi),
        )
        val subtitleProviders: Array<SubtitleRepo> = arrayOf(
            SubtitleRepo(openSubtitlesApi),
            SubtitleRepo(addic7ed),
            SubtitleRepo(subDlApi),
            SubtitleRepo(subSourceApi),
        )
        val allApis: Array<AuthRepo> = arrayOf(
            *syncApis,
            *subtitleProviders,
        )

        fun accounts(prefix: String): Array<AuthData> {
            return cachedAccounts[prefix] ?: emptyArray()
        }

        fun updateAccounts(prefix: String, array: Array<AuthData>) {
            cachedAccounts[prefix] = array
        }

        fun updateAccountsId(prefix: String, id: Int) {
            cachedAccountIds[prefix] = id
        }

        fun initMainAPI() {
        }

        fun secondsToReadable(seconds: Int, completedValue: String): String {
            if (seconds <= 0) return completedValue
            val minutes = seconds / 60
            return "${minutes}m"
        }
    }
}
