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
import com.lagradost.cloudstream3.syncproviders.OAuth2Interface
import com.lagradost.cloudstream3.ui.APIRepository.Companion.noneApi
import com.lagradost.cloudstream3.ui.APIRepository.Companion.randomApi
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.result.START_ACTION_RESUME_LATEST
import com.lagradost.cloudstream3.ui.search.*
import com.lagradost.cloudstream3.ui.search.SearchFragment.Companion.filterSearchResponse
import com.lagradost.cloudstream3.ui.search.SearchHelper.handleSearchClickCallback
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.removeLastWatched
import com.lagradost.cloudstream3.utils.DataStoreHelper.setResultWatchState
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.HOMEPAGE_API
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbarView
import com.lagradost.cloudstream3.utils.UIHelper.getGridIsCompact
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIcons
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.widget.CenterZoomLayoutManager
import kotlinx.android.synthetic.main.fragment_home.*

const val HOME_BOOKMARK_VALUE = "home_bookmarked_last"

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
                bottomSheetDialogBuilder.dismiss()
            }

            // Span settings
            recycle.spanCount = currentSpan

            recycle.adapter = SearchAdapter(item.list, recycle) { callback ->
                handleSearchClickCallback(this, callback)
                if (callback.action == SEARCH_ACTION_LOAD || callback.action == SEARCH_ACTION_PLAY_FILE) {
                    bottomSheetDialogBuilder.dismiss()
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

    @SuppressLint("SetTextI18n")
    private fun chooseRandomMainPage() {
        val home = currentHomePage
        if (home != null && home.items.isNotEmpty()) {
            val currentList =
                home.items.shuffled().filter { !it.list.isNullOrEmpty() }.flatMap { it.list }.distinctBy { it.url }
                    .toList()

            if (currentList.isNullOrEmpty()) {
                toggleMainVisibility(false)
            } else {
                val randomItems = currentList.shuffled()
                val randomSize = randomItems.size
                home_main_poster_recyclerview.adapter =
                    HomeChildItemAdapter(randomItems, R.layout.home_result_big_grid) { callback ->
                        handleSearchClickCallback(activity, callback)
                    }
                home_main_poster_recyclerview.post {
                    (home_main_poster_recyclerview.layoutManager as CenterZoomLayoutManager?)?.let { manager ->

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
        } else {
            toggleMainVisibility(false)
        }
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
            homeViewModel.loadStoredData(ctx, WatchType.fromInternalId(ctx.getKey(HOME_BOOKMARK_VALUE)))
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
                    Pair(R.string.cartoons, listOf(TvType.Cartoon)),
                    Pair(R.string.anime, listOf(TvType.Anime, TvType.ONA, TvType.AnimeMovie)),
                    Pair(R.string.torrent, listOf(TvType.Torrent)),
                ).filter { item -> currentApi.supportedTypes.any { type -> item.second.contains(type) } }
                home_provider_meta_info?.text = typeChoices.joinToString(separator = ", ") { getString(it.first) }
                home_provider_meta_info?.isVisible = true
            }
        }

        observe(homeViewModel.page) { data ->
            when (data) {
                is Resource.Success -> {
                    val d = data.value

                    currentHomePage = d
                    (home_master_recycler?.adapter as ParentItemAdapter?)?.items =
                        d.items.mapNotNull {
                            try {
                                HomePageList(it.name, it.list.filterSearchResponse())
                            } catch (e: Exception) {
                                logError(e)
                                null
                            }
                        }

                    home_master_recycler?.adapter?.notifyDataSetChanged()
                    chooseRandomMainPage()

                    home_loading.visibility = View.GONE
                    home_loading_error.visibility = View.GONE
                    home_loaded.visibility = View.VISIBLE
                }
                is Resource.Failure -> {
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

                    home_loading.visibility = View.GONE
                    home_loading_error.visibility = View.VISIBLE
                    home_loaded.visibility = View.GONE
                }
                is Resource.Loading -> {
                    home_loading.visibility = View.VISIBLE
                    home_loading_error.visibility = View.GONE
                    home_loaded.visibility = View.GONE
                }
            }
        }


        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> = ParentItemAdapter(listOf(), { callback ->
            handleSearchClickCallback(activity, callback)
        }, { item ->
            activity?.loadHomepageList(item)
        })

        observe(homeViewModel.availableWatchStatusTypes) { availableWatchStatusTypes ->
            context?.setKey(HOME_BOOKMARK_VALUE, availableWatchStatusTypes.first.internalId)
            home_bookmark_select?.setOnClickListener {
                it.popupMenuNoIcons(availableWatchStatusTypes.second.map { type ->
                    Pair(
                        type.internalId,
                        type.stringRes
                    )
                }) {
                    homeViewModel.loadStoredData(it.context, WatchType.fromInternalId(this.itemId))
                }
            }
            home_bookmarked_parent_item_title?.text = getString(availableWatchStatusTypes.first.stringRes)
        }

        observe(homeViewModel.bookmarks) { bookmarks ->
            home_bookmarked_holder.isVisible = bookmarks.isNotEmpty()
            (home_bookmarked_child_recyclerview?.adapter as HomeChildItemAdapter?)?.cardList = bookmarks
            home_bookmarked_child_recyclerview?.adapter?.notifyDataSetChanged()

            home_bookmarked_child_more_info.setOnClickListener {
                activity?.loadHomepageList(
                    HomePageList(
                        home_bookmarked_parent_item_title?.text?.toString() ?: getString(R.string.error_bookmarks_text),
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

        home_bookmarked_child_recyclerview.adapter = HomeChildItemAdapter(ArrayList()) { callback ->
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

        home_watch_child_recyclerview.adapter = HomeChildItemAdapter(ArrayList()) { callback ->
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
                                SearchClickCallback(SEARCH_ACTION_LOAD, callback.view, callback.card)
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
            (home_main_poster_recyclerview.adapter as HomeChildItemAdapter?)?.cardList?.get(index)?.let { random ->
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
        home_main_poster_recyclerview.layoutManager = centerLayoutManager  // scale

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
            for (syncApi in OAuth2Interface.OAuth2Apis) {
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