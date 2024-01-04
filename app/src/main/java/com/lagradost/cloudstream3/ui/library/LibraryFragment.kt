package com.lagradost.cloudstream3.ui.library

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
import android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.widget.SearchView
import androidx.core.view.allViews
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.openBrowser
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentLibraryBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.debugAssert
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_SHOW_METADATA
import com.lagradost.cloudstream3.ui.settings.SettingsFragment
import com.lagradost.cloudstream3.utils.AppUtils.loadResult
import com.lagradost.cloudstream3.utils.AppUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.AppUtils.reduceDragSensitivity
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import kotlin.math.abs

const val LIBRARY_FOLDER = "library_folder"


enum class LibraryOpenerType(@StringRes val stringRes: Int) {
    Default(R.string.action_default),
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

    var binding: FragmentLibraryBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val layout =
            if (SettingsFragment.isTvSettings()) R.layout.fragment_library_tv else R.layout.fragment_library
        val root = inflater.inflate(layout, container, false)
        binding = try {
            FragmentLibraryBinding.bind(root)
        } catch (t: Throwable) {
            CommonActivity.showToast(
                txt(R.string.unable_to_inflate, t.message ?: ""),
                Toast.LENGTH_LONG
            )
            logError(t)
            null
        }

        return root

        //return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        binding?.viewpager?.currentItem?.let { currentItem ->
            outState.putInt(VIEWPAGER_ITEM_KEY, currentItem)
        }
        super.onSaveInstanceState(outState)
    }

    @SuppressLint("ResourceType", "CutPasteId")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fixPaddingStatusbar(binding?.searchStatusBarPadding)

        binding?.sortFab?.setOnClickListener(sortChangeClickListener)
        binding?.librarySort?.setOnClickListener(sortChangeClickListener)

        binding?.libraryRoot?.findViewById<TextView>(R.id.search_src_text)?.apply {
            tag = "tv_no_focus_tag"
            //Expand the Appbar when search bar is focused, fixing scroll up issue
            setOnFocusChangeListener { _, _ ->
                binding?.searchBar?.setExpanded(true)
            }
        }

        // Set the color for the search exit icon to the correct theme text color
        val searchExitIcon = binding?.mainSearch?.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        val searchExitIconColor = TypedValue()

        activity?.theme?.resolveAttribute(android.R.attr.textColor, searchExitIconColor, true)
        searchExitIcon?.setColorFilter(searchExitIconColor.data)

        val searchCallback = Runnable {
            val newText = binding?.mainSearch?.query?.toString() ?: return@Runnable
            libraryViewModel.sort(ListSorting.Query, newText)
        }

        binding?.mainSearch?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
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

                binding?.mainSearch?.removeCallbacks(searchCallback)

                // Delay the execution of the search operation by 1 second (adjust as needed)
                // this prevents running search when the user is typing
                binding?.mainSearch?.postDelayed(searchCallback, 1000)

                return true
            }
        })

        libraryViewModel.reloadPages(false)

        binding?.listSelector?.setOnClickListener {
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
            val availableProviders = synchronized(allProviders) {
                allProviders.filter {
                    it.supportedSyncNames.contains(syncId)
                }.map { it.name } +
                        // Add the api if it exists
                        (APIHolder.getApiFromNameNull(apiName)?.let { listOf(it.name) }
                            ?: emptyList())
            }
            val baseOptions = listOf(
                LibraryOpenerType.Default,
                LibraryOpenerType.None,
                LibraryOpenerType.Browser,
                LibraryOpenerType.Search
            )

            val items = baseOptions.map { txt(it.stringRes).asString(this) } + availableProviders

            val savedSelection = getKey<LibraryOpener>("$currentAccount/$LIBRARY_FOLDER", key)
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
                    "$currentAccount/$LIBRARY_FOLDER",
                    key,
                    savedData,
                )
            }
        }

        binding?.providerSelector?.setOnClickListener {
            val syncName = libraryViewModel.currentSyncApi?.syncIdName ?: return@setOnClickListener
            activity?.showPluginSelectionDialog(syncName.name, syncName)
        }

        binding?.viewpager?.setPageTransformer(LibraryScrollTransformer())

        binding?.viewpager?.adapter =
            binding?.viewpager?.adapter ?: ViewpagerAdapter(
                mutableListOf(),
                { isScrollingDown: Boolean ->
                    if (isScrollingDown) {
                        binding?.sortFab?.shrink()
                    } else {
                        binding?.sortFab?.extend()
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
                        (activity as? MainActivity)?.loadPopup(searchClickCallback.card, load = false)
                    /*activity?.showPluginSelectionDialog(
                            syncId,
                            syncName,
                            searchClickCallback.card.apiName
                        )*/
                    }

                    SEARCH_ACTION_LOAD -> {
                        // This basically first selects the individual opener and if that is default then
                        // selects the whole list opener
                        val savedListSelection =
                            getKey<LibraryOpener>("$currentAccount/$LIBRARY_FOLDER", syncName.name)
                        val savedSelection = getKey<LibraryOpener>(
                            "$currentAccount/$LIBRARY_FOLDER",
                            syncId
                        ).takeIf {
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

        binding?.apply {
            viewpager.offscreenPageLimit = 2
            viewpager.reduceDragSensitivity()
            searchBar.setExpanded(true)
        }

        val startLoading = Runnable {
            binding?.apply {
                gridview.numColumns = context?.getSpanCount() ?: 3
                gridview.adapter =
                    context?.let { LoadingPosterAdapter(it, 6 * 3) }
                libraryLoadingOverlay.isVisible = true
                libraryLoadingShimmer.startShimmer()
                emptyListTextview.isVisible = false
            }
        }

        val stopLoading = Runnable {
            binding?.apply {
                gridview.adapter = null
                libraryLoadingOverlay.isVisible = false
                libraryLoadingShimmer.stopShimmer()
            }
        }

        val handler = Handler(Looper.getMainLooper())

        observe(libraryViewModel.pages) { resource ->
            when (resource) {
                is Resource.Success -> {
                    handler.removeCallbacks(startLoading)
                    val pages = resource.value
                    val showNotice = pages.all { it.items.isEmpty() }

                    binding?.apply {
                        emptyListTextview.isVisible = showNotice
                        if (showNotice) {
                            if (libraryViewModel.availableApiNames.size > 1) {
                                emptyListTextview.setText(R.string.empty_library_logged_in_message)
                            } else {
                                emptyListTextview.setText(R.string.empty_library_no_accounts_message)
                            }
                        }

                        (viewpager.adapter as? ViewpagerAdapter)?.pages = pages
                        //fix focus on the viewpager itself
                        (viewpager.getChildAt(0) as RecyclerView).apply {
                            tag = "tv_no_focus_tag"
                            //isFocusable = false
                        }

                        // Using notifyItemRangeChanged keeps the animations when sorting
                        viewpager.adapter?.notifyItemRangeChanged(
                            0,
                            viewpager.adapter?.itemCount ?: 0
                        )

                        libraryViewModel.currentPage.value?.let { page ->
                            binding?.viewpager?.setCurrentItem(page, false)
                        }

                        // Only stop loading after 300ms to hide the fade effect the viewpager produces when updating
                        // Without this there would be a flashing effect:
                        // loading -> show old viewpager -> black screen -> show new viewpager
                        handler.postDelayed(stopLoading, 300)

                        savedInstanceState?.getInt(VIEWPAGER_ITEM_KEY)?.let { currentPos ->
                            if (currentPos < 0) return@let
                            viewpager.setCurrentItem(currentPos, false)
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
                            viewpager.startAnimation(hideAnimation)
                            viewpager.startAnimation(showAnimation)
                        }

                        TabLayoutMediator(
                            libraryTabLayout,
                            viewpager,
                        ) { tab, position ->
                            tab.text = pages.getOrNull(position)?.title?.asStringNull(context)
                            tab.view.tag = "tv_no_focus_tag"
                            tab.view.nextFocusDownId = R.id.search_result_root

                            tab.view.setOnClickListener {
                                val currentItem =
                                    binding?.viewpager?.currentItem ?: return@setOnClickListener
                                val distance = abs(position - currentItem)
                                hideViewpager(distance)
                            }
                            //Expand the appBar on tab focus
                            tab.view.setOnFocusChangeListener { _, _ ->
                                binding?.searchBar?.setExpanded(true)
                            }
                        }.attach()

                        binding?.libraryTabLayout?.addOnTabSelectedListener(object: TabLayout.OnTabSelectedListener {
                            override fun onTabSelected(tab: TabLayout.Tab?) {
                                binding?.libraryTabLayout?.selectedTabPosition?.let { page ->
                                    libraryViewModel.switchPage(page)
                                }
                            }
                            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
                            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
                        })
                    }
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

        observe(libraryViewModel.currentPage) { position ->
            val all = binding?.viewpager?.allViews?.toList()
                ?.filterIsInstance<AutofitRecyclerView>()

            all?.forEach { view ->
                view.isVisible = view.tag == position
                view.isFocusable = view.tag == position

                if (view.tag == position)
                    view.descendantFocusability = FOCUS_AFTER_DESCENDANTS
                else
                    view.descendantFocusability = FOCUS_BLOCK_DESCENDANTS
            }
        }

        /*binding?.viewpager?.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {

                super.onPageSelected(position)
            }
        })*/
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        (binding?.viewpager?.adapter as? ViewpagerAdapter)?.rebind()
        super.onConfigurationChanged(newConfig)
    }

    private val sortChangeClickListener = View.OnClickListener { view ->
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
}

class MenuSearchView(context: Context) : SearchView(context) {
    override fun onActionViewCollapsed() {
        super.onActionViewCollapsed()
    }
}