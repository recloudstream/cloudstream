package com.lagradost.cloudstream3.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.NotificationItemBinding
import com.lagradost.cloudstream3.utils.DataStoreHelper.SubscribedData
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute

class NotificationAdapter(
    private val onItemClick: (SubscribedData) -> Unit,
    private val onDeleteClick: (SubscribedData) -> Unit,
) : ListAdapter<SubscribedData, NotificationAdapter.NotificationViewHolder>(SubscribedDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = NotificationItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NotificationViewHolder(
        private val binding: NotificationItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SubscribedData) {
            binding.itemPoster.loadImage(item.posterUrl, item.posterHeaders)
            binding.itemTitle.text = item.name

            val context = binding.root.context
            val maxEp = item.lastSeenEpisodeCount.values.filterNotNull().maxOrNull()

            if (maxEp != null && maxEp > 0) {
                binding.itemBadge.isVisible = true
                binding.itemSubtitle.text = "${context.getString(R.string.subscription_episode_released, maxEp)} • ${item.apiName}"
                binding.itemSubtitle.setTextColor(context.colorFromAttribute(R.attr.colorPrimary))
            } else {
                binding.itemBadge.isVisible = false
                binding.itemSubtitle.text = "${context.getString(R.string.subscription_list_name)} • ${item.apiName}"
                binding.itemSubtitle.setTextColor(context.colorFromAttribute(R.attr.grayTextColor))
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }

            binding.itemDelete.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }

    private class SubscribedDiffCallback : DiffUtil.ItemCallback<SubscribedData>() {
        override fun areItemsTheSame(oldItem: SubscribedData, newItem: SubscribedData): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SubscribedData, newItem: SubscribedData): Boolean {
            return oldItem == newItem
        }
    }
}
