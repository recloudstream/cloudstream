package com.lagradost.cloudstream3.ui.library

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import androidx.annotation.StringRes
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.google.android.material.tabs.TabLayoutMediator
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.openBrowser
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.debugAssert
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_SHOW_METADATA
import com.lagradost.cloudstream3.utils.AppUtils.loadResult
import com.lagradost.cloudstream3.utils.AppUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.AppUtils.reduceDragSensitivity
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import kotlinx.android.synthetic.main.fragment_library.*
import kotlin.math.abs

const val LIBRARY_FOLDER = "library_folder"


enum class LibraryOpenerType(@StringRes val stringRes: Int) {
    Default(R.string.default_subtitles), // TODO FIX AFTER MERGE
    Provider(R.string.none),
    Browser(R.string.browser),
    Search(R.string.search),
    None(R.string.none),
}

/** Used to store how the user wants to open said poster */
data class LibraryOpener(
    val openType: LibraryOpenerType,
    val providerData: ProviderLibraryData?,
)

data class ProviderLibraryData(
    val apiName: String
)

class LibraryFragment : Fragment() {
    companion object {
        fun newInstance() = LibraryFragment()

        /**
         * Store which page was last seen when exiting the fragment and returning
         **/
        const val VIEWPAGER_ITEM_KEY = "viewpager_item"
    }

    private val libraryViewModel: LibraryViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewpager?.currentItem?.let { currentItem ->
            outState.putInt(VIEWPAGER_ITEM_KEY, currentItem)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context?.fixPaddingStatusbar(search_status_bar_padding)

        sort_fab?.setOnClickListener {
            val methods = libraryViewModel.sortingMethods.map {
                txt(it.stringRes).asString(view.context)
            }

            activity?.showBottomDialog(methods,
                libraryViewModel.sortingMethods.indexOf(libraryViewModel.currentSortingMethod),
                txt(R.string.sort_by).asString(view.context),
                false,
                {},
                {
                    val method = libraryViewModel.sortingMethods[it]
                    libraryViewModel.sort(method)
                })
        }

        main_search?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                libraryViewModel.sort(ListSorting.Query, query)
                return true
            }

