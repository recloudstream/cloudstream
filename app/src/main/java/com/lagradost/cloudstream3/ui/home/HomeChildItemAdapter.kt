package com.lagradost.cloudstream3.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder

class HomeChildItemAdapter(
    var cardList: List<SearchResponse>,
    val layout: Int = R.layout.home_result_grid,
    private val clickCallback: (SearchClickCallback) -> Unit
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false), clickCallback
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

    override fun getItemId(position: Int): Long {
        return (cardList[position].id ?: position).toLong()
    }

    class CardViewHolder
    constructor(itemView: View, private val clickCallback: (SearchClickCallback) -> Unit) :
        RecyclerView.ViewHolder(itemView) {

        fun bind(card: SearchResponse, index: Int) {
            SearchResultBuilder.bind(clickCallback, card, itemView)
            itemView.tag = index
            //val ani = ScaleAnimation(0.9f, 1.0f, 0.9f, 1f)
            //ani.fillAfter = true
            //ani.duration = 200
            //itemView.startAnimation(ani)
        }
    }
}
