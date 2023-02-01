package com.lagradost.cloudstream3.ui.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.APIHolder.getApiProviderLangSettings
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.MainActivity.Companion.bookmarksUpdatedEvent
import com.lagradost.cloudstream3.MainActivity.Companion.mainPluginsLoadedEvent
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.APIRepository.Companion.noneApi
import com.lagradost.cloudstream3.ui.APIRepository.Companion.randomApi
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.search.*
import com.lagradost.cloudstream3.ui.search.SearchHelper.handleSearchClickCallback
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.AppUtils.addProgramsToContinueWatching
import com.lagradost.cloudstream3.utils.AppUtils.isRecyclerScrollable
import com.lagradost.cloudstream3.utils.AppUtils.loadResult
import com.lagradost.cloudstream3.utils.AppUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.AppUtils.ownHide
import com.lagradost.cloudstream3.utils.AppUtils.ownShow
import com.lagradost.cloudstream3.utils.AppUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.SubtitleHelper.getFlagFromIso
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import com.lagradost.cloudstream3.utils.USER_SELECTED_HOMEPAGE_API
import kotlinx.android.synthetic.main.activity_main_tv.*
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.fragment_home.home_api_fab
import kotlinx.android.synthetic.main.fragment_home.home_change_api_loading
import kotlinx.android.synthetic.main.fragment_home.home_loading
import kotlinx.android.synthetic.main.fragment_home.home_loading_error
import kotlinx.android.synthetic.main.fragment_home.home_loading_shimmer
import kotlinx.android.synthetic.main.fragment_home.home_loading_statusbar
import kotlinx.android.synthetic.main.fragment_home.home_master_recycler
import kotlinx.android.synthetic.main.fragment_home.home_reload_connection_open_in_browser
import kotlinx.android.synthetic.main.fragment_home.home_reload_connectionerror
import kotlinx.android.synthetic.main.fragment_home.result_error_text
import kotlinx.android.synthetic.main.fragment_home_tv.*
import kotlinx.android.synthetic.main.fragment_result.*
import kotlinx.android.synthetic.main.fragment_search.*
import kotlinx.android.synthetic.main.home_episodes_expanded.*
import kotlinx.android.synthetic.main.tvtypes_chips.*
import kotlinx.android.synthetic.main.tvtypes_chips.view.*
import java.util.*


const val HOME_BOOKMARK_VALUE_LIST = "home_bookmarked_last_list"
const val HOME_PREF_HOMEPAGE = "home_pref_homepage"

