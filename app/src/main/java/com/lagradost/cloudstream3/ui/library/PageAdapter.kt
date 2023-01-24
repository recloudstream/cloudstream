package com.lagradost.cloudstream3.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import kotlinx.android.synthetic.main.search_result_grid_expanded.view.*
import kotlin.math.roundToInt

class PageAdapter(
    override val items: MutableList<SyncAPI.LibraryItem>,
    private val resView: AutofitRecyclerView,
    val clickCallback: (SearchClickCallback) -> Unit
) :
    AppUtils.DiffAdapter<SyncAPI.LibraryItem>(items) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return LibraryItemViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.search_result_grid_expanded, parent, false)
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is LibraryItemViewHolder -> {
                holder.bind(items[position], position)
            }
        }
    }

    inner class LibraryItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: ImageView = itemView.imageView

        private val compactView = false//itemView.context.getGridIsCompact()
        private val coverHeight: Int =
            if (compactView) 80.toPx else (resView.itemWidth / 0.68).roundToInt()

        fun bind(item: SyncAPI.LibraryItem, position: Int) {
            SearchResultBuilder.bind(
                this@PageAdapter.clickCallback,
                item,
                position,
                itemView,
            )

            // See searchAdaptor for this, it basically fixes the height
            if (!compactView) {
                cardView.apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        coverHeight
                    )
                }
            }

            val showProgress = item.episodesCompleted != null && item.episodesTotal != null
            itemView.watchProgress.isVisible = showProgress
            if (showProgress) {
                itemView.watchProgress.max = item.episodesTotal!!
                itemView.watchProgress.progress = item.episodesCompleted!!
            }

            itemView.imageText.text = item.name

            val showRating = (item.personalRating ?: 0) != 0
            itemView.text_rating.isVisible = showRating
            if (showRating) {
                itemView.text_rating.text = item.personalRating.toString()
            }
        }
    }
}