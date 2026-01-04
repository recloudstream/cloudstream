package com.lagradost.cloudstream3.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.SearchHistoryItemBinding
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState

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
    private val clickCallback: (SearchHistoryCallback) -> Unit,
) : NoStateAdapter<SearchHistoryItem>(diffCallback = BaseDiffCallback(itemSame = { a,b ->
    a.searchedAt == b.searchedAt && a.searchText == b.searchText
})) {
    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            SearchHistoryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )
    }

    override fun onBindContent(
        holder: ViewHolderState<Any>,
        item: SearchHistoryItem,
        position: Int
    ) {
        val binding = holder.view as? SearchHistoryItemBinding ?: return
        binding.apply {
            homeHistoryTitle.text = item.searchText

            homeHistoryRemove.setOnClickListener {
                clickCallback.invoke(SearchHistoryCallback(item, SEARCH_HISTORY_REMOVE))
            }
            homeHistoryTab.setOnClickListener {
                clickCallback.invoke(SearchHistoryCallback(item, SEARCH_HISTORY_OPEN))
            }
        }
    }
}
