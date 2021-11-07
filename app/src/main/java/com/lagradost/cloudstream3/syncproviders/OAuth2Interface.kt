package com.lagradost.cloudstream3.syncproviders

import android.content.Context
import java.util.concurrent.TimeUnit

interface OAuth2Interface {
    val key : String
    val name : String
    val redirectUrl : String

    fun handleRedirect(context: Context, url : String)
    fun authenticate(context: Context)

    fun loginInfo(context: Context) : LoginInfo?
    fun logOut(context: Context)

    class LoginInfo(
        val profilePicture : String?,
        val name : String?,
    )

    companion object {
        val malApi = MALApi("mal_account_0")
        val aniListApi = AniListApi("anilist_account_0")

        val OAuth2Apis get() = listOf(
            malApi, aniListApi
        )

        const val appString = "cloudstreamapp"

        val unixTime: Long
            get() = System.currentTimeMillis() / 1000L
        val unixTimeMS: Long
            get() = System.currentTimeMillis()

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