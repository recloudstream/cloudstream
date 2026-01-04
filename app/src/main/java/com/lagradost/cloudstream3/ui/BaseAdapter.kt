package com.lagradost.cloudstream3.ui

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.children
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.viewbinding.ViewBinding
import coil3.dispose
import java.util.concurrent.CopyOnWriteArrayList

open class ViewHolderState<T>(val view: ViewBinding) : ViewHolder(view.root) {
    open fun save(): T? = null
    open fun restore(state: T) = Unit
}

abstract class NoStateAdapter<T : Any>(
    diffCallback: DiffUtil.ItemCallback<T> = BaseDiffCallback()
) : BaseAdapter<T, Any>(0, diffCallback)

/**
 * BaseAdapter is a persistent state stored adapter that supports headers and footers.
 * This should be used for restoring eg scroll or focus related to a view when it is recreated.
 *
 * Id is a per fragment based unique id used to store the underlying data done in an internal ViewModel.
 *
 * diffCallback is how the view should be handled when updating, override onUpdateContent for updates
 *
 * NOTE:
 *
 * By default it should save automatically, but you can also call save(recycle)
 *
 * By default no state is stored, but doing an id != 0 will store
 *
 * By default no headers or footers exist, override footers and headers count
 */
abstract class BaseAdapter<
        T : Any,
        S : Any>(
    val id: Int = 0,
    diffCallback: DiffUtil.ItemCallback<T> = BaseDiffCallback()
) : RecyclerView.Adapter<ViewHolderState<S>>() {
    open val footers: Int = 0
    open val headers: Int = 0

    val immutableCurrentList: List<T> get() = mDiffer.currentList

    fun getItem(position: Int): T {
        return mDiffer.currentList[position]
    }

    fun getItemOrNull(position: Int): T? {
        return mDiffer.currentList.getOrNull(position)
    }

    private val mDiffer: AsyncListDiffer<T> = AsyncListDiffer(
        object : NonFinalAdapterListUpdateCallback(this) {
            override fun onMoved(fromPosition: Int, toPosition: Int) {
                super.onMoved(fromPosition + headers, toPosition + headers)
            }

            override fun onRemoved(position: Int, count: Int) {
                super.onRemoved(position + headers, count)
            }

            override fun onChanged(position: Int, count: Int, payload: Any?) {
                super.onChanged(position + headers, count, payload)
            }

            override fun onInserted(position: Int, count: Int) {
                super.onInserted(position + headers, count)
            }
        },
        AsyncDifferConfig.Builder(diffCallback).build()
    )

    /**
     * Instantly submits a **new and fresh** list. This means that no changes like moves are done as
     * we assume the new list is not the same thing as the old list, nothing is shared.
     *
     * The views are rendered instantly as a result, so no fade/pop-ins or similar.
     *
     * Use `submitList` for general use, as that can reuse old views.
     * */
    open fun submitIncomparableList(list: List<T>?, commitCallback : Runnable? = null) {
        // This leverages a quirk in the submitList function that has a fast case for null arrays
        // What this implies is that as long as we do a double submit we can ensure no pop-ins,
        // as the changes are the entire list instead of calculating deltas
        submitList(null)
        submitList(list, commitCallback)
    }

    /**
     * @param commitCallback Optional runnable that is executed when the List is committed, if it is committed.
     * This is needed for some tasks as submitList will use a background thread for diff
     * */
    open fun submitList(list: Collection<T>?, commitCallback : Runnable? = null) {
        // deep copy at least the top list, because otherwise adapter can go crazy
        if (list.isNullOrEmpty()) {
            mDiffer.submitList(null, commitCallback) // It is "faster" to submit null than emptyList()
        } else {
            mDiffer.submitList(CopyOnWriteArrayList(list), commitCallback)
        }
    }

    override fun getItemCount(): Int {
        return mDiffer.currentList.size + footers + headers
    }

    open fun onUpdateContent(holder: ViewHolderState<S>, item: T, position: Int) =
        onBindContent(holder, item, position)

    open fun onBindContent(holder: ViewHolderState<S>, item: T, position: Int) = Unit
    open fun onBindFooter(holder: ViewHolderState<S>) = Unit
    open fun onBindHeader(holder: ViewHolderState<S>) = Unit
    open fun onCreateContent(parent: ViewGroup): ViewHolderState<S> = throw NotImplementedError()
    open fun onCreateCustomContent(
        parent: ViewGroup,
        viewType: Int
    ) = onCreateContent(parent)

    open fun onCreateFooter(parent: ViewGroup): ViewHolderState<S> = throw NotImplementedError()
    open fun onCreateCustomFooter(
        parent: ViewGroup,
        viewType: Int
    ) = onCreateFooter(parent)

    open fun onCreateHeader(parent: ViewGroup): ViewHolderState<S> = throw NotImplementedError()
    open fun onCreateCustomHeader(
        parent: ViewGroup,
        viewType: Int
    ) = onCreateHeader(parent)

    override fun onViewAttachedToWindow(holder: ViewHolderState<S>) {}
    override fun onViewDetachedFromWindow(holder: ViewHolderState<S>) {}

    @Suppress("UNCHECKED_CAST")
    fun save(recyclerView: RecyclerView) {
        for (child in recyclerView.children) {
            val holder =
                recyclerView.findContainingViewHolder(child) as? ViewHolderState<S> ?: continue
            setState(holder)
        }
    }

    fun clearState() {
        layoutManagerStates[id]?.clear()
    }

    @Suppress("UNCHECKED_CAST")
    private fun getState(holder: ViewHolderState<S>): S? =
        layoutManagerStates[id]?.get(holder.absoluteAdapterPosition) as? S

    private fun setState(holder: ViewHolderState<S>) {
        if (id == 0) return
        if (!layoutManagerStates.contains(id)) {
            layoutManagerStates[id] = HashMap()
        }
        layoutManagerStates[id]?.let { map ->
            map[holder.absoluteAdapterPosition] = holder.save()
        }
    }

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) {
            if (v !is RecyclerView) return
            save(v)
        }
    }

    final override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        recyclerView.addOnAttachStateChangeListener(attachListener)
        super.onAttachedToRecyclerView(recyclerView)
    }

    final override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        recyclerView.removeOnAttachStateChangeListener(attachListener)
        super.onDetachedFromRecyclerView(recyclerView)
    }

    open fun customContentViewType(item: T): Int = 0
    open fun customFooterViewType(): Int = 0
    open fun customHeaderViewType(): Int = 0

    final override fun getItemViewType(position: Int): Int {
        if (position < headers) {
            return HEADER or customHeaderViewType()
        }
        val realPosition = position - headers
        if (realPosition >= mDiffer.currentList.size) {
            return FOOTER or customFooterViewType()
        }
        return CONTENT or customContentViewType(getItem(realPosition))
    }

    final override fun onViewRecycled(holder: ViewHolderState<S>) {
        setState(holder)
        onClearView(holder)
        super.onViewRecycled(holder)
    }

    /** Same as onViewRecycled, but for the purpose of cleaning the view of any relevant data.
     *
     * If an item view has large or expensive data bound to it such as large bitmaps, this may be a good place to release those resources.
     *
     * Use this with `clearImage`
     * */
    open fun onClearView(holder: ViewHolderState<S>) {}

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderState<S> {
        return when (viewType and TYPE_MASK) {
            CONTENT -> onCreateCustomContent(parent, viewType and CUSTOM_MASK)
            HEADER -> onCreateCustomHeader(parent, viewType and CUSTOM_MASK)
            FOOTER -> onCreateCustomFooter(parent, viewType and CUSTOM_MASK)
            else -> throw NotImplementedError()
        }
    }

    // https://medium.com/@domen.lanisnik/efficiently-updating-recyclerview-items-using-payloads-1305f65f3068
    override fun onBindViewHolder(
        holder: ViewHolderState<S>,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        when (getItemViewType(position) and TYPE_MASK) {
            CONTENT -> {
                val realPosition = position - headers
                val item = getItem(realPosition)
                onUpdateContent(holder, item, realPosition)
            }

            FOOTER -> {
                onBindFooter(holder)
            }

            HEADER -> {
                onBindHeader(holder)
            }
        }
    }

    final override fun onBindViewHolder(holder: ViewHolderState<S>, position: Int) {
        when (getItemViewType(position) and TYPE_MASK) {
            CONTENT -> {
                val realPosition = position - headers
                val item = getItem(realPosition)
                onBindContent(holder, item, realPosition)
            }

            FOOTER -> {
                onBindFooter(holder)
            }

            HEADER -> {
                onBindHeader(holder)
            }
        }

        getState(holder)?.let { state ->
            holder.restore(state)
        }
    }

    companion object {
        val layoutManagerStates = hashMapOf<Int, HashMap<Int, Any?>>()
        fun clearImage(image: ImageView?) {
            image?.dispose()
        }

        // Use the lowermost MASK_SIZE bits for the custom content,
        // use the uppermost 32 - MASK_SIZE to the type
        private const val MASK_SIZE = 28
        private const val CUSTOM_MASK = (1 shl MASK_SIZE) - 1
        private const val TYPE_MASK = CUSTOM_MASK.inv()
        const val HEADER: Int = 3 shl MASK_SIZE
        const val FOOTER: Int = 2 shl MASK_SIZE
        /** For custom content, write `CONTENT or X` when calling setMaxRecycledViews  */
        const val CONTENT: Int = 1 shl MASK_SIZE
    }
}

class BaseDiffCallback<T : Any>(
    val itemSame: (T, T) -> Boolean = { a, b -> a.hashCode() == b.hashCode() },
    val contentSame: (T, T) -> Boolean = { a, b -> a.hashCode() == b.hashCode() }
) : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = itemSame(oldItem, newItem)
    override fun areContentsTheSame(oldItem: T, newItem: T): Boolean = contentSame(oldItem, newItem)
    override fun getChangePayload(oldItem: T, newItem: T): Any? = Any()
}