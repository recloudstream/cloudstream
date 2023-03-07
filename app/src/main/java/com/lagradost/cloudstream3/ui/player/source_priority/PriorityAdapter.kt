package com.lagradost.cloudstream3.ui.player.source_priority

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.AppUtils
import kotlinx.android.synthetic.main.player_prioritize_item.view.*

data class SourcePriority<T>(
    val data: T,
    val name: String,
    var priority: Int
)

class PriorityAdapter<T>(override val items: MutableList<SourcePriority<T>>) :
    AppUtils.DiffAdapter<SourcePriority<T>>(items) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return PriorityViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.player_prioritize_item, parent, false)
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is PriorityViewHolder -> holder.bind(items[position])
        }
    }

    class PriorityViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        fun <T> bind(item: SourcePriority<T>) {
            val plusButton: ImageView = itemView.add_button
            val subtractButton: ImageView = itemView.subtract_button
            val priorityText: TextView = itemView.priority_text
            val priorityNumber: TextView = itemView.priority_number
            priorityText.text = item.name

            fun updatePriority() {
                priorityNumber.text = item.priority.toString()
            }

            updatePriority()
            plusButton.setOnClickListener {
                // If someone clicks til the integer limit then they deserve to crash.
                item.priority++
                updatePriority()
            }

            subtractButton.setOnClickListener {
                item.priority--
                updatePriority()
            }
        }
    }
}