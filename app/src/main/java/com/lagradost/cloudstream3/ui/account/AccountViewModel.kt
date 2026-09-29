package com.lagradost.cloudstream3.ui.account

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.cloudstream3.CloudStreamApp.Companion.context
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKeys
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.account.AccountHelper.showPinInputDialog
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAccounts
import com.lagradost.cloudstream3.utils.DataStoreHelper.getDefaultAccount
import com.lagradost.cloudstream3.utils.DataStoreHelper.setAccount

class AccountViewModel : ViewModel() {
    private fun releaseUnusedProfileImagePermission(
        context: Context,
        image: String?,
        accounts: List<DataStoreHelper.Account>,
    ) {
        if (image == null || accounts.any { it.customImage == image }) return
        val uri = image.toUri()
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return

        try {
            val resolver = context.applicationContext.contentResolver
            val hasReadPermission = resolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
            if (hasReadPermission) {
                resolver.releasePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        } catch (error: Exception) {
            logError(error)
        }
    }

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
        val oldCustomImage = currentAccounts.getOrNull(overrideIndex)?.customImage

        if (overrideIndex != -1) {
            currentAccounts[overrideIndex] = account
        } else currentAccounts.add(account)

        val currentHomePage = DataStoreHelper.currentHomePage

        setAccount(account)

        DataStoreHelper.currentHomePage = currentHomePage
        DataStoreHelper.accounts = currentAccounts.toTypedArray()
        releaseUnusedProfileImagePermission(context, oldCustomImage, currentAccounts)

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
        val deletedCustomImage =
            currentAccounts.firstOrNull { it.keyIndex == account.keyIndex }?.customImage

        currentAccounts.removeIf { it.keyIndex == account.keyIndex }

        DataStoreHelper.accounts = currentAccounts.toTypedArray()
        releaseUnusedProfileImagePermission(context, deletedCustomImage, currentAccounts)

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
