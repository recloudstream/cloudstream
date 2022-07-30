package com.lagradost.cloudstream3.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchFragment.Companion.filterSearchResponse
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import kotlinx.android.synthetic.main.homepage_parent.view.*


class ParentItemAdapter(
    private var items: MutableList<HomeViewModel.ExpandableHomepageList>,
    private val clickCallback: (SearchClickCallback) -> Unit,
    private val moreInfoClickCallback: (HomeViewModel.ExpandableHomepageList) -> Unit,
    private val expandCallback: ((String) -> Unit)? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, i: Int): ParentViewHolder {
        //println("onCreateViewHolder $i")
        val layout =
            if (parent.context.isTvSettings()) R.layout.homepage_parent_tv else R.layout.homepage_parent
        return ParentViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            clickCallback,
            moreInfoClickCallback,
            expandCallback
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        //println("onBindViewHolder $position")

        when (holder) {
            is ParentViewHolder -> {
                holder.bind(items[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemId(position: Int): Long {
        return items[position].list.name.hashCode().toLong()
    }

    @JvmName("updateListHomePageList")
    fun updateList(newList: List<HomePageList>) {
        updateList(newList.map { HomeViewModel.ExpandableHomepageList(it, 1, false) }
            .toMutableList())
    }

    @JvmName("updateListExpandableHomepageList")
    fun updateList(
        newList: MutableList<HomeViewModel.ExpandableHomepageList>,
        recyclerView: RecyclerView? = null
    ) {
        // this
        // 1. prevents deep copy that makes this.items == newList
        // 2. filters out undesirable results
        // 3. moves empty results to the bottom (sortedBy is a stable sort)
        val new =
            newList.map { it.copy(list = it.list.copy(list = it.list.list.filterSearchResponse())) }
                .sortedBy { it.list.list.isEmpty() }

        val diffResult = DiffUtil.calculateDiff(
            SearchDiffCallback(items, new)
        )
        items.clear()
        items.addAll(new)

        val mAdapter = this
        diffResult.dispatchUpdatesTo(object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) {
                mAdapter.notifyItemRangeInserted(position, count)
            }

            override fun onRemoved(position: Int, count: Int) {
                mAdapter.notifyItemRangeRemoved(position, count)
            }

            override fun onMoved(fromPosition: Int, toPosition: Int) {
                mAdapter.notifyItemMoved(fromPosition, toPosition)
            }

            override fun onChanged(position: Int, count: Int, payload: Any?) {
                // I know kinda messy, what this does is using the update or bind instead of onCreateViewHolder -> bind
                recyclerView?.apply {
                    // this loops every viewHolder in the recycle view and checks the position to see if it is within the update range
                    val missingUpdates = (position until (position + count)).toMutableSet()
                    for (i in 0 until mAdapter.itemCount) {
                        val viewHolder = getChildViewHolder(getChildAt(i))
                        val absolutePosition = viewHolder.absoluteAdapterPosition
                        if (absolutePosition >= position && absolutePosition < position + count) {
                            val expand = items.getOrNull(absolutePosition) ?: continue
                            if (viewHolder is ParentViewHolder) {
                                missingUpdates -= absolutePosition
                                if (viewHolder.title.text == expand.list.name) {
                                    viewHolder.update(expand)
                                } else {
                                    viewHolder.bind(expand)
                                }
                            }
                        }
                    }

                    // just in case some item did not get updated
                    for (i in missingUpdates) {
                        mAdapter.notifyItemChanged(i, payload)
                    }
                } ?: run { // in case we don't have a nice
                    mAdapter.notifyItemRangeChanged(position, count, payload)
                }
            }
        })

        //diffResult.dispatchUpdatesTo(this)
    }

    class ParentViewHolder
    constructor(
        itemView: View,
        private val clickCallback: (SearchClickCallback) -> Unit,
        private val moreInfoClickCallback: (HomeViewModel.ExpandableHomepageList) -> Unit,
        private val expandCallback: ((String) -> Unit)? = null,
    ) :
        RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.home_parent_item_title
        val recyclerView: RecyclerView = itemView.home_child_recyclerview
        private val moreInfo: FrameLayout? = itemView.home_child_more_info

        fun update(expand: HomeViewModel.ExpandableHomepageList) {
            val info = expand.list
            (recyclerView.adapter as? HomeChildItemAdapter?)?.apply {
                updateList(info.list.toMutableList())
                hasNext = expand.hasNext
            } ?: run {
                recyclerView.adapter = HomeChildItemAdapter(
                    info.list.toMutableList(),
                    clickCallback = clickCallback,
                    nextFocusUp = recyclerView.nextFocusUpId,
                    nextFocusDown = recyclerView.nextFocusDownId,
                ).apply {
                    isHorizontal = info.isHorizontalImages
                }
            }
        }

        fun bind(expand: HomeViewModel.ExpandableHomepageList) {
            val info = expand.list
            recyclerView.adapter = HomeChildItemAdapter(
                info.list.toMutableList(),
                clickCallback = clickCallback,
                nextFocusUp = recyclerView.nextFocusUpId,
                nextFocusDown = recyclerView.nextFocusDownId,
            ).apply {
                isHorizontal = info.isHorizontalImages
                hasNext = expand.hasNext
            }

            title.text = info.name

            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                var expandCount = 0
                val name = expand.list.name

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)

                    val adapter = recyclerView.adapter
                    if (adapter !is HomeChildItemAdapter) return

                    val count = adapter.itemCount
                    val hasNext = adapter.hasNext
                    if (!recyclerView.canScrollHorizontally(1) && hasNext && expandCount != count) {
                        expandCount = count
                        expandCallback?.invoke(name)
                    }
                }
            })

            //(recyclerView.adapter as HomeChildItemAdapter).notifyDataSetChanged()

            moreInfo?.setOnClickListener {
                moreInfoClickCallback.invoke(expand)
            }
        }
    }
}

class SearchDiffCallback(
    private val oldList: List<HomeViewModel.ExpandableHomepageList>,
    private val newList: List<HomeViewModel.ExpandableHomepageList>
) :
    DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition].list.name == newList[newItemPosition].list.name

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        oldList[oldItemPosition] == newList[newItemPosition]
    //{
    //    val ret = oldList[oldItemPosition].list.list.size == newList[newItemPosition].list.list.size
    //    println(">>>>>>>>>>>>>>>> $ret ${oldList[oldItemPosition].list.list.size} == ${newList[newItemPosition].list.list.size}")
    //    return ret
    //}
}