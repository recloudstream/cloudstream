package com.lagradost.cloudstream3.ui.quicksearch

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.APIHolder.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.APIHolder.filterSearchResultByFilmQuality
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.home.HomeFragment
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.loadHomepageList
import com.lagradost.cloudstream3.ui.home.ParentItemAdapter
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.ui.search.SearchViewModel
import com.lagradost.cloudstream3.utils.AppUtils.ownShow
import com.lagradost.cloudstream3.utils.UIHelper
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import kotlinx.android.synthetic.main.quick_search.*
import java.util.concurrent.locks.ReentrantLock

class QuickSearchFragment : Fragment() {
    companion object {
        const val AUTOSEARCH_KEY = "autosearch"
        const val PROVIDER_KEY = "providers"

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

    private var bottomSheetDialog: BottomSheetDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        searchViewModel = ViewModelProvider(this)[SearchViewModel::class.java]
        bottomSheetDialog?.ownShow()
        return inflater.inflate(R.layout.quick_search, container, false)
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
        quick_search_autofit_results.spanCount = HomeFragment.currentSpan
        HomeFragment.currentSpan = HomeFragment.currentSpan
        HomeFragment.configEvent.invoke(HomeFragment.currentSpan)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fixGrid()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context?.fixPaddingStatusbar(quick_search_root)
        fixGrid()

        arguments?.getStringArray(PROVIDER_KEY)?.let {
            providers = it.toSet()
        }

        val isSingleProvider = providers?.size == 1
        val isSingleProviderQuickSearch = if (isSingleProvider) {
            getApiFromNameNull(providers?.first())?.hasQuickSearch ?: false
        } else false

        if (isSingleProvider) {
            quick_search_autofit_results.adapter = activity?.let {
                SearchAdapter(
                    ArrayList(),
                    quick_search_autofit_results,
                ) { callback ->
                    SearchHelper.handleSearchClickCallback(activity, callback)
                }
            }
            try {
                quick_search?.queryHint = getString(R.string.search_hint_site).format(providers?.first())
            } catch (e: Exception) {
                logError(e)
            }
        } else {
            quick_search_master_recycler?.adapter =
                ParentItemAdapter(mutableListOf(), { callback ->
                    SearchHelper.handleSearchClickCallback(activity, callback)
                    //when (callback.action) {
                    //SEARCH_ACTION_LOAD -> {
                    //    clickCallback?.invoke(callback)
                    //}
                    //    else -> SearchHelper.handleSearchClickCallback(activity, callback)
                    //}
                }, { item ->
                    bottomSheetDialog = activity?.loadHomepageList(item, dismissCallback = {
                        bottomSheetDialog = null
                    })
                })
            quick_search_master_recycler?.layoutManager = GridLayoutManager(context, 1)
        }

        quick_search_autofit_results?.isVisible = isSingleProvider
        quick_search_master_recycler?.isGone = isSingleProvider

        val listLock = ReentrantLock()
        observe(searchViewModel.currentSearch) { list ->
            try {
                // https://stackoverflow.com/questions/6866238/concurrent-modification-exception-adding-to-an-arraylist
                listLock.lock()
                (quick_search_master_recycler?.adapter as ParentItemAdapter?)?.apply {
                    updateList(list.map { ongoing ->
                        val ongoingList = HomePageList(
                            ongoing.apiName,
                            if (ongoing.data is Resource.Success) ongoing.data.value else ArrayList()
                        )
                        ongoingList
                    })
                }
            } catch (e: Exception) {
                logError(e)
            } finally {
                listLock.unlock()
            }
        }

        val searchExitIcon =
            quick_search?.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)

        //val searchMagIcon =
        //    quick_search?.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)

        //searchMagIcon?.scaleX = 0.65f
        //searchMagIcon?.scaleY = 0.65f


        quick_search?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                if (search(context, query, false))
                    UIHelper.hideKeyboard(quick_search)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                if (isSingleProviderQuickSearch)
                    search(context, newText, true)
                return true
            }
        })

        quick_search_loading_bar.alpha = 0f
        observe(searchViewModel.searchResponse) {
            when (it) {
                is Resource.Success -> {
                    it.value.let { data ->
                        (quick_search_autofit_results?.adapter as? SearchAdapter)?.updateList(
                            context?.filterSearchResultByFilmQuality(data) ?: data
                        )
                    }
                    searchExitIcon?.alpha = 1f
                    quick_search_loading_bar?.alpha = 0f
                }
                is Resource.Failure -> {
                    // Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                    searchExitIcon?.alpha = 1f
                    quick_search_loading_bar?.alpha = 0f
                }
                is Resource.Loading -> {
                    searchExitIcon?.alpha = 0f
                    quick_search_loading_bar?.alpha = 1f
                }
            }
        }


        //quick_search.setOnQueryTextFocusChangeListener { _, b ->
        //    if (b) {
        //        // https://stackoverflow.com/questions/12022715/unable-to-show-keyboard-automatically-in-the-searchview
        //        UIHelper.showInputMethod(view.findFocus())
        //    }
        //}

        quick_search_back.setOnClickListener {
            activity?.popCurrentPage()
        }

        arguments?.getString(AUTOSEARCH_KEY)?.let {
            quick_search?.setQuery(it, true)
            arguments?.remove(AUTOSEARCH_KEY)
        }
    }
}