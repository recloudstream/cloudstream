package com.lagradost.cloudstream3.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import kotlinx.android.synthetic.main.library_viewpager_page.view.*
import me.xdrop.fuzzywuzzy.FuzzySearch

data class Page(
    val title: String, var items: List<LibraryItem>
) {
    fun sort(method: ListSorting?, query: String? = null) {
        items = when (method) {
            ListSorting.Query ->
                if (query != null) {
                    items.sortedBy {
                        -FuzzySearch.partialRatio(
                            query.lowercase(), it.name.lowercase()
                        )
                    }
                } else items
            ListSorting.RatingHigh -> items.sortedBy { -(it.personalRating ?: 0) }
            ListSorting.RatingLow -> items.sortedBy { (it.personalRating ?: 0) }
            ListSorting.AlphabeticalA -> items.sortedBy { it.name }
            ListSorting.AlphabeticalZ -> items.sortedBy { it.name }.reversed()
            else -> items
        }
    }
}

data class LibraryItem(
    override val name: String,
    override val url: String,
    val listName: String,
    val episodesCompleted: Int?,
    val episodesTotal: Int?,
    /** Out of 100 */
    val personalRating: Int?,
    override val apiName: String,
    override var type: TvType?,
    override var posterUrl: String?,
    override var posterHeaders: Map<String, String>?,
    override var id: Int?,
    override var quality: SearchQuality?,
) : SearchResponse


class ViewpagerAdapter(var pages: List<Page>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return PageViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.library_viewpager_page, parent, false)
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is PageViewHolder -> {
                holder.bind(pages[position])
            }
        }
    }

    inner class PageViewHolder(private val itemViewTest: View) :
        RecyclerView.ViewHolder(itemViewTest) {
        fun bind(page: Page) {
            if (itemViewTest.page_recyclerview?.adapter == null) {
                itemViewTest.page_recyclerview?.adapter = PageAdapter(page.items.toMutableList())
                itemView.page_recyclerview?.spanCount = 4
            } else {
                (itemViewTest.page_recyclerview?.adapter as? PageAdapter)?.updateList(page.items)
                itemViewTest.page_recyclerview?.scrollToPosition(0)
            }
        }
    }

    override fun getItemCount(): Int {
        return pages.size
    }
}