package com.lagradost.cloudstream3.ui.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.syncproviders.OAuth2API
import com.lagradost.cloudstream3.ui.APIRepository.Companion.noneApi
import com.lagradost.cloudstream3.ui.APIRepository.Companion.randomApi
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.result.START_ACTION_RESUME_LATEST
import com.lagradost.cloudstream3.ui.search.*
import com.lagradost.cloudstream3.ui.search.SearchFragment.Companion.filterSearchResponse
import com.lagradost.cloudstream3.ui.search.SearchHelper.handleSearchClickCallback
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.removeLastWatched
import com.lagradost.cloudstream3.utils.DataStoreHelper.setResultWatchState
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.HOMEPAGE_API
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbarView
import com.lagradost.cloudstream3.utils.UIHelper.getGridIsCompact
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIcons
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.widget.CenterZoomLayoutManager
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_home.*
import java.util.*

const val HOME_BOOKMARK_VALUE_LIST = "home_bookmarked_last_list"

class HomeFragment : Fragment() {
    companion object {
        val configEvent = Event<Int>()
        var currentSpan = 1

        fun Activity.loadHomepageList(item: HomePageList) {
            val context = this
            val bottomSheetDialogBuilder = BottomSheetDialog(context)
            bottomSheetDialogBuilder.setContentView(R.layout.home_episodes_expanded)
            val title = bottomSheetDialogBuilder.findViewById<TextView>(R.id.home_expanded_text)!!
            title.text = item.name
            val recycle = bottomSheetDialogBuilder.findViewById<AutofitRecyclerView>(R.id.home_expanded_recycler)!!
            val titleHolder = bottomSheetDialogBuilder.findViewById<FrameLayout>(R.id.home_expanded_drag_down)!!

            titleHolder.setOnClickListener {
                bottomSheetDialogBuilder.dismissSafe(this)
            }

            // Span settings
            recycle.spanCount = currentSpan

            recycle.adapter = SearchAdapter(item.list, recycle) { callback ->
                handleSearchClickCallback(this, callback)
                if (callback.action == SEARCH_ACTION_LOAD || callback.action == SEARCH_ACTION_PLAY_FILE) {
                    bottomSheetDialogBuilder.dismissSafe(this)
                }
            }

            val spanListener = { span: Int ->
                recycle.spanCount = span
                (recycle.adapter as SearchAdapter).notifyDataSetChanged()
            }

            configEvent += spanListener

            bottomSheetDialogBuilder.setOnDismissListener {
                configEvent -= spanListener
            }

            (recycle.adapter as SearchAdapter).notifyDataSetChanged()

            bottomSheetDialogBuilder.show()
        }
    }

    private val homeViewModel: HomeViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        //homeViewModel =
        //     ViewModelProvider(this).get(HomeViewModel::class.java)

        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    private var currentHomePage: HomePageResponse? = null

    private fun toggleMainVisibility(visible: Boolean) {
        home_main_holder.isVisible = visible
    }

