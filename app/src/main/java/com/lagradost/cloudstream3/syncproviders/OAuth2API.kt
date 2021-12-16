package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.syncproviders.providers.AniListApi
import com.lagradost.cloudstream3.syncproviders.providers.MALApi
import java.util.concurrent.TimeUnit

interface OAuth2API {
    val key: String
    val name: String
    val redirectUrl: String

    // don't change this as all keys depend on it
    val idPrefix : String

    fun handleRedirect(url: String)
    fun authenticate()

    fun loginInfo(): LoginInfo?
    fun logOut()

    class LoginInfo(
        val profilePicture: String?,
        val name: String?,

        val accountIndex: Int,
    )

    companion object {
        val malApi = MALApi(0)
        val aniListApi = AniListApi(0)

        // used to login via app intent
        val OAuth2Apis
            get() = listOf<OAuth2API>(
                malApi, aniListApi
            )

        // this needs init with context and can be accessed in settings
        val OAuth2accountApis
            get() = listOf<AccountManager>(
                malApi, aniListApi
            )

        // used for active syncing
        val SyncApis
            get() = listOf<SyncAPI>(
                malApi, aniListApi
            )

        const val appString = "cloudstreamapp"

        val unixTime: Long
            get() = System.currentTimeMillis() / 1000L

        const val maxStale = 60 * 10

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
}