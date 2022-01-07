package com.lagradost.cloudstream3.ui.quicksearch

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.loadHomepageList
import com.lagradost.cloudstream3.ui.home.ParentItemAdapter
import com.lagradost.cloudstream3.ui.search.*
import com.lagradost.cloudstream3.ui.search.SearchFragment.Companion.filterSearchResponse
import com.lagradost.cloudstream3.utils.UIHelper
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import kotlinx.android.synthetic.main.fragment_search.*
import kotlinx.android.synthetic.main.quick_search.*
import java.util.concurrent.locks.ReentrantLock

class QuickSearchFragment(var isMainApis: Boolean = false) : Fragment() {
    companion object {
        fun pushSearch(activity: Activity?, autoSearch: String? = null) {
            activity.navigate(R.id.global_to_navigation_quick_search, Bundle().apply {
                putBoolean("mainapi", true)
                putString("autosearch", autoSearch)
            })
        }

        fun pushSync(activity: Activity?, autoSearch: String? = null, callback: (SearchClickCallback) -> Unit) {
            clickCallback = callback
            activity.navigate(R.id.global_to_navigation_quick_search, Bundle().apply {
                putBoolean("mainapi", false)
                putString("autosearch", autoSearch)
            })
        }

        var clickCallback: ((SearchClickCallback) -> Unit)? = null
    }

    private val searchViewModel: SearchViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        return inflater.inflate(R.layout.quick_search, container, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        clickCallback = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context?.fixPaddingStatusbar(quick_search_root)

        arguments?.getBoolean("mainapi", true)?.let {
            isMainApis = it
        }

        val listLock = ReentrantLock()
        observe(searchViewModel.currentSearch) { list ->
            try {
                // https://stackoverflow.com/questions/6866238/concurrent-modification-exception-adding-to-an-arraylist
                listLock.lock()
                (quick_search_master_recycler?.adapter as ParentItemAdapter?)?.apply {
                    items = list.map { ongoing ->
                        val ongoingList = HomePageList(
                            ongoing.apiName,
                            if (ongoing.data is Resource.Success) ongoing.data.value.filterSearchResponse() else ArrayList()
                        )
                        ongoingList
                    }
                    notifyDataSetChanged()
                }
            } catch (e: Exception) {
                logError(e)
            } finally {
                listLock.unlock()
            }
        }

        val masterAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder> = ParentItemAdapter(listOf(), { callback ->
            when (callback.action) {
                SEARCH_ACTION_LOAD -> {
                    if (isMainApis) {
                        activity?.popCurrentPage()

                        SearchHelper.handleSearchClickCallback(activity, callback)
                    } else {
                        clickCallback?.invoke(callback)
                    }
                }
                else -> SearchHelper.handleSearchClickCallback(activity, callback)
            }
        }, { item ->
            activity?.loadHomepageList(item)
        })

        val searchExitIcon = quick_search.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        val searchMagIcon = quick_search.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)

        searchMagIcon.scaleX = 0.65f
        searchMagIcon.scaleY = 0.65f
        quick_search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                searchViewModel.searchAndCancel(query = query, isMainApis = isMainApis, ignoreSettings = true)
                quick_search?.let {
                    UIHelper.hideKeyboard(it)
                }

                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                //searchViewModel.quickSearch(newText)
                return true
            }
        })

        quick_search_loading_bar.alpha = 0f
        observe(searchViewModel.searchResponse) {
            when (it) {
                is Resource.Success -> {
                    it.value.let { data ->
                        if (data.isNotEmpty()) {
                            (search_autofit_results?.adapter as SearchAdapter?)?.apply {
                                cardList = data.toList()
                                notifyDataSetChanged()
                            }
                        }
                    }
                    searchExitIcon.alpha = 1f
                    quick_search_loading_bar.alpha = 0f
                }
                is Resource.Failure -> {
                    // Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                    searchExitIcon.alpha = 1f
                    quick_search_loading_bar.alpha = 0f
                }
                is Resource.Loading -> {
                    searchExitIcon.alpha = 0f
                    quick_search_loading_bar.alpha = 1f
                }
            }
        }

        quick_search_master_recycler.adapter = masterAdapter
        quick_search_master_recycler.layoutManager = GridLayoutManager(context, 1)

        //quick_search.setOnQueryTextFocusChangeListener { _, b ->
        //    if (b) {
        //        // https://stackoverflow.com/questions/12022715/unable-to-show-keyboard-automatically-in-the-searchview
        //        UIHelper.showInputMethod(view.findFocus())
        //    }
        //}

        quick_search_back.setOnClickListener {
            activity?.popCurrentPage()
        }

        arguments?.getString("autosearch")?.let {
            quick_search.setQuery(it, true)
            arguments?.remove("autosearch")
        }
    }
}