package com.lagradost.cloudstream3.ui.search

import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.APIHolder.filterSearchResultByFilmQuality
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.APIHolder.getApiProviderLangSettings
import com.lagradost.cloudstream3.APIHolder.getApiSettings
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKeys
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.home.HomeFragment
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.bindChips
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.currentSpan
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.loadHomepageList
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.updateChips
import com.lagradost.cloudstream3.ui.home.ParentItemAdapter
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.AppUtils.ownHide
import com.lagradost.cloudstream3.utils.AppUtils.ownShow
import com.lagradost.cloudstream3.utils.AppUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import kotlinx.android.synthetic.main.fragment_search.*
import kotlinx.android.synthetic.main.tvtypes_chips.*
import java.util.concurrent.locks.ReentrantLock

const val SEARCH_PREF_TAGS = "search_pref_tags"
const val SEARCH_PREF_PROVIDERS = "search_pref_providers"

class SearchFragment : Fragment() {
    companion object {
        fun List<SearchResponse>.filterSearchResponse(): List<SearchResponse> {
            return this.filter { response ->
                if (response is AnimeSearchResponse) {
                    val status = response.dubStatus
                    (status.isNullOrEmpty()) || (status.any {
                        APIRepository.dubStatusActive.contains(it)
                    })
                } else {
                    true
                }
            }
        }

        const val SEARCH_QUERY = "search_query"

        fun newInstance(query: String): Bundle {
            return Bundle().apply {
                putString(SEARCH_QUERY, query)
            }
        }
    }

