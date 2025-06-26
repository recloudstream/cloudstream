package com.lagradost.cloudstream3.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.HomeRemoveGridBinding
import com.lagradost.cloudstream3.databinding.HomeRemoveGridExpandedBinding
import com.lagradost.cloudstream3.databinding.HomeResultGridBinding
import com.lagradost.cloudstream3.databinding.HomeResultGridExpandedBinding
import com.lagradost.cloudstream3.ui.BaseAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.UIHelper.isBottomLayout
import com.lagradost.cloudstream3.utils.UIHelper.toPx

class HomeScrollViewHolderState(view: ViewBinding) : ViewHolderState<Boolean>(view) {
    // very shitty that we cant store the state when the view clears,
    // but this is because the focus clears before the view is removed
    // so we have to manually store it
    var wasFocused: Boolean = false
    override fun save(): Boolean = wasFocused
    override fun restore(state: Boolean) {
        if (state) {
            wasFocused = false
            // only refocus if tv
            if (isLayout(TV)) {
                itemView.requestFocus()
            }
        }
    }
}

class ResumeItemAdapter(
    fragment: Fragment,
    nextFocusUp: Int? = null,
    nextFocusDown: Int? = null,
    clickCallback: (SearchClickCallback) -> Unit,
    private val removeCallback: (View) -> Unit,
) : HomeChildItemAdapter(
    fragment = fragment,
    id = "resumeAdapter".hashCode(),
    nextFocusUp = nextFocusUp,
    nextFocusDown = nextFocusDown,
    clickCallback = clickCallback
) {
    // As there is no popup on TV we instead use the footer to clear
    override val footers = if (isLayout(TV or EMULATOR)) 1 else 0

    override fun onCreateFooter(parent: ViewGroup): ViewHolderState<Boolean> {
        val expanded = parent.context.isBottomLayout()
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (expanded) HomeRemoveGridExpandedBinding.inflate(
            inflater,
            parent,
            false
        ) else HomeRemoveGridBinding.inflate(inflater, parent, false)
        return HomeScrollViewHolderState(binding)
    }

    override fun onBindFooter(holder: ViewHolderState<Boolean>) {
        this.applyBinding(holder, false)
        holder.itemView.apply {
            if (isLayout(TV)) {
                isFocusableInTouchMode = true
                isFocusable = true
            }

            if (nextFocusUp != null) {
                nextFocusUpId = nextFocusUp
            }

            if (nextFocusDown != null) {
                nextFocusDownId = nextFocusDown
            }

            setOnClickListener { v ->
                removeCallback.invoke(v ?: return@setOnClickListener)
            }
        }
    }
}

open class HomeChildItemAdapter(
    fragment: Fragment,
    id: Int,
    protected val nextFocusUp: Int? = null,
    protected val nextFocusDown: Int? = null,
    private val clickCallback: (SearchClickCallback) -> Unit,
) :
    BaseAdapter<SearchResponse, Boolean>(fragment, id) {
    var isHorizontal: Boolean = false
    var hasNext: Boolean = false

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Boolean> {
        val expanded = parent.context.isBottomLayout()
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (expanded) HomeResultGridExpandedBinding.inflate(
            inflater,
            parent,
            false
        ) else HomeResultGridBinding.inflate(inflater, parent, false)
        return HomeScrollViewHolderState(binding)
    }

    protected fun applyBinding(holder: ViewHolderState<Boolean>, isFirstItem: Boolean) {
        val context = holder.view.root.context
        val scale = PreferenceManager.getDefaultSharedPreferences(context)
            ?.getInt(context.getString(R.string.poster_size_key), 0) ?: 0
        // Scale by +10% per step
        val mul = 1.0f + scale * 0.1f
        val min = (114.toPx.toFloat() * mul).toInt()
        val max = (180.toPx.toFloat() * mul).toInt()

        when (val binding = holder.view) {
            is HomeResultGridBinding -> {
                binding.backgroundCard.apply {

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

                if (isFirstItem) { // to fix tv
                    binding.backgroundCard.nextFocusLeftId = R.id.nav_rail_view
                }
            }
        }
    }

    override fun onBindContent(
        holder: ViewHolderState<Boolean>,
        item: SearchResponse,
        position: Int
    ) {
        applyBinding(holder, position == 0)

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
            nextFocusUp,
            nextFocusDown
        )

        holder.itemView.tag = position
    }
}
