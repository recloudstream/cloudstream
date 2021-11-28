package com.lagradost.cloudstream3.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import kotlinx.android.synthetic.main.homepage_parent.view.*

class ParentItemAdapter(
    var items: List<HomePageList>,
    private val clickCallback: (SearchClickCallback) -> Unit,
    private val moreInfoClickCallback: (HomePageList) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, i: Int): ParentViewHolder {
        val layout = R.layout.homepage_parent
        return ParentViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false), clickCallback, moreInfoClickCallback
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

    class ParentViewHolder
    constructor(
        itemView: View,
        private val clickCallback: (SearchClickCallback) -> Unit,
        private val moreInfoClickCallback: (HomePageList) -> Unit
    ) :
        RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.home_parent_item_title
        val recyclerView: RecyclerView = itemView.home_child_recyclerview
        private val moreInfo: FrameLayout = itemView.home_child_more_info
        fun bind(info: HomePageList) {
            title.text = info.name
            recyclerView.adapter = HomeChildItemAdapter(
                info.list,
                clickCallback = clickCallback,
                nextFocusUp = recyclerView.nextFocusUpId,
                nextFocusDown = recyclerView.nextFocusDownId
            )
            (recyclerView.adapter as HomeChildItemAdapter).notifyDataSetChanged()

            moreInfo.setOnClickListener {
                moreInfoClickCallback.invoke(info)
            }
        }
    }
}