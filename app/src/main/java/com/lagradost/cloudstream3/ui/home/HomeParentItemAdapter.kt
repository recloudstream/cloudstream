package com.lagradost.cloudstream3.ui.home

import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.HomepageParentBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.BaseAdapter
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.isRecyclerScrollable

class LoadClickCallback(
    val action: Int = 0,
    val view: View,
    val position: Int,
    val response: LoadResponse
)

open class ParentItemAdapter(
    id: Int,
    private val clickCallback: (SearchClickCallback) -> Unit,
    private val moreInfoClickCallback: (HomeViewModel.ExpandableHomepageList) -> Unit,
    private val expandCallback: ((String) -> Unit)? = null,
) : BaseAdapter<HomeViewModel.ExpandableHomepageList, Bundle>(
    id,
    diffCallback = BaseDiffCallback(
        itemSame = { a, b -> a.list.name == b.list.name },
        contentSame = { a, b ->
            a.list.list == b.list.list
        })
) {
    companion object {
        val sharedPool =
            RecyclerView.RecycledViewPool().apply { this.setMaxRecycledViews(CONTENT, 8) }
    }

    data class ParentItemHolder(val binding: ViewBinding) : ViewHolderState<Bundle>(binding) {
        override fun save(): Bundle = Bundle().apply {
            val recyclerView = (binding as? HomepageParentBinding)?.homeChildRecyclerview
            putParcelable(
                "value",
                recyclerView?.layoutManager?.onSaveInstanceState()
            )
            (recyclerView?.adapter as? BaseAdapter<*, *>)?.save(recyclerView)
        }

        override fun restore(state: Bundle) {
            (binding as? HomepageParentBinding)?.homeChildRecyclerview?.layoutManager?.onRestoreInstanceState(
                state.getSafeParcelable<Parcelable>("value")
            )
        }
    }

    override fun submitList(
        list: Collection<HomeViewModel.ExpandableHomepageList>?,
        commitCallback: Runnable?
    ) {
        super.submitList(list?.sortedBy { it.list.list.isEmpty() }, commitCallback)
    }

    override fun onUpdateContent(
        holder: ViewHolderState<Bundle>,
        item: HomeViewModel.ExpandableHomepageList,
        position: Int
    ) {
        val binding = holder.view
        if (binding !is HomepageParentBinding) return
        (binding.homeChildRecyclerview.adapter as? HomeChildItemAdapter)?.submitList(item.list.list)
    }

    override fun onBindContent(
        holder: ViewHolderState<Bundle>,
        item: HomeViewModel.ExpandableHomepageList,
        position: Int
    ) {
        val startFocus = R.id.nav_rail_view
        val endFocus = FOCUS_SELF
        val binding = holder.view as? HomepageParentBinding ?: return
        val info = item.list

        val currentAdapter = (binding.homeChildRecyclerview.adapter as? HomeChildItemAdapter)
            ?.apply {
                if (isHorizontal != info.isHorizontalImages) isHorizontal = info.isHorizontalImages
                if (hasNext != item.hasNext) hasNext = item.hasNext
                submitIncomparableList(info.list)
            }
            ?: HomeChildItemAdapter(
                id = id + position + 100,
                clickCallback = clickCallback,
                nextFocusUp = binding.homeChildRecyclerview.nextFocusUpId,
                nextFocusDown = binding.homeChildRecyclerview.nextFocusDownId,
            ).also { adapter ->
                adapter.isHorizontal = info.isHorizontalImages
                adapter.hasNext = item.hasNext
                adapter.submitList(info.list)

                binding.homeChildRecyclerview.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    var expandCount = 0
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        val adapter = recyclerView.adapter as? HomeChildItemAdapter ?: return
                        if (!recyclerView.isRecyclerScrollable() && adapter.hasNext && expandCount != adapter.itemCount) {
                            expandCount = adapter.itemCount
                            expandCallback?.invoke(info.name)
                        }
                    }
                })
                binding.homeChildRecyclerview.adapter = adapter
            }

        binding.homeChildRecyclerview.setRecycledViewPool(HomeChildItemAdapter.sharedPool)
        binding.homeChildRecyclerview.setHasFixedSize(true)
        binding.homeChildRecyclerview.isNestedScrollingEnabled = false
        (binding.homeChildRecyclerview.layoutManager as? LinearLayoutManager)?.initialPrefetchItemCount = 8
        binding.homeChildRecyclerview.setLinearListLayout(
            isHorizontal = true,
            nextLeft = startFocus,
            nextRight = endFocus
        )

        binding.homeChildMoreInfo.text = info.name
        if (isLayout(PHONE)) binding.homeChildMoreInfo.setOnClickListener { moreInfoClickCallback(item) }
    }

    override fun onCreateContent(parent: ViewGroup): ParentItemHolder {
        val layoutResId = when {
            isLayout(TV) -> R.layout.homepage_parent_tv
            isLayout(EMULATOR) -> R.layout.homepage_parent_emulator
            else -> R.layout.homepage_parent
        }

        val inflater = LayoutInflater.from(parent.context)
        val binding = try {
            HomepageParentBinding.bind(inflater.inflate(layoutResId, parent, false))
        } catch (t: Throwable) {
            logError(t)
            // just in case someone forgot we don't want to crash
            HomepageParentBinding.inflate(inflater)
        }

        return ParentItemHolder(binding)
    }
}

@Suppress("DEPRECATION")
inline fun <reified T> Bundle.getSafeParcelable(key: String): T? =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) getParcelable(key)
    else getParcelable(key, T::class.java)