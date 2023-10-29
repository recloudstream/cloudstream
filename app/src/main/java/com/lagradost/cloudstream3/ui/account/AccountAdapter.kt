package com.lagradost.cloudstream3.ui.account

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.setImage
import com.lagradost.cloudstream3.utils.DataStoreHelper

class AccountAdapter(private val accounts: List<DataStoreHelper.Account>, private val onItemClick: (DataStoreHelper.Account) -> Unit) :
    RecyclerView.Adapter<AccountAdapter.AccountViewHolder>() {

    inner class AccountViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val accountName: TextView = itemView.findViewById(R.id.account_name)
        val accountImage: ImageView = itemView.findViewById(R.id.account_image)
        val lockIcon: ImageView = itemView.findViewById(R.id.lock_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.account_list_item, parent, false)
        return AccountViewHolder(view)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        val account = accounts[position]

        // Populate data into the UI elements
        holder.accountName.text = account.name
        holder.accountImage.setImage(account.image)

        if (account.lockPin != null) {
            holder.lockIcon.isVisible = true
        }

        holder.itemView.setOnClickListener {
            onItemClick(account)
        }
    }

    override fun getItemCount(): Int {
        return accounts.size
    }
}
