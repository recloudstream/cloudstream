package com.lagradost.cloudstream3.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import kotlinx.android.synthetic.main.homepage_parent.view.*


class ParentItemAdapter(
    private var items: MutableList<HomeViewModel.ExpandableHomepageList>,
    private val clickCallback: (SearchClickCallback) -> Unit,
    private val moreInfoClickCallback: (HomePageList) -> Unit,
    private val expandCallback: ((String) -> Unit)? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, i: Int): ParentViewHolder {
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
    fun updateList(newList: MutableList<HomeViewModel.ExpandableHomepageList>) {
        val diffResult = DiffUtil.calculateDiff(
            SearchDiffCallback(items, newList)
        )
        items.clear()
        items.addAll(newList.map { it.copy(list = it.list.copy()) }) // I have to do this otherwise it is a "copy" and dispatchUpdatesTo wont work

        /*val mAdapter = this
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
                mAdapter.notifyItemRangeChanged(position, count, payload)
            }
        })*/

        diffResult.dispatchUpdatesTo(this)
    }

    class ParentViewHolder
    constructor(
        itemView: View,
        private val clickCallback: (SearchClickCallback) -> Unit,
        private val moreInfoClickCallback: (HomePageList) -> Unit,
        private val expandCallback: ((String) -> Unit)? = null,
    ) :
        RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.home_parent_item_title
        val recyclerView: RecyclerView = itemView.home_child_recyclerview
        private val moreInfo: FrameLayout? = itemView.home_child_more_info

        fun bind(expand: HomeViewModel.ExpandableHomepageList) {
            val info = expand.list
            recyclerView.adapter = HomeChildItemAdapter(
                info.list.toMutableList(),
                clickCallback = clickCallback,
                nextFocusUp = recyclerView.nextFocusUpId,
                nextFocusDown = recyclerView.nextFocusDownId,
            ).apply {
                isHorizontal = info.isHorizontalImages
            }

            title.text = info.name

            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                var expandCount = 0
                val name = expand.list.name

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)

                    val count = recyclerView.adapter?.itemCount ?: return
                    if (!recyclerView.canScrollHorizontally(1) && expand.hasNext && expandCount != count) {
                        expandCount = count
                        expandCallback?.invoke(name)
                    }
                }
            })

            //(recyclerView.adapter as HomeChildItemAdapter).notifyDataSetChanged()

            moreInfo?.setOnClickListener {
                moreInfoClickCallback.invoke(info)
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