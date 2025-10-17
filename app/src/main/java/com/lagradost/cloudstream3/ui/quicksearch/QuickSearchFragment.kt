package com.lagradost.cloudstream3.ui.quicksearch

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.QuickSearchBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.home.HomeFragment
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.loadHomepageList
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.ui.home.ParentItemAdapter
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.ui.search.SearchViewModel
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.AppContextUtils.filterSearchResultByFilmQuality
import com.lagradost.cloudstream3.utils.AppContextUtils.isRecyclerScrollable
import com.lagradost.cloudstream3.utils.AppContextUtils.ownShow
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.UIHelper
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import java.util.concurrent.locks.ReentrantLock

class QuickSearchFragment : Fragment() {
    companion object {
        const val AUTOSEARCH_KEY = "autosearch"
        const val PROVIDER_KEY = "providers"

        fun pushSearch(
            autoSearch: String? = null,
            providers: Array<String>? = null
        ) {
            pushSearch(activity, autoSearch, providers)
        }

        fun pushSearch(
            activity: Activity?,
            autoSearch: String? = null,
            providers: Array<String>? = null
        ) {
            activity.navigate(R.id.global_to_navigation_quick_search, Bundle().apply {
                providers?.let {
                    putStringArray(PROVIDER_KEY, it)
                }
                autoSearch?.let {
                    putString(
                        AUTOSEARCH_KEY,
                        it.trim()
                            .removeSuffix("(DUB)")
                            .removeSuffix("(SUB)")
                            .removeSuffix("(Dub)")
                            .removeSuffix("(Sub)").trim()
                    )
                }
            })
        }

        var clickCallback: ((SearchClickCallback) -> Unit)? = null
    }

    private var providers: Set<String>? = null
    private lateinit var searchViewModel: SearchViewModel
    var binding: QuickSearchBinding? = null


