package com.lagradost.cloudstream3.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.SearchResultGridBinding
import com.lagradost.cloudstream3.databinding.SearchResultGridExpandedBinding
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.utils.UIHelper.isBottomLayout
import com.lagradost.cloudstream3.utils.UIHelper.toPx
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
    private var cardList: List<SearchResponse>,
    private val resView: AutofitRecyclerView,
    private val clickCallback: (SearchClickCallback) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var hasNext: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
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
            ) //R.layout.search_result_grid_expanded else R.layout.search_result_grid



        return CardViewHolder(
            layout,
            clickCallback,
            resView
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> {
                holder.bind(cardList[position], position)
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    fun updateList(newList: List<SearchResponse>) {
        val diffResult = DiffUtil.calculateDiff(
            SearchResponseDiffCallback(cardList, newList)
        )
        cardList = newList
        diffResult.dispatchUpdatesTo(this)
    }

    class CardViewHolder(
        val binding: ViewBinding,
        private val clickCallback: (SearchClickCallback) -> Unit,
        resView: AutofitRecyclerView
    ) :
        RecyclerView.ViewHolder(binding.root) {

        private val compactView = false//itemView.context.getGridIsCompact()
        private val coverHeight: Int =
            if (compactView) 80.toPx else (resView.itemWidth / 0.68).roundToInt()

        private val cardView = when(binding) {
            is SearchResultGridExpandedBinding -> binding.imageView
            is SearchResultGridBinding -> binding.imageView
            else -> null
        }

        fun bind(card: SearchResponse, position: Int) {
            if (!compactView) {
                cardView?.apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        coverHeight
                    )
                }
            }

            SearchResultBuilder.bind(clickCallback, card, position, itemView)
        }
    }
}

class SearchResponseDiffCallback(
    private val oldList: List<SearchResponse>,
    private val newList: List<SearchResponse>
) :
    DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition].name == newList[newItemPosition].name

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition] == newList[newItemPosition]
}