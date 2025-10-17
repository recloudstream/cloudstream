package com.lagradost.cloudstream3.ui.library

import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.doOnAttach
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView.OnFlingListener
import com.google.android.material.appbar.AppBarLayout
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.LibraryViewpagerPageBinding
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.BaseAdapter
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.home.getSafeParcelable
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount

class ViewpagerAdapterViewHolderState(val binding: LibraryViewpagerPageBinding) :
    ViewHolderState<Bundle>(binding) {
    override fun save(): Bundle =
        Bundle().apply {
            putParcelable(
                "pageRecyclerview",
                binding.pageRecyclerview.layoutManager?.onSaveInstanceState()
            )
        }

    override fun restore(state: Bundle) {
        state.getSafeParcelable<Parcelable>("pageRecyclerview")?.let { recycle ->
            binding.pageRecyclerview.layoutManager?.onRestoreInstanceState(recycle)
        }
    }
}

class ViewpagerAdapter(
    fragment: Fragment,
    val scrollCallback: (isScrollingDown: Boolean) -> Unit,
    val clickCallback: (SearchClickCallback) -> Unit
) : BaseAdapter<SyncAPI.Page, Bundle>(fragment,
    id = "ViewpagerAdapter".hashCode(),
    diffCallback = BaseDiffCallback(
    itemSame = { a, b ->
        a.title == b.title
    },
    contentSame = { a, b ->
        a.items == b.items && a.title == b.title
    }
)) {
    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Bundle> {
        return ViewpagerAdapterViewHolderState(
            LibraryViewpagerPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }
    override fun onUpdateContent(
        holder: ViewHolderState<Bundle>,
        item: SyncAPI.Page,
        position: Int
    ) {
        val binding = holder.view
        if (binding !is LibraryViewpagerPageBinding) return
        (binding.pageRecyclerview.adapter as? PageAdapter)?.updateList(item.items)
        binding.pageRecyclerview.scrollToPosition(0)
    }

    override fun onBindContent(holder: ViewHolderState<Bundle>, item: SyncAPI.Page, position: Int) {
        val binding = holder.view
        if (binding !is LibraryViewpagerPageBinding) return

        binding.pageRecyclerview.tag = position
        binding.pageRecyclerview.apply {
            spanCount =
                binding.root.context.getSpanCount() ?: 3
            if (adapter == null) { //  || rebind
                // Only add the items after it has been attached since the items rely on ItemWidth
                // Which is only determined after the recyclerview is attached.
                // If this fails then item height becomes 0 when there is only one item
                doOnAttach {
                    adapter = PageAdapter(
                        item.items.toMutableList(),
                        this,
                        clickCallback
                    )
                }
            } else {
                (adapter as? PageAdapter)?.updateList(item.items)
                // scrollToPosition(0)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    val diff = scrollY - oldScrollY

                    //Expand the top Appbar based on scroll direction up/down, simulate phone behavior
                    if (isLayout(TV or EMULATOR)) {
                        binding.root.rootView.findViewById<AppBarLayout>(R.id.search_bar)
                            ?.apply {
                                if (diff <= 0)
                                    setExpanded(true)
                                else
                                    setExpanded(false)
                            }
                    }
                    if (diff == 0) return@setOnScrollChangeListener

                    scrollCallback.invoke(diff > 0)
                }
            } else {
                onFlingListener = object : OnFlingListener() {
                    override fun onFling(velocityX: Int, velocityY: Int): Boolean {
                        scrollCallback.invoke(velocityY > 0)
                        return false
                    }
                }
            }
        }
    }
}