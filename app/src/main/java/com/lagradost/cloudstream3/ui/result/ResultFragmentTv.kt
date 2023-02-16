package com.lagradost.cloudstream3.ui.result

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.APIHolder.updateHasTrailers
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.ResourceSome
import com.lagradost.cloudstream3.mvvm.Some
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.player.ExtractorLinkGenerator
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialogInstant
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import kotlinx.android.synthetic.main.fragment_result_tv.*

class ResultFragmentTv : ResultFragment() {
    override val resultLayout = R.layout.fragment_result_tv

    private var currentRecommendations: List<SearchResponse> = emptyList()

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
            is String -> {
                setRecommendations(currentRecommendations, data)
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

    override fun setTrailers(trailers: List<ExtractorLink>?) {
        context?.updateHasTrailers()
        if (!LoadResponse.isTrailersEnabled) return

        result_play_trailer?.isGone = trailers.isNullOrEmpty()
        result_play_trailer?.setOnClickListener {
            if (trailers.isNullOrEmpty()) return@setOnClickListener
            activity.navigate(
                R.id.global_to_navigation_player, GeneratorPlayer.newInstance(
                    ExtractorLinkGenerator(
                        trailers,
                        emptyList()
                    )
                )
            )
        }
    }

    override fun setRecommendations(rec: List<SearchResponse>?, validApiName: String?) {
        currentRecommendations = rec ?: emptyList()
        val isInvalid = rec.isNullOrEmpty()
        result_recommendations?.isGone = isInvalid
        result_recommendations_holder?.isGone = isInvalid
        val matchAgainst = validApiName ?: rec?.firstOrNull()?.apiName
        (result_recommendations?.adapter as? SearchAdapter)?.updateList(rec?.filter { it.apiName == matchAgainst }
            ?: emptyList())

        rec?.map { it.apiName }?.distinct()?.let { apiNames ->
            // very dirty selection
            result_recommendations_filter_selection?.isVisible = apiNames.size > 1
            result_recommendations_filter_selection?.update(apiNames.map { txt(it) to it })
            result_recommendations_filter_selection?.select(apiNames.indexOf(matchAgainst))
        } ?: run {
            result_recommendations_filter_selection?.isVisible = false
        }
    }

    var loadingDialog: Dialog? = null
    var popupDialog: Dialog? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        result_episodes?.layoutManager =
                //LinearListLayout(result_episodes ?: return, result_episodes?.context).apply {
            LinearListLayout(result_episodes?.context).apply {
                setHorizontal()
            }
        (result_episodes?.adapter as EpisodeAdapter?)?.apply {
            layout = R.layout.result_episode_both_tv
        }
        //result_episodes?.setMaxViewPoolSize(0, Int.MAX_VALUE)

        result_season_selection.setAdapter()
        result_range_selection.setAdapter()
        result_dub_selection.setAdapter()
        result_recommendations_filter_selection.setAdapter()

        observe(viewModel.selectPopup) { popup ->
            when (popup) {
                is Some.Success -> {
                    popupDialog?.dismissSafe(activity)

                    popupDialog = activity?.let { act ->
                        val pop = popup.value
                        val options = pop.getOptions(act)
                        val title = pop.getTitle(act)

                        act.showBottomDialogInstant(
                            options, title, {
                                popupDialog = null
                                pop.callback(null)
                            }, {
                                popupDialog = null
                                pop.callback(it)
                            }
                        )
                    }
                }
                is Some.None -> {
                    popupDialog?.dismissSafe(activity)
                    popupDialog = null
                }
            }
        }

        observe(viewModel.loadedLinks) { load ->
            when (load) {
                is Some.Success -> {
                    if (loadingDialog?.isShowing != true) {
                        loadingDialog?.dismissSafe(activity)
                        loadingDialog = null
                    }
                    loadingDialog = loadingDialog ?: context?.let { ctx ->
                        val builder = BottomSheetDialog(ctx)
                        builder.setContentView(R.layout.bottom_loading)
                        builder.setOnDismissListener {
                            loadingDialog = null
                            viewModel.cancelLinks()
                        }
                        //builder.setOnCancelListener {
                        //    it?.dismiss()
                        //}
                        builder.setCanceledOnTouchOutside(true)
                        builder.show()
                        builder
                    }
                }
                is Some.None -> {
                    loadingDialog?.dismissSafe(activity)
                    loadingDialog = null
                }
            }
        }


        observe(viewModel.episodesCountText) { count ->
            result_episodes_text.setText(count)
        }

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

        result_back?.setOnClickListener {
            activity?.popCurrentPage()
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