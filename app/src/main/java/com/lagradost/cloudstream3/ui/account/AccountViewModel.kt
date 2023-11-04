package com.lagradost.cloudstream3.ui.account

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.cloudstream3.AcraApplication.Companion.context
import com.lagradost.cloudstream3.ui.account.AccountHelper.showPinInputDialog
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAccounts
import com.lagradost.cloudstream3.utils.DataStoreHelper.setAccount

class AccountViewModel : ViewModel() {
    private val _accounts: MutableLiveData<List<DataStoreHelper.Account>> = MutableLiveData(
        context?.let { getAccounts(it) } ?: DataStoreHelper.accounts.toList()
    )

    val accounts: LiveData<List<DataStoreHelper.Account>> = _accounts

    private val _isEditing = MutableLiveData(false)
    val isEditing: LiveData<Boolean> = _isEditing

    private val _isAllowedLogin = MutableLiveData(false)
    val isAllowedLogin: LiveData<Boolean> = _isAllowedLogin

    private val _selectedKeyIndex = MutableLiveData(0)
    val selectedKeyIndex: LiveData<Int> = _selectedKeyIndex

    fun setIsEditing(value: Boolean) {
        _isEditing.postValue(value)
    }

    fun toggleIsEditing() {
        _isEditing.postValue(!(_isEditing.value ?: false))
    }

    fun handleAccountUpdate(context: Context) {
        _accounts.postValue(getAccounts(context))
        _selectedKeyIndex.postValue(DataStoreHelper.selectedKeyIndex)
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

        setAccount(account, false)

        DataStoreHelper.currentHomePage = currentHomePage
        DataStoreHelper.accounts = currentAccounts.toTypedArray()

        _accounts.postValue(getAccounts(context))
        _selectedKeyIndex.postValue(account.keyIndex)
    }

    fun handleAccountSelect(
        account: DataStoreHelper.Account,
        context: Context
    ) {
        // Check if the selected account has a lock PIN set
        if (account.lockPin != null) {
            // The selected account has a PIN set, prompt the user to enter the PIN
            showPinInputDialog(
                context,
                account.lockPin,
                false
            ) { pin ->
                if (pin == null) return@showPinInputDialog
                // Pin is correct, proceed
                _isAllowedLogin.postValue(true)
                _selectedKeyIndex.postValue(account.keyIndex)
                setAccount(account, true)
            }
        } else {
            // No PIN set for the selected account, proceed
            _isAllowedLogin.postValue(true)
            _selectedKeyIndex.postValue(account.keyIndex)
            setAccount(account, true)
        }
    }
}