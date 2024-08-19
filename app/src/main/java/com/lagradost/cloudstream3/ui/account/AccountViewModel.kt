package com.lagradost.cloudstream3.ui.account

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.cloudstream3.AcraApplication.Companion.context
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKeys
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.ui.account.AccountHelper.showPinInputDialog
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAccounts
import com.lagradost.cloudstream3.utils.DataStoreHelper.getDefaultAccount
import com.lagradost.cloudstream3.utils.DataStoreHelper.setAccount

class AccountViewModel : ViewModel() {
    private fun getAllAccounts(): List<DataStoreHelper.Account> {
        return context?.let { getAccounts(it) } ?: DataStoreHelper.accounts.toList()
    }

    private val _accounts: MutableLiveData<List<DataStoreHelper.Account>> = MutableLiveData(getAllAccounts())
    val accounts: LiveData<List<DataStoreHelper.Account>> = _accounts

    private val _isEditing = MutableLiveData(false)
    val isEditing: LiveData<Boolean> = _isEditing

    private val _isAllowedLogin = MutableLiveData(false)
    val isAllowedLogin: LiveData<Boolean> = _isAllowedLogin

    private val _selectedKeyIndex = MutableLiveData(
        getAllAccounts().indexOfFirst {
            it.keyIndex == DataStoreHelper.selectedKeyIndex
        }
    )
    val selectedKeyIndex: LiveData<Int> = _selectedKeyIndex

    fun setIsEditing(value: Boolean) {
        _isEditing.postValue(value)
    }

    fun toggleIsEditing() {
        _isEditing.postValue(!(_isEditing.value ?: false))
    }

    fun handleAccountUpdate(
        account: DataStoreHelper.Account,
        context: Context
    ) {
        val currentAccounts = getAccounts(context).toMutableList()

        val overrideIndex = currentAccounts.indexOfFirst { it.keyIndex == account.keyIndex }

        if (overrideIndex != -1) {
            currentAccounts[overrideIndex] = account
        } else currentAccounts.add(account)

        val currentHomePage = DataStoreHelper.currentHomePage

        setAccount(account)

        DataStoreHelper.currentHomePage = currentHomePage
        DataStoreHelper.accounts = currentAccounts.toTypedArray()

        _accounts.postValue(getAccounts(context))
        _selectedKeyIndex.postValue(getAccounts(context).indexOf(account))
        MainActivity.reloadAccountEvent(true)
    }

    fun handleAccountDelete(
        account: DataStoreHelper.Account,
        context: Context
    ) {
        removeKeys(account.keyIndex.toString())

        val currentAccounts = getAccounts(context).toMutableList()

        currentAccounts.removeIf { it.keyIndex == account.keyIndex }

        DataStoreHelper.accounts = currentAccounts.toTypedArray()

        if (account.keyIndex == DataStoreHelper.selectedKeyIndex) {
            setAccount(getDefaultAccount(context))
        }

        _accounts.postValue(getAccounts(context))
        _selectedKeyIndex.postValue(getAllAccounts().indexOfFirst {
            it.keyIndex == DataStoreHelper.selectedKeyIndex
        })
        MainActivity.reloadAccountEvent(true)
    }

    fun handleAccountSelect(
        account: DataStoreHelper.Account,
        context: Context,
        forStartup: Boolean = false,
        reloadForActivity: Boolean = false
    ) {
        if (reloadForActivity) {
            _accounts.postValue(getAccounts(context))
            _selectedKeyIndex.postValue(getAccounts(context).indexOf(account))
            MainActivity.reloadAccountEvent(true)
            return
        }

        // Check if the selected account has a lock PIN set
        if (account.lockPin != null) {
            // The selected account has a PIN set, prompt the user to enter the PIN
            showPinInputDialog(
                context,
                account.lockPin,
                false,
                forStartup
            ) { pin ->
                if (pin == null) return@showPinInputDialog
                // Pin is correct, proceed
                _isAllowedLogin.postValue(true)
                _selectedKeyIndex.postValue(getAccounts(context).indexOf(account))
                setAccount(account)
                MainActivity.reloadAccountEvent(true)
            }
        } else {
            // No PIN set for the selected account, proceed
            _isAllowedLogin.postValue(true)
            _selectedKeyIndex.postValue(getAccounts(context).indexOf(account))
            setAccount(account)
            MainActivity.reloadAccountEvent(true)
        }
    }
}