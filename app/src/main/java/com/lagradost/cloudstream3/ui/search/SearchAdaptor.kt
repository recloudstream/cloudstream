package com.lagradost.cloudstream3.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.SearchResultGridBinding
import com.lagradost.cloudstream3.databinding.SearchResultGridExpandedBinding
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.newSharedPool
import com.lagradost.cloudstream3.utils.UIHelper.isBottomLayout
import kotlin.math.roundToInt

/** Click */
const val SEARCH_ACTION_LOAD = 0

/** Long press */
const val SEARCH_ACTION_SHOW_METADATA = 1
const val SEARCH_ACTION_PLAY_FILE = 2
const val SEARCH_ACTION_FOCUSED = 4

class SearchClickCallback(
    val action: Int,
    val view: View,
    val position: Int,
    val card: SearchResponse
)

class SearchAdapter(
    private val resView: AutofitRecyclerView,
    var isHorizontal: Boolean = false,
    private val clickCallback: (SearchClickCallback) -> Unit,
) : NoStateAdapter<SearchResponse>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
    if (a.id != null || b.id != null) {
        a.id == b.id
    } else {
        a.name == b.name
    }
})) {
    companion object {
        val sharedPool =
            newSharedPool { setMaxRecycledViews(CONTENT, 10) }
    }

    var hasNext: Boolean = false

    val coverRatio: Double get() = if (isHorizontal) 1.8 else 0.68

    private val coverHeight: Int get() = (resView.itemWidth / coverRatio).roundToInt()

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        val inflater = LayoutInflater.from(parent.context)

        val layout =
            if (parent.context.isBottomLayout()) SearchResultGridExpandedBinding.inflate(
                inflater,
                parent,
                false
            ) else SearchResultGridBinding.inflate(
                inflater,
                parent,
                false
            )
        return ViewHolderState(layout)
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        clearImage(
            when (val binding = holder.view) {
                is SearchResultGridExpandedBinding -> binding.imageView
                is SearchResultGridBinding -> binding.imageView
                else -> null
            }
        )
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: SearchResponse, position: Int) {
        val isCardHorizontal = isHorizontal ||
            item.type == TvType.Live ||
            item is LiveSearchResponse ||
            !item.backgroundPosterUrl.isNullOrEmpty() ||
            APIHolder.isApiHorizontal(item.apiName)

        val currentRatio = if (isCardHorizontal) 1.8 else 0.68
        val currentCoverHeight = (resView.itemWidth / currentRatio).roundToInt()

        val imageView = when (val binding = holder.view) {
            is SearchResultGridExpandedBinding -> binding.imageView
            is SearchResultGridBinding -> binding.imageView
            else -> null
        }

        if (imageView != null) {
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                currentCoverHeight
            )
            if (imageView.layoutParams.width != params.width || imageView.layoutParams.height != params.height) {
                imageView.layoutParams = params
            }
        }
        SearchResultBuilder.bind(clickCallback, item, position, holder.view.root, isHorizontal = isCardHorizontal)
    }
}