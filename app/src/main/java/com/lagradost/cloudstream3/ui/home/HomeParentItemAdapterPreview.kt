package com.lagradost.cloudstream3.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
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
import com.lagradost.cloudstream3.ui.result.ResultViewModel2
import com.lagradost.cloudstream3.ui.result.START_ACTION_RESUME_LATEST
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_SHOW_METADATA
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showOptionSelectStringRes
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbarView
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlinx.android.synthetic.main.fragment_home_head.view.*
import kotlinx.android.synthetic.main.fragment_home_head.view.home_bookmarked_child_recyclerview
import kotlinx.android.synthetic.main.fragment_home_head.view.home_watch_parent_item_title
import kotlinx.android.synthetic.main.fragment_home_head_tv.view.*
import kotlinx.android.synthetic.main.fragment_home_head_tv.view.home_bookmarked_holder
import kotlinx.android.synthetic.main.fragment_home_head_tv.view.home_none_padding
import kotlinx.android.synthetic.main.fragment_home_head_tv.view.home_plan_to_watch_btt
import kotlinx.android.synthetic.main.fragment_home_head_tv.view.home_preview
import kotlinx.android.synthetic.main.fragment_home_head_tv.view.home_preview_viewpager
import kotlinx.android.synthetic.main.fragment_home_head_tv.view.home_type_completed_btt
import kotlinx.android.synthetic.main.fragment_home_head_tv.view.home_type_dropped_btt
import kotlinx.android.synthetic.main.fragment_home_head_tv.view.home_type_on_hold_btt
import kotlinx.android.synthetic.main.fragment_home_head_tv.view.home_type_watching_btt
import kotlinx.android.synthetic.main.fragment_home_head_tv.view.home_watch_child_recyclerview
import kotlinx.android.synthetic.main.fragment_home_head_tv.view.home_watch_holder
import kotlinx.android.synthetic.main.toast.view.*

