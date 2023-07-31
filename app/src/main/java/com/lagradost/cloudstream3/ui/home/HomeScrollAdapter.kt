package com.lagradost.cloudstream3.ui.home

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.databinding.HomeScrollViewBinding
import com.lagradost.cloudstream3.databinding.HomeScrollViewTvBinding
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.UIHelper.setImage

class HomeScrollAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var items: MutableList<LoadResponse> = mutableListOf()
    var hasMoreItems: Boolean = false

    fun getItem(position: Int): LoadResponse? {
        return items.getOrNull(position)
    }

    fun setItems(newItems: List<LoadResponse>, hasNext: Boolean): Boolean {
        val isSame = newItems.firstOrNull()?.url == items.firstOrNull()?.url
        hasMoreItems = hasNext

        val diffResult = DiffUtil.calculateDiff(
            HomeScrollDiffCallback(this.items, newItems)
        )

        items.clear()
        items.addAll(newItems)


        diffResult.dispatchUpdatesTo(this)

        return isSame
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (isTvSettings()) {
            HomeScrollViewTvBinding.inflate(inflater, parent, false)
        } else {
            HomeScrollViewBinding.inflate(inflater, parent, false)
        }

        return CardViewHolder(
            binding,
            //forceHorizontalPosters
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> {
                holder.bind(items[position])
            }
        }
    }

    class CardViewHolder
    constructor(
        val binding: ViewBinding,
        //private val forceHorizontalPosters: Boolean? = null
    ) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: LoadResponse) {
            val isHorizontal =
                binding is HomeScrollViewTvBinding || itemView.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            val posterUrl =
                if (isHorizontal) card.backgroundPosterUrl ?: card.posterUrl else card.posterUrl
                    ?: card.backgroundPosterUrl

            when (binding) {
                is HomeScrollViewBinding -> {
                    binding.homeScrollPreview.setImage(posterUrl)
                    binding.homeScrollPreviewTags.apply {
                        text = card.tags?.joinToString(" • ") ?: ""
                        isGone = card.tags.isNullOrEmpty()
                    }
                    binding.homeScrollPreviewTitle.text = card.name
                }

                is HomeScrollViewTvBinding -> {
                    binding.homeScrollPreview.setImage(posterUrl)
                }
            }
        }
    }

    class HomeScrollDiffCallback(
        private val oldList: List<LoadResponse>,
        private val newList: List<LoadResponse>
    ) :
        DiffUtil.Callback() {
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldList[oldItemPosition].url == newList[newItemPosition].url

        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldList[oldItemPosition] == newList[newItemPosition]
    }

    override fun getItemCount(): Int {
        return items.size
    }
}