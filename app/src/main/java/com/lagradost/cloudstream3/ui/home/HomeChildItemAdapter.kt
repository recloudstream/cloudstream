package com.lagradost.cloudstream3.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.HomeResultGridBinding
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.utils.UIHelper.IsBottomLayout
import com.lagradost.cloudstream3.utils.UIHelper.toPx

class HomeChildItemAdapter(
    val cardList: MutableList<SearchResponse>,

    private val nextFocusUp: Int? = null,
    private val nextFocusDown: Int? = null,
    private val clickCallback: (SearchClickCallback) -> Unit,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var isHorizontal: Boolean = false
    var hasNext: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (parent.context.IsBottomLayout()) R.layout.home_result_grid_expanded else R.layout.home_result_grid

        val root = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        val binding = HomeResultGridBinding.bind(root)

        return CardViewHolder(
            binding,
            clickCallback,
            itemCount,
            nextFocusUp,
            nextFocusDown,
            isHorizontal
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> {
                holder.itemCount = itemCount // i know ugly af
                holder.bind(cardList[position], position)
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    override fun getItemId(position: Int): Long {
        return (cardList[position].id ?: position).toLong()
    }

    fun updateList(newList: List<SearchResponse>) {
        val diffResult = DiffUtil.calculateDiff(
            HomeChildDiffCallback(this.cardList, newList)
        )

        cardList.clear()
        cardList.addAll(newList)

        diffResult.dispatchUpdatesTo(this)
    }

    class CardViewHolder
    constructor(
        val binding: HomeResultGridBinding,
        private val clickCallback: (SearchClickCallback) -> Unit,
        var itemCount: Int,
        private val nextFocusUp: Int? = null,
        private val nextFocusDown: Int? = null,
        private val isHorizontal: Boolean = false
    ) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: SearchResponse, position: Int) {

            // TV focus fixing
            val nextFocusBehavior = when (position) {
                0 -> true
                itemCount - 1 -> false
                else -> null
            }

            binding.backgroundCard.apply {
                val min = 114.toPx
                val max = 180.toPx

                layoutParams =
                    layoutParams.apply {
                        width = if (!isHorizontal) {
                            min
                        } else {
                            max
                        }
                        height = if (!isHorizontal) {
                            max
                        } else {
                            min
                        }
                    }
            }


            SearchResultBuilder.bind(
                clickCallback,
                card,
                position,
                itemView,
                nextFocusBehavior,
                nextFocusUp,
                nextFocusDown
            )
            itemView.tag = position

            if (position == 0) { // to fix tv
                binding.backgroundCard.nextFocusLeftId = R.id.nav_rail_view
            }
            //val ani = ScaleAnimation(0.9f, 1.0f, 0.9f, 1f)
            //ani.fillAfter = true
            //ani.duration = 200
            //itemView.startAnimation(ani)
        }
    }
}

class HomeChildDiffCallback(
    private val oldList: List<SearchResponse>,
    private val newList: List<SearchResponse>
) :
    DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition].name == newList[newItemPosition].name

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition] == newList[newItemPosition] && oldItemPosition < oldList.size - 1 // always update the last item
}