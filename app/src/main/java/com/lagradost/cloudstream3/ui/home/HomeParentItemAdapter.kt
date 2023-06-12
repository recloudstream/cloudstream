package com.lagradost.cloudstream3.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.lagradost.cloudstream3.APIHolder.getId
import com.lagradost.cloudstream3.AcraApplication.Companion.getActivity
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.result.LinearListLayout
import com.lagradost.cloudstream3.ui.result.ResultViewModel2
import com.lagradost.cloudstream3.ui.result.START_ACTION_RESUME_LATEST
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchFragment.Companion.filterSearchResponse
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.AppUtils.isRecyclerScrollable
import com.lagradost.cloudstream3.utils.AppUtils.loadResult
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbarView
import kotlinx.android.synthetic.main.activity_main_tv.*
import kotlinx.android.synthetic.main.activity_main_tv.view.*
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.fragment_home.view.*
import kotlinx.android.synthetic.main.fragment_home_head_tv.*
import kotlinx.android.synthetic.main.fragment_home_head_tv.view.*
import kotlinx.android.synthetic.main.fragment_home_head_tv.view.home_preview
import kotlinx.android.synthetic.main.fragment_home_head_tv.view.home_preview_viewpager
import kotlinx.android.synthetic.main.homepage_parent.view.*

class LoadClickCallback(
    val action: Int = 0,
    val view: View,
    val position: Int,
    val response: LoadResponse
)

