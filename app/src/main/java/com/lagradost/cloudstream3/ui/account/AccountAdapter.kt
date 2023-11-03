package com.lagradost.cloudstream3.ui.account

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.AccountListItemAddBinding
import com.lagradost.cloudstream3.databinding.AccountListItemBinding
import com.lagradost.cloudstream3.databinding.AccountListItemEditingBinding
import com.lagradost.cloudstream3.ui.account.AccountHelper.showAccountEditDialog
import com.lagradost.cloudstream3.ui.result.setImage
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.DataStoreHelper

class AccountAdapter(
    private val accounts: List<DataStoreHelper.Account>,
    private val accountSelectCallback: (DataStoreHelper.Account) -> Unit,
    private val accountCreateCallback: (DataStoreHelper.Account) -> Unit,
    private val accountEditCallback: (DataStoreHelper.Account) -> Unit
) : RecyclerView.Adapter<AccountAdapter.AccountViewHolder>() {

    companion object {
        private const val VIEW_TYPE_ACCOUNT = 0
        private const val VIEW_TYPE_ADD_ACCOUNT = 1
        const val VIEW_TYPE_EDIT_ACCOUNT = 2
    }

    inner class AccountViewHolder(private val binding: ViewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(account: DataStoreHelper.Account?) {
            when (binding) {
                is AccountListItemBinding -> binding.apply {
                    if (account == null) return@apply

                    val isLastUsedAccount = account.keyIndex == DataStoreHelper.selectedKeyIndex

                    accountName.text = account.name
                    accountImage.setImage(account.image)
                    lockIcon.isVisible = account.lockPin != null
                    outline.isVisible = isLastUsedAccount

                    if (isTvSettings()) {
                        root.isFocusableInTouchMode = true
                        if (isLastUsedAccount) {
                            root.requestFocus()
                        }
                    }

                    root.setOnClickListener {
                        accountSelectCallback.invoke(account)
                    }
                }

                is AccountListItemEditingBinding -> binding.apply {
                    if (account == null) return@apply

                    val isLastUsedAccount = account.keyIndex == DataStoreHelper.selectedKeyIndex

                    accountName.text = account.name
                    accountImage.setImage(account.image)
                    lockIcon.isVisible = account.lockPin != null
                    outline.isVisible = isLastUsedAccount

                    if (isTvSettings()) {
                        root.isFocusableInTouchMode = true
                        if (isLastUsedAccount) {
                            root.requestFocus()
                        }
                    }

                    root.setOnClickListener {
                        showAccountEditDialog(root.context, account, isNewAccount = false) {
                            accountEditCallback.invoke(it)
                        }
                    }
                }

                is AccountListItemAddBinding -> binding.apply {
                    root.setOnClickListener {
                        val remainingImages =
                            DataStoreHelper.profileImages.toSet() - accounts.filter { it.customImage == null }
                                .mapNotNull { DataStoreHelper.profileImages.getOrNull(it.defaultImageIndex) }.toSet()

                        val image =
                            DataStoreHelper.profileImages.indexOf(remainingImages.randomOrNull() ?: DataStoreHelper.profileImages.random())
                        val keyIndex = (accounts.maxOfOrNull { it.keyIndex } ?: 0) + 1

                        val accountName = root.context.getString(R.string.account)

                        showAccountEditDialog(root.context, DataStoreHelper.Account(
                            keyIndex = keyIndex,
                            name = "$accountName $keyIndex",
                            customImage = null,
                            defaultImageIndex = image
                        ), isNewAccount = true) {
                            accountCreateCallback.invoke(it)
                        }
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder =
        AccountViewHolder(
            binding = when (viewType) {
                VIEW_TYPE_ACCOUNT -> {
                    AccountListItemBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                }
                VIEW_TYPE_ADD_ACCOUNT -> {
                    AccountListItemAddBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                }
                VIEW_TYPE_EDIT_ACCOUNT -> {
                    AccountListItemEditingBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                }
                else -> throw IllegalArgumentException("Invalid view type")
            }
        )

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        holder.bind(accounts.getOrNull(position))
    }

    var viewType = 0

    override fun getItemViewType(position: Int): Int {
        if (viewType != 0 && position != accounts.count()) {
            return viewType
        }

        return when (position) {
            accounts.count() -> VIEW_TYPE_ADD_ACCOUNT
            else -> VIEW_TYPE_ACCOUNT
        }
    }

    override fun getItemCount(): Int {
        return accounts.count() + 1
    }
}