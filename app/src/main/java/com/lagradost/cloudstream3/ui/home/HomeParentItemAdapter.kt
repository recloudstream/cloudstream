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
    private var items: MutableList<HomePageList>,
    private val clickCallback: (SearchClickCallback) -> Unit,
    private val moreInfoClickCallback: (HomePageList) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, i: Int): ParentViewHolder {
        val layout =
            if (parent.context.isTvSettings()) R.layout.homepage_parent_tv else R.layout.homepage_parent
        return ParentViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            clickCallback,
            moreInfoClickCallback
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
        return items[position].name.hashCode().toLong()
    }

    fun updateList(newList: List<HomePageList>) {
        // this moves all bad results to the bottom
        val endList = mutableListOf<HomePageList>()
        val newFilteredList = mutableListOf<HomePageList>()
        for (item in newList) {
            if (item.list.isEmpty()) {
                endList.add(item)
            } else {
                newFilteredList.add(item)
            }
        }
        newFilteredList.addAll(endList)

        val diffResult = DiffUtil.calculateDiff(
            SearchDiffCallback(this.items, newFilteredList)
        )

        items.clear()
        items.addAll(newFilteredList)

        diffResult.dispatchUpdatesTo(this)
    }

    class ParentViewHolder
    constructor(
        itemView: View,
        private val clickCallback: (SearchClickCallback) -> Unit,
        private val moreInfoClickCallback: (HomePageList) -> Unit
    ) :
        RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.home_parent_item_title
        val recyclerView: RecyclerView = itemView.home_child_recyclerview
        private val moreInfo: FrameLayout? = itemView.home_child_more_info
        fun bind(info: HomePageList) {
            title.text = info.name
            recyclerView.adapter = HomeChildItemAdapter(
                info.list.toMutableList(),
                clickCallback = clickCallback,
                nextFocusUp = recyclerView.nextFocusUpId,
                nextFocusDown = recyclerView.nextFocusDownId
            )
            //(recyclerView.adapter as HomeChildItemAdapter).notifyDataSetChanged()

            moreInfo?.setOnClickListener {
                moreInfoClickCallback.invoke(info)
            }
        }
    }
}

class SearchDiffCallback(
    private val oldList: List<HomePageList>,
    private val newList: List<HomePageList>
) :
    DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition].name == newList[newItemPosition].name

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition] == newList[newItemPosition]
}