package com.lagradost.cloudstream3.ui.library

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.activityViewModels
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.providers.MALApi
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import kotlinx.android.synthetic.main.fragment_library.*
import kotlinx.android.synthetic.main.library_viewpager_page.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LibraryFragment : Fragment() {

    companion object {
        fun newInstance() = LibraryFragment()
    }

    private val libraryViewModel: LibraryViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context?.fixPaddingStatusbar(library_root)

        val sortView =
            menu_toolbar?.menu?.findItem(R.id.sort_button)
        sortView?.setOnMenuItemClickListener {
            val methods = libraryViewModel.sortingMethods
                .map { txt(it.stringRes).asString(context ?: view.context) }

            activity?.showBottomDialog(
                methods,
                libraryViewModel.sortingMethods.indexOf(libraryViewModel.currentSortingMethod),
                "Sort by",
                false,
                {},
                {
                    val method = libraryViewModel.sortingMethods[it]
                    libraryViewModel.sort(method)
                }
            )
            true
        }

        val searchView =
            menu_toolbar?.menu?.findItem(R.id.search_button)?.actionView as? MenuSearchView
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                libraryViewModel.sort(ListSorting.Query, newText)
                return true
            }
        })

        libraryViewModel.loadPages()

        viewpager?.setPageTransformer(HomeScrollTransformer())
        viewpager?.adapter = viewpager.adapter ?: ViewpagerAdapter(emptyList())
        viewpager?.offscreenPageLimit = 10

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