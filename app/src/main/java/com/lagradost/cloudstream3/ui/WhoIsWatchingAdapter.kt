package com.lagradost.cloudstream3.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.databinding.WhoIsWatchingAccountAddBinding
import com.lagradost.cloudstream3.databinding.WhoIsWatchingAccountBinding
import com.lagradost.cloudstream3.ui.result.setImage
import com.lagradost.cloudstream3.utils.DataStoreHelper

class WhoIsWatchingAdapter(
    private val selectCallBack: (DataStoreHelper.Account) -> Unit = { },
    private val editCallBack: (DataStoreHelper.Account) -> Unit = { },
    private val addAccountCallback: () -> Unit = {}
) :
    ListAdapter<DataStoreHelper.Account, WhoIsWatchingAdapter.WhoIsWatchingHolder>(DiffCallback()) {

    companion object {
        const val FOOTER = 1
        const val NORMAL = 0
    }

    override fun getItemCount(): Int {
        return currentList.size + 1
    }

    override fun getItemViewType(position: Int): Int = when (position) {
        currentList.size -> FOOTER
        else -> NORMAL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WhoIsWatchingHolder =
        WhoIsWatchingHolder(
            binding = when (viewType) {
                NORMAL -> WhoIsWatchingAccountBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )

                FOOTER -> WhoIsWatchingAccountAddBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )

                else -> throw NotImplementedError()
            },
            selectCallBack = selectCallBack,
            addAccountCallback = addAccountCallback,
            editCallBack = editCallBack,
        )


    override fun onBindViewHolder(holder: WhoIsWatchingHolder, position: Int) =
        holder.bind(currentList.getOrNull(position))

    class WhoIsWatchingHolder(
        val binding: ViewBinding,
        val selectCallBack: (DataStoreHelper.Account) -> Unit,
        val addAccountCallback: () -> Unit,
        val editCallBack: (DataStoreHelper.Account) -> Unit
    ) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: DataStoreHelper.Account?) {
            when (binding) {
                is WhoIsWatchingAccountBinding -> binding.apply {
                    if(card == null) return@apply
                    outline.isVisible = card.keyIndex == DataStoreHelper.selectedKeyIndex
                    profileText.text = card.name
                    profileImageBackground.setImage(card.image)
                    root.setOnClickListener {
                        selectCallBack(card)
                    }
                    root.setOnLongClickListener {
                        editCallBack(card)
                        return@setOnLongClickListener true
                    }
                }

                is WhoIsWatchingAccountAddBinding -> binding.apply {
                    root.setOnClickListener {
                        addAccountCallback()
                    }
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DataStoreHelper.Account>() {
        override fun areItemsTheSame(
            oldItem: DataStoreHelper.Account,
            newItem: DataStoreHelper.Account
        ): Boolean = oldItem.keyIndex == newItem.keyIndex

        override fun areContentsTheSame(
            oldItem: DataStoreHelper.Account,
            newItem: DataStoreHelper.Account
        ): Boolean = oldItem == newItem
    }
}