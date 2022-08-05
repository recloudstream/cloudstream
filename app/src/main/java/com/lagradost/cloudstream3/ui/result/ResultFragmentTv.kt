package com.lagradost.cloudstream3.ui.result

import android.os.Bundle
import android.view.View
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.discord.panels.OverlappingPanelsLayout
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.ResourceSome
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import kotlinx.android.synthetic.main.fragment_result_swipe.*
import kotlinx.android.synthetic.main.fragment_result_tv.*
import kotlinx.android.synthetic.main.result_recommendations.*

class ResultFragmentTv : ResultFragment() {
    override val resultLayout = R.layout.fragment_result_tv

    private fun handleSelection(data: Any) {
        when (data) {
            is EpisodeRange -> {
                viewModel.changeRange(data)
            }
            is Int -> {
                viewModel.changeSeason(data)
            }
            is DubStatus -> {
                viewModel.changeDubStatus(data)
            }
        }
    }

    private fun RecyclerView?.select(index: Int) {
        (this?.adapter as? SelectAdaptor?)?.select(index, this)
    }

    private fun RecyclerView?.update(data: List<SelectData>) {
        (this?.adapter as? SelectAdaptor?)?.updateSelectionList(data)
        this?.isVisible = data.size > 1
    }

    private fun RecyclerView?.setAdapter() {
        this?.adapter = SelectAdaptor { data ->
            handleSelection(data)
        }
    }

    private fun hasNoFocus(): Boolean {
        val focus = activity?.currentFocus
        if (focus == null || !focus.isVisible) return true
        return focus == this.result_root
    }

    override fun updateEpisodes(episodes: ResourceSome<List<ResultEpisode>>) {
        super.updateEpisodes(episodes)
        if (episodes is ResourceSome.Success && hasNoFocus()) {
            result_episodes?.requestFocus()
        }
    }

    override fun updateMovie(data: ResourceSome<Pair<UiText, ResultEpisode>>) {
        super.updateMovie(data)
        if (data is ResourceSome.Success && hasNoFocus()) {
            result_play_movie?.requestFocus()
        }
    }

    override fun setRecommendations(rec: List<SearchResponse>?, validApiName: String?) {
        val isInvalid = rec.isNullOrEmpty()
        result_recommendations?.isGone = isInvalid
        result_recommendations_btt?.isGone = isInvalid
        val matchAgainst = validApiName ?: rec?.firstOrNull()?.apiName
        (result_recommendations?.adapter as SearchAdapter?)?.updateList(rec?.filter { it.apiName == matchAgainst } ?: emptyList())

        rec?.map { it.apiName }?.distinct()?.let { apiNames ->
            // very dirty selection
            result_recommendations_filter_button?.isVisible = apiNames.size > 1
            result_recommendations_filter_button?.text = matchAgainst

        } ?: run {
            result_recommendations_filter_button?.isVisible = false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (result_episodes?.adapter as EpisodeAdapter?)?.apply {
            layout = R.layout.result_episode_both_tv
        }

        result_season_selection.setAdapter()
        result_range_selection.setAdapter()
        result_dub_selection.setAdapter()

        observe(viewModel.selectedRangeIndex) { selected ->
            result_range_selection.select(selected)
        }
        observe(viewModel.selectedSeasonIndex) { selected ->
            result_season_selection.select(selected)
        }
        observe(viewModel.selectedDubStatusIndex) { selected ->
            result_dub_selection.select(selected)
        }
        observe(viewModel.rangeSelections) {
            result_range_selection.update(it)
        }
        observe(viewModel.dubSubSelections) {
            result_dub_selection.update(it)
        }
        observe(viewModel.seasonSelections) {
            result_season_selection.update(it)
        }

        result_recommendations?.spanCount = 8
        result_recommendations?.adapter =
            SearchAdapter(
                ArrayList(),
                result_recommendations,
            ) { callback ->
                SearchHelper.handleSearchClickCallback(activity, callback)
            }
    }
}