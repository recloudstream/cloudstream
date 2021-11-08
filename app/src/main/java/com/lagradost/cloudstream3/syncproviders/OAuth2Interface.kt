package com.lagradost.cloudstream3.syncproviders

import android.content.Context
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.removeKeys
import com.lagradost.cloudstream3.utils.DataStore.setKey
import java.util.concurrent.TimeUnit

interface OAuth2Interface {
    val key: String
    val name: String
    val redirectUrl: String

    fun handleRedirect(context: Context, url: String)
    fun authenticate(context: Context)

    fun loginInfo(context: Context): LoginInfo?
    fun logOut(context: Context)

    class LoginInfo(
        val profilePicture: String?,
        val name: String?,

        val accountIndex: Int,
    )

    abstract class AccountManager(private val defIndex: Int) : OAuth2Interface {
        // don't change this as all keys depend on it
        open val idPrefix: String
            get() {
                throw(NotImplementedError())
            }

        var accountIndex = defIndex
        protected val accountId get() = "${idPrefix}_account_$accountIndex"
        private val accountActiveKey get() = "${idPrefix}_active"

        // int array of all accounts indexes
        private val accountsKey get() = "${idPrefix}_accounts"

        protected fun Context.removeAccountKeys() {
            this.removeKeys(accountId)
            val accounts = getAccounts(this).toMutableList()
            accounts.remove(accountIndex)
            this.setKey(accountsKey, accounts.toIntArray())

            init(this)
        }

        fun getAccounts(context: Context): IntArray {
            return context.getKey(accountsKey, intArrayOf())!!
        }

        fun init(context: Context) {
            accountIndex = context.getKey(accountActiveKey, defIndex)!!
            val accounts = getAccounts(context)
            if (accounts.isNotEmpty() && this.loginInfo(context) == null) {
                accountIndex = accounts.first()
            }
        }

        protected fun Context.switchToNewAccount() {
            val accounts = getAccounts(this)
            accountIndex = (accounts.maxOrNull() ?: 0) + 1
        }

        protected fun Context.registerAccount() {
            this.setKey(accountActiveKey, accountIndex)
            val accounts = getAccounts(this).toMutableList()
            if (!accounts.contains(accountIndex)) {
                accounts.add(accountIndex)
            }

            this.setKey(accountsKey, accounts.toIntArray())
        }

        fun changeAccount(context: Context, index: Int) {
            accountIndex = index
            context.setKey(accountActiveKey, index)
        }
    }

    companion object {
        val malApi = MALApi(0)
        val aniListApi = AniListApi(0)

        val OAuth2Apis
            get() = listOf<OAuth2Interface>(
                malApi, aniListApi
            )

        // this needs init with context
        val OAuth2accountApis
            get() = listOf<AccountManager>(
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