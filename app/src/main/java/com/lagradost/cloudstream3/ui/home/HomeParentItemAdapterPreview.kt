package com.lagradost.cloudstream3.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.lagradost.cloudstream3.APIHolder.getId
import com.lagradost.cloudstream3.AcraApplication.Companion.getActivity
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.FragmentHomeHeadBinding
import com.lagradost.cloudstream3.databinding.FragmentHomeHeadTvBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.debugException
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.selectHomepage
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.ResultViewModel2
import com.lagradost.cloudstream3.ui.result.START_ACTION_RESUME_LATEST
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_SHOW_METADATA
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showOptionSelectStringRes
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbarMargin
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbarView
import com.lagradost.cloudstream3.utils.UIHelper.populateChips

class HomeParentItemAdapterPreview(
    items: MutableList<HomeViewModel.ExpandableHomepageList>,
    private val viewModel: HomeViewModel,
) : ParentItemAdapter(items, clickCallback = {
    viewModel.click(it)
}, moreInfoClickCallback = {
    viewModel.popup(it)
}, expandCallback = {
    viewModel.expand(it)
}) {
    val headItems = 1

    companion object {
        private const val VIEW_TYPE_HEADER = 2
        private const val VIEW_TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int) = when (position) {
        0 -> VIEW_TYPE_HEADER
        else -> VIEW_TYPE_ITEM
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> {}
            else -> super.onBindViewHolder(holder, position - headItems)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val inflater = LayoutInflater.from(parent.context)
                val binding = if (isTvSettings()) FragmentHomeHeadTvBinding.inflate(
                    inflater,
                    parent,
                    false
                ) else FragmentHomeHeadBinding.inflate(inflater, parent, false)
                HeaderViewHolder(
                    binding,
                    viewModel,
                )
            }

            VIEW_TYPE_ITEM -> super.onCreateViewHolder(parent, viewType)
            else -> error("Unhandled viewType=$viewType")
        }
    }

    override fun getItemCount(): Int {
        return super.getItemCount() + headItems
    }

    override fun getItemId(position: Int): Long {
        if (position == 0) return 0//previewData.hashCode().toLong()
        return super.getItemId(position - headItems)
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is HeaderViewHolder -> {
                holder.onViewDetachedFromWindow()
            }

            else -> super.onViewDetachedFromWindow(holder)
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is HeaderViewHolder -> {
                holder.onViewAttachedToWindow()
            }

            else -> super.onViewAttachedToWindow(holder)
        }
    }

    class HeaderViewHolder
    constructor(
        val binding: ViewBinding,
        val viewModel: HomeViewModel,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var previewAdapter: HomeScrollAdapter = HomeScrollAdapter()
        private var resumeAdapter: HomeChildItemAdapter = HomeChildItemAdapter(
            ArrayList(),
            nextFocusUp = itemView.nextFocusUpId,
            nextFocusDown = itemView.nextFocusDownId
        ) { callback ->
            if (callback.action != SEARCH_ACTION_SHOW_METADATA) {
                viewModel.click(callback)
                return@HomeChildItemAdapter
            }
            callback.view.context?.getActivity()?.showOptionSelectStringRes(
                callback.view,
                callback.card.posterUrl,
                listOf(
                    R.string.action_open_watching,
                    R.string.action_remove_watching
                ),
                listOf(
                    R.string.action_open_play,
                    R.string.action_open_watching,
                    R.string.action_remove_watching
                )
            ) { (isTv, actionId) ->
                when (actionId + if (isTv) 0 else 1) {
                    // play
                    0 -> {
                        viewModel.click(
                            SearchClickCallback(
                                START_ACTION_RESUME_LATEST,
                                callback.view,
                                -1,
                                callback.card
                            )
                        )
                    }
                    //info
                    1 -> {
                        viewModel.click(
                            SearchClickCallback(
                                SEARCH_ACTION_LOAD,
                                callback.view,
                                -1,
                                callback.card
                            )
                        )
                    }
                    // remove
                    2 -> {
                        val card = callback.card
                        if (card is DataStoreHelper.ResumeWatchingResult) {
                            DataStoreHelper.removeLastWatched(card.parentId)
                            viewModel.reloadStored()
                        }
                    }
                }
            }
        }
        private var bookmarkAdapter: HomeChildItemAdapter = HomeChildItemAdapter(
            ArrayList(),
            nextFocusUp = itemView.nextFocusUpId,
            nextFocusDown = itemView.nextFocusDownId
        ) { callback ->
            if (callback.action != SEARCH_ACTION_SHOW_METADATA) {
                viewModel.click(callback)
                return@HomeChildItemAdapter
            }
            callback.view.context?.getActivity()?.showOptionSelectStringRes(
                callback.view,
                callback.card.posterUrl,
                listOf(
                    R.string.action_open_watching,
                    R.string.action_remove_from_bookmarks,
                ),
                listOf(
                    R.string.action_open_play,
                    R.string.action_open_watching,
                    R.string.action_remove_from_bookmarks
                )
            ) { (isTv, actionId) ->
                when (actionId + if (isTv) 0 else 1) { // play
                    0 -> {
                        viewModel.click(
                            SearchClickCallback(
                                START_ACTION_RESUME_LATEST,
                                callback.view,
                                -1,
                                callback.card
                            )
                        )
                    }

                    1 -> { // info
                        viewModel.click(
                            SearchClickCallback(
                                SEARCH_ACTION_LOAD,
                                callback.view,
                                -1,
                                callback.card
                            )
                        )
                    }

                    2 -> { // remove
                        DataStoreHelper.setResultWatchState(
                            callback.card.id,
                            WatchType.NONE.internalId
                        )
                        viewModel.reloadStored()
                    }
                }
            }
        }


        private val previewViewpager: ViewPager2 =
            itemView.findViewById(R.id.home_preview_viewpager)

        private val previewViewpagerText: ViewGroup =
            itemView.findViewById(R.id.home_preview_viewpager_text)

        // private val previewHeader: FrameLayout = itemView.findViewById(R.id.home_preview)
        private var resumeHolder: View = itemView.findViewById(R.id.home_watch_holder)
        private var resumeRecyclerView: RecyclerView =
            itemView.findViewById(R.id.home_watch_child_recyclerview)
        private var bookmarkHolder: View = itemView.findViewById(R.id.home_bookmarked_holder)
        private var bookmarkRecyclerView: RecyclerView =
            itemView.findViewById(R.id.home_bookmarked_child_recyclerview)

        private var homeAccount: View? =
            itemView.findViewById(R.id.home_preview_switch_account)

        private var topPadding: View? = itemView.findViewById(R.id.home_padding)

        private val homeNonePadding: View = itemView.findViewById(R.id.home_none_padding)

        private val previewCallback: ViewPager2.OnPageChangeCallback =
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    previewAdapter.apply {
                        if (position >= itemCount - 1 && hasMoreItems) {
                            hasMoreItems = false // don't make two requests
                            viewModel.loadMoreHomeScrollResponses()
                        }
                    }
                    val item = previewAdapter.getItem(position) ?: return
                    onSelect(item, position)
                }
            }

        fun onSelect(item: LoadResponse, position: Int) {
            (binding as? FragmentHomeHeadTvBinding)?.apply {
                homePreviewDescription.isGone =
                    item.plot.isNullOrBlank()
                homePreviewDescription.text =
                    item.plot ?: ""

                homePreviewText.text = item.name
                populateChips(
                    homePreviewTags,
                    item.tags ?: emptyList(),
                    R.style.ChipFilledSemiTransparent
                )

                homePreviewTags.isGone =
                    item.tags.isNullOrEmpty()

                homePreviewPlayBtt.setOnClickListener { view ->
                    viewModel.click(
                        LoadClickCallback(
                            START_ACTION_RESUME_LATEST,
                            view,
                            position,
                            item
                        )
                    )
                }

                homePreviewInfoBtt.setOnClickListener { view ->
                    viewModel.click(
                        LoadClickCallback(0, view, position, item)
                    )
                }

            }
            (binding as? FragmentHomeHeadBinding)?.apply {
                //homePreviewImage.setImage(item.posterUrl, item.posterHeaders)

                homePreviewPlay.setOnClickListener { view ->
                    viewModel.click(
                        LoadClickCallback(
                            START_ACTION_RESUME_LATEST,
                            view,
                            position,
                            item
                        )
                    )
                }

                homePreviewInfo.setOnClickListener { view ->
                    viewModel.click(
                        LoadClickCallback(0, view, position, item)
                    )
                }

                // very ugly code, but I don't care
                val id = item.getId()
                val watchType =
                    DataStoreHelper.getResultWatchState(id)
                homePreviewBookmark.setText(watchType.stringRes)
                homePreviewBookmark.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable(
                        homePreviewBookmark.context,
                        watchType.iconRes
                    ),
                    null,
                    null
                )

                homePreviewBookmark.setOnClickListener { fab ->
                    fab.context.getActivity()?.showBottomDialog(
                        WatchType.values()
                            .map { fab.context.getString(it.stringRes) }
                            .toList(),
                        DataStoreHelper.getResultWatchState(id).ordinal,
                        fab.context.getString(R.string.action_add_to_bookmarks),
                        showApply = false,
                        {}) {
                        val newValue = WatchType.values()[it]
                        homePreviewBookmark.setCompoundDrawablesWithIntrinsicBounds(
                            null,
                            ContextCompat.getDrawable(
                                homePreviewBookmark.context,
                                newValue.iconRes
                            ),
                            null,
                            null
                        )
                        homePreviewBookmark.setText(newValue.stringRes)

                        ResultViewModel2.updateWatchStatus(
                            item,
                            newValue
                        )
                    }
                }
            }
        }

        fun onViewDetachedFromWindow() {
            previewViewpager.unregisterOnPageChangeCallback(previewCallback)
        }

        fun onViewAttachedToWindow() {
            previewViewpager.registerOnPageChangeCallback(previewCallback)

            binding.root.findViewTreeLifecycleOwner()?.apply {
                observe(viewModel.preview) {
                    updatePreview(it)
                }
                if (binding is FragmentHomeHeadTvBinding) {
                    observe(viewModel.apiName) { name ->
                        binding.homePreviewChangeApi.text = name
                    }
                }
                observe(viewModel.resumeWatching) {
                    updateResume(it)
                }
                observe(viewModel.bookmarks) {
                    updateBookmarks(it)
                }
                observe(viewModel.availableWatchStatusTypes) { (checked, visible) ->
                    for ((chip, watch) in toggleList) {
                        chip.apply {
                            isVisible = visible.contains(watch)
                            isChecked = checked.contains(watch)
                        }
                    }
                    toggleListHolder?.isGone = visible.isEmpty()
                }
            } ?: debugException { "Expected findViewTreeLifecycleOwner" }
        }

        private val toggleList = listOf<Pair<Chip, WatchType>>(
            Pair(itemView.findViewById(R.id.home_type_watching_btt), WatchType.WATCHING),
            Pair(itemView.findViewById(R.id.home_type_completed_btt), WatchType.COMPLETED),
            Pair(itemView.findViewById(R.id.home_type_dropped_btt), WatchType.DROPPED),
            Pair(itemView.findViewById(R.id.home_type_on_hold_btt), WatchType.ONHOLD),
            Pair(itemView.findViewById(R.id.home_plan_to_watch_btt), WatchType.PLANTOWATCH),
        )

        private val toggleListHolder: ChipGroup? = itemView.findViewById(R.id.home_type_holder)

        init {
            previewViewpager.setPageTransformer(HomeScrollTransformer())

            previewViewpager.adapter = previewAdapter
            resumeRecyclerView.adapter = resumeAdapter
            bookmarkRecyclerView.adapter = bookmarkAdapter

            resumeRecyclerView.setLinearListLayout(
                nextLeft = R.id.nav_rail_view,
                nextRight = FOCUS_SELF
            )
            bookmarkRecyclerView.setLinearListLayout(
                nextLeft = R.id.nav_rail_view,
                nextRight = FOCUS_SELF
            )

            fixPaddingStatusbarMargin(topPadding)

            for ((chip, watch) in toggleList) {
                chip.isChecked = false
                chip.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        viewModel.loadStoredData(setOf(watch))
                    }
                    // Else if all are unchecked -> Do not load data
                    else if (toggleList.all { !it.first.isChecked }) {
                        viewModel.loadStoredData(emptySet())
                    }
                }
            }

            homeAccount?.setOnClickListener { v ->
                DataStoreHelper.showWhoIsWatching(v?.context ?: return@setOnClickListener)
            }

            (binding as? FragmentHomeHeadTvBinding)?.apply {
                homePreviewChangeApi.setOnClickListener { view ->
                    view.context.selectHomepage(viewModel.repo?.name) { api ->
                        viewModel.loadAndCancel(api, forceReload = true, fromUI = true)
                    }
                }

                // This makes the hidden next buttons only available when on the info button
                // Otherwise you might be able to go to the next item without being at the info button
                homePreviewInfoBtt.setOnFocusChangeListener { _, hasFocus ->
                    homePreviewHiddenNextFocus.isFocusable = hasFocus
                }

                homePreviewPlayBtt.setOnFocusChangeListener { _, hasFocus ->
                    homePreviewHiddenPrevFocus.isFocusable = hasFocus
                }

                homePreviewHiddenNextFocus.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) return@setOnFocusChangeListener
                    previewViewpager.setCurrentItem(previewViewpager.currentItem + 1, true)
                    homePreviewInfoBtt.requestFocus()
                }

                homePreviewHiddenPrevFocus.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) return@setOnFocusChangeListener
                    if (previewViewpager.currentItem <= 0) {
                        (activity as? MainActivity)?.binding?.navRailView?.requestFocus()
                    } else {
                        previewViewpager.setCurrentItem(previewViewpager.currentItem - 1, true)
                        binding.homePreviewPlayBtt.requestFocus()
                    }
                }
            }

            (binding as? FragmentHomeHeadBinding)?.apply {
                homeSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean {
                        viewModel.queryTextSubmit(query)
                        return true
                    }

                    override fun onQueryTextChange(newText: String): Boolean {
                        viewModel.queryTextChange(newText)
                        return true
                    }
                })
            }
        }

        private fun updatePreview(preview: Resource<Pair<Boolean, List<LoadResponse>>>) {
            if (preview is Resource.Success) {
                homeNonePadding.apply {
                    val params = layoutParams
                    params.height = 0
                    layoutParams = params
                }
            } else {
                fixPaddingStatusbarView(homeNonePadding)
            }

            when (preview) {
                is Resource.Success -> {
                    if (!previewAdapter.setItems(
                            preview.value.second,
                            preview.value.first
                        )
                    ) {
                        // this might seam weird and useless, however this prevents a very weird andrid bug were the viewpager is not rendered properly
                        // I have no idea why that happens, but this is my ducktape solution
                        previewViewpager.setCurrentItem(0, false)
                        previewViewpager.beginFakeDrag()
                        previewViewpager.fakeDragBy(1f)
                        previewViewpager.endFakeDrag()
                        previewCallback.onPageSelected(0)
                        previewViewpager.isVisible = true
                        previewViewpagerText.isVisible = true
                        //previewHeader.isVisible = true
                    }
                }

                else -> {
                    previewAdapter.setItems(listOf(), false)
                    previewViewpager.setCurrentItem(0, false)
                    previewViewpager.isVisible = false
                    previewViewpagerText.isVisible = false
                    //previewHeader.isVisible = false
                }
            }
        }

        private fun updateResume(resumeWatching: List<SearchResponse>) {
            resumeHolder.isVisible = resumeWatching.isNotEmpty()
            resumeAdapter.updateList(resumeWatching)

            if (binding is FragmentHomeHeadBinding) {
                binding.homeWatchParentItemTitle.setOnClickListener {
                    viewModel.popup(
                        HomeViewModel.ExpandableHomepageList(
                            HomePageList(
                                binding.homeWatchParentItemTitle.text.toString(),
                                resumeWatching,
                                false
                            ), 1, false
                        ),
                        deleteCallback = {
                            viewModel.deleteResumeWatching()
                        }
                    )
                }
            }
        }

        private fun updateBookmarks(data: Pair<Boolean, List<SearchResponse>>) {
            val (visible, list) = data
            bookmarkHolder.isVisible = visible
            bookmarkAdapter.updateList(list)

            if (binding is FragmentHomeHeadBinding) {
                binding.homeBookmarkParentItemTitle.setOnClickListener {
                    val items = toggleList.map { it.first }.filter { it.isChecked }
                    if (items.isEmpty()) return@setOnClickListener // we don't want to show an empty dialog
                    val textSum = items
                        .mapNotNull { it.text }.joinToString()

                    viewModel.popup(
                        HomeViewModel.ExpandableHomepageList(
                            HomePageList(
                                textSum,
                                list,
                                false
                            ), 1, false
                        ), deleteCallback = {
                            viewModel.deleteBookmarks(list)
                        }
                    )
                }
            }
        }
    }
}
