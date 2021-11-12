package com.lagradost.cloudstream3.syncproviders

import android.content.Context
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.removeKeys
import com.lagradost.cloudstream3.utils.DataStore.setKey

abstract class AccountManager(private val defIndex: Int) : OAuth2API {
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
