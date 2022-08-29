package com.lagradost.cloudstream3.ui.result

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings

typealias SelectData = Pair<UiText?, Any>

class SelectAdaptor(val callback: (Any) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val selection: MutableList<SelectData> = mutableListOf()
    private var selectedIndex: Int = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return SelectViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.result_selection, parent, false),
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SelectViewHolder -> {
                holder.bind(selection[position], position == selectedIndex, callback)
            }
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if(holder.itemView.hasFocus()) {
            holder.itemView.clearFocus()
        }
    }

    override fun getItemCount(): Int {
        return selection.size
    }

    fun select(newIndex: Int, recyclerView: RecyclerView?) {
        if(recyclerView == null) return
        if(newIndex == selectedIndex) return
        val oldIndex = selectedIndex
        selectedIndex = newIndex
        recyclerView.apply {
            for (i in 0 until itemCount) {
                val viewHolder = getChildViewHolder( getChildAt(i) ?: continue) ?: continue
                val pos = viewHolder.absoluteAdapterPosition
                if (viewHolder is SelectViewHolder) {
                    if (pos == oldIndex) {
                        viewHolder.update(false)
                    } else if (pos == newIndex) {
                        viewHolder.update(true)
                    }
                }
            }
        }
    }

    fun updateSelectionList(newList: List<SelectData>) {
        val diffResult = DiffUtil.calculateDiff(
            SelectDataCallback(this.selection, newList)
        )

        selection.clear()
        selection.addAll(newList)

        diffResult.dispatchUpdatesTo(this)
    }


    private class SelectViewHolder
    constructor(
        itemView: View,
    ) :
        RecyclerView.ViewHolder(itemView) {
        private val item: MaterialButton = itemView as MaterialButton

        fun update(isSelected: Boolean) {
            item.isSelected = isSelected
        }

        fun bind(
            data: SelectData, isSelected: Boolean, callback: (Any) -> Unit
        ) {
            val isTrueTv = isTrueTvSettings()
            if (isTrueTv) {
                item.isFocusable = true
                item.isFocusableInTouchMode = true
            }

            item.isSelected = isSelected
            item.setText(data.first)
            item.setOnClickListener {
                callback.invoke(data.second)
            }
        }
    }
}

class SelectDataCallback(
    private val oldList: List<SelectData>,
    private val newList: List<SelectData>
) :
    DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition].second == newList[newItemPosition].second

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition] == newList[newItemPosition]
}