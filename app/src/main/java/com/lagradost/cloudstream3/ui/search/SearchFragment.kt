package com.lagradost.cloudstream3.ui.search

import android.app.Activity
import android.content.Intent
import android.content.DialogInterface
import android.content.res.Configuration
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKeys
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.FragmentSearchBinding
import com.lagradost.cloudstream3.databinding.HomeSelectMainpageBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.BaseAdapter
import com.lagradost.cloudstream3.ui.home.HomeFragment
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.bindChips
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.currentSpan
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.loadHomepageList
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.updateChips
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.ui.home.ParentItemAdapter
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.AppContextUtils.filterSearchResultByFilmQuality
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiProviderLangSettings
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiSettings
import com.lagradost.cloudstream3.utils.AppContextUtils.ownHide
import com.lagradost.cloudstream3.utils.AppContextUtils.ownShow
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock

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
                if(query.isNotBlank()) putString(SEARCH_QUERY, query)
            }
        }
    }

    private val searchViewModel: SearchViewModel by activityViewModels()
    private var bottomSheetDialog: BottomSheetDialog? = null
    var binding: FragmentSearchBinding? = null

    private val speechRecognizerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    binding?.mainSearch?.setQuery(recognizedText, true)
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        bottomSheetDialog?.ownShow()


        binding = try {
            val layout = if (isLayout(TV or EMULATOR)) R.layout.fragment_search_tv else R.layout.fragment_search
            val root = inflater.inflate(layout, container, false)
            FragmentSearchBinding.bind(root)
        } catch (t : Throwable) {
            FragmentSearchBinding.inflate(inflater)
        }

        return binding?.root
    }

    private fun fixGrid() {
        activity?.getSpanCount()?.let {
            currentSpan = it
        }
        binding?.searchAutofitResults?.spanCount = currentSpan
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
        binding = null
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
        // don't resume state from prev search
        (binding?.searchMasterRecycler?.adapter as? BaseAdapter<*,*>)?.clear()
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
                binding?.tvtypesChipsScroll?.tvtypesChips,
                selectedSearchTypes,
                validAPIs.flatMap { api -> api.supportedTypes }.distinct()
            ) { list ->
                if (selectedSearchTypes.toSet() != list.toSet()) {
                    DataStoreHelper.searchPreferenceTags = list
                    selectedSearchTypes.clear()
                    selectedSearchTypes.addAll(list)
                    search(binding?.mainSearch?.query?.toString())
                }
            }
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fixPaddingStatusbar(binding?.searchRoot)
        fixGrid()
        reloadRepos()

        binding?.apply {
            val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> =
                SearchAdapter(
                    ArrayList(),
                    searchAutofitResults,
                ) { callback ->
                    SearchHelper.handleSearchClickCallback(callback)
                }

            searchRoot.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)?.tag = "tv_no_focus_tag"
            searchAutofitResults.adapter = adapter
            searchLoadingBar.alpha = 0f
        }

        binding?.voiceSearch?.setOnClickListener { searchView ->
            searchView?.context?.let { ctx ->
                try {
                    if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
                        showToast(R.string.speech_recognition_unavailable)
                    } else {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, ctx.getString(R.string.begin_speaking))
                        }
                        speechRecognizerLauncher.launch(intent)
                    }
                } catch (_ : Throwable) {
                    // launch may throw
                    showToast(R.string.speech_recognition_unavailable)
                }
            }
        }

        val searchExitIcon =
            binding?.mainSearch?.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        // val searchMagIcon =
        //    binding?.mainSearch?.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
        // searchMagIcon.scaleX = 0.65f
        // searchMagIcon.scaleY = 0.65f

        // Set the color for the search exit icon to the correct theme text color
        val searchExitIconColor = TypedValue()

        activity?.theme?.resolveAttribute(android.R.attr.textColor, searchExitIconColor, true)
        searchExitIcon?.setColorFilter(searchExitIconColor.data)

        selectedApis = DataStoreHelper.searchPreferenceProviders.toMutableSet()

        binding?.searchFilter?.setOnClickListener { searchView ->
            searchView?.context?.let { ctx ->
                val validAPIs = ctx.filterProviderByPreferredMedia(hasHomePageIsRequired = false)
                var currentValidApis = listOf<MainAPI>()
                val currentSelectedApis = if (selectedApis.isEmpty()) validAPIs.map { it.name }
                    .toMutableSet() else selectedApis

                val builder =
                    BottomSheetDialog(ctx)

                builder.behavior.state = BottomSheetBehavior.STATE_EXPANDED

                val selectMainpageBinding: HomeSelectMainpageBinding = HomeSelectMainpageBinding.inflate(
                    builder.layoutInflater,
                    null,
                    false
                )
                builder.setContentView(selectMainpageBinding.root)
                builder.show()
                builder.let { dialog ->
                    val previousSelectedApis = selectedApis.toSet()
                    val previousSelectedSearchTypes = selectedSearchTypes.toSet()

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
                        DataStoreHelper.searchPreferenceTags = types

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

                    bindChips(
                        selectMainpageBinding.tvtypesChipsScroll.tvtypesChips,
                        selectedSearchTypes,
                        validAPIs.flatMap { api -> api.supportedTypes }.distinct()
                    ) { list ->
                        updateList(list)

                        // refresh selected chips in main chips
                        if (selectedSearchTypes.toSet() != list.toSet()) {
                            selectedSearchTypes.clear()
                            selectedSearchTypes.addAll(list)
                            updateChips(binding?.tvtypesChipsScroll?.tvtypesChips, selectedSearchTypes)

                        }
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
                        DataStoreHelper.searchPreferenceProviders = currentSelectedApis.toList()
                        selectedApis = currentSelectedApis

                        // run search when dialog is close
                        if(previousSelectedApis != selectedApis.toSet() || previousSelectedSearchTypes != selectedSearchTypes.toSet()) {
                            search(binding?.mainSearch?.query?.toString())
                        }
                    }
                    updateList(selectedSearchTypes.toList())
                }
            }
        }

        val settingsManager = context?.let { PreferenceManager.getDefaultSharedPreferences(it) }
        val isAdvancedSearch = settingsManager?.getBoolean("advanced_search", true) ?: true

        selectedSearchTypes = DataStoreHelper.searchPreferenceTags.toMutableList()

        if (isLayout(TV)) {
            binding?.searchFilter?.isFocusable = true
            binding?.searchFilter?.isFocusableInTouchMode = true
        }

        binding?.mainSearch?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                search(query)

                binding?.mainSearch?.let {
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
                binding?.apply {
                    searchHistoryHolder.isVisible = showHistory
                    searchMasterRecycler.isVisible = !showHistory && isAdvancedSearch
                    searchAutofitResults.isVisible = !showHistory && !isAdvancedSearch
                }

                return true
            }
        })

        binding?.searchClearCallHistory?.setOnClickListener {
            activity?.let { ctx ->
                val builder: AlertDialog.Builder = AlertDialog.Builder(ctx)
                val dialogClickListener =
                    DialogInterface.OnClickListener { _, which ->
                        when (which) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                removeKeys("$currentAccount/$SEARCH_HISTORY_KEY")
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
            binding?.searchClearCallHistory?.isVisible = list.isNotEmpty()
            (binding?.searchHistoryRecycler?.adapter as? SearchHistoryAdaptor?)?.updateList(list)
        }

        searchViewModel.updateHistory()

        observe(searchViewModel.searchResponse) {
            when (it) {
                is Resource.Success -> {
                    it.value.let { data ->
                        val list = data.list
                        if (list.isNotEmpty()) {
                            (binding?.searchAutofitResults?.adapter as? SearchAdapter)?.updateList(list)
                        }
                    }
                    searchExitIcon?.alpha = 1f
                    binding?.searchLoadingBar?.alpha = 0f
                }
                is Resource.Failure -> {
                    // Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                    searchExitIcon?.alpha = 1f
                    binding?.searchLoadingBar?.alpha = 0f
                }
                is Resource.Loading -> {
                    searchExitIcon?.alpha = 0f
                    binding?.searchLoadingBar?.alpha = 1f
                }
            }
        }

        val listLock = ReentrantLock()
        observe(searchViewModel.currentSearch) { list ->
            try {
                // https://stackoverflow.com/questions/6866238/concurrent-modification-exception-adding-to-an-arraylist
                listLock.lock()
                (binding?.searchMasterRecycler?.adapter as ParentItemAdapter?)?.apply {
                    val newItems = list.map { ongoing ->
                        val dataList = ongoing.value.list
                        val dataListFiltered =
                            context?.filterSearchResultByFilmQuality(dataList) ?: dataList

                        val homePageList = HomePageList(
                            ongoing.key,
                            dataListFiltered
                        )

                        val expandableList = HomeViewModel.ExpandableHomepageList(
                            homePageList,
                            ongoing.value.currentPage,
                            ongoing.value.hasNext
                        )

                        expandableList
                    }

                    submitList(newItems)
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

        val masterAdapter =
            ParentItemAdapter(fragment = this, id = "masterAdapter".hashCode(), { callback ->
                SearchHelper.handleSearchClickCallback(callback)
            }, { item ->
                bottomSheetDialog = activity?.loadHomepageList(item, dismissCallback = {
                    bottomSheetDialog = null
                }, expandCallback = { name -> searchViewModel.expandAndReturn(name) })
            }, expandCallback = { name ->
                ioSafe {
                    searchViewModel.expandAndReturn(name)
                }
            })

        val historyAdapter = SearchHistoryAdaptor(mutableListOf()) { click ->
            val searchItem = click.item
            when (click.clickAction) {
                SEARCH_HISTORY_OPEN -> {
                    searchViewModel.clearSearch()
                    if (searchItem.type.isNotEmpty())
                        updateChips(binding?.tvtypesChipsScroll?.tvtypesChips, searchItem.type.toMutableList())
                    binding?.mainSearch?.setQuery(searchItem.searchText, true)
                }
                SEARCH_HISTORY_REMOVE -> {
                    removeKey("$currentAccount/$SEARCH_HISTORY_KEY", searchItem.key)
                    searchViewModel.updateHistory()
                }
                else -> {
                    // wth are you doing???
                }
            }
        }

        binding?.apply {
            searchHistoryRecycler.adapter = historyAdapter
            searchHistoryRecycler.setLinearListLayout(isHorizontal = false, nextRight = FOCUS_SELF)
            //searchHistoryRecycler.layoutManager = GridLayoutManager(context, 1)

            searchMasterRecycler.adapter = masterAdapter
            //searchMasterRecycler.setLinearListLayout(isHorizontal = false, nextRight = FOCUS_SELF)

            searchMasterRecycler.layoutManager = GridLayoutManager(context, 1)

            // Automatically search the specified query, this allows the app search to launch from intent
            var sq = arguments?.getString(SEARCH_QUERY) ?: savedInstanceState?.getString(SEARCH_QUERY)
            if(sq.isNullOrBlank()) {
                sq = MainActivity.nextSearchQuery
            }

            sq?.let { query ->
                if (query.isBlank()) return@let
                mainSearch.setQuery(query, true)
                // Clear the query as to not make it request the same query every time the page is opened
                arguments?.remove(SEARCH_QUERY)
                savedInstanceState?.remove(SEARCH_QUERY)
                MainActivity.nextSearchQuery = null
            }
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
