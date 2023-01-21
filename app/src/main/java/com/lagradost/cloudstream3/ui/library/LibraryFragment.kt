package com.lagradost.cloudstream3.ui.library

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.activityViewModels
import com.google.android.material.tabs.TabLayoutMediator
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.debugAssert
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_SHOW_METADATA
import com.lagradost.cloudstream3.utils.AppUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import kotlinx.android.synthetic.main.fragment_library.*

const val LIBRARY_FOLDER = "library_folder"


enum class LibraryOpenerType {
    Provider,
    Browser,
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
    }

    private val libraryViewModel: LibraryViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context?.fixPaddingStatusbar(library_root)

        sort_fab?.setOnClickListener {
            val methods = libraryViewModel.sortingMethods.map {
                txt(it.stringRes).asString(
                    context ?: view.context
                )
            }

            activity?.showBottomDialog(methods,
                libraryViewModel.sortingMethods.indexOf(libraryViewModel.currentSortingMethod),
                "Sort by",
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

            override fun onQueryTextChange(newText: String?): Boolean {
                libraryViewModel.sort(ListSorting.Query, newText)
                return true
            }
        })

        libraryViewModel.loadPages()

        list_selector?.setOnClickListener {
            val items = libraryViewModel.availableApiNames
            val currentItem = libraryViewModel.currentApiName.value

            activity?.showBottomDialog(items,
                items.indexOf(currentItem),
                "Select library",
                false,
                {}) {
                val selectedItem = items.getOrNull(it) ?: return@showBottomDialog
                libraryViewModel.switchList(selectedItem)
            }
        }


        /**
         * Shows a plugin selection dialogue and saves the response
         **/
        fun showPluginSelectionDialog(key: String, syncId: SyncIdName) {
            val availableProviders = allProviders.filter {
                it.supportedSyncNames.contains(syncId)
            }.map { it.name }

            val baseOptions = listOf(LibraryOpenerType.Browser.name)

            val items = baseOptions + availableProviders

            val savedSelection = getKey<LibraryOpener>(LIBRARY_FOLDER, key)
            val selectedIndex =
                when {
                    savedSelection == null -> -1
                    // If provider
                    savedSelection.openType == LibraryOpenerType.Provider
                            && savedSelection.providerData?.apiName != null -> {
                        availableProviders.indexOf(savedSelection.providerData.apiName).takeIf { it != -1 }
                            ?.plus(baseOptions.size) ?: -1
                    }
                    // Else base option
                    else -> baseOptions.indexOf(savedSelection.openType.name)
                }

            activity?.showBottomDialog(
                items,
                selectedIndex,
                "Open with",
                true,
                {},
            ) {
                val savedData = if (it < baseOptions.size) {
                    LibraryOpener(
                        LibraryOpenerType.valueOf(baseOptions[it]),
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
            showPluginSelectionDialog(syncName.name, syncName)
        }

        viewpager?.setPageTransformer(LibraryScrollTransformer())
        viewpager?.adapter =
            viewpager.adapter ?: ViewpagerAdapter(emptyList(), { isScrollingDown: Boolean ->
                if (isScrollingDown) {
                    sort_fab?.shrink()
                } else {
                    sort_fab?.extend()
                }
            }) callback@{ searchClickCallback ->

                // To prevent future accidents
                debugAssert({
                    searchClickCallback.card !is LibraryItem
                }, {
                    "searchClickCallback ${searchClickCallback.card} is not a LibraryItem"
                })
                val syncId = (searchClickCallback.card as LibraryItem).syncId

                println("SEARCH CLICK $searchClickCallback")
                when (searchClickCallback.action) {
                    SEARCH_ACTION_SHOW_METADATA -> {
                        val syncName =
                            libraryViewModel.currentSyncApi?.syncIdName ?: return@callback
                        showPluginSelectionDialog(syncId, syncName)
                    }
                    SEARCH_ACTION_LOAD -> {
                        val savedSelection = getKey<LibraryOpener>(LIBRARY_FOLDER, syncId)
                        activity?.loadSearchResult(searchClickCallback.card)
                    }
                }
            }
        viewpager?.offscreenPageLimit = 2

        observe(libraryViewModel.pages) { pages ->
            (viewpager.adapter as? ViewpagerAdapter)?.pages = pages
            viewpager.adapter?.notifyItemChanged(viewpager?.currentItem ?: 0)

            TabLayoutMediator(
                library_tab_layout,
                viewpager,
            ) { tab, position ->
                tab.text = pages.getOrNull(position)?.title
            }.attach()
        }
    }
}

class MenuSearchView(context: Context) : SearchView(context) {
    override fun onActionViewCollapsed() {
        super.onActionViewCollapsed()
    }
}