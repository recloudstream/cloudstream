package com.lagradost.cloudstream3.ui.account

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.databinding.AccountListItemBinding
import com.lagradost.cloudstream3.ui.result.setImage
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.DataStoreHelper

class AccountAdapter(
    private val accounts: List<DataStoreHelper.Account>,
    private val onItemClick: (DataStoreHelper.Account) -> Unit
) : RecyclerView.Adapter<AccountAdapter.AccountViewHolder>() {

    inner class AccountViewHolder(private val binding: AccountListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(account: DataStoreHelper.Account) {
            val isLastUsedAccount = account.keyIndex == DataStoreHelper.selectedKeyIndex

            binding.accountName.text = account.name
            binding.accountImage.setImage(account.image)
            binding.lockIcon.isVisible = account.lockPin != null
            binding.outline.isVisible = isLastUsedAccount

            if (isTvSettings()) {
                binding.root.isFocusableInTouchMode = true
                if (isLastUsedAccount) {
                    binding.root.requestFocus()
                }
            }

            binding.root.setOnClickListener {
                onItemClick(account)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val binding = AccountListItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )

        return AccountViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        holder.bind(accounts[position])
    }

    override fun getItemCount(): Int {
        return accounts.size
    }
}