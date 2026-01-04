package com.lagradost.cloudstream3.ui.result

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.databinding.ResultSelectionBinding
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.utils.setText

typealias SelectData = Pair<UiText?, Any>

class SelectAdaptor(val callback: (Any) -> Unit) :
    NoStateAdapter<SelectData>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
        a.second == b.second
    }, contentSame = { a, b ->
        a == b
    })) {
    private var selectedIndex: Int = -1

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            ResultSelectionBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: SelectData, position: Int) {
        when (val binding = holder.view) {
            is ResultSelectionBinding -> {
                binding.root.apply {
                    if (isLayout(TV)) {
                        isFocusable = true
                        isFocusableInTouchMode = true
                    }

                    isSelected = position == selectedIndex
                    setText(item.first)
                    setOnClickListener {
                        callback.invoke(item.second)
                    }
                }
            }
        }
    }

    override fun onViewDetachedFromWindow(holder: ViewHolderState<Any>) {
        if (holder.itemView.hasFocus()) {
            holder.itemView.clearFocus()
        }
    }

    fun select(newIndex: Int, recyclerView: RecyclerView?) {
        if (recyclerView == null) return
        if (newIndex == selectedIndex) return
        val oldIndex = selectedIndex
        selectedIndex = newIndex

        notifyItemChanged(selectedIndex)
        notifyItemChanged(oldIndex)
    }
}