class HomeParentItemAdapterPreview(
    items: MutableList<HomeViewModel.ExpandableHomepageList>,
    val clickCallback: (SearchClickCallback) -> Unit,
    private val moreInfoClickCallback: (HomeViewModel.ExpandableHomepageList) -> Unit,
    expandCallback: ((String) -> Unit)? = null,
    private val loadCallback: (LoadClickCallback) -> Unit,
    private val loadMoreCallback: (() -> Unit),
    private val changeHomePageCallback: ((View) -> Unit),
    private val reloadStored: (() -> Unit),
    private val loadStoredData: ((Set<WatchType>) -> Unit),
    private val searchQueryCallback: ((Pair<Boolean, String>) -> Unit)
) : ParentItemAdapter(items, clickCallback, moreInfoClickCallback, expandCallback) {
    private var previewData: Resource<Pair<Boolean, List<LoadResponse>>> = Resource.Loading()
    private var resumeWatchingData: List<SearchResponse> = listOf()
    private var bookmarkData: Pair<Boolean, List<SearchResponse>> =
        false to listOf()
    private var apiName: String = "NONE"

    val headItems = 1

    private var availableWatchStatusTypes: Pair<Set<WatchType>, Set<WatchType>> =
        setOf<WatchType>() to setOf()

    fun setAvailableWatchStatusTypes(data: Pair<Set<WatchType>, Set<WatchType>>) {
        availableWatchStatusTypes = data
        holder?.setAvailableWatchStatusTypes(data)
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 2
        private const val VIEW_TYPE_ITEM = 1
    }

    fun setResumeWatchingData(resumeWatching: List<SearchResponse>) {
        resumeWatchingData = resumeWatching
        holder?.updateResume(resumeWatchingData)
    }

    fun setPreviewData(preview: Resource<Pair<Boolean, List<LoadResponse>>>) {
        previewData = preview
        holder?.updatePreview(preview)
    }

    fun setApiName(name: String) {
        apiName = name
        holder?.updateApiName(name)
    }

    fun setBookmarkData(data: Pair<Boolean, List<SearchResponse>>) {
        bookmarkData = data
        holder?.updateBookmarks(data)
    }

    override fun getItemViewType(position: Int) = when (position) {
        0 -> VIEW_TYPE_HEADER
        else -> VIEW_TYPE_ITEM
    }

    var holder: HeaderViewHolder? = null

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> {
                holder.updatePreview(previewData)
                holder.updateResume(resumeWatchingData)
                holder.updateBookmarks(bookmarkData)
                holder.setAvailableWatchStatusTypes(availableWatchStatusTypes)
                holder.updateApiName(apiName)
            }
            else -> super.onBindViewHolder(holder, position - 1)
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        println("onCreateViewHolder $viewType")
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    if (isTvSettings()) R.layout.fragment_home_head_tv else R.layout.fragment_home_head,
                    parent,
                    false
                ),
                loadCallback,
                loadMoreCallback,
                changeHomePageCallback,
                clickCallback,
                reloadStored,
                loadStoredData,
                searchQueryCallback,
                moreInfoClickCallback
            ).also {
                this.holder = it
            }
            VIEW_TYPE_ITEM -> super.onCreateViewHolder(parent, viewType)
            else -> error("Unhandled viewType=$viewType")
        }
    }

    override fun getItemCount(): Int {
        return super.getItemCount() + headItems
    }

    override fun getItemId(position: Int): Long {
        if (position == 0) return previewData.hashCode().toLong()
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
        itemView: View,
        private val clickCallback: ((LoadClickCallback) -> Unit)?,
        private val loadMoreCallback: (() -> Unit),
        private val changeHomePageCallback: ((View) -> Unit),
        private val searchClickCallback: (SearchClickCallback) -> Unit,
        private val reloadStored: () -> Unit,
        private val loadStoredData: ((Set<WatchType>) -> Unit),
        private val searchQueryCallback: ((Pair<Boolean, String>) -> Unit),
        private val moreInfoClickCallback: (HomeViewModel.ExpandableHomepageList) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private var previewAdapter: HomeScrollAdapter? = null
        private val previewViewpager: ViewPager2? = itemView.home_preview_viewpager
        private val previewHeader: FrameLayout? = itemView.home_preview
        private val previewCallback: ViewPager2.OnPageChangeCallback =
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    // home_search?.isIconified = true
                    //home_search?.isVisible = true
                    //home_search?.clearFocus()

                    previewAdapter?.apply {
                        if (position >= itemCount - 1 && hasMoreItems) {
                            hasMoreItems = false // dont make two requests
                            loadMoreCallback()
                            //homeViewModel.loadMoreHomeScrollResponses()
                        }
                    }
                    previewAdapter?.getItem(position)
                        ?.apply {
                            //itemView.home_preview_title_holder?.let { parent ->
                            //    TransitionManager.beginDelayedTransition(
                            //        parent,
                            //        ChangeBounds()
                            //    )
                            //}
                            itemView.home_preview_description?.isGone =
                                this.plot.isNullOrBlank()
                            itemView.home_preview_description?.text =
                                this.plot ?: ""
                            itemView.home_preview_text?.text = this.name
                            itemView.home_preview_tags?.apply {
                                removeAllViews()
                                tags?.forEach { tag ->
                                    val chip = Chip(context)
                                    val chipDrawable =
                                        ChipDrawable.createFromAttributes(
                                            context,
                                            null,
                                            0,
                                            R.style.ChipFilledSemiTransparent
                                        )
                                    chip.setChipDrawable(chipDrawable)
                                    chip.text = tag
                                    chip.isChecked = false
                                    chip.isCheckable = false
                                    chip.isFocusable = false
                                    chip.isClickable = false
                                    addView(chip)
                                }
                            }
                            itemView.home_preview_tags?.isGone =
                                tags.isNullOrEmpty()
                            itemView.home_preview_image?.setImage(
                                posterUrl,
                                posterHeaders
                            )
                            //  itemView.home_preview_title?.text = name

                            itemView.home_preview_play?.setOnClickListener { view ->
                                clickCallback?.invoke(
                                    LoadClickCallback(
                                        START_ACTION_RESUME_LATEST,
                                        view,
                                        position,
                                        this
                                    )
                                )
                            }
                            itemView.home_preview_info?.setOnClickListener { view ->
                                clickCallback?.invoke(
                                    LoadClickCallback(0, view, position, this)
                                )
                            }

                            itemView.home_preview_play_btt?.setOnClickListener { view ->
                                clickCallback?.invoke(
                                    LoadClickCallback(
                                        START_ACTION_RESUME_LATEST,
                                        view,
                                        position,
                                        this
                                    )
                                )
                            }

                            // This makes the hidden next buttons only available when on the info button
                            // Otherwise you might be able to go to the next item without being at the info button
                            itemView.home_preview_info_btt?.setOnFocusChangeListener { _, hasFocus ->
                                itemView.home_preview_hidden_next_focus?.isFocusable = hasFocus
                            }
                            itemView.home_preview_play_btt?.setOnFocusChangeListener { _, hasFocus ->
                                itemView.home_preview_hidden_prev_focus?.isFocusable = hasFocus
                            }


                            itemView.home_preview_info_btt?.setOnClickListener { view ->
                                clickCallback?.invoke(
                                    LoadClickCallback(0, view, position, this)
                                )
                            }

                            itemView.home_preview_hidden_next_focus?.setOnFocusChangeListener { _, hasFocus ->
                                if (hasFocus) {
                                    previewViewpager?.apply {
                                        setCurrentItem(currentItem + 1, true)
                                    }
                                    itemView.home_preview_info_btt?.requestFocus()
                                }
                            }

                            itemView.home_preview_hidden_prev_focus?.setOnFocusChangeListener { _, hasFocus ->
                                if (hasFocus) {
                                    previewViewpager?.apply {
                                        if (currentItem <= 0) {
                                            nav_rail_view?.menu?.getItem(0)?.actionView?.requestFocus()
                                        } else {
                                            setCurrentItem(currentItem - 1, true)
                                            itemView.home_preview_play_btt?.requestFocus()
                                        }
                                    }
                                }
                            }
                            // very ugly code, but I dont care
                            val watchType =
                                DataStoreHelper.getResultWatchState(this.getId())
                            itemView.home_preview_bookmark?.setText(watchType.stringRes)
                            itemView.home_preview_bookmark?.setCompoundDrawablesWithIntrinsicBounds(
                                null,
                                ContextCompat.getDrawable(
                                    itemView.home_preview_bookmark.context,
                                    watchType.iconRes
                                ),
                                null,
                                null
                            )
                            itemView.home_preview_bookmark?.setOnClickListener { fab ->
                                fab.context.getActivity()?.showBottomDialog(
                                    WatchType.values()
                                        .map { fab.context.getString(it.stringRes) }
                                        .toList(),
                                    DataStoreHelper.getResultWatchState(this.getId()).ordinal,
                                    fab.context.getString(R.string.action_add_to_bookmarks),
                                    showApply = false,
                                    {}) {
                                    val newValue = WatchType.values()[it]
                                    itemView.home_preview_bookmark?.setCompoundDrawablesWithIntrinsicBounds(
                                        null,
                                        ContextCompat.getDrawable(
                                            itemView.home_preview_bookmark.context,
                                            newValue.iconRes
                                        ),
                                        null,
                                        null
                                    )
                                    itemView.home_preview_bookmark?.setText(newValue.stringRes)

                                    ResultViewModel2.updateWatchStatus(
                                        this,
                                        newValue
                                    )
                                    reloadStored()
                                }
                            }
                        }
                }
            }

        private var resumeAdapter: HomeChildItemAdapter? = null
        private var resumeHolder: View? = itemView.home_watch_holder
        private var resumeRecyclerView: RecyclerView? = itemView.home_watch_child_recyclerview

        private var bookmarkHolder: View? = itemView.home_bookmarked_holder
        private var bookmarkAdapter: HomeChildItemAdapter? = null
        private var bookmarkRecyclerView: RecyclerView? =
            itemView.home_bookmarked_child_recyclerview

        fun onViewDetachedFromWindow() {
            previewViewpager?.unregisterOnPageChangeCallback(previewCallback)
        }

        fun onViewAttachedToWindow() {
            previewViewpager?.registerOnPageChangeCallback(previewCallback)
        }

        private val toggleList = listOf(
            Pair(itemView.home_type_watching_btt, WatchType.WATCHING),
            Pair(itemView.home_type_completed_btt, WatchType.COMPLETED),
            Pair(itemView.home_type_dropped_btt, WatchType.DROPPED),
            Pair(itemView.home_type_on_hold_btt, WatchType.ONHOLD),
            Pair(itemView.home_plan_to_watch_btt, WatchType.PLANTOWATCH),
        )

        init {
            itemView.home_preview_change_api?.setOnClickListener { view ->
                changeHomePageCallback(view)
            }
            itemView.home_preview_change_api2?.setOnClickListener { view ->
                changeHomePageCallback(view)
            }

            previewViewpager?.apply {
                //if (!isTvSettings())
                setPageTransformer(HomeScrollTransformer())
                //else
                //    setPageTransformer(null)

                if (adapter == null)
                    adapter = HomeScrollAdapter(
                        if (isTvSettings()) R.layout.home_scroll_view_tv else R.layout.home_scroll_view,
                        if (isTvSettings()) true else null
                    )
            }
            previewAdapter = previewViewpager?.adapter as? HomeScrollAdapter?
            // previewViewpager?.registerOnPageChangeCallback(previewCallback)

            if (resumeAdapter == null) {
                resumeRecyclerView?.adapter = HomeChildItemAdapter(
                    ArrayList(),
                    nextFocusUp = itemView.nextFocusUpId,
                    nextFocusDown = itemView.nextFocusDownId
                ) { callback ->
                    if (callback.action != SEARCH_ACTION_SHOW_METADATA) {
                        searchClickCallback(callback)
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
                                searchClickCallback.invoke(
                                    SearchClickCallback(
                                        START_ACTION_RESUME_LATEST,
                                        callback.view,
                                        -1,
                                        callback.card
                                    )
                                )
                                reloadStored()
                            }
                            //info
                            1 -> {
                                searchClickCallback(
                                    SearchClickCallback(
                                        SEARCH_ACTION_LOAD,
                                        callback.view,
                                        -1,
                                        callback.card
                                    )
                                )

                                reloadStored()
                            }
                            // remove
                            2 -> {
                                val card = callback.card
                                if (card is DataStoreHelper.ResumeWatchingResult) {
                                    DataStoreHelper.removeLastWatched(card.parentId)
                                    reloadStored()
                                }
                            }
                        }
                    }
                }
            }
            resumeAdapter = resumeRecyclerView?.adapter as? HomeChildItemAdapter
            if (bookmarkAdapter == null) {
                bookmarkRecyclerView?.adapter = HomeChildItemAdapter(
                    ArrayList(),
                    nextFocusUp = itemView.nextFocusUpId,
                    nextFocusDown = itemView.nextFocusDownId
                ) { callback ->
                    if (callback.action != SEARCH_ACTION_SHOW_METADATA) {
                        searchClickCallback(callback)
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
                                searchClickCallback.invoke(
                                    SearchClickCallback(
                                        START_ACTION_RESUME_LATEST,
                                        callback.view,
                                        -1,
                                        callback.card
                                    )
                                )
                                reloadStored()
                            }
                            1 -> { // info
                                searchClickCallback(
                                    SearchClickCallback(
                                        SEARCH_ACTION_LOAD,
                                        callback.view,
                                        -1,
                                        callback.card
                                    )
                                )

                                reloadStored()
                            }
                            2 -> { // remove
                                DataStoreHelper.setResultWatchState(
                                    callback.card.id,
                                    WatchType.NONE.internalId
                                )
                                reloadStored()
                            }
                        }
                    }
                }
            }
            bookmarkAdapter = bookmarkRecyclerView?.adapter as? HomeChildItemAdapter

            for ((chip, watch) in toggleList) {
                chip?.isChecked = false
                chip?.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        loadStoredData(
                            setOf(watch)
                            // If we filter all buttons then two can be checked at the same time
                            // Revert this if you want to go back to multi selection
//                    toggleList.filter { it.first?.isChecked == true }.map { it.second }.toSet()
                        )
                    }
                    // Else if all are unchecked -> Do not load data
                    else if (toggleList.all { it.first?.isChecked != true }) {
                        loadStoredData(emptySet())
                    }
                }
            }

            itemView.home_search?.context?.fixPaddingStatusbar(itemView.home_search)

            itemView.home_search?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    searchQueryCallback.invoke(false to query)
                    //QuickSearchFragment.pushSearch(activity, query, currentApiName?.let { arrayOf(it) }
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    searchQueryCallback.invoke(true to newText)
                    //searchViewModel.quickSearch(newText)
                    return true
                }
            })
        }

        fun updateApiName(name: String) {
            itemView.home_preview_change_api2?.text = name
            itemView.home_preview_change_api?.text = name
        }

        fun updatePreview(preview: Resource<Pair<Boolean, List<LoadResponse>>>) {
            itemView.home_preview_change_api2?.isGone = preview is Resource.Success
            if (preview is Resource.Success) {
                itemView.home_none_padding?.apply {
                    val params = layoutParams
                    params.height = 0
                    layoutParams = params
                }
            } else {
                itemView.home_none_padding?.context?.fixPaddingStatusbarView(itemView.home_none_padding)
            }
            when (preview) {
                is Resource.Success -> {
                    if (true != previewAdapter?.setItems(
                            preview.value.second,
                            preview.value.first
                        )
                    ) {
                        // this might seam weird and useless, however this prevents a very weird andrid bug were the viewpager is not rendered properly
                        // I have no idea why that happens, but this is my ducktape solution
                        previewViewpager?.setCurrentItem(0, false)
                        previewViewpager?.beginFakeDrag()
                        previewViewpager?.fakeDragBy(1f)
                        previewViewpager?.endFakeDrag()
                        previewCallback.onPageSelected(0)
                        previewHeader?.isVisible = true
                    }
                }
                else -> {
                    previewAdapter?.setItems(listOf(), false)
                    previewViewpager?.setCurrentItem(0, false)
                    previewHeader?.isVisible = false
                }
            }
            // previewViewpager?.postDelayed({ previewViewpager?.scr(100, 0) }, 1000)
            //previewViewpager?.postInvalidate()
        }

        fun updateResume(resumeWatching: List<SearchResponse>) {
            resumeHolder?.isVisible = resumeWatching.isNotEmpty()
            resumeAdapter?.updateList(resumeWatching)

            if (!isTvSettings()) {
                itemView.home_watch_parent_item_title?.setOnClickListener {
                    moreInfoClickCallback.invoke(
                        HomeViewModel.ExpandableHomepageList(
                            HomePageList(
                                itemView.home_watch_parent_item_title?.text.toString(),
                                resumeWatching,
                                false
                            ), 1, false
                        )
                    )
                }
            }
        }

        fun updateBookmarks(data: Pair<Boolean, List<SearchResponse>>) {
            bookmarkHolder?.isVisible = data.first
            bookmarkAdapter?.updateList(data.second)
            if (!isTvSettings()) {
                itemView.home_bookmark_parent_item_title?.setOnClickListener {
                    val items = toggleList.mapNotNull { it.first }.filter { it.isChecked }
                    if (items.isEmpty()) return@setOnClickListener // we don't want to show an empty dialog
                    val textSum = items
                        .mapNotNull { it.text }.joinToString()

                    moreInfoClickCallback.invoke(
                        HomeViewModel.ExpandableHomepageList(
                            HomePageList(
                                textSum,
                                data.second,
                                false
                            ), 1, false
                        )
                    )
                }
            }
        }

        fun setAvailableWatchStatusTypes(availableWatchStatusTypes: Pair<Set<WatchType>, Set<WatchType>>) {
            for ((chip, watch) in toggleList) {
                chip?.apply {
                    isVisible = availableWatchStatusTypes.second.contains(watch)
                    isChecked = availableWatchStatusTypes.first.contains(watch)
                }
            }
        }
    }
}