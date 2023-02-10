package com.lagradost.cloudstream3.ui.library

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnAttach
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnFlingListener
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import kotlinx.android.synthetic.main.library_viewpager_page.view.*

class ViewpagerAdapter(
    var pages: List<SyncAPI.Page>,
    val scrollCallback: (isScrollingDown: Boolean) -> Unit,
    val clickCallback: (SearchClickCallback) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return PageViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.library_viewpager_page, parent, false)
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is PageViewHolder -> {
                holder.bind(pages[position], unbound.remove(position))
            }
        }
    }

    private val unbound = mutableSetOf<Int>()
    /**
     * Used to mark all pages for re-binding and forces all items to be refreshed
     * Without this the pages will still use the same adapters
     **/
    fun rebind() {
        unbound.addAll(0..pages.size)
        this.notifyItemRangeChanged(0, pages.size)
    }

    inner class PageViewHolder(private val itemViewTest: View) :
        RecyclerView.ViewHolder(itemViewTest) {
        fun bind(page: SyncAPI.Page, rebind: Boolean) {
            itemView.page_recyclerview?.spanCount =
                this@PageViewHolder.itemView.context.getSpanCount() ?: 3

            if (itemViewTest.page_recyclerview?.adapter == null || rebind) {
                // Only add the items after it has been attached since the items rely on ItemWidth
                // Which is only determined after the recyclerview is attached.
                // If this fails then item height becomes 0 when there is only one item
                itemViewTest.page_recyclerview?.doOnAttach {
                    itemViewTest.page_recyclerview?.adapter = PageAdapter(
                        page.items.toMutableList(),
                        itemViewTest.page_recyclerview,
                        clickCallback
                    )
                }
            } else {
                (itemViewTest.page_recyclerview?.adapter as? PageAdapter)?.updateList(page.items)
                itemViewTest.page_recyclerview?.scrollToPosition(0)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                itemViewTest.page_recyclerview.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                    val diff = scrollY - oldScrollY
                    if (diff == 0) return@setOnScrollChangeListener

                    scrollCallback.invoke(diff > 0)
                }
            } else {
                itemViewTest.page_recyclerview.onFlingListener = object : OnFlingListener() {
                    override fun onFling(velocityX: Int, velocityY: Int): Boolean {
                        scrollCallback.invoke(velocityY > 0)
                        return false
                    }
                }
            }

        }
    }

    override fun getItemCount(): Int {
        return pages.size
    }
}