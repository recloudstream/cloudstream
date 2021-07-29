package com.lagradost.cloudstream3.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private val clickCallback: (SearchResponse) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, i: Int): ParentViewHolder {
        val layout = R.layout.homepage_parent
        return ParentViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false), clickCallback
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
    constructor(itemView: View, private val clickCallback: (SearchResponse) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.home_parent_item_title
        val recyclerView: RecyclerView = itemView.home_child_recyclerview
        fun bind(info: HomePageList) {
            title.text = info.name
            recyclerView.adapter = HomeChildItemAdapter(info.list, clickCallback)
            recyclerView.layoutManager = GridLayoutManager(itemView.context, 1)
            (recyclerView.adapter as HomeChildItemAdapter).notifyDataSetChanged()
        }
    }

}