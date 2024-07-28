package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKeys
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.syncproviders.providers.*
import java.util.concurrent.TimeUnit

abstract class AccountManager(private val defIndex: Int) : AuthAPI {
    companion object {
        val malApi = MALApi(0).also { api ->
            LoadResponse.Companion.malIdPrefix = api.idPrefix
        }
        val aniListApi = AniListApi(0).also { api ->
            LoadResponse.Companion.aniListIdPrefix = api.idPrefix
        }
        val simklApi = SimklApi(0).also { api ->
            LoadResponse.Companion.simklIdPrefix = api.idPrefix
        }
        val openSubtitlesApi = OpenSubtitlesApi(0)
        val addic7ed = Addic7ed()
        val subDlApi = SubDlApi(0)
        val localListApi = LocalList()
        val subSourceApi = SubSourceApi()

        // used to login via app intent
        val OAuth2Apis
            get() = listOf<OAuth2API>(
                malApi, aniListApi, simklApi
            )

        // this needs init with context and can be accessed in settings
        val accountManagers
            get() = listOf(
                malApi, aniListApi, openSubtitlesApi, subDlApi, simklApi //nginxApi
            )

        // used for active syncing
        val SyncApis
            get() = listOf(
                SyncRepo(malApi), SyncRepo(aniListApi), SyncRepo(localListApi), SyncRepo(simklApi)
            )

        val inAppAuths
            get() = listOf<InAppAuthAPIManager>(
                openSubtitlesApi,
                subDlApi
                )//, nginxApi)

        val subtitleProviders
            get() = listOf(
                openSubtitlesApi,
                addic7ed,
                subDlApi,
                subSourceApi
            )

        const val APP_STRING = "cloudstreamapp"
        const val APP_STRING_REPO = "cloudstreamrepo"
        const val APP_STRING_PLAYER = "cloudstreamplayer"

        // Instantly start the search given a query
        const val APP_STRING_SEARCH = "cloudstreamsearch"

        // Instantly resume watching a show
        const val APP_STRING_RESUME_WATCHING = "cloudstreamcontinuewatching"

        val unixTime: Long
            get() = System.currentTimeMillis() / 1000L
        val unixTimeMs: Long
            get() = System.currentTimeMillis()

        const val MAX_STALE = 60 * 10

        fun secondsToReadable(seconds: Int, completedValue: String): String {
            var secondsLong = seconds.toLong()
            val days = TimeUnit.SECONDS
                .toDays(secondsLong)
            secondsLong -= TimeUnit.DAYS.toSeconds(days)

            val hours = TimeUnit.SECONDS
                .toHours(secondsLong)
            secondsLong -= TimeUnit.HOURS.toSeconds(hours)

            val minutes = TimeUnit.SECONDS
                .toMinutes(secondsLong)
            secondsLong -= TimeUnit.MINUTES.toSeconds(minutes)
            if (minutes < 0) {
                return completedValue
            }
            //println("$days $hours $minutes")
            return "${if (days != 0L) "$days" + "d " else ""}${if (hours != 0L) "$hours" + "h " else ""}${minutes}m"
        }
    }

    var accountIndex = defIndex
    private var lastAccountIndex = defIndex
    protected val accountId get() = "${idPrefix}_account_$accountIndex"
    private val accountActiveKey get() = "${idPrefix}_active"

    // int array of all accounts indexes
    private val accountsKey get() = "${idPrefix}_accounts"

    protected fun removeAccountKeys() {
        removeKeys(accountId)
        val accounts = getAccounts()?.toMutableList() ?: mutableListOf()
        accounts.remove(accountIndex)
        setKey(accountsKey, accounts.toIntArray())

        init()
    }

    fun getAccounts(): IntArray? {
        return getKey(accountsKey, intArrayOf())
    }

    fun init() {
        accountIndex = getKey(accountActiveKey, defIndex)!!
        val accounts = getAccounts()
        if (accounts?.isNotEmpty() == true && this.loginInfo() == null) {
            accountIndex = accounts.first()
        }
    }

    protected fun switchToNewAccount() {
        val accounts = getAccounts()
        lastAccountIndex = accountIndex
        accountIndex = (accounts?.maxOrNull() ?: 0) + 1
    }
    protected fun switchToOldAccount() {
        accountIndex = lastAccountIndex
    }

    protected fun registerAccount() {
        setKey(accountActiveKey, accountIndex)
        val accounts = getAccounts()?.toMutableList() ?: mutableListOf()
        if (!accounts.contains(accountIndex)) {
            accounts.add(accountIndex)
        }

        setKey(accountsKey, accounts.toIntArray())
    }

    fun changeAccount(index: Int) {
        accountIndex = index
        setKey(accountActiveKey, index)
    }
}
