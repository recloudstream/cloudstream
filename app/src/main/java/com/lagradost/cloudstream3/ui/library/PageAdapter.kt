package com.lagradost.cloudstream3.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.utils.AppUtils
import kotlinx.android.synthetic.main.search_result_grid_expanded.view.*

class PageAdapter(
    override val items: MutableList<LibraryItem>,
    val clickCallback: (SearchClickCallback) -> Unit
) :
    AppUtils.DiffAdapter<LibraryItem>(items) {

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
        fun bind(item: LibraryItem, position: Int) {
            SearchResultBuilder.bind(
                this@PageAdapter.clickCallback,
                item,
                position,
                itemView,
            )

            // Set watch progress bar
//            val showProgress = item.episodesCompleted != null && item.episodesTotal != null
//            itemView.watchProgress.isVisible = showProgress
//
//            if (showProgress) {
//                itemView.watchProgress.max = item.episodesTotal!!
//                itemView.watchProgress.progress = item.episodesCompleted!!
//            }
            itemView.imageText.text = item.name
            val showRating = (item.personalRating ?: 0) != 0
            itemView.text_rating.isVisible = showRating
            if (showRating) {
                itemView.text_rating.text = item.personalRating.toString()
            }
        }
    }
}