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
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import kotlinx.android.synthetic.main.fragment_library.*

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

        sort_fab?.setOnClickListener {
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

        viewpager?.setPageTransformer(LibraryScrollTransformer())
        viewpager?.adapter =
            viewpager.adapter ?: ViewpagerAdapter(emptyList()) { isScrollingDown: Boolean ->
                if (isScrollingDown) {
                    sort_fab?.shrink()
                } else {
                    sort_fab?.extend()
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