            // This is required to prevent the first text change
            // When this is attached it'll immediately send a onQueryTextChange("")
            // Which we do not want
            var hasInitialized = false
            override fun onQueryTextChange(newText: String?): Boolean {
                if (!hasInitialized) {
                    hasInitialized = true
                    return true
                }

                libraryViewModel.sort(ListSorting.Query, newText)
                return true
            }
        })

        libraryViewModel.reloadPages(false)

        list_selector?.setOnClickListener {
            val items = libraryViewModel.availableApiNames
            val currentItem = libraryViewModel.currentApiName.value

            activity?.showBottomDialog(items,
                items.indexOf(currentItem),
                txt(R.string.select_library).asString(it.context),
                false,
                {}) { index ->
                val selectedItem = items.getOrNull(index) ?: return@showBottomDialog
                libraryViewModel.switchList(selectedItem)
            }
        }


        /**
         * Shows a plugin selection dialogue and saves the response
         **/
        fun Activity.showPluginSelectionDialog(
            key: String,
            syncId: SyncIdName,
            apiName: String? = null,
        ) {
            val availableProviders = allProviders.filter {
                it.supportedSyncNames.contains(syncId)
            }.map { it.name } +
                    // Add the api if it exists
                    (APIHolder.getApiFromNameNull(apiName)?.let { listOf(it.name) } ?: emptyList())

            val baseOptions = listOf(
                LibraryOpenerType.Default,
                LibraryOpenerType.None,
                LibraryOpenerType.Browser,
                LibraryOpenerType.Search
            )

            val items = baseOptions.map { txt(it.stringRes).asString(this) } + availableProviders

            val savedSelection = getKey<LibraryOpener>(LIBRARY_FOLDER, key)
            val selectedIndex =
                when {
                    savedSelection == null -> 0
                    // If provider
                    savedSelection.openType == LibraryOpenerType.Provider
                            && savedSelection.providerData?.apiName != null -> {
                        availableProviders.indexOf(savedSelection.providerData.apiName)
                            .takeIf { it != -1 }
                            ?.plus(baseOptions.size) ?: 0
                    }
                    // Else base option
                    else -> baseOptions.indexOf(savedSelection.openType)
                }

            this.showBottomDialog(
                items,
                selectedIndex,
                txt(R.string.open_with).asString(this),
                false,
                {},
            ) {
                val savedData = if (it < baseOptions.size) {
                    LibraryOpener(
                        baseOptions[it],
                        null
                    )
                } else {
                    LibraryOpener(
                        LibraryOpenerType.Provider,
                        ProviderLibraryData(items[it])
                    )
                }

                setKey(
                    LIBRARY_FOLDER,
                    key,
                    savedData,
                )
            }
        }

        provider_selector?.setOnClickListener {
            val syncName = libraryViewModel.currentSyncApi?.syncIdName ?: return@setOnClickListener
            activity?.showPluginSelectionDialog(syncName.name, syncName)
        }

        viewpager?.setPageTransformer(LibraryScrollTransformer())
        viewpager?.adapter =
            viewpager.adapter ?: ViewpagerAdapter(mutableListOf(), { isScrollingDown: Boolean ->
                if (isScrollingDown) {
                    sort_fab?.shrink()
                } else {
                    sort_fab?.extend()
                }
            }) callback@{ searchClickCallback ->
                // To prevent future accidents
                debugAssert({
                    searchClickCallback.card !is SyncAPI.LibraryItem
                }, {
                    "searchClickCallback ${searchClickCallback.card} is not a LibraryItem"
                })

                val syncId = (searchClickCallback.card as SyncAPI.LibraryItem).syncId
                val syncName =
                    libraryViewModel.currentSyncApi?.syncIdName ?: return@callback

                when (searchClickCallback.action) {
                    SEARCH_ACTION_SHOW_METADATA -> {
                        activity?.showPluginSelectionDialog(
                            syncId,
                            syncName,
                            searchClickCallback.card.apiName
                        )
                    }

                    SEARCH_ACTION_LOAD -> {
                        // This basically first selects the individual opener and if that is default then
                        // selects the whole list opener
                        val savedListSelection =
                            getKey<LibraryOpener>(LIBRARY_FOLDER, syncName.name)
                        val savedSelection = getKey<LibraryOpener>(LIBRARY_FOLDER, syncId).takeIf {
                            it?.openType != LibraryOpenerType.Default
                        } ?: savedListSelection

                        when (savedSelection?.openType) {
                            null, LibraryOpenerType.Default -> {
                                // Prevents opening MAL/AniList as a provider
                                if (APIHolder.getApiFromNameNull(searchClickCallback.card.apiName) != null) {
                                    activity?.loadSearchResult(
                                        searchClickCallback.card
                                    )
                                } else {
                                    // Search when no provider can open
                                    QuickSearchFragment.pushSearch(
                                        activity,
                                        searchClickCallback.card.name
                                    )
                                }
                            }
                            LibraryOpenerType.None -> {}
                            LibraryOpenerType.Provider ->
                                savedSelection.providerData?.apiName?.let { apiName ->
                                    activity?.loadResult(
                                        searchClickCallback.card.url,
                                        apiName,
                                    )
                                }
                            LibraryOpenerType.Browser ->
                                openBrowser(searchClickCallback.card.url)
                            LibraryOpenerType.Search -> {
                                QuickSearchFragment.pushSearch(
                                    activity,
                                    searchClickCallback.card.name
                                )
                            }
                        }
                    }
                }
            }

        viewpager?.offscreenPageLimit = 2
        viewpager?.reduceDragSensitivity()

        val startLoading = Runnable {
            gridview?.numColumns = context?.getSpanCount() ?: 3
            gridview?.adapter =
                context?.let { LoadingPosterAdapter(it, 6 * 3) }
            library_loading_overlay?.isVisible = true
            library_loading_shimmer?.startShimmer()
            empty_list_textview?.isVisible = false
        }

        val stopLoading = Runnable {
            gridview?.adapter = null
            library_loading_overlay?.isVisible = false
            library_loading_shimmer?.stopShimmer()
        }

        val handler = Handler(Looper.getMainLooper())

        observe(libraryViewModel.pages) { resource ->
            when (resource) {
                is Resource.Success -> {
                    handler.removeCallbacks(startLoading)
                    val pages = resource.value
                    val showNotice = pages.all { it.items.isEmpty() }
                    empty_list_textview?.isVisible = showNotice
                    if (showNotice) {
                        if (libraryViewModel.availableApiNames.size > 1) {
                            empty_list_textview?.setText(R.string.empty_library_logged_in_message)
                        } else {
                            empty_list_textview?.setText(R.string.empty_library_no_accounts_message)
                        }
                    }

                    (viewpager.adapter as? ViewpagerAdapter)?.pages = pages
                    // Using notifyItemRangeChanged keeps the animations when sorting
                    viewpager.adapter?.notifyItemRangeChanged(0, viewpager.adapter?.itemCount ?: 0)

                    // Only stop loading after 300ms to hide the fade effect the viewpager produces when updating
                    // Without this there would be a flashing effect:
                    // loading -> show old viewpager -> black screen -> show new viewpager
                    handler.postDelayed(stopLoading, 300)

                    savedInstanceState?.getInt(VIEWPAGER_ITEM_KEY)?.let { currentPos ->
                        if (currentPos < 0) return@let
                        viewpager?.setCurrentItem(currentPos, false)
                        // Using remove() sets the key to 0 instead of removing it
                        savedInstanceState.putInt(VIEWPAGER_ITEM_KEY, -1)
                    }

                    // Since the animation to scroll multiple items is so much its better to just hide
                    // the viewpager a bit while the fastest animation is running
                    fun hideViewpager(distance: Int) {
                        if (distance < 3) return

                        val hideAnimation = AlphaAnimation(1f, 0f).apply {
                            duration = distance * 50L
                            fillAfter = true
                        }
                        val showAnimation = AlphaAnimation(0f, 1f).apply {
                            duration = distance * 50L
                            startOffset = distance * 100L
                            fillAfter = true
                        }
                        viewpager?.startAnimation(hideAnimation)
                        viewpager?.startAnimation(showAnimation)
                    }

                    TabLayoutMediator(
                        library_tab_layout,
                        viewpager,
                    ) { tab, position ->
                        tab.text = pages.getOrNull(position)?.title?.asStringNull(context)
                        tab.view.setOnClickListener {
                            val currentItem = viewpager?.currentItem ?: return@setOnClickListener
                            val distance = abs(position - currentItem)
                            hideViewpager(distance)
                        }
                    }.attach()
                }
                is Resource.Loading -> {
                    // Only start loading after 200ms to prevent loading cached lists
                    handler.postDelayed(startLoading, 200)
                }
                is Resource.Failure -> {
                    stopLoading.run()
                    // No user indication it failed :(
                    // TODO
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        (viewpager.adapter as? ViewpagerAdapter)?.rebind()
        super.onConfigurationChanged(newConfig)
    }
}

class MenuSearchView(context: Context) : SearchView(context) {
    override fun onActionViewCollapsed() {
        super.onActionViewCollapsed()
    }
}