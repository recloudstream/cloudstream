package com.lagradost.cloudstream3.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.HomeResultGridBinding
import com.lagradost.cloudstream3.databinding.HomeResultGridExpandedBinding
import com.lagradost.cloudstream3.ui.BaseAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.UIHelper.IsBottomLayout
import com.lagradost.cloudstream3.utils.UIHelper.toPx

class HomeScrollViewHolderState(view: ViewBinding) : ViewHolderState<Boolean>(view) {
    /*private fun recursive(view : View) : Boolean {
        if (view.isFocused) {
            println("VIEW: $view | id=${view.id}")
        }
        return (view as? ViewGroup)?.children?.any { recursive(it) } ?: false
    }*/

    // very shitty that we cant store the state when the view clears,
    // but this is because the focus clears before the view is removed
    // so we have to manually store it
    var wasFocused: Boolean = false
    override fun save(): Boolean = wasFocused
    override fun restore(state: Boolean) {
        if (state) {
            wasFocused = false
            // only refocus if tv
            if(isLayout(TV)) {
                itemView.requestFocus()
            }
        }
    }
}

class HomeChildItemAdapter(
    fragment: Fragment,
    id: Int,
    private val nextFocusUp: Int? = null,
    private val nextFocusDown: Int? = null,
    private val clickCallback: (SearchClickCallback) -> Unit,
) :
    BaseAdapter<SearchResponse, Boolean>(fragment, id) {
    var isHorizontal: Boolean = false
    var hasNext: Boolean = false

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Boolean> {
        val expanded = parent.context.IsBottomLayout()
        /* val layout = if (bottom) R.layout.home_result_grid_expanded else R.layout.home_result_grid

         val root = LayoutInflater.from(parent.context).inflate(layout, parent, false)
         val binding = HomeResultGridBinding.bind(root)*/

        val inflater = LayoutInflater.from(parent.context)
        val binding = if (expanded) HomeResultGridExpandedBinding.inflate(
            inflater,
            parent,
            false
        ) else HomeResultGridBinding.inflate(inflater, parent, false)
        return HomeScrollViewHolderState(binding)
    }

    override fun onBindContent(
        holder: ViewHolderState<Boolean>,
        item: SearchResponse,
        position: Int
    ) {
        when (val binding = holder.view) {
            is HomeResultGridBinding -> {
                binding.backgroundCard.apply {
                    val min = 114.toPx
                    val max = 180.toPx

                    layoutParams =
                        layoutParams.apply {
                            width = if (!isHorizontal) {
                                min
                            } else {
                                max
                            }
                            height = if (!isHorizontal) {
                                max
                            } else {
                                min
                            }
                        }
                }
            }

            is HomeResultGridExpandedBinding -> {
                binding.backgroundCard.apply {
                    val min = 114.toPx
                    val max = 180.toPx

                    layoutParams =
                        layoutParams.apply {
                            width = if (!isHorizontal) {
                                min
                            } else {
                                max
                            }
                            height = if (!isHorizontal) {
                                max
                            } else {
                                min
                            }
                        }
                }

                if (position == 0) { // to fix tv
                    binding.backgroundCard.nextFocusLeftId = R.id.nav_rail_view
                }
            }
        }

        SearchResultBuilder.bind(
            clickCallback = { click ->
                // ok, so here we hijack the callback to fix the focus
                when (click.action) {
                    SEARCH_ACTION_LOAD -> (holder as? HomeScrollViewHolderState)?.wasFocused = true
                }
                clickCallback(click)
            },
            item,
            position,
            holder.itemView,
            null, // nextFocusBehavior,
            nextFocusUp,
            nextFocusDown
        )

        holder.itemView.tag = position
    }
}