    private var bottomSheetDialog: BottomSheetDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        searchViewModel = ViewModelProvider(this)[SearchViewModel::class.java]
        bottomSheetDialog?.ownShow()
        val localBinding = QuickSearchBinding.inflate(inflater, container, false)
        binding = localBinding
        return localBinding.root
        //return inflater.inflate(R.layout.quick_search, container, false)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        clickCallback = null
    }

    fun search(context: Context?, query: String, isQuickSearch: Boolean): Boolean {
        (providers ?: context?.filterProviderByPreferredMedia(hasHomePageIsRequired = false)
            ?.map { it.name }?.toSet())?.let { active ->
            searchViewModel.searchAndCancel(
                query = query,
                ignoreSettings = false,
                providersActive = active,
                isQuickSearch = isQuickSearch
            )
            return true
        }
        return false
    }

    private fun fixGrid() {
        activity?.getSpanCount()?.let {
            HomeFragment.currentSpan = it
        }
        binding?.quickSearchAutofitResults?.spanCount = HomeFragment.currentSpan
        HomeFragment.configEvent.invoke(HomeFragment.currentSpan)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fixGrid()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fixPaddingStatusbar(binding?.quickSearchRoot)
        fixGrid()

        arguments?.getStringArray(PROVIDER_KEY)?.let {
            providers = it.toSet()
        }

        val isSingleProvider = providers?.size == 1
        val isSingleProviderQuickSearch = if (isSingleProvider) {
            getApiFromNameNull(providers?.first())?.hasQuickSearch ?: false
        } else false

        val firstProvider = providers?.firstOrNull()
        if (isSingleProvider && firstProvider != null) {
            binding?.quickSearchAutofitResults?.apply {
                adapter = SearchAdapter(
                    ArrayList(),
                    this,
                ) { callback ->
                    SearchHelper.handleSearchClickCallback(callback)
                }
            }

            binding?.quickSearchAutofitResults?.addOnScrollListener(object :
                RecyclerView.OnScrollListener() {
                var expandCount = 0

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)

                    val adapter = recyclerView.adapter
                    if (adapter !is SearchAdapter) return

                    val count = adapter.itemCount
                    val currentHasNext = adapter.hasNext

                    if (!recyclerView.isRecyclerScrollable() && currentHasNext && expandCount != count) {
                        expandCount = count
                        ioSafe {
                            searchViewModel.expandAndReturn(firstProvider)
                        }
                    }
                }
            })

            try {
                binding?.quickSearch?.queryHint =
                    getString(R.string.search_hint_site).format(firstProvider)
            } catch (e: Exception) {
                logError(e)
            }
        } else {
            binding?.quickSearchMasterRecycler?.adapter =
                ParentItemAdapter(
                    fragment = this,
                    id = "quickSearchMasterRecycler".hashCode(),
                    { callback ->
                        SearchHelper.handleSearchClickCallback(callback)
                        //when (callback.action) {
                        //SEARCH_ACTION_LOAD -> {
                        //    clickCallback?.invoke(callback)
                        //}
                        //    else -> SearchHelper.handleSearchClickCallback(activity, callback)
                        //}
                    },
                    { item ->
                        bottomSheetDialog = activity?.loadHomepageList(item, dismissCallback = {
                            bottomSheetDialog = null
                        }, expandCallback = { searchViewModel.expandAndReturn(it) })
                    },
                    expandCallback = { name ->
                        ioSafe {
                            searchViewModel.expandAndReturn(name)
                        }
                    })
            binding?.quickSearchMasterRecycler?.layoutManager = GridLayoutManager(context, 1)
        }
        binding?.quickSearchAutofitResults?.isVisible = isSingleProvider
        binding?.quickSearchMasterRecycler?.isGone = isSingleProvider

        val listLock = ReentrantLock()
        observe(searchViewModel.currentSearch) { list ->
            try {
                // https://stackoverflow.com/questions/6866238/concurrent-modification-exception-adding-to-an-arraylist
                listLock.lock()
                (binding?.quickSearchMasterRecycler?.adapter as ParentItemAdapter?)?.apply {
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

        val searchExitIcon =
            binding?.quickSearch?.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)

        //val searchMagIcon =
        //    binding?.quickSearch?.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)

        // searchMagIcon?.scaleX = 0.65f
        // searchMagIcon?.scaleY = 0.65f

        // Set the color for the search exit icon to the correct theme text color
        val searchExitIconColor = TypedValue()

        activity?.theme?.resolveAttribute(android.R.attr.textColor, searchExitIconColor, true)
        searchExitIcon?.setColorFilter(searchExitIconColor.data)

        binding?.quickSearch?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                if (search(context, query, false))
                    UIHelper.hideKeyboard(binding?.quickSearch)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                if (isSingleProviderQuickSearch)
                    search(context, newText, true)
                return true
            }
        })
        binding?.quickSearchLoadingBar?.alpha = 0f
        observe(searchViewModel.searchResponse) {
            when (it) {
                is Resource.Success -> {
                    it.value.let { data ->
                        val adapter =
                            (binding?.quickSearchAutofitResults?.adapter as? SearchAdapter)
                        adapter?.updateList(
                            context?.filterSearchResultByFilmQuality(data.list) ?: data.list
                        )
                        adapter?.hasNext = data.hasNext
                    }
                    searchExitIcon?.alpha = 1f
                    binding?.quickSearchLoadingBar?.alpha = 0f
                }

                is Resource.Failure -> {
                    // Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                    searchExitIcon?.alpha = 1f
                    binding?.quickSearchLoadingBar?.alpha = 0f
                }

                is Resource.Loading -> {
                    searchExitIcon?.alpha = 0f
                    binding?.quickSearchLoadingBar?.alpha = 1f
                }
            }
        }


        //quick_search.setOnQueryTextFocusChangeListener { _, b ->
        //    if (b) {
        //        // https://stackoverflow.com/questions/12022715/unable-to-show-keyboard-automatically-in-the-searchview
        //        UIHelper.showInputMethod(view.findFocus())
        //    }
        //}
        if (isLayout(PHONE or EMULATOR)) {
            binding?.quickSearchBack?.apply {
                isVisible = true
                setOnClickListener {
                    activity?.popCurrentPage()
                }
            }
        }

        if (isLayout(TV)) {
            binding?.quickSearch?.requestFocus()
        }

        arguments?.getString(AUTOSEARCH_KEY)?.let {
            binding?.quickSearch?.setQuery(it, true)
            arguments?.remove(AUTOSEARCH_KEY)
        }
    }
}