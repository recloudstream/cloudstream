package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKeys
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey

abstract class AccountManager(private val defIndex: Int) : OAuth2API {
    var accountIndex = defIndex
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
        accountIndex = (accounts?.maxOrNull() ?: 0) + 1
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