    private fun fixGrid() {
        val compactView = activity?.getGridIsCompact() ?: false
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation

        currentSpan = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            spanCountLandscape
        } else {
            spanCountPortrait
        }
        configEvent.invoke(currentSpan)
    }

    private val apiChangeClickListener = View.OnClickListener { view ->
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
        val currentPrefMedia = settingsManager.getInt(getString(R.string.preferred_media_settings), 0)
        val validAPIs = AppUtils.filterProviderByPreferredMedia(apis, currentPrefMedia).toMutableList()

        validAPIs.add(0, randomApi)
        validAPIs.add(0, noneApi)
        view.popupMenuNoIconsAndNoStringRes(validAPIs.mapIndexed { index, api -> Pair(index, api.name) }) {
            homeViewModel.loadAndCancel(validAPIs[itemId].name, currentPrefMedia)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fixGrid()
    }

    override fun onResume() {
        super.onResume()
        reloadStored()
    }
/*
    override fun onStop() {
        backEvent -= ::handleBack
        super.onStop()
    }*/

    private fun reloadStored() {
        context?.let { ctx ->
            homeViewModel.loadResumeWatching(ctx)
            val list = EnumSet.noneOf(WatchType::class.java)
            ctx.getKey<IntArray>(HOME_BOOKMARK_VALUE_LIST)?.map { WatchType.fromInternalId(it) }?.let {
                list.addAll(it)
            }
            homeViewModel.loadStoredData(ctx, list)
        }
    }

    /*private fun handleBack(poppedFragment: Boolean) {
        if (poppedFragment) {
            reloadStored()
        }
    }*/

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fixGrid()

        home_change_api.setOnClickListener(apiChangeClickListener)
        home_change_api_loading.setOnClickListener(apiChangeClickListener)

        observe(homeViewModel.apiName) { apiName ->
            context?.setKey(HOMEPAGE_API, apiName)
            home_provider_name?.text = apiName
            home_provider_meta_info?.isVisible = false

            getApiFromNameNull(apiName)?.let { currentApi ->
                val typeChoices = listOf(
                    Pair(R.string.movies, listOf(TvType.Movie)),
                    Pair(R.string.tv_series, listOf(TvType.TvSeries)),
                    Pair(R.string.documentaries, listOf(TvType.Documentary)),
                    Pair(R.string.cartoons, listOf(TvType.Cartoon)),
                    Pair(R.string.anime, listOf(TvType.Anime, TvType.ONA, TvType.AnimeMovie)),
                    Pair(R.string.torrent, listOf(TvType.Torrent)),
                ).filter { item -> currentApi.supportedTypes.any { type -> item.second.contains(type) } }
                home_provider_meta_info?.text = typeChoices.joinToString(separator = ", ") { getString(it.first) }
                home_provider_meta_info?.isVisible = true
            }
        }

        observe(homeViewModel.randomItems) { items ->
            if (items.isNullOrEmpty()) {
                toggleMainVisibility(false)
            } else {
                val tempAdapter = home_main_poster_recyclerview.adapter as HomeChildItemAdapter?
                // no need to reload if it has the same data
                if (tempAdapter != null && tempAdapter.cardList == items) {
                    toggleMainVisibility(true)
                    return@observe
                }

                val randomSize = items.size
                home_main_poster_recyclerview.adapter =
                    HomeChildItemAdapter(
                        items,
                        R.layout.home_result_big_grid,
                        nextFocusUp = home_main_poster_recyclerview.nextFocusUpId,
                        nextFocusDown = home_main_poster_recyclerview.nextFocusDownId
                    ) { callback ->
                        handleSearchClickCallback(activity, callback)
                    }
                home_main_poster_recyclerview?.post {
                    (home_main_poster_recyclerview?.layoutManager as CenterZoomLayoutManager?)?.let { manager ->
                        manager.updateSize(forceUpdate = true)
                        if (randomSize > 2) {
                            manager.scrollToPosition(randomSize / 2)
                            manager.snap { dx ->
                                home_main_poster_recyclerview?.post {
                                    // this is the best I can do, fuck android for not including instant scroll
                                    home_main_poster_recyclerview?.smoothScrollBy(dx, 0)
                                }
                            }
                        }
                    }
                }
                toggleMainVisibility(true)
            }
        }

        observe(homeViewModel.page) { data ->
            when (data) {
                is Resource.Success -> {
                    home_loading_shimmer?.stopShimmer()

                    val d = data.value

                    currentHomePage = d
                    (home_master_recycler?.adapter as ParentItemAdapter?)?.items =
                        d?.items?.mapNotNull {
                            try {
                                HomePageList(it.name, it.list.filterSearchResponse())
                            } catch (e: Exception) {
                                logError(e)
                                null
                            }
                        } ?: listOf()

                    home_master_recycler?.adapter?.notifyDataSetChanged()

                    home_loading?.isVisible = false
                    home_loading_error?.isVisible = false
                    home_loaded?.isVisible = true
                }
                is Resource.Failure -> {
                    home_loading_shimmer?.stopShimmer()

                    result_error_text.text = data.errorString

                    home_reload_connectionerror.setOnClickListener(apiChangeClickListener)

                    home_reload_connection_open_in_browser.setOnClickListener { view ->
                        val validAPIs = apis//.filter { api -> api.hasMainPage }

                        view.popupMenuNoIconsAndNoStringRes(validAPIs.mapIndexed { index, api ->
                            Pair(
                                index,
                                api.name
                            )
                        }) {
                            val i = Intent(Intent.ACTION_VIEW)
                            i.data = Uri.parse(validAPIs[itemId].mainUrl)
                            startActivity(i)
                        }
                    }

                    home_loading?.isVisible = false
                    home_loading_error?.isVisible = true
                    home_loaded?.isVisible = false
                }
                is Resource.Loading -> {
                    home_loading_shimmer?.startShimmer()
                    home_loading?.isVisible = true
                    home_loading_error?.isVisible = false
                    home_loaded?.isVisible = false
                }
            }
        }


        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> = ParentItemAdapter(listOf(), { callback ->
            handleSearchClickCallback(activity, callback)
        }, { item ->
            activity?.loadHomepageList(item)
        })

        val toggleList = listOf(
            Pair(home_type_watching_btt, WatchType.WATCHING),
            Pair(home_type_completed_btt, WatchType.COMPLETED),
            Pair(home_type_dropped_btt, WatchType.DROPPED),
            Pair(home_type_on_hold_btt, WatchType.ONHOLD),
            Pair(home_plan_to_watch_btt, WatchType.PLANTOWATCH),
        )

        for (item in toggleList) {
            val watch = item.second
            item.first?.setOnClickListener { itemView ->
                val list = EnumSet.noneOf(WatchType::class.java)
                itemView.context.getKey<IntArray>(HOME_BOOKMARK_VALUE_LIST)?.map { WatchType.fromInternalId(it) }?.let {
                    list.addAll(it)
                }

                if (list.contains(watch)) {
                    list.remove(watch)
                } else {
                    list.add(watch)
                }
                homeViewModel.loadStoredData(itemView.context, list)
            }

            item.first?.setOnLongClickListener { itemView ->
                homeViewModel.loadStoredData(itemView.context, EnumSet.of(watch))
                return@setOnLongClickListener true
            }
        }

        observe(homeViewModel.availableWatchStatusTypes) { availableWatchStatusTypes ->
            context?.setKey(
                HOME_BOOKMARK_VALUE_LIST,
                availableWatchStatusTypes.first.map { it.internalId }.toIntArray()
            )

            for (item in toggleList) {
                val watch = item.second
                item.first?.apply {
                    isVisible = availableWatchStatusTypes.second.contains(watch)
                    isSelected = availableWatchStatusTypes.first.contains(watch)
                }
            }

            /*home_bookmark_select?.setOnClickListener {
                it.popupMenuNoIcons(availableWatchStatusTypes.second.map { type ->
                    Pair(
                        type.internalId,
                        type.stringRes
                    )
                }) {
                    homeViewModel.loadStoredData(it.context, WatchType.fromInternalId(this.itemId))
                }
            }
            home_bookmarked_parent_item_title?.text = getString(availableWatchStatusTypes.first.stringRes)*/
        }

        observe(homeViewModel.bookmarks) { pair ->
            home_bookmarked_holder.isVisible = pair.first

            val bookmarks = pair.second
            (home_bookmarked_child_recyclerview?.adapter as HomeChildItemAdapter?)?.cardList = bookmarks
            home_bookmarked_child_recyclerview?.adapter?.notifyDataSetChanged()

            home_bookmarked_child_more_info?.setOnClickListener {
                activity?.loadHomepageList(
                    HomePageList(
                        getString(R.string.error_bookmarks_text), //home_bookmarked_parent_item_title?.text?.toString() ?: getString(R.string.error_bookmarks_text),
                        bookmarks
                    )
                )
            }
        }

        observe(homeViewModel.resumeWatching) { resumeWatching ->
            home_watch_holder?.isVisible = resumeWatching.isNotEmpty()
            (home_watch_child_recyclerview?.adapter as HomeChildItemAdapter?)?.cardList = resumeWatching
            home_watch_child_recyclerview?.adapter?.notifyDataSetChanged()

            home_watch_child_more_info?.setOnClickListener {
                activity?.loadHomepageList(
                    HomePageList(
                        home_watch_parent_item_title?.text?.toString() ?: getString(R.string.continue_watching),
                        resumeWatching
                    )
                )
            }
        }

        home_bookmarked_child_recyclerview.adapter = HomeChildItemAdapter(
            ArrayList(),
            nextFocusUp = home_bookmarked_child_recyclerview?.nextFocusUpId,
            nextFocusDown = home_bookmarked_child_recyclerview?.nextFocusDownId
        ) { callback ->
            if (callback.action == SEARCH_ACTION_SHOW_METADATA) {
                val id = callback.card.id
                if (id != null) {
                    callback.view.popupMenuNoIcons(listOf(Pair(0, R.string.action_remove_from_bookmarks))) {
                        if (itemId == 0) {
                            activity?.setResultWatchState(id, WatchType.NONE.internalId)
                            reloadStored()
                        }
                    }
                }
            } else {
                handleSearchClickCallback(activity, callback)
            }
        }

        home_watch_child_recyclerview.adapter = HomeChildItemAdapter(
            ArrayList(),
            nextFocusUp = home_watch_child_recyclerview?.nextFocusUpId,
            nextFocusDown = home_watch_child_recyclerview?.nextFocusDownId
        ) { callback ->
            if (callback.action == SEARCH_ACTION_SHOW_METADATA) {
                val id = callback.card.id
                if (id != null) {
                    callback.view.popupMenuNoIcons(
                        listOf(
                            Pair(1, R.string.action_open_watching),
                            Pair(0, R.string.action_remove_watching)
                        )
                    ) {
                        if (itemId == 1) {
                            handleSearchClickCallback(
                                activity,
                                SearchClickCallback(SEARCH_ACTION_LOAD, callback.view, -1, callback.card)
                            )
                            reloadStored()
                        }
                        if (itemId == 0) {
                            val card = callback.card
                            if (card is DataStoreHelper.ResumeWatchingResult) {
                                context?.removeLastWatched(card.parentId)
                                reloadStored()
                            }
                        }
                    }
                }
            } else {
                handleSearchClickCallback(activity, callback)
            }
        }

        context?.fixPaddingStatusbarView(home_statusbar)
        context?.fixPaddingStatusbar(home_loading_statusbar)

        home_master_recycler.adapter = adapter
        home_master_recycler.layoutManager = GridLayoutManager(context, 1)

        LinearSnapHelper().attachToRecyclerView(home_main_poster_recyclerview) // snap
        val centerLayoutManager = CenterZoomLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        centerLayoutManager.setOnSizeListener { index ->
            (home_main_poster_recyclerview?.adapter as HomeChildItemAdapter?)?.cardList?.get(index)?.let { random ->
                home_main_play.setOnClickListener {
                    activity.loadSearchResult(random, START_ACTION_RESUME_LATEST)
                }
                home_main_info.setOnClickListener {
                    activity.loadSearchResult(random)
                }

                home_main_text.text =
                    random.name + if (random is AnimeSearchResponse && !random.dubStatus.isNullOrEmpty()) {
                        random.dubStatus.joinToString(prefix = " • ", separator = " | ") { it.name }
                    } else ""
            }
        }
        home_main_poster_recyclerview?.layoutManager = centerLayoutManager  // scale

        reloadStored()
        val apiName = context?.getKey<String>(HOMEPAGE_API)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
        val currentPrefMedia = settingsManager.getInt(getString(R.string.preferred_media_settings), 0)
        if (homeViewModel.apiName.value != apiName || apiName == null) {
            //println("Caught home: " + homeViewModel.apiName.value + " at " + apiName)
            homeViewModel.loadAndCancel(apiName, currentPrefMedia)
        }

        // nice profile pic on homepage
        home_profile_picture_holder?.isVisible = false
        context?.let { ctx ->
            // just in case
            if (ctx.isTvSettings()) {
                home_change_api_loading?.isFocusable = true
                home_change_api_loading?.isFocusableInTouchMode = true
                home_change_api?.isFocusable = true
                home_change_api?.isFocusableInTouchMode = true
                // home_bookmark_select?.isFocusable = true
                // home_bookmark_select?.isFocusableInTouchMode = true
            }

            for (syncApi in OAuth2API.OAuth2Apis) {
                val login = syncApi.loginInfo(ctx)
                val pic = login?.profilePicture
                if (pic != null) {
                    home_profile_picture.setImage(pic)
                    home_profile_picture_holder.isVisible = true
                    break
                }
            }
        }
    }
}