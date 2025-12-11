package com.lagradost.cloudstream3.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import com.lagradost.cloudstream3.databinding.SearchSuggestionFooterBinding
import com.lagradost.cloudstream3.databinding.SearchSuggestionItemBinding
import com.lagradost.cloudstream3.ui.BaseAdapter
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout

const val SEARCH_SUGGESTION_CLICK = 0
const val SEARCH_SUGGESTION_FILL = 1
const val SEARCH_SUGGESTION_CLEAR = 2

data class SearchSuggestionCallback(
    val suggestion: String,
    val clickAction: Int,
)

class SearchSuggestionAdapter(
    private val clickCallback: (SearchSuggestionCallback) -> Unit,
) : BaseAdapter<String, Any>(diffCallback = BaseDiffCallback(itemSame = { a, b -> a == b })) {
    
    // Add footer for TV and EMULATOR layouts only
    override val footers = if (isLayout(TV or EMULATOR)) 1 else 0
    
    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            SearchSuggestionItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )
    }

    override fun onBindContent(
        holder: ViewHolderState<Any>,
        item: String,
        position: Int
    ) {
        val binding = holder.view as? SearchSuggestionItemBinding ?: return
        binding.apply {
            suggestionText.text = item
            
            // Click on the whole item to search
            suggestionItem.setOnClickListener {
                clickCallback.invoke(SearchSuggestionCallback(item, SEARCH_SUGGESTION_CLICK))
            }
            
            // Click on the arrow to fill the search box without searching
            suggestionFill.setOnClickListener {
                clickCallback.invoke(SearchSuggestionCallback(item, SEARCH_SUGGESTION_FILL))
            }
        }
    }
    
    override fun onCreateFooter(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            SearchSuggestionFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }
    
    override fun onBindFooter(holder: ViewHolderState<Any>) {
        val binding = holder.view as? SearchSuggestionFooterBinding ?: return
        binding.clearSuggestionsButton.apply {
            if (isLayout(TV or EMULATOR)) {
                isFocusable = true
                isFocusableInTouchMode = true
            }
            setOnClickListener {
                clickCallback.invoke(SearchSuggestionCallback("", SEARCH_SUGGESTION_CLEAR))
            }
        }
    }
}