open class ParentItemAdapter(
    private var items: MutableList<HomeViewModel.ExpandableHomepageList>,
    private val clickCallback: (SearchClickCallback) -> Unit,
    private val moreInfoClickCallback: (HomeViewModel.ExpandableHomepageList) -> Unit,
    private val expandCallback: ((String) -> Unit)? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ParentViewHolder(
            LayoutInflater.from(parent.context).inflate(
                if (isTvSettings()) R.layout.homepage_parent_tv else R.layout.homepage_parent,
                parent,
                false
            ),
            clickCallback,
            moreInfoClickCallback,
            expandCallback
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ParentViewHolder -> {
                holder.bind(items[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemId(position: Int): Long {
        return items[position].list.name.hashCode().toLong()
    }

    @JvmName("updateListHomePageList")
    fun updateList(newList: List<HomePageList>) {
        updateList(newList.map { HomeViewModel.ExpandableHomepageList(it, 1, false) }
            .toMutableList())
    }

    @JvmName("updateListExpandableHomepageList")
    fun updateList(
        newList: MutableList<HomeViewModel.ExpandableHomepageList>,
        recyclerView: RecyclerView? = null
    ) {
        // this
        // 1. prevents deep copy that makes this.items == newList
        // 2. filters out undesirable results
        // 3. moves empty results to the bottom (sortedBy is a stable sort)
        val new =
            newList.map { it.copy(list = it.list.copy(list = it.list.list.filterSearchResponse())) }
                .sortedBy { it.list.list.isEmpty() }

        val diffResult = DiffUtil.calculateDiff(
            SearchDiffCallback(items, new)
        )
        items.clear()
        items.addAll(new)

        //val mAdapter = this
        val delta = if (this@ParentItemAdapter is HomeParentItemAdapterPreview) {
            headItems
        } else {
            0
        }

        diffResult.dispatchUpdatesTo(object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) {
                //notifyItemRangeChanged(position + delta, count)
                notifyItemRangeInserted(position + delta, count)
            }

            override fun onRemoved(position: Int, count: Int) {
                notifyItemRangeRemoved(position + delta, count)
            }

            override fun onMoved(fromPosition: Int, toPosition: Int) {
                notifyItemMoved(fromPosition + delta, toPosition + delta)
            }

            override fun onChanged(_position: Int, count: Int, payload: Any?) {

                val position = _position + delta

                // I know kinda messy, what this does is using the update or bind instead of onCreateViewHolder -> bind
                recyclerView?.apply {
                    // this loops every viewHolder in the recycle view and checks the position to see if it is within the update range
                    val missingUpdates = (position until (position + count)).toMutableSet()
                    for (i in 0 until itemCount) {
                        val child = getChildAt(i) ?: continue
                        val viewHolder = getChildViewHolder(child) ?: continue
                        if (viewHolder !is ParentViewHolder) continue

                        val absolutePosition = viewHolder.bindingAdapterPosition
                        if (absolutePosition >= position && absolutePosition < position + count) {
                            val expand = items.getOrNull(absolutePosition - delta) ?: continue
                            missingUpdates -= absolutePosition
                            //println("Updating ${viewHolder.title.text} ($absolutePosition $position) -> ${expand.list.name}")
                            if (viewHolder.title.text == expand.list.name) {
                                viewHolder.update(expand)
                            } else {
                                viewHolder.bind(expand)
                            }
                        }
                    }

                    // just in case some item did not get updated
                    for (i in missingUpdates) {
                        notifyItemChanged(i, payload)
                    }
                } ?: run {
                    // in case we don't have a nice
                    notifyItemRangeChanged(position, count, payload)
                }
            }
        })

        //diffResult.dispatchUpdatesTo(this)
    }

    class ParentViewHolder
    constructor(
        itemView: View,
        private val clickCallback: (SearchClickCallback) -> Unit,
        private val moreInfoClickCallback: (HomeViewModel.ExpandableHomepageList) -> Unit,
        private val expandCallback: ((String) -> Unit)? = null,
    ) :
        RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.home_child_more_info
        private val recyclerView: RecyclerView = itemView.home_child_recyclerview

        fun update(expand: HomeViewModel.ExpandableHomepageList) {
            val info = expand.list
            (recyclerView.adapter as? HomeChildItemAdapter?)?.apply {
                updateList(info.list.toMutableList())
                hasNext = expand.hasNext
            } ?: run {
                recyclerView.adapter = HomeChildItemAdapter(
                    info.list.toMutableList(),
                    clickCallback = clickCallback,
                    nextFocusUp = recyclerView.nextFocusUpId,
                    nextFocusDown = recyclerView.nextFocusDownId,
                ).apply {
                    isHorizontal = info.isHorizontalImages
                }
                recyclerView.setLinearListLayout()
            }
        }

        fun bind(expand: HomeViewModel.ExpandableHomepageList) {
            val info = expand.list
            recyclerView.adapter = HomeChildItemAdapter(
                info.list.toMutableList(),
                clickCallback = clickCallback,
                nextFocusUp = recyclerView.nextFocusUpId,
                nextFocusDown = recyclerView.nextFocusDownId,
            ).apply {
                isHorizontal = info.isHorizontalImages
                hasNext = expand.hasNext
            }
            recyclerView.setLinearListLayout()
            title.text = info.name

            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                var expandCount = 0
                val name = expand.list.name

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)

                    val adapter = recyclerView.adapter
                    if (adapter !is HomeChildItemAdapter) return

                    val count = adapter.itemCount
                    val hasNext = adapter.hasNext
                    /*println(
                        "scolling ${recyclerView.isRecyclerScrollable()} ${
                            recyclerView.canScrollHorizontally(
                                1
                            )
                        }"
                    )*/
                    //!recyclerView.canScrollHorizontally(1)
                    if (!recyclerView.isRecyclerScrollable() && hasNext && expandCount != count) {
                        expandCount = count
                        expandCallback?.invoke(name)
                    }
                }
            })

            //(recyclerView.adapter as HomeChildItemAdapter).notifyDataSetChanged()
            if (!isTvSettings()) {
                title.setOnClickListener {
                    moreInfoClickCallback.invoke(expand)
                }
            }
        }
    }
}

class SearchDiffCallback(
    private val oldList: List<HomeViewModel.ExpandableHomepageList>,
    private val newList: List<HomeViewModel.ExpandableHomepageList>
) :
    DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition].list.name == newList[newItemPosition].list.name

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        oldList[oldItemPosition] == newList[newItemPosition]
}