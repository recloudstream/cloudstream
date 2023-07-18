package com.lagradost.cloudstream3.ui.result

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.APIHolder.updateHasTrailers
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.FragmentResultTvBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup
import com.lagradost.cloudstream3.ui.player.ExtractorLinkGenerator
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.utils.AppUtils.html
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialogInstant
import com.lagradost.cloudstream3.utils.UIHelper
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import kotlinx.coroutines.delay

class ResultFragmentTv : ResultFragment() {
    override var layout = R.layout.fragment_result_tv

    private var binding: FragmentResultTvBinding? = null

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = super.onCreateView(inflater, container, savedInstanceState) ?: return null
        binding = FragmentResultTvBinding.bind(root)

        return root
    }

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
        return focus == binding?.resultRoot
    }

    override fun setTrailers(trailers: List<ExtractorLink>?) {
        context?.updateHasTrailers()
        if (!LoadResponse.isTrailersEnabled) return
        binding?.resultPlayTrailer?.apply {
            isGone = trailers.isNullOrEmpty()
            setOnClickListener {
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
    }

    private fun setRecommendations(rec: List<SearchResponse>?, validApiName: String?) {
        currentRecommendations = rec ?: emptyList()
        val isInvalid = rec.isNullOrEmpty()
        binding?.apply {
            resultRecommendationsList.isGone = isInvalid
            resultRecommendationsHolder.isGone = isInvalid
            val matchAgainst = validApiName ?: rec?.firstOrNull()?.apiName
            (resultRecommendationsList.adapter as? SearchAdapter)?.updateList(rec?.filter { it.apiName == matchAgainst }
                ?: emptyList())

            rec?.map { it.apiName }?.distinct()?.let { apiNames ->
                // very dirty selection
                resultRecommendationsFilterSelection.isVisible = apiNames.size > 1
                resultRecommendationsFilterSelection.update(apiNames.map { txt(it) to it })
                resultRecommendationsFilterSelection.select(apiNames.indexOf(matchAgainst))
            } ?: run {
                resultRecommendationsFilterSelection.isVisible = false
            }
        }
    }

    var loadingDialog: Dialog? = null
    var popupDialog: Dialog? = null

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.apply {
            resultEpisodes.layoutManager =
                LinearListLayout(resultEpisodes.context).apply {
                    setHorizontal()
                }

            resultSeasonSelection.setAdapter()
            resultRangeSelection.setAdapter()
            resultDubSelection.setAdapter()
            resultRecommendationsFilterSelection.setAdapter()

            resultCastItems.setOnFocusChangeListener { _, hasFocus ->
                // Always escape focus
                if (hasFocus) binding?.resultBookmarkButton?.requestFocus()
            }
            resultBack.setOnClickListener {
                activity?.popCurrentPage()
            }

            resultRecommendationsList.spanCount = 8
            resultRecommendationsList.adapter =
                SearchAdapter(
                    ArrayList(),
                    resultRecommendationsList,
                ) { callback ->
                    SearchHelper.handleSearchClickCallback(callback)
                }

            resultEpisodes.adapter =
                EpisodeAdapter(
                    false,
                    { episodeClick ->
                        viewModel.handleAction(activity, episodeClick)
                    },
                    { downloadClickEvent ->
                        DownloadButtonSetup.handleDownloadClick(downloadClickEvent)
                    }
                )
        }

        observe(viewModel.watchStatus) { watchType ->
            binding?.resultBookmarkButton?.apply {
                setText(watchType.stringRes)
                setOnClickListener { view ->
                    activity?.showBottomDialog(
                        WatchType.values().map { view.context.getString(it.stringRes) }.toList(),
                        watchType.ordinal,
                        view.context.getString(R.string.action_add_to_bookmarks),
                        showApply = false,
                        {}) {
                        viewModel.updateWatchStatus(WatchType.values()[it])
                    }
                }
            }
        }

        observeNullable(viewModel.movie) { data ->
            binding?.apply {
                resultPlayMovie.isVisible = data is Resource.Success
                (data as? Resource.Success)?.value?.let { (text, ep) ->
                    resultPlayMovie.setText(text)
                    resultPlayMovie.setOnClickListener {
                        viewModel.handleAction(
                            activity,
                            EpisodeClickEvent(ACTION_CLICK_DEFAULT, ep)
                        )
                    }
                    resultPlayMovie.setOnLongClickListener {
                        viewModel.handleAction(
                            activity,
                            EpisodeClickEvent(ACTION_SHOW_OPTIONS, ep)
                        )
                        return@setOnLongClickListener true
                    }
                    if (hasNoFocus()) {
                        resultPlayMovie.requestFocus()
                    }
                }
            }
        }

        observeNullable(viewModel.selectPopup) { popup ->
            if (popup == null) {
                popupDialog?.dismissSafe(activity)
                popupDialog = null
                return@observeNullable
            }

            popupDialog?.dismissSafe(activity)

            popupDialog = activity?.let { act ->
                val options = popup.getOptions(act)
                val title = popup.getTitle(act)

                act.showBottomDialogInstant(
                    options, title, {
                        popupDialog = null
                        popup.callback(null)
                    }, {
                        popupDialog = null
                        popup.callback(it)
                    }
                )
            }
        }

        observeNullable(viewModel.loadedLinks) { load ->
            if (load == null) {
                loadingDialog?.dismissSafe(activity)
                loadingDialog = null
                return@observeNullable
            }
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


        observeNullable(viewModel.episodesCountText) { count ->
            binding?.resultEpisodesText.setText(count)
        }

        observe(viewModel.selectedRangeIndex) { selected ->
            binding?.resultRangeSelection.select(selected)
        }
        observe(viewModel.selectedSeasonIndex) { selected ->
            binding?.resultSeasonSelection.select(selected)
        }
        observe(viewModel.selectedDubStatusIndex) { selected ->
            binding?.resultDubSelection.select(selected)
        }
        observe(viewModel.rangeSelections) {
            binding?.resultRangeSelection.update(it)
        }
        observe(viewModel.dubSubSelections) {
            binding?.resultDubSelection.update(it)
        }
        observe(viewModel.seasonSelections) {
            binding?.resultSeasonSelection.update(it)
        }
        observe(viewModel.recommendations) { recommendations ->
            setRecommendations(recommendations, null)
        }
        observe(viewModel.episodeSynopsis) { description ->
            view.context?.let { ctx ->
                val builder: AlertDialog.Builder =
                    AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                builder.setMessage(description.html())
                    .setTitle(R.string.synopsis)
                    .setOnDismissListener {
                        viewModel.releaseEpisodeSynopsis()
                    }
                    .show()
            }
        }
        observeNullable(viewModel.episodes) { episodes ->
            binding?.apply {
                resultEpisodes.isVisible = episodes is Resource.Success
                resultEpisodeLoading.isVisible = episodes is Resource.Loading
                if (episodes is Resource.Success) {
                    /*
                     * Okay so what is this fuckery?
                     * Basically Android TV will crash if you request a new focus while
                     * the adapter gets updated.
                     *
                     * This means that if you load thumbnails and request a next focus at the same time
                     * the app will crash without any way to catch it!
                     *
                     * How to bypass this?
                     * This code basically steals the focus for 500ms and puts it in an inescapable view
                     * then lets out the focus by requesting focus to result_episodes
                     */

                    val hasEpisodes =
                        !(resultEpisodes.adapter as? EpisodeAdapter?)?.cardList.isNullOrEmpty()

                    if (hasEpisodes) {
                        // Make it impossible to focus anywhere else!
                        temporaryNoFocus.isFocusable = true
                        temporaryNoFocus.requestFocus()
                    }

                    (resultEpisodes.adapter as? EpisodeAdapter)?.updateList(episodes.value)

                    if (hasEpisodes) main {
                        delay(500)
                        temporaryNoFocus.isFocusable = false
                        // This might make some people sad as it changes the focus when leaving an episode :(
                        temporaryNoFocus.requestFocus()
                    }

                    if (hasNoFocus())
                        binding?.resultEpisodes?.requestFocus()
                }
            }
        }

        observeNullable(viewModel.page) { data ->
            if (data == null) return@observeNullable
            binding?.apply {
                when (data) {
                    is Resource.Success -> {
                        val d = data.value
                        resultVpn.setText(d.vpnText)
                        resultInfo.setText(d.metaText)
                        resultNoEpisodes.setText(d.noEpisodesFoundText)
                        resultTitle.setText(d.titleText)
                        resultMetaSite.setText(d.apiName)
                        resultMetaType.setText(d.typeText)
                        resultMetaYear.setText(d.yearText)
                        resultMetaDuration.setText(d.durationText)
                        resultMetaRating.setText(d.ratingText)
                        resultCastText.setText(d.actorsText)
                        resultNextAiring.setText(d.nextAiringEpisode)
                        resultNextAiringTime.setText(d.nextAiringDate)
                        resultPoster.setImage(d.posterImage)
                        resultDescription.setTextHtml(d.plotText)

                        resultComingSoon.isVisible = d.comingSoon
                        resultDataHolder.isGone = d.comingSoon
                        UIHelper.populateChips(resultTag, d.tags)
                        resultCastItems.isGone = d.actors.isNullOrEmpty()
                        (resultCastItems.adapter as? ActorAdaptor)?.updateList(
                            d.actors ?: emptyList()
                        )
                    }

                    is Resource.Loading -> {

                    }

                    is Resource.Failure -> {
                        resultErrorText.text =
                            (this@ResultFragmentTv.context?.let { getStoredData(it) }?.url?.plus("\n")
                                ?: "") + data.errorString
                    }
                }

                resultFinishLoading.isVisible = data is Resource.Success

                resultLoading.isVisible = data is Resource.Loading

                resultLoadingError.isVisible = data is Resource.Failure
                resultReloadConnectionOpenInBrowser.isVisible = data is Resource.Failure
            }
        }
    }
}