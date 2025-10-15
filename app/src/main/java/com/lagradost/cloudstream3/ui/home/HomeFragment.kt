package com.lagradost.cloudstream3.ui.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.FragmentHomeBinding
import com.lagradost.cloudstream3.databinding.HomeEpisodesExpandedBinding
import com.lagradost.cloudstream3.databinding.HomeSelectMainpageBinding
import com.lagradost.cloudstream3.databinding.TvtypesChipsBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.ui.APIRepository.Companion.noneApi
import com.lagradost.cloudstream3.ui.APIRepository.Companion.randomApi
import com.lagradost.cloudstream3.ui.account.AccountHelper.showAccountSelectLinear
import com.lagradost.cloudstream3.ui.account.AccountViewModel
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_PLAY_FILE
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchHelper.handleSearchClickCallback
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiProviderLangSettings
import com.lagradost.cloudstream3.utils.AppContextUtils.isNetworkAvailable
import com.lagradost.cloudstream3.utils.AppContextUtils.isRecyclerScrollable
import com.lagradost.cloudstream3.utils.AppContextUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.AppContextUtils.ownHide
import com.lagradost.cloudstream3.utils.AppContextUtils.ownShow
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.SubtitleHelper.getFlagFromIso
import com.lagradost.cloudstream3.utils.TvChannelUtils
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.txt


