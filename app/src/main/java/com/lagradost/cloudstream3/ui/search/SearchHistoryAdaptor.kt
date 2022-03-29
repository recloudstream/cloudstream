package com.lagradost.cloudstream3.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import kotlinx.android.synthetic.main.search_history_item.view.*

data class SearchHistoryItem(
    @JsonProperty("searchedAt") val searchedAt: Long,
    @JsonProperty("searchText") val searchText: String,
    @JsonProperty("type") val type: List<TvType>,
    @JsonProperty("key") val key: String,
)

data class SearchHistoryCallback(
    val item: SearchHistoryItem,
    val clickAction: Int,
)

const val SEARCH_HISTORY_OPEN = 0
const val SEARCH_HISTORY_REMOVE = 1

class SearchHistoryAdaptor(
    private val cardList: MutableList<SearchHistoryItem>,
    private val clickCallback: (SearchHistoryCallback) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return CardViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.search_history_item, parent, false),
            clickCallback,
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> {
                holder.bind(cardList[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    fun updateList(newList: List<SearchHistoryItem>) {
        val diffResult = DiffUtil.calculateDiff(
            SearchHistoryDiffCallback(this.cardList, newList)
        )

        cardList.clear()
        cardList.addAll(newList)

        diffResult.dispatchUpdatesTo(this)
    }

    class CardViewHolder
    constructor(
        itemView: View,
        private val clickCallback: (SearchHistoryCallback) -> Unit,
    ) :
        RecyclerView.ViewHolder(itemView) {
        private val removeButton: ImageView = itemView.home_history_remove
        private val openButton: View = itemView.home_history_tab
        private val title: TextView = itemView.home_history_title

        fun bind(card: SearchHistoryItem) {
            title.text = card.searchText

            removeButton.setOnClickListener {
                clickCallback.invoke(SearchHistoryCallback(card, SEARCH_HISTORY_REMOVE))
            }
            openButton.setOnClickListener {
                clickCallback.invoke(SearchHistoryCallback(card, SEARCH_HISTORY_OPEN))
            }
        }
    }
}

class SearchHistoryDiffCallback(
    private val oldList: List<SearchHistoryItem>,
    private val newList: List<SearchHistoryItem>
) :
    DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition].searchText == newList[newItemPosition].searchText

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition] == newList[newItemPosition]
}