class HomeFragment : Fragment() {
    companion object {
        val configEvent = Event<Int>()
        var currentSpan = 1
        val listHomepageItems = mutableListOf<SearchResponse>()

        private val errorProfilePics = listOf(
            R.drawable.monke_benene,
            R.drawable.monke_burrito,
            R.drawable.monke_coco,
            R.drawable.monke_cookie,
            R.drawable.monke_flusdered,
            R.drawable.monke_funny,
            R.drawable.monke_like,
            R.drawable.monke_party,
            R.drawable.monke_sob,
            R.drawable.monke_drink,
        )

        val errorProfilePic = errorProfilePics.random()

        //fun Activity.loadHomepageList(
        //    item: HomePageList,
        //    deleteCallback: (() -> Unit)? = null,
        //) {
        //    loadHomepageList(
        //        expand = HomeViewModel.ExpandableHomepageList(item, 1, false),
        //        deleteCallback = deleteCallback,
        //        expandCallback = null
        //    )
        //}

        // returns a BottomSheetDialog that will be hidden with OwnHidden upon hide, and must be saved to be able call ownShow in onCreateView
        fun Activity.loadHomepageList(
            expand: HomeViewModel.ExpandableHomepageList,
            deleteCallback: (() -> Unit)? = null,
            expandCallback: (suspend (String) -> HomeViewModel.ExpandableHomepageList?)? = null,
            dismissCallback : (() -> Unit),
        ): BottomSheetDialog {
            val context = this
            val bottomSheetDialogBuilder = BottomSheetDialog(context)

            bottomSheetDialogBuilder.setContentView(R.layout.home_episodes_expanded)
            val title = bottomSheetDialogBuilder.findViewById<TextView>(R.id.home_expanded_text)!!

            //title.findViewTreeLifecycleOwner().lifecycle.addObserver()

            val item = expand.list
            title.text = item.name
            val recycle =
                bottomSheetDialogBuilder.findViewById<AutofitRecyclerView>(R.id.home_expanded_recycler)!!
            val titleHolder =
                bottomSheetDialogBuilder.findViewById<FrameLayout>(R.id.home_expanded_drag_down)!!

            // main {
            //(bottomSheetDialogBuilder.ownerActivity as androidx.fragment.app.FragmentActivity?)?.supportFragmentManager?.fragments?.lastOrNull()?.viewLifecycleOwner?.apply {
            //    println("GOT LIFE: lifecycle $this")
            //    this.lifecycle.addObserver(object : DefaultLifecycleObserver {
            //        override fun onResume(owner: LifecycleOwner) {
            //            super.onResume(owner)
            //            println("onResume!!!!")
            //            bottomSheetDialogBuilder?.ownShow()
            //        }

            //        override fun onStop(owner: LifecycleOwner) {
            //            super.onStop(owner)
            //            bottomSheetDialogBuilder?.ownHide()
            //        }
            //    })
            //}
            // }
            val delete = bottomSheetDialogBuilder.home_expanded_delete
            delete.isGone = deleteCallback == null
            if (deleteCallback != null) {
                delete.setOnClickListener {
                    try {
                        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
                        val dialogClickListener =
                            DialogInterface.OnClickListener { _, which ->
                                when (which) {
                                    DialogInterface.BUTTON_POSITIVE -> {
                                        deleteCallback.invoke()
                                        bottomSheetDialogBuilder.dismissSafe(this)
                                    }
                                    DialogInterface.BUTTON_NEGATIVE -> {}
                                }
                            }

                        builder.setTitle(R.string.delete_file)
                            .setMessage(
                                context.getString(R.string.delete_message).format(
                                    item.name
                                )
                            )
                            .setPositiveButton(R.string.delete, dialogClickListener)
                            .setNegativeButton(R.string.cancel, dialogClickListener)
                            .show().setDefaultFocus()
                    } catch (e: Exception) {
                        logError(e)
                        // ye you somehow fucked up formatting did you?
                    }
                }
            }

            titleHolder.setOnClickListener {
                bottomSheetDialogBuilder.dismissSafe(this)
            }


            // Span settings
            recycle.spanCount = currentSpan

            recycle.adapter = SearchAdapter(item.list.toMutableList(), recycle) { callback ->
                handleSearchClickCallback(this, callback)
                if (callback.action == SEARCH_ACTION_LOAD || callback.action == SEARCH_ACTION_PLAY_FILE) {
                    bottomSheetDialogBuilder.ownHide() // we hide here because we want to resume it later
                    //bottomSheetDialogBuilder.dismissSafe(this)
                }
            }.apply {
                hasNext = expand.hasNext
            }

            recycle.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                var expandCount = 0
                val name = expand.list.name

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)

                    val adapter = recyclerView.adapter
                    if (adapter !is SearchAdapter) return

                    val count = adapter.itemCount
                    val currentHasNext = adapter.hasNext
                    //!recyclerView.canScrollVertically(1)
                    if (!recyclerView.isRecyclerScrollable() && currentHasNext && expandCount != count) {
                        expandCount = count
                        ioSafe {
                            expandCallback?.invoke(name)?.let { newExpand ->
                                (recyclerView.adapter as? SearchAdapter?)?.apply {
                                    hasNext = newExpand.hasNext
                                    updateList(newExpand.list.list)
                                }
                            }
                        }
                    }
                }
            })

            val spanListener = { span: Int ->
                recycle.spanCount = span
                //(recycle.adapter as SearchAdapter).notifyDataSetChanged()
            }

            configEvent += spanListener

            bottomSheetDialogBuilder.setOnDismissListener {
                dismissCallback.invoke()
                configEvent -= spanListener
            }

            //(recycle.adapter as SearchAdapter).notifyDataSetChanged()

            bottomSheetDialogBuilder.show()
            return bottomSheetDialogBuilder
        }

        fun getPairList(
            anime: Chip?,
            cartoons: Chip?,
            tvs: Chip?,
            docs: Chip?,
            movies: Chip?,
            asian: Chip?,
            livestream: Chip?,
            nsfw: Chip?,
            others: Chip?,
        ): List<Pair<Chip?, List<TvType>>> {
            // This list should be same order as home screen to aid navigation
            return listOf(
                Pair(movies, listOf(TvType.Movie, TvType.Torrent)),
                Pair(tvs, listOf(TvType.TvSeries)),
                Pair(anime, listOf(TvType.Anime, TvType.OVA, TvType.AnimeMovie)),
                Pair(asian, listOf(TvType.AsianDrama)),
                Pair(cartoons, listOf(TvType.Cartoon)),
                Pair(docs, listOf(TvType.Documentary)),
                Pair(livestream, listOf(TvType.Live)),
                Pair(nsfw, listOf(TvType.NSFW)),
                Pair(others, listOf(TvType.Others)),
            )
        }

        private fun getPairList(header: ChipGroup) = getPairList(
            header.home_select_anime,
            header.home_select_cartoons,
            header.home_select_tv_series,
            header.home_select_documentaries,
            header.home_select_movies,
            header.home_select_asian,
            header.home_select_livestreams,
            header.home_select_nsfw,
            header.home_select_others
        )

        fun validateChips(header: ChipGroup?, validTypes: List<TvType>) {
            if (header == null) return
            val pairList = getPairList(header)
            for ((button, types) in pairList) {
                val isValid = validTypes.any { types.contains(it) }
                button?.isVisible = isValid
            }
        }

        fun updateChips(header: ChipGroup?, selectedTypes: List<TvType>) {
            if (header == null) return
            val pairList = getPairList(header)
            for ((button, types) in pairList) {
                button?.isChecked =
                    button?.isVisible == true && selectedTypes.any { types.contains(it) }
            }
        }

        fun bindChips(
            header: ChipGroup?,
            selectedTypes: List<TvType>,
            validTypes: List<TvType>,
            callback: (List<TvType>) -> Unit
        ) {
            if (header == null) return
            val pairList = getPairList(header)
            for ((button, types) in pairList) {
                val isValid = validTypes.any { types.contains(it) }
                button?.isVisible = isValid
                button?.isChecked = isValid && selectedTypes.any { types.contains(it) }
                button?.setOnCheckedChangeListener { _, _ ->
                    val list = ArrayList<TvType>()
                    for ((sbutton, vvalidTypes) in pairList) {
                        if (sbutton?.isChecked == true)
                            list.addAll(vvalidTypes)
                    }
                    callback(list)
                }
            }
        }

        fun Context.selectHomepage(selectedApiName: String?, callback: (String) -> Unit) {
            val validAPIs = filterProviderByPreferredMedia().toMutableList()

            validAPIs.add(0, randomApi)
            validAPIs.add(0, noneApi)
            //val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            //builder.setView(R.layout.home_select_mainpage)
            val builder =
                BottomSheetDialog(this)

            builder.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            builder.setContentView(R.layout.home_select_mainpage)
            builder.show()
            builder.let { dialog ->
                val isMultiLang = getApiProviderLangSettings().let { set ->
                    set.size > 1 || set.contains(AllLanguagesName)
                }
                //dialog.window?.setGravity(Gravity.BOTTOM)

                var currentApiName = selectedApiName

                var currentValidApis: MutableList<MainAPI> = mutableListOf()
                val preSelectedTypes = this.getKey<List<String>>(HOME_PREF_HOMEPAGE)
                    ?.mapNotNull { listName -> TvType.values().firstOrNull { it.name == listName } }
                    ?.toMutableList()
                    ?: mutableListOf(TvType.Movie, TvType.TvSeries)

                val cancelBtt = dialog.findViewById<MaterialButton>(R.id.cancel_btt)
                val applyBtt = dialog.findViewById<MaterialButton>(R.id.apply_btt)

                cancelBtt?.setOnClickListener {
                    dialog.dismissSafe()
                }

                applyBtt?.setOnClickListener {
                    if (currentApiName != selectedApiName) {
                        currentApiName?.let(callback)
                    }
                    dialog.dismissSafe()
                }

                val listView = dialog.findViewById<ListView>(R.id.listview1)
                val arrayAdapter = ArrayAdapter<String>(this, R.layout.sort_bottom_single_choice)
                listView?.adapter = arrayAdapter
                listView?.choiceMode = AbsListView.CHOICE_MODE_SINGLE

                listView?.setOnItemClickListener { _, _, i, _ ->
                    if (currentValidApis.isNotEmpty()) {
                        currentApiName = currentValidApis[i].name
                        //to switch to apply simply remove this
                        currentApiName?.let(callback)
                        dialog.dismissSafe()
                    }
                }

                fun updateList() {
                    this.setKey(HOME_PREF_HOMEPAGE, preSelectedTypes)

                    arrayAdapter.clear()
                    currentValidApis = validAPIs.filter { api ->
                        api.hasMainPage && api.supportedTypes.any {
                            preSelectedTypes.contains(it)
                        }
                    }.sortedBy { it.name.lowercase() }.toMutableList()
                    currentValidApis.addAll(0, validAPIs.subList(0, 2))

                    val names =
                        currentValidApis.map { if (isMultiLang) "${getFlagFromIso(it.lang)?.plus(" ") ?: ""}${it.name}" else it.name }
                    val index = currentValidApis.map { it.name }.indexOf(currentApiName)
                    listView?.setItemChecked(index, true)
                    arrayAdapter.addAll(names)
                    arrayAdapter.notifyDataSetChanged()
                }

                bindChips(
                    dialog.home_select_group,
                    preSelectedTypes,
                    validAPIs.flatMap { it.supportedTypes }.distinct()
                ) { list ->
                    preSelectedTypes.clear()
                    preSelectedTypes.addAll(list)
                    updateList()
                }
                updateList()
            }
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
        bottomSheetDialog?.ownShow()
        val layout =
            if (isTvSettings()) R.layout.fragment_home_tv else R.layout.fragment_home
        return inflater.inflate(layout, container, false)
    }

    override fun onDestroyView() {
        bottomSheetDialog?.ownHide()
        super.onDestroyView()
    }

    private fun fixGrid() {
        activity?.getSpanCount()?.let {
            currentSpan = it
        }
        configEvent.invoke(currentSpan)
    }

    private val apiChangeClickListener = View.OnClickListener { view ->
        view.context.selectHomepage(currentApiName) { api ->
            homeViewModel.loadAndCancel(api)
        }
        /*val validAPIs = view.context?.filterProviderByPreferredMedia()?.toMutableList() ?: mutableListOf()

        validAPIs.add(0, randomApi)
        validAPIs.add(0, noneApi)
        view.popupMenuNoIconsAndNoStringRes(validAPIs.mapIndexed { index, api -> Pair(index, api.name) }) {
            homeViewModel.loadAndCancel(validAPIs[itemId].name)
        }*/
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        //(home_preview_viewpager?.adapter as? HomeScrollAdapter)?.notifyDataSetChanged()
        fixGrid()
    }

    fun bookmarksUpdated(_data : Boolean) {
        reloadStored()
    }

    override fun onResume() {
        super.onResume()
        reloadStored()
        bookmarksUpdatedEvent += ::bookmarksUpdated
        afterPluginsLoadedEvent += ::afterPluginsLoaded
        mainPluginsLoadedEvent += ::afterMainPluginsLoaded
    }

    override fun onStop() {
        bookmarksUpdatedEvent -= ::bookmarksUpdated
        afterPluginsLoadedEvent -= ::afterPluginsLoaded
        mainPluginsLoadedEvent -= ::afterMainPluginsLoaded
        super.onStop()
    }

    private fun reloadStored() {
        homeViewModel.loadResumeWatching()
        val list = EnumSet.noneOf(WatchType::class.java)
        getKey<IntArray>(HOME_BOOKMARK_VALUE_LIST)?.map { WatchType.fromInternalId(it) }?.let {
            list.addAll(it)
        }
        homeViewModel.loadStoredData(list)
    }

    private fun afterMainPluginsLoaded(unused: Boolean = false) {
        loadHomePage(false)
    }

    private fun afterPluginsLoaded(forceReload: Boolean) {
        loadHomePage(forceReload)
    }

    private fun loadHomePage(forceReload: Boolean) {
        val apiName = context?.getKey<String>(USER_SELECTED_HOMEPAGE_API)

        if (homeViewModel.apiName.value != apiName || apiName == null || forceReload) {
            //println("Caught home: " + homeViewModel.apiName.value + " at " + apiName)
            homeViewModel.loadAndCancel(apiName, forceReload)
        }
    }

    private fun homeHandleSearch(callback: SearchClickCallback) {
        if (callback.action == SEARCH_ACTION_FOCUSED) {
            //focusCallback(callback.card)
        } else {
            handleSearchClickCallback(activity, callback)
        }
    }

    private var currentApiName: String? = null
    private var toggleRandomButton = false

    private var bottomSheetDialog: BottomSheetDialog? = null

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fixGrid()

        home_change_api_loading?.setOnClickListener(apiChangeClickListener)
        home_api_fab?.setOnClickListener(apiChangeClickListener)
        home_random?.setOnClickListener {
            if (listHomepageItems.isNotEmpty()) {
                activity.loadSearchResult(listHomepageItems.random())
            }
        }

        //Load value for toggling Random button. Hide at startup
        context?.let {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(it)
            toggleRandomButton =
                settingsManager.getBoolean(getString(R.string.random_button_key), false)
            home_random?.visibility = View.GONE
        }

        observe(homeViewModel.preview) { preview ->
            (home_master_recycler?.adapter as? HomeParentItemAdapterPreview?)?.setPreviewData(
                preview
            )
        }

        observe(homeViewModel.apiName) { apiName ->
            currentApiName = apiName
            home_api_fab?.text = apiName
            (home_master_recycler?.adapter as? HomeParentItemAdapterPreview?)?.setApiName(
                apiName
            )
        }

        observe(homeViewModel.page) { data ->
            when (data) {
                is Resource.Success -> {
                    home_loading_shimmer?.stopShimmer()

                    val d = data.value
                    val mutableListOfResponse = mutableListOf<SearchResponse>()
                    listHomepageItems.clear()

                    (home_master_recycler?.adapter as? ParentItemAdapter)?.updateList(
                        d.values.toMutableList(),
                        home_master_recycler
                    )

                    home_loading?.isVisible = false
                    home_loading_error?.isVisible = false
                    home_master_recycler?.isVisible = true
                    //home_loaded?.isVisible = true
                    if (toggleRandomButton) {
                        //Flatten list
                        d.values.forEach { dlist ->
                            mutableListOfResponse.addAll(dlist.list.list)
                        }
                        listHomepageItems.addAll(mutableListOfResponse.distinctBy { it.url })
                        home_random?.isVisible = listHomepageItems.isNotEmpty()
                    } else {
                        home_random?.isGone = true
                    }
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
                            try {
                                val i = Intent(Intent.ACTION_VIEW)
                                i.data = Uri.parse(validAPIs[itemId].mainUrl)
                                startActivity(i)
                            } catch (e: Exception) {
                                logError(e)
                            }
                        }
                    }

                    home_loading?.isVisible = false
                    home_loading_error?.isVisible = true
                    home_master_recycler?.isVisible = false
                    //home_loaded?.isVisible = false
                }
                is Resource.Loading -> {
                    (home_master_recycler?.adapter as? ParentItemAdapter)?.updateList(listOf())
                    home_loading_shimmer?.startShimmer()
                    home_loading?.isVisible = true
                    home_loading_error?.isVisible = false
                    home_master_recycler?.isVisible = false
                    //home_loaded?.isVisible = false
                }
            }
        }



        observe(homeViewModel.availableWatchStatusTypes) { availableWatchStatusTypes ->
            context?.setKey(
                HOME_BOOKMARK_VALUE_LIST,
                availableWatchStatusTypes.first.map { it.internalId }.toIntArray()
            )
            (home_master_recycler?.adapter as? HomeParentItemAdapterPreview?)?.setAvailableWatchStatusTypes(
                availableWatchStatusTypes
            )
        }

        observe(homeViewModel.bookmarks) { data ->
            (home_master_recycler?.adapter as? HomeParentItemAdapterPreview?)?.setBookmarkData(
                data
            )
        }

        observe(homeViewModel.resumeWatching) { resumeWatching ->
            (home_master_recycler?.adapter as? HomeParentItemAdapterPreview?)?.setResumeWatchingData(
                resumeWatching
            )
            if (isTrueTvSettings()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ioSafe {
                        activity?.addProgramsToContinueWatching(resumeWatching.mapNotNull { it as? DataStoreHelper.ResumeWatchingResult })
                    }
                }
            }
        }


        //context?.fixPaddingStatusbarView(home_statusbar)
        //context?.fixPaddingStatusbar(home_padding)
        context?.fixPaddingStatusbar(home_loading_statusbar)

        home_master_recycler?.adapter =
            HomeParentItemAdapterPreview(mutableListOf(), { callback ->
                homeHandleSearch(callback)
            }, { item ->
                bottomSheetDialog = activity?.loadHomepageList(item, expandCallback = {
                    homeViewModel.expandAndReturn(it)
                }, dismissCallback = {
                    bottomSheetDialog = null
                })
            }, { name ->
                homeViewModel.expand(name)
            }, { load ->
                activity?.loadResult(load.response.url, load.response.apiName, load.action)
            }, {
                homeViewModel.loadMoreHomeScrollResponses()
            }, {
                apiChangeClickListener.onClick(it)
            }, reloadStored = {
                reloadStored()
            }, loadStoredData = {
                homeViewModel.loadStoredData(it)
            }, { (isQuickSearch, text) ->
                if (!isQuickSearch) {
                    QuickSearchFragment.pushSearch(
                        activity,
                        text,
                        currentApiName?.let { arrayOf(it) })
                }
            })

        reloadStored()
        loadHomePage(false)
        home_master_recycler?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) { //check for scroll down
                    home_api_fab?.shrink() // hide
                    home_random?.shrink()
                } else if (dy < -5) {
                    if (!isTvSettings()) {
                        home_api_fab?.extend() // show
                        home_random?.extend()
                    }
                }

                super.onScrolled(recyclerView, dx, dy)
            }
        })

        // nice profile pic on homepage
        //home_profile_picture_holder?.isVisible = false
        // just in case
        if (isTvSettings()) {
            home_api_fab?.isVisible = false
            if (isTrueTvSettings()) {
                home_change_api_loading?.isVisible = true
                home_change_api_loading?.isFocusable = true
                home_change_api_loading?.isFocusableInTouchMode = true
            }
            // home_bookmark_select?.isFocusable = true
            // home_bookmark_select?.isFocusableInTouchMode = true
        } else {
            home_api_fab?.isVisible = true
            home_change_api_loading?.isVisible = false
        }
        //TODO READD THIS
        /*for (syncApi in OAuth2Apis) {
            val login = syncApi.loginInfo()
            val pic = login?.profilePicture
            if (home_profile_picture?.setImage(
                    pic,
                    errorImageDrawable = errorProfilePic
                ) == true
            ) {
                home_profile_picture_holder?.isVisible = true
                break
            }
        }*/
    }
}
