package com.lagradost.cloudstream3.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.HomeResultGridBinding
import com.lagradost.cloudstream3.databinding.HomeResultGridExpandedBinding
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.utils.AppUtils.isRtl
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
        val expanded = parent.context.IsBottomLayout()
        /* val layout = if (bottom) R.layout.home_result_grid_expanded else R.layout.home_result_grid

         val root = LayoutInflater.from(parent.context).inflate(layout, parent, false)
         val binding = HomeResultGridBinding.bind(root)*/

        val inflater = LayoutInflater.from(parent.context)
        val binding = if (expanded) HomeResultGridExpandedBinding.inflate(
            inflater,
            parent,
            false
        ) else HomeResultGridBinding.inflate(inflater, parent, false)


        return CardViewHolder(
            binding,
            clickCallback,
            itemCount,
            nextFocusUp,
            nextFocusDown,
            isHorizontal,
            parent.isRtl()
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
        val binding: ViewBinding,
        private val clickCallback: (SearchClickCallback) -> Unit,
        var itemCount: Int,
        private val nextFocusUp: Int? = null,
        private val nextFocusDown: Int? = null,
        private val isHorizontal: Boolean = false,
        private val isRtl: Boolean
    ) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: SearchResponse, position: Int) {

            // TV focus fixing
            /*val nextFocusBehavior = when (position) {
                0 -> true
                itemCount - 1 -> false
                else -> null
            }

            if (position == 0) { // to fix tv
                if (isRtl) {
                    itemView.nextFocusRightId = R.id.nav_rail_view
                    itemView.nextFocusLeftId = -1
                }
                else {
                    itemView.nextFocusLeftId = R.id.nav_rail_view
                    itemView.nextFocusRightId = -1
                }
            } else {
                itemView.nextFocusRightId = -1
                itemView.nextFocusLeftId = -1
            }*/


            when (binding) {
                is HomeResultGridBinding -> {
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


                }

                is HomeResultGridExpandedBinding -> {
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

                    if (position == 0) { // to fix tv
                        binding.backgroundCard.nextFocusLeftId = R.id.nav_rail_view
                    }
                }
            }

            SearchResultBuilder.bind(
                clickCallback,
                card,
                position,
                itemView,
                null, // nextFocusBehavior,
                nextFocusUp,
                nextFocusDown
            )
            itemView.tag = position

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