    private val searchViewModel: SearchViewModel by activityViewModels()
    private var bottomSheetDialog: BottomSheetDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        bottomSheetDialog?.ownShow()
        return inflater.inflate(
            if (isTvSettings()) R.layout.fragment_search_tv else R.layout.fragment_search,
            container,
            false
        )
    }

    private fun fixGrid() {
        activity?.getSpanCount()?.let {
            currentSpan = it
        }
        search_autofit_results.spanCount = currentSpan
        currentSpan = currentSpan
        HomeFragment.configEvent.invoke(currentSpan)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fixGrid()
    }

    override fun onDestroyView() {
        hideKeyboard()
        bottomSheetDialog?.ownHide()
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        afterPluginsLoadedEvent += ::reloadRepos
    }

    override fun onStop() {
        super.onStop()
        afterPluginsLoadedEvent -= ::reloadRepos
    }

    var selectedSearchTypes = mutableListOf<TvType>()
    var selectedApis = mutableSetOf<String>()

    /**
     * Will filter all providers by preferred media and selectedSearchTypes.
     * If that results in no available providers then only filter
     * providers by preferred media
     **/
    fun search(query: String?) {
        if (query == null) return

        context?.let { ctx ->
            val default = enumValues<TvType>().sorted().filter { it != TvType.NSFW }
                .map { it.ordinal.toString() }.toSet()
            val preferredTypes = (PreferenceManager.getDefaultSharedPreferences(ctx)
                .getStringSet(this.getString(R.string.prefer_media_type_key), default)
                ?.ifEmpty { default } ?: default)
                .mapNotNull { it.toIntOrNull() ?: return@mapNotNull null }

            val settings = ctx.getApiSettings()

            val notFilteredBySelectedTypes = selectedApis.filter { name ->
                settings.contains(name)
            }.map { name ->
                name to getApiFromNameNull(name)?.supportedTypes
            }.filter { (_, types) ->
                types?.any { preferredTypes.contains(it.ordinal) } == true
            }

            searchViewModel.searchAndCancel(
                query = query,
                providersActive = notFilteredBySelectedTypes.filter { (_, types) ->
                    types?.any { selectedSearchTypes.contains(it) } == true
                }.ifEmpty { notFilteredBySelectedTypes }.map { it.first }.toSet()
            )
        }
    }

    // Null if defined as a variable
    // This needs to be run after view created

    private fun reloadRepos(success: Boolean = false) = main {
        searchViewModel.reloadRepos()
        context?.filterProviderByPreferredMedia()?.let { validAPIs ->
            bindChips(
                home_select_group,
                selectedSearchTypes,
                validAPIs.flatMap { api -> api.supportedTypes }.distinct()
            ) { list ->
                if (selectedSearchTypes.toSet() != list.toSet()) {
                    setKey(SEARCH_PREF_TAGS, selectedSearchTypes)
                    selectedSearchTypes.clear()
                    selectedSearchTypes.addAll(list)
                    search(main_search?.query?.toString())
                }
            }
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        context?.fixPaddingStatusbar(searchRoot)
        fixGrid()
        reloadRepos()

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = activity?.let {
            SearchAdapter(
                ArrayList(),
                search_autofit_results,
            ) { callback ->
                SearchHelper.handleSearchClickCallback(activity, callback)
            }
        }

        search_autofit_results.adapter = adapter
        search_loading_bar.alpha = 0f

        val searchExitIcon =
            main_search.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        // val searchMagIcon =
        //    main_search.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
        //searchMagIcon.scaleX = 0.65f
        //searchMagIcon.scaleY = 0.65f

        context?.let { ctx ->
            val validAPIs = ctx.filterProviderByPreferredMedia()
            selectedApis = ctx.getKey(
                SEARCH_PREF_PROVIDERS,
                defVal = validAPIs.map { it.name }
            )!!.toMutableSet()
        }

        search_filter.setOnClickListener { searchView ->
            searchView?.context?.let { ctx ->
                val validAPIs = ctx.filterProviderByPreferredMedia(hasHomePageIsRequired = false)
                var currentValidApis = listOf<MainAPI>()
                val currentSelectedApis = if (selectedApis.isEmpty()) validAPIs.map { it.name }
                    .toMutableSet() else selectedApis

                val builder =
                    BottomSheetDialog(ctx)

                builder.behavior.state = BottomSheetBehavior.STATE_EXPANDED
                builder.setContentView(R.layout.home_select_mainpage)
                builder.show()
                builder.let { dialog ->
                    val isMultiLang = ctx.getApiProviderLangSettings().let { set ->
                        set.size > 1 || set.contains(AllLanguagesName)
                    }

                    val cancelBtt = dialog.findViewById<MaterialButton>(R.id.cancel_btt)
                    val applyBtt = dialog.findViewById<MaterialButton>(R.id.apply_btt)

                    val listView = dialog.findViewById<ListView>(R.id.listview1)
                    val arrayAdapter = ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
                    listView?.adapter = arrayAdapter
                    listView?.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE

                    listView?.setOnItemClickListener { _, _, i, _ ->
                        if (currentValidApis.isNotEmpty()) {
                            val api = currentValidApis[i].name
                            if (currentSelectedApis.contains(api)) {
                                listView.setItemChecked(i, false)
                                currentSelectedApis -= api
                            } else {
                                listView.setItemChecked(i, true)
                                currentSelectedApis += api
                            }
                        }
                    }

                    fun updateList(types: List<TvType>) {
                        setKey(SEARCH_PREF_TAGS, types.map { it.name })

                        arrayAdapter.clear()
                        currentValidApis = validAPIs.filter { api ->
                            api.supportedTypes.any {
                                types.contains(it)
                            }
                        }.sortedBy { it.name.lowercase() }

                        val names = currentValidApis.map {
                            if (isMultiLang) "${
                                SubtitleHelper.getFlagFromIso(
                                    it.lang
                                )?.plus(" ") ?: ""
                            }${it.name}" else it.name
                        }
                        for ((index, api) in currentValidApis.map { it.name }.withIndex()) {
                            listView?.setItemChecked(index, currentSelectedApis.contains(api))
                        }

                        //arrayAdapter.notifyDataSetChanged()
                        arrayAdapter.addAll(names)
                        arrayAdapter.notifyDataSetChanged()
                    }

                    val selectedSearchTypes = getKey<List<String>>(SEARCH_PREF_TAGS)
                        ?.mapNotNull { listName ->
                            TvType.values().firstOrNull { it.name == listName }
                        }
                        ?.toMutableList()
                        ?: mutableListOf(TvType.Movie, TvType.TvSeries)

                    bindChips(
                        dialog.home_select_group,
                        selectedSearchTypes,
                        TvType.values().toList()
                    ) { list ->
                        updateList(list)
                    }

                    cancelBtt?.setOnClickListener {
                        dialog.dismissSafe()
                    }

                    cancelBtt?.setOnClickListener {
                        dialog.dismissSafe()
                    }

                    applyBtt?.setOnClickListener {
                        //if (currentApiName != selectedApiName) {
                        //    currentApiName?.let(callback)
                        //}
                        dialog.dismissSafe()
                    }

                    dialog.setOnDismissListener {
                        context?.setKey(SEARCH_PREF_PROVIDERS, currentSelectedApis.toList())
                        selectedApis = currentSelectedApis
                    }
                    updateList(selectedSearchTypes.toList())
                }
            }
        }

        val settingsManager = context?.let { PreferenceManager.getDefaultSharedPreferences(it) }
        val isAdvancedSearch = settingsManager?.getBoolean("advanced_search", true) ?: true

        selectedSearchTypes = context?.getKey<List<String>>(SEARCH_PREF_TAGS)
            ?.mapNotNull { listName -> TvType.values().firstOrNull { it.name == listName } }
            ?.toMutableList()
            ?: mutableListOf(TvType.Movie, TvType.TvSeries)

        if (isTrueTvSettings()) {
            search_filter.isFocusable = true
            search_filter.isFocusableInTouchMode = true
        }

        main_search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                search(query)

                main_search?.let {
                    hideKeyboard(it)
                }

                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                //searchViewModel.quickSearch(newText)
                val showHistory = newText.isBlank()
                if (showHistory) {
                    searchViewModel.clearSearch()
                    searchViewModel.updateHistory()
                }

                search_history_holder?.isVisible = showHistory

                search_master_recycler?.isVisible = !showHistory && isAdvancedSearch
                search_autofit_results?.isVisible = !showHistory && !isAdvancedSearch

                return true
            }
        })

        search_clear_call_history?.setOnClickListener {
            activity?.let { ctx ->
                val builder: AlertDialog.Builder = AlertDialog.Builder(ctx)
                val dialogClickListener =
                    DialogInterface.OnClickListener { _, which ->
                        when (which) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                removeKeys(SEARCH_HISTORY_KEY)
                                searchViewModel.updateHistory()
                            }
                            DialogInterface.BUTTON_NEGATIVE -> {
                            }
                        }
                    }

                try {
                    builder.setTitle(R.string.clear_history).setMessage(
                        ctx.getString(R.string.delete_message).format(
                            ctx.getString(R.string.history)
                        )
                    )
                        .setPositiveButton(R.string.sort_clear, dialogClickListener)
                        .setNegativeButton(R.string.cancel, dialogClickListener)
                        .show().setDefaultFocus()
                } catch (e: Exception) {
                    logError(e)
                    // ye you somehow fucked up formatting did you?
                }
            }


        }

        observe(searchViewModel.currentHistory) { list ->
            search_clear_call_history?.isVisible = list.isNotEmpty()
            (search_history_recycler.adapter as? SearchHistoryAdaptor?)?.updateList(list)
        }

        searchViewModel.updateHistory()

        observe(searchViewModel.searchResponse) {
            when (it) {
                is Resource.Success -> {
                    it.value.let { data ->
                        if (data.isNotEmpty()) {
                            (search_autofit_results?.adapter as? SearchAdapter)?.updateList(data)
                        }
                    }
                    searchExitIcon.alpha = 1f
                    search_loading_bar.alpha = 0f
                }
                is Resource.Failure -> {
                    // Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                    searchExitIcon.alpha = 1f
                    search_loading_bar.alpha = 0f
                }
                is Resource.Loading -> {
                    searchExitIcon.alpha = 0f
                    search_loading_bar.alpha = 1f
                }
            }
        }

        val listLock = ReentrantLock()
        observe(searchViewModel.currentSearch) { list ->
            try {
                // https://stackoverflow.com/questions/6866238/concurrent-modification-exception-adding-to-an-arraylist
                listLock.lock()
                (search_master_recycler?.adapter as ParentItemAdapter?)?.apply {
                    val newItems = list.map { ongoing ->
                        val dataList =
                            if (ongoing.data is Resource.Success) ongoing.data.value else ArrayList()
                        val dataListFiltered =
                            context?.filterSearchResultByFilmQuality(dataList) ?: dataList
                        val ongoingList = HomePageList(
                            ongoing.apiName,
                            dataListFiltered
                        )
                        ongoingList
                    }
                    updateList(newItems)

                    //notifyDataSetChanged()
                }
            } catch (e: Exception) {
                logError(e)
            } finally {
                listLock.unlock()
            }
        }


        /*main_search.setOnQueryTextFocusChangeListener { _, b ->
            if (b) {
                // https://stackoverflow.com/questions/12022715/unable-to-show-keyboard-automatically-in-the-searchview
                showInputMethod(view.findFocus())
            }
        }*/
        //main_search.onActionViewExpanded()*/

        val masterAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder> =
            ParentItemAdapter(mutableListOf(), { callback ->
                SearchHelper.handleSearchClickCallback(activity, callback)
            }, { item ->
                bottomSheetDialog = activity?.loadHomepageList(item, dismissCallback = {
                    bottomSheetDialog = null
                })
            })

        val historyAdapter = SearchHistoryAdaptor(mutableListOf()) { click ->
            val searchItem = click.item
            when (click.clickAction) {
                SEARCH_HISTORY_OPEN -> {
                    searchViewModel.clearSearch()
                    if (searchItem.type.isNotEmpty())
                        updateChips(home_select_group, searchItem.type.toMutableList())
                    main_search?.setQuery(searchItem.searchText, true)
                }
                SEARCH_HISTORY_REMOVE -> {
                    removeKey(SEARCH_HISTORY_KEY, searchItem.key)
                    searchViewModel.updateHistory()
                }
                else -> {
                    // wth are you doing???
                }
            }
        }

        search_history_recycler?.adapter = historyAdapter
        search_history_recycler?.layoutManager = GridLayoutManager(context, 1)

        search_master_recycler?.adapter = masterAdapter
        search_master_recycler?.layoutManager = GridLayoutManager(context, 1)

        // Automatically search the specified query, this allows the app search to launch from intent
        arguments?.getString(SEARCH_QUERY)?.let { query ->
            if (query.isBlank()) return@let
            main_search?.setQuery(query, true)
            // Clear the query as to not make it request the same query every time the page is opened
            arguments?.putString(SEARCH_QUERY, null)
        }

        // SubtitlesFragment.push(activity)
        //searchViewModel.search("iron man")
        //(activity as AppCompatActivity).loadResult("https://shiro.is/overlord-dubbed", "overlord-dubbed", "Shiro")
/*
        (activity as AppCompatActivity?)?.supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.enter_anim,
                R.anim.exit_anim,
                R.anim.pop_enter,
                R.anim.pop_exit)
            .add(R.id.homeRoot, PlayerFragment.newInstance(PlayerData(0, null,0)))
            .commit()*/
    }

}