private const val TAG = "HomeFragment"

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
            dismissCallback: (() -> Unit),
        ): BottomSheetDialog {
            val context = this
            val bottomSheetDialogBuilder = BottomSheetDialog(context)
            val binding: HomeEpisodesExpandedBinding = HomeEpisodesExpandedBinding.inflate(
                bottomSheetDialogBuilder.layoutInflater,
                null,
                false
            )
            bottomSheetDialogBuilder.setContentView(binding.root)
            //val title = bottomSheetDialogBuilder.findViewById<TextView>(R.id.home_expanded_text)!!

            //title.findViewTreeLifecycleOwner().lifecycle.addObserver()

            val item = expand.list
            binding.homeExpandedText.text = item.name
            // val recycle =
            //    bottomSheetDialogBuilder.findViewById<AutofitRecyclerView>(R.id.home_expanded_recycler)!!
            //val titleHolder =
            //    bottomSheetDialogBuilder.findViewById<FrameLayout>(R.id.home_expanded_drag_down)!!

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
            //val delete = bottomSheetDialogBuilder.home_expanded_delete
            binding.homeExpandedDelete.isGone = deleteCallback == null
            if (deleteCallback != null) {
                binding.homeExpandedDelete.setOnClickListener {
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

                        builder.setTitle(R.string.clear_history)
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
            binding.homeExpandedDragDown.setOnClickListener {
                bottomSheetDialogBuilder.dismissSafe(this)
            }


            // Span settings
            binding.homeExpandedRecycler.spanCount = currentSpan

            binding.homeExpandedRecycler.adapter =
                SearchAdapter(item.list.toMutableList(), binding.homeExpandedRecycler) { callback ->
                    handleSearchClickCallback(callback)
                    if (callback.action == SEARCH_ACTION_LOAD || callback.action == SEARCH_ACTION_PLAY_FILE) {
                        bottomSheetDialogBuilder.ownHide() // we hide here because we want to resume it later
                        //bottomSheetDialogBuilder.dismissSafe(this)
                    }
                }.apply {
                    hasNext = expand.hasNext
                }

            binding.homeExpandedRecycler.addOnScrollListener(object :
                RecyclerView.OnScrollListener() {
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
                binding.homeExpandedRecycler.spanCount = span
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

        private fun getPairList(
            anime: Chip?,
            cartoons: Chip?,
            tvs: Chip?,
            docs: Chip?,
            movies: Chip?,
            asian: Chip?,
            livestream: Chip?,
            torrent: Chip?,
            nsfw: Chip?,
            others: Chip?,
        ): List<Pair<Chip?, List<TvType>>> {
            // This list should be same order as home screen to aid navigation
            return listOf(
                Pair(movies, listOf(TvType.Movie)),
                Pair(tvs, listOf(TvType.TvSeries)),
                Pair(anime, listOf(TvType.Anime, TvType.OVA, TvType.AnimeMovie)),
                Pair(asian, listOf(TvType.AsianDrama)),
                Pair(cartoons, listOf(TvType.Cartoon)),
                Pair(docs, listOf(TvType.Documentary)),
                Pair(livestream, listOf(TvType.Live)),
                Pair(torrent, listOf(TvType.Torrent)),
                Pair(nsfw, listOf(TvType.NSFW)),
                Pair(others, listOf(TvType.Others)),
            )
        }

        private fun getPairList(header: TvtypesChipsBinding) = getPairList(
            header.homeSelectAnime,
            header.homeSelectCartoons,
            header.homeSelectTvSeries,
            header.homeSelectDocumentaries,
            header.homeSelectMovies,
            header.homeSelectAsian,
            header.homeSelectLivestreams,
            header.homeSelectTorrents,
            header.homeSelectNsfw,
            header.homeSelectOthers
        )

        fun validateChips(header: TvtypesChipsBinding?, validTypes: List<TvType>) {
            if (header == null) return
            val pairList = getPairList(header)
            for ((button, types) in pairList) {
                val isValid = validTypes.any { types.contains(it) }
                button?.isVisible = isValid
            }
        }

        fun updateChips(header: TvtypesChipsBinding?, selectedTypes: List<TvType>) {
            if (header == null) return
            val pairList = getPairList(header)
            for ((button, types) in pairList) {
                button?.isChecked =
                    button?.isVisible == true && selectedTypes.any { types.contains(it) }
            }
        }

        fun bindChips(
            header: TvtypesChipsBinding?,
            selectedTypes: List<TvType>,
            validTypes: List<TvType>,
            callback: (List<TvType>) -> Unit
        ) {
            bindChips(header, selectedTypes, validTypes, callback, null, null)
        }

        fun bindChips(
            header: TvtypesChipsBinding?,
            selectedTypes: List<TvType>,
            validTypes: List<TvType>,
            callback: (List<TvType>) -> Unit,
            nextFocusDown: Int?,
            nextFocusUp: Int?
        ) {
            if (header == null) return
            val pairList = getPairList(header)
            for ((button, types) in pairList) {
                val isValid = validTypes.any { types.contains(it) }
                button?.isVisible = isValid
                button?.isChecked = isValid && selectedTypes.any { types.contains(it) }
                button?.isFocusable = true
                if (isLayout(TV)) {
                    button?.isFocusableInTouchMode = true
                }

                if (nextFocusDown != null)
                    button?.nextFocusDownId = nextFocusDown

                if (nextFocusUp != null)
                    button?.nextFocusUpId = nextFocusUp

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
            val binding: HomeSelectMainpageBinding = HomeSelectMainpageBinding.inflate(
                builder.layoutInflater,
                null,
                false
            )

            builder.setContentView(binding.root)
            builder.show()
            builder.let { dialog ->
                val isMultiLang = getApiProviderLangSettings().let { set ->
                    set.size > 1 || set.contains(AllLanguagesName)
                }
                //dialog.window?.setGravity(Gravity.BOTTOM)

                var currentApiName = selectedApiName

                var currentValidApis: MutableList<MainAPI> = mutableListOf()
                val preSelectedTypes = DataStoreHelper.homePreference.toMutableList()

                binding.cancelBtt.setOnClickListener {
                    dialog.dismissSafe()
                }

                binding.applyBtt.setOnClickListener {
                    if (currentApiName != selectedApiName) {
                        currentApiName?.let(callback)
                    }
                    dialog.dismissSafe()
                }

                var pinnedphashset = DataStoreHelper.pinnedProviders.toHashSet()

                val listView = dialog.findViewById<ListView>(R.id.listview1)

                val arrayAdapter = object : ArrayAdapter<String>(
                    this, R.layout.sort_bottom_single_provider_choice,
                    mutableListOf()
                ) {
                    override fun getView(
                        position: Int,
                        convertView: View?,
                        parent: ViewGroup
                    ): View {
                        val view = convertView ?: LayoutInflater.from(context)
                            .inflate(R.layout.sort_bottom_single_provider_choice, parent, false)
                        val titleText = view.findViewById<TextView>(R.id.text1)
                        val pinIcon = view.findViewById<ImageView>(R.id.pinicon)
                        val name = getItem(position)
                        titleText?.text = name
                        val isPinned =
                            pinnedphashset.contains(currentValidApis[position].name ?: "")
                        pinIcon.visibility = if (isPinned) View.VISIBLE else View.GONE
                        return view
                    }
                }
                listView?.adapter = arrayAdapter
                listView?.choiceMode = AbsListView.CHOICE_MODE_SINGLE

                listView?.setOnItemClickListener { _, _, i, _ ->
                    if (currentValidApis.isNotEmpty()) {
                        currentApiName = currentValidApis[i].name
                        //to switch to apply simply remove this
                        currentApiName.let(callback)
                        dialog.dismissSafe()
                    }
                }

                fun updateList() {
                    DataStoreHelper.homePreference = preSelectedTypes
                    val pinnedp = DataStoreHelper.pinnedProviders.toList()
                    pinnedphashset = pinnedp.toHashSet()
                    arrayAdapter.clear()
                    val sortedApis = validAPIs
                        .filter {
                            it.hasMainPage && (pinnedphashset.contains(it.name) || it.supportedTypes.any(
                                preSelectedTypes::contains
                            ))
                        }
                        .sortedBy { it.name.lowercase() }

                    val sortedApiMap = LinkedHashMap<String, MainAPI>().apply {
                        sortedApis.forEach { put(it.name, it) }
                    }

                    val pinnedApis = pinnedp.asReversed().mapNotNull { name ->
                        sortedApiMap[name]
                    }

                    val remainingApis = sortedApis.filterNot { pinnedphashset.contains(it.name) }

                    currentValidApis = mutableListOf<MainAPI>().apply {
                        addAll(validAPIs.take(2))
                        addAll(pinnedApis)
                        addAll(remainingApis)
                    }

                    val names =
                        currentValidApis.map { if (isMultiLang) "${getFlagFromIso(it.lang)?.plus(" ") ?: ""}${it.name}" else it.name }
                    val index = currentValidApis.map { it.name }.indexOf(currentApiName)
                    listView?.setItemChecked(index, true)
                    arrayAdapter.addAll(names)
                    arrayAdapter.notifyDataSetChanged()
                }
                // pin provider on hold
                listView?.setOnItemLongClickListener { _, _, i, _ ->
                    if (currentValidApis.isNotEmpty() && i > 1) {
                        val pinnedp = DataStoreHelper.pinnedProviders.toMutableList()
                        val thisapi = currentValidApis[i].name
                        if (pinnedp.contains(thisapi)) {
                            pinnedp.remove(thisapi)
                        } else {
                            pinnedp.add(thisapi)
                        }
                        DataStoreHelper.pinnedProviders = pinnedp.toTypedArray()
                        updateList()
                    }
                    true
                }

                bindChips(
                    binding.tvtypesChipsScroll.tvtypesChips,
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
    private val accountViewModel: AccountViewModel by activityViewModels()

    fun addMovies(cards: List<SearchResponse>) {
        val ctx = context ?: run {
            Log.e(TAG, "Context is null, aborting addMovies")
            return
        }

        try {
            val existingId = TvChannelUtils.getChannelId(ctx, getString(R.string.app_name))
            if (existingId != null) {
                Log.d(TAG, "Channel ID: $existingId")

                val programCards = cards

                TvChannelUtils.addPrograms(
                    context = ctx,
                    channelId = existingId,
                    items = programCards
                )
            } else {
                Log.d(TAG, "Channel does not exist")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding movies: $e")
        }
    }

    private fun deleteAll() {
        val ctx = context ?: run {
            Log.e(TAG, "Context is null, aborting deleteAll")
            return
        }

        try {
            val existingId = TvChannelUtils.getChannelId(ctx, getString(R.string.app_name))
            if (existingId != null) {
                Log.d(TAG, "Channel ID: $existingId")
                TvChannelUtils.deleteStoredPrograms(ctx)
            } else {
                Log.d(TAG, "Channel does not exist")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting programs: ${e.message}")
        }
    }

    var binding: FragmentHomeBinding? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        //homeViewModel =
        //     ViewModelProvider(this).get(HomeViewModel::class.java)

        bottomSheetDialog?.ownShow()
        val layout =
            if (isLayout(TV or EMULATOR)) R.layout.fragment_home_tv else R.layout.fragment_home
        val root = inflater.inflate(layout, container, false)
        binding = try {
            FragmentHomeBinding.bind(root)
        } catch (t: Throwable) {
            showToast(txt(R.string.unable_to_inflate, t.message ?: ""), Toast.LENGTH_LONG)
            logError(t)
            null
        }

        return root
    }

    override fun onDestroyView() {

        bottomSheetDialog?.ownHide()
        binding = null
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
            homeViewModel.loadAndCancel(api, forceReload = true, fromUI = true)
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

    private var currentApiName: String? = null
    private var toggleRandomButton = false

    private var bottomSheetDialog: BottomSheetDialog? = null
    private var homeMasterAdapter: HomeParentItemAdapterPreview? = null

    var lastSavedHomepage: String? = null

    fun saveHomepageToTV(page :  Map<String, HomeViewModel.ExpandableHomepageList>) {
        // No need to update for phone
        if(isLayout(PHONE)) {
            return
        }
        val (name, data) = page.entries.firstOrNull() ?: return
        // Modifying homepage is an expensive operation, and therefore we avoid it at all cost
        if(name == lastSavedHomepage) {
            return
        }
        Log.i(TAG, "Adding programs $name to TV")
        lastSavedHomepage = name
        ioSafe {
            // empty the channel
            deleteAll()
            // insert the program from first array
            addMovies(data.list.list)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fixGrid()

        context?.let { HomeChildItemAdapter.updatePosterSize(it) }

        binding?.apply {
            //homeChangeApiLoading.setOnClickListener(apiChangeClickListener)
            //homeChangeApiLoading.setOnClickListener(apiChangeClickListener)
            homeApiFab.setOnClickListener(apiChangeClickListener)
            homeApiFab.setOnLongClickListener {
                if (currentApiName == noneApi.name) return@setOnLongClickListener false
                homeViewModel.loadAndCancel(currentApiName, forceReload = true, fromUI = true)
                showToast(R.string.action_reload, Toast.LENGTH_SHORT)
                true
            }
            homeChangeApi.setOnClickListener(apiChangeClickListener)
            homeSwitchAccount.setOnClickListener {
                activity?.showAccountSelectLinear()
            }

            homeRandom.setOnClickListener {
                if (listHomepageItems.isNotEmpty()) {
                    activity.loadSearchResult(listHomepageItems.random())
                }
            }
            homeMasterAdapter = HomeParentItemAdapterPreview(
                fragment = this@HomeFragment,
                homeViewModel, accountViewModel
            )
            homeMasterRecycler.adapter = homeMasterAdapter
            //fixPaddingStatusbar(homeLoadingStatusbar)

            homeApiFab.isVisible = isLayout(PHONE)

            homePreviewReloadProvider.setOnClickListener {
                homeViewModel.loadAndCancel(
                    homeViewModel.apiName.value ?: noneApi.name,
                    forceReload = true,
                    fromUI = true
                )
                showToast(R.string.action_reload, Toast.LENGTH_SHORT)
                true
            }

            homePreviewSearchButton.setOnClickListener { _ ->
                // Open blank screen.
                homeViewModel.queryTextSubmit("")
            }

            homeMasterRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if(isLayout(PHONE)) {
                        // Fab is only relevant to Phone
                        if (dy > 0) { //check for scroll down
                            homeApiFab.shrink() // hide
                            homeRandom.shrink()
                        } else if (dy < -5) {
                            if (isLayout(PHONE)) {
                                homeApiFab.extend() // show
                                homeRandom.extend()
                            }
                        }
                    } else {
                        // Header scrolling is only relevant to TV/Emulator

                        val view = recyclerView.findViewHolderForAdapterPosition(0)?.itemView
                        val scrollParent = binding?.homeApiHolder

                        if (view == null) {
                            // The first view is not visible, so we can assume we have scrolled past it
                            scrollParent?.isVisible = false
                        } else {
                            // A bit weird, but this is a major limitation we are working around here
                            // 1. We cant have a real parent to the recyclerview as android cant layout that without lagging
                            // 2. We cant put the view in the recyclerview, as it should always be shown
                            // 3. We cant mirror the view in the recyclerview as then it causes focus issues when swaping out the mirror view
                            //
                            // This means that if we want to have a parent view to the recyclerview we are out of luck
                            // Instead this uses getLocationInWindow to calculate how much the view should be scrolled
                            // as recyclerView has no scrollY (always 0)
                            //
                            // Then it manually "scrolls" it to the correct position
                            //
                            // Hopefully getLocationInWindow acts correctly on all devices
                            val rect = IntArray(2)
                            view.getLocationInWindow(rect)
                            scrollParent?.isVisible = true
                            scrollParent?.translationY = rect[1].toFloat() - 60.toPx
                        }
                    }
                    super.onScrolled(recyclerView, dx, dy)
                }
            })

        }

        //Load value for toggling Random button. Hide at startup
        context?.let {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(it)
            toggleRandomButton =
                settingsManager.getBoolean(
                    getString(R.string.random_button_key),
                    false
                ) && isLayout(PHONE)
            binding?.homeRandom?.visibility = View.GONE
        }

        observe(homeViewModel.apiName) { apiName ->
            currentApiName = apiName
            binding?.apply {
                homeApiFab.text = apiName
                homeChangeApi.text = apiName
                homePreviewReloadProvider.isGone = (apiName == noneApi.name)
                homePreviewSearchButton.isGone = (apiName == noneApi.name)
            }
        }

        observe(homeViewModel.page) { data ->
            binding?.apply {
                when (data) {
                    is Resource.Success -> {
                        homeLoadingShimmer.stopShimmer()

                        val d = data.value
                        saveHomepageToTV(d)

                        val mutableListOfResponse = mutableListOf<SearchResponse>()
                        listHomepageItems.clear()

                        (homeMasterRecycler.adapter as? ParentItemAdapter)?.submitList(d.values.map {

                            it.copy(
                                list = it.list.copy(list = it.list.list.toMutableList())
                            )
                        }.toMutableList())

                        homeLoading.isVisible = false
                        homeLoadingError.isVisible = false
                        homeMasterRecycler.isVisible = true
                        //home_loaded?.isVisible = true
                        if (toggleRandomButton) {
                            //Flatten list
                            d.values.forEach { dlist ->
                                mutableListOfResponse.addAll(dlist.list.list)
                            }
                            listHomepageItems.addAll(mutableListOfResponse.distinctBy { it.url })

                            homeRandom.isVisible = listHomepageItems.isNotEmpty()
                        } else {
                            homeRandom.isGone = true
                        }
                    }

                    is Resource.Failure -> {
                        homeLoadingShimmer.stopShimmer()
                        homeReloadConnectionerror.setOnClickListener(apiChangeClickListener)
                        homeReloadConnectionOpenInBrowser.setOnClickListener { view ->
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

                        homeLoading.isVisible = false
                        homeLoadingError.isVisible = true
                        homeMasterRecycler.isVisible = false

                        // Based on https://github.com/recloudstream/cloudstream/pull/1438
                        val hasNoNetworkConnection = context?.isNetworkAvailable() == false
                        val isNetworkError = data.isNetworkError

                        // Show the downloads button if we have any sort of network shenanigans
                        homeReloadConnectionGoToDownloads.isVisible =
                            hasNoNetworkConnection || isNetworkError

                        // Only hide the open in browser button if we know this is not network shenanigans related to cs3
                        homeReloadConnectionOpenInBrowser.isGone = hasNoNetworkConnection

                        resultErrorText.text = if (hasNoNetworkConnection) {
                            getString(R.string.no_internet_connection)
                        } else {
                            data.errorString
                        }

                        homeReloadConnectionGoToDownloads.setOnClickListener {
                            activity.navigate(R.id.navigation_downloads)
                        }
                    }

                    is Resource.Loading -> {
                        (homeMasterRecycler.adapter as? ParentItemAdapter)?.submitList(listOf())
                        homeLoadingShimmer.startShimmer()
                        homeLoading.isVisible = true
                        homeLoadingError.isVisible = false
                        homeMasterRecycler.isVisible = false
                        //home_loaded?.isVisible = false
                    }
                }
            }
        }


        //context?.fixPaddingStatusbarView(home_statusbar)
        //context?.fixPaddingStatusbar(home_padding)

        observeNullable(homeViewModel.popup) { item ->
            if (item == null) {
                bottomSheetDialog?.dismissSafe()
                bottomSheetDialog = null
                return@observeNullable
            }

            // don't recreate
            if (bottomSheetDialog != null) {
                return@observeNullable
            }

            val (items, delete) = item

            bottomSheetDialog = activity?.loadHomepageList(items, expandCallback = {
                homeViewModel.expandAndReturn(it)
            }, dismissCallback = {
                homeViewModel.popup(null)
                bottomSheetDialog = null
            }, deleteCallback = delete)
        }

        homeViewModel.reloadStored()
        homeViewModel.loadAndCancel(DataStoreHelper.currentHomePage, false)
        //loadHomePage(false)

        // nice profile pic on homepage
        //home_profile_picture_holder?.isVisible = false
        // just in case

        //TODO READD THIS
        /*for (syncApi in OAuth2Apis) {
            val login = SyncAPI2.loginInfo()
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
