package com.lagradost.cloudstream3.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.homepage_parent.view.*

class ParentItemAdapter(
    var itemList: List<HomePageList>,
    private val clickCallback: (SearchResponse) -> Unit,
    private val moreInfoClickCallback: (List<SearchResponse>) -> Unit,
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
                holder.bind(itemList[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    class ParentViewHolder
    constructor(
        itemView: View,
        private val clickCallback: (SearchResponse) -> Unit,
        private val moreInfoClickCallback: (List<SearchResponse>) -> Unit
    ) :
        RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.home_parent_item_title
        val recyclerView: RecyclerView = itemView.home_child_recyclerview
        private val moreInfo: FrameLayout = itemView.home_child_more_info
        fun bind(info: HomePageList) {
            title.text = info.name
            recyclerView.adapter = HomeChildItemAdapter(info.list, clickCallback)
            (recyclerView.adapter as HomeChildItemAdapter).notifyDataSetChanged()

            moreInfo.setOnClickListener {
                moreInfoClickCallback.invoke(info.list)
            }
        }
    }
}