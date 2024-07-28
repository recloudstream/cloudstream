package com.lagradost.cloudstream3.ui

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView


/**
 * ListUpdateCallback that dispatches update events to the given adapter.
 *
 * @see DiffUtil.DiffResult.dispatchUpdatesTo
 */
open class NonFinalAdapterListUpdateCallback
/**
 * Creates an AdapterListUpdateCallback that will dispatch update events to the given adapter.
 *
 * @param mAdapter The Adapter to send updates to.
 */(private var mAdapter: RecyclerView.Adapter<*>) :
    ListUpdateCallback {

        override fun onInserted(position: Int, count: Int) {
        mAdapter.notifyItemRangeInserted(position, count)
    }

    override fun onRemoved(position: Int, count: Int) {
        mAdapter.notifyItemRangeRemoved(position, count)
    }

    override fun onMoved(fromPosition: Int, toPosition: Int) {
        mAdapter.notifyItemMoved(fromPosition, toPosition)
    }

    @SuppressLint("UnknownNullness") // b/240775049: Cannot annotate properly
    override fun onChanged(position: Int, count: Int, payload: Any?) {
        mAdapter.notifyItemRangeChanged(position, count, payload)
    }
}

