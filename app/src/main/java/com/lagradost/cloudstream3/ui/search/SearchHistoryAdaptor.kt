package com.lagradost.cloudstream3.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.SearchHistoryFooterBinding
import com.lagradost.cloudstream3.databinding.SearchHistoryItemBinding
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout

data class SearchHistoryItem(
    @JsonProperty("searchedAt") val searchedAt: Long,
    @JsonProperty("searchText") val searchText: String,
    @JsonProperty("type") val type: List<TvType>,
    @JsonProperty("key") val key: String,
)

data class SearchHistoryCallback(
    val item: SearchHistoryItem?,
    val clickAction: Int,
)

const val SEARCH_HISTORY_OPEN = 0
const val SEARCH_HISTORY_REMOVE = 1
const val SEARCH_HISTORY_CLEAR = 2

class SearchHistoryAdaptor(
    private val clickCallback: (SearchHistoryCallback) -> Unit,
) : NoStateAdapter<SearchHistoryItem>(diffCallback = BaseDiffCallback(itemSame = { a,b ->
    a.searchedAt == b.searchedAt && a.searchText == b.searchText
})) {
    
    // Add footer for all layouts
    override val footers = 1
    
    override fun submitList(list: Collection<SearchHistoryItem>?, commitCallback: Runnable?) {
        super.submitList(list, commitCallback)
        // Notify footer to rebind when list changes to update visibility
        if (footers > 0) {
            notifyItemChanged(itemCount - 1)
        }
    }
    
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
    
    override fun onCreateFooter(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            SearchHistoryFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }
    
    override fun onBindFooter(holder: ViewHolderState<Any>) {
        val binding = holder.view as? SearchHistoryFooterBinding ?: return
        // Hide footer when list is empty
        binding.searchClearCallHistory.apply {
            isGone = immutableCurrentList.isEmpty()
            if (isLayout(TV or EMULATOR)) {
                isFocusable = true
                isFocusableInTouchMode = true
            }
            setOnClickListener {
                clickCallback.invoke(SearchHistoryCallback(null, SEARCH_HISTORY_CLEAR))
            }
        }
    }
}
