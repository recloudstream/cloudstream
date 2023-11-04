package com.lagradost.cloudstream3.ui.account

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.cloudstream3.utils.DataStoreHelper

class AccountViewModel : ViewModel() {
    private val _accountsLiveData = MutableLiveData<List<DataStoreHelper.Account>>()
    val accountsLiveData: LiveData<List<DataStoreHelper.Account>> get() = _accountsLiveData

    init {
        // Default to using the getAccounts function to retrieve initial data
        val initialAccounts = DataStoreHelper.accounts.toList()
        _accountsLiveData.value = initialAccounts
    }

    fun updateAccounts(newAccounts: List<DataStoreHelper.Account>) {
        _accountsLiveData.value = newAccounts
    }
}
