package com.lagradost.cloudstream3.ui.account

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKeys
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream4.state.ActionHandler
import com.lagradost.cloudstream4.state.DefaultStateContainer
import com.lagradost.cloudstream4.state.SearchableData
import com.lagradost.cloudstream4.state.StateContainer
import com.lagradost.cloudstream4.state.sortByInt
import kotlinx.collections.immutable.persistentMapOf
import kotlin.random.Random
import kotlin.random.nextInt

@Immutable
data class AccountState(
    /** Mostly used for focus stuff on TV */
    val lastLoginKeyIndex : Int = 0,

    /** Is currently open to editing */
    val canEditAccount: Boolean = false,

    /** keyIndex -> DataStoreHelper.Account sorted by keyIndex as keyIndex is unique and in add order */
    val accounts: SearchableData<Int, DataStoreHelper.Account> = SearchableData.from(
        data = persistentMapOf(),
        /** This will cause bugs if changed! */
        sortedBy = sortByInt { it.keyIndex }
    ),

    /**
     * We are currectly editing this account with this value, it may be an existing/new account
     * non-null = editing, null = not editing
     * */
    val editAccount: DataStoreHelper.Account? = null,

    /** We are currently logged in with this account, we use state instead of effect to prevent bugs */
    val loggedInWith: Int? = null
)

@Immutable
sealed class AccountAction {
    data class SetEditMode(val canEditAccount: Boolean) : AccountAction()
    object NewAccount : AccountAction()
    data class EditAccount(val account: DataStoreHelper.Account?) : AccountAction()
    data class SaveAccount(val account: DataStoreHelper.Account) : AccountAction()
    data class LoginWithAccount(val accountKeyIndex: Int) : AccountAction()
    data class DeleteAccount(val accountKeyIndex: Int) : AccountAction()
}

class AccountViewModel2(
    val defaultAccount: DataStoreHelper.Account,
    initialState : AccountState,
) : ViewModel(), StateContainer<AccountState> by DefaultStateContainer(initialState), ActionHandler<AccountAction> {
    fun updateAccount(
        updater: (SearchableData<Int, DataStoreHelper.Account>) -> SearchableData<Int, DataStoreHelper.Account>
    ) {
        updateState {
            /** `adding` has will "Replace" on same key, and "Add" on a new key */
            val newAccounts = updater(accounts)
            DataStoreHelper.accounts =
                Array(newAccounts.sorted.size) { i ->
                    /** i -> keyIndex -> Account; As keyIndex != index */
                    newAccounts.data[newAccounts.sorted[i]]!!
                }
            copy(accounts = newAccounts)
        }

        MainActivity.reloadAccountEvent(true)
    }

    override fun onAction(action: AccountAction) {
        when (action) {
            is AccountAction.SetEditMode -> {
                updateState { copy(canEditAccount = action.canEditAccount) }
            }

            is AccountAction.NewAccount -> {
                updateState {
                    copy(
                        editAccount = DataStoreHelper.Account(
                            /** Last => maxOf, this WILL break if we allow other sorting orders */
                            keyIndex = (accounts.sorted.lastOrNull() ?: 0) + 1,
                            /** This is not "translated", but that should not really be a problem */
                            name = "Account ${accounts.data.size + 1}",
                            customImage = null,
                            defaultImageIndex = Random.nextInt(DataStoreHelper.profileImages.indices),
                            lockPin = null,
                        )
                    )
                }
            }

            is AccountAction.DeleteAccount -> {
                removeKeys(action.accountKeyIndex.toString())

                /** If we remove the logged in account,
                 * then we switch to the default account */
                if (action.accountKeyIndex == DataStoreHelper.selectedKeyIndex) {
                    val nextAccount = state.value.accounts.data[defaultAccount.keyIndex]
                    /** This should never be non-null, but just in case! */
                        ?: defaultAccount
                    DataStoreHelper.setAccount(nextAccount)
                    updateState { copy(lastLoginKeyIndex = nextAccount.keyIndex) }
                }

                updateAccount { accounts ->
                    val pendingAccounts = accounts.removing(action.accountKeyIndex)

                    /** We must always have a default account */
                    if (pendingAccounts.data.containsKey(defaultAccount.keyIndex)) {
                        pendingAccounts
                    } else {
                        pendingAccounts.adding(defaultAccount.keyIndex, defaultAccount)
                    }
                }
            }

            is AccountAction.SaveAccount -> {
                updateAccount { accounts ->
                    /** `adding` has will "Replace" on same key, and "Add" on a new key */
                    accounts.adding(action.account.keyIndex, action.account)
                }
            }

            is AccountAction.LoginWithAccount -> {
                val accounts = state.value.accounts
                val loginAccount = accounts.data[action.accountKeyIndex] ?: return
                // Show toast if we have many accounts, as it is unnecessary for 1 account
                DataStoreHelper.setAccount(loginAccount, showToast = accounts.data.size > 1)
                updateState { copy(loggedInWith = loginAccount.keyIndex, lastLoginKeyIndex = loginAccount.keyIndex) }
            }

            is AccountAction.EditAccount -> {
                updateState { copy(editAccount = action.account) }
            }
        }
    }
}