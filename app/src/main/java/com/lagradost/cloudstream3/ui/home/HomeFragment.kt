package com.lagradost.cloudstream3.ui.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.MainActivity.Companion.backEvent
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.getGridIsCompact
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.result.START_ACTION_RESUME_LATEST
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_SHOW_METADATA
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchHelper.handleSearchClickCallback
import com.lagradost.cloudstream3.utils.AppUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper.setResultWatchState
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.HOMEPAGE_API
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIcons
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
                if (callback.action == SEARCH_ACTION_LOAD) {
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

    private lateinit var homeViewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    private var currentHomePage: HomePageResponse? = null
    var currentMainIndex = 0
    var currentMainList: ArrayList<SearchResponse> = ArrayList()

    private fun toggleMainVisibility(visible: Boolean) {
        home_main_holder.visibility = if (visible) View.VISIBLE else View.GONE
    }

    @SuppressLint("SetTextI18n")
    private fun chooseRandomMainPage(item: SearchResponse? = null): SearchResponse? {
        val home = currentHomePage
        if (home != null && home.items.isNotEmpty()) {
            var random: SearchResponse? = item

            var breakCount = 0
            val MAX_BREAK_COUNT = 10

            while (random?.posterUrl == null) {
                random = home.items.random().list.random()
                breakCount++
                if (breakCount > MAX_BREAK_COUNT) {
                    break
                }
            }

            if (random?.posterUrl != null) {
                home_main_poster.setOnClickListener {
                    activity.loadSearchResult(random)
                }
                home_main_play.setOnClickListener {
                    activity.loadSearchResult(random, START_ACTION_RESUME_LATEST)
                }
                home_main_info.setOnClickListener {
                    activity.loadSearchResult(random)
                }

                home_main_text.text = random.name + if (random is AnimeSearchResponse) {
                    random.dubStatus?.joinToString(prefix = " • ", separator = " | ") { it.name }
                } else ""
                val glideUrl =
                    GlideUrl(random.posterUrl)
                requireContext().let {
                    Glide.with(it)
                        .load(glideUrl)
                        .into(home_main_poster)
/*
                    Glide.with(it)
                        .load(glideUrl)
                        .apply(RequestOptions.bitmapTransform(BlurTransformation(80, 3)))
                        .into(result_poster_blur)*/
                }

                toggleMainVisibility(true)
                return random
            } else {
                toggleMainVisibility(false)
                return null
            }
        }
        return null
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
        val validAPIs = apis.filter { api -> api.hasMainPage }

        view.popupMenuNoIconsAndNoStringRes(validAPIs.mapIndexed { index, api -> Pair(index, api.name) }) {
            homeViewModel.load(validAPIs[itemId])
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fixGrid()
    }

    override fun onResume() {
        backEvent += ::handleBack
        super.onResume()
    }

    override fun onStop() {
        backEvent -= ::handleBack
        super.onStop()
    }

    private fun reloadStored() {
        context?.let { ctx ->
            homeViewModel.loadStoredData(ctx, WatchType.fromInternalId(ctx.getKey(HOME_BOOKMARK_VALUE)))
        }
    }

    private fun handleBack(poppedFragment: Boolean) {
        if (poppedFragment) {
            reloadStored()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fixGrid()

        home_reroll_next.setOnClickListener {
            currentMainIndex++
            if (currentMainIndex >= currentMainList.size) {
                val newItem = chooseRandomMainPage()
                if (newItem != null) {
                    currentMainList.add(newItem)
                }
                currentMainIndex = currentMainList.size - 1
            }
            chooseRandomMainPage(currentMainList[currentMainIndex])
        }

        home_reroll_prev.setOnClickListener {
            currentMainIndex--
            if (currentMainIndex < 0) {
                val newItem = chooseRandomMainPage()
                if (newItem != null) {
                    currentMainList.add(0, newItem)
                }
                currentMainIndex = 0
            }
            chooseRandomMainPage(currentMainList[currentMainIndex])
        }

        home_change_api.setOnClickListener(apiChangeClickListener)

        observe(homeViewModel.apiName) {
            context?.setKey(HOMEPAGE_API, it)
        }

        observe(homeViewModel.page) { data ->
            when (data) {
                is Resource.Success -> {
                    val d = data.value
                    currentHomePage = d
                    (home_master_recycler?.adapter as ParentItemAdapter?)?.items = d.items
                    home_master_recycler?.adapter?.notifyDataSetChanged()
                    currentMainList.clear()
                    chooseRandomMainPage()?.let { response ->
                        currentMainList.add(response)
                    }
                    currentMainIndex = 0

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
            home_bookmarked_holder.visibility = if (bookmarks.isNotEmpty()) View.VISIBLE else View.GONE
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

        context?.fixPaddingStatusbar(home_root)

        home_master_recycler.adapter = adapter
        home_master_recycler.layoutManager = GridLayoutManager(context, 1)

        reloadStored()
        homeViewModel.load(context?.getKey<String>(HOMEPAGE_API))
    }
}