package com.lagradost.cloudstream3.ui.result

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.FragmentResultTvBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.player.ExtractorLinkGenerator
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.result.ResultFragment.getStoredData
import com.lagradost.cloudstream3.ui.result.ResultFragment.updateUIEvent
import com.lagradost.cloudstream3.ui.revamp.compose.screens.CloneflixMovieDetailsComposeScreen
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.utils.AppContextUtils.loadCache
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialogInstant
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.utils.UIHelper.setNavigationBarColorCompat
import com.lagradost.cloudstream3.utils.txt

class ResultFragmentTv : BaseFragment<FragmentResultTvBinding>(
    BindingCreator.Inflate(FragmentResultTvBinding::inflate)
) {

    private lateinit var viewModel: ResultViewModel2

    private var composePageState by mutableStateOf<Resource<ResultData>?>(null)
    private var composeEpisodesState by mutableStateOf<List<ResultEpisode>>(emptyList())
    private var composeRecommendationsState by mutableStateOf<List<SearchResponse>>(emptyList())
    private var composeActorsState by mutableStateOf<List<ActorData>>(emptyList())
    private var composeWatchStatusState by mutableStateOf(WatchType.NONE)
    private var composeSeasonsState by mutableStateOf<List<String>>(emptyList())
    private var composeSelectedSeasonIndexState by mutableStateOf(0)
    private var composeHasTrailersState by mutableStateOf(false)
    private var composeResumeWatchingState by mutableStateOf<ResumeWatchingStatus?>(null)

    override fun onDestroyView() {
        updateUIEvent -= ::updateUI
        activity?.detachBackPressedCallback(this@ResultFragmentTv.toString())
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel =
            ViewModelProvider(this)[ResultViewModel2::class.java]
        viewModel.EPISODE_RANGE_SIZE = 50
        updateUIEvent += ::updateUI

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    private fun updateUI(id: Int?) {
        viewModel.reloadEpisodes()
    }

    private var loadingDialog: Dialog? = null
    private var popupDialog: Dialog? = null

    private fun reloadViewModel(forceReload: Boolean) {
        if (!viewModel.hasLoaded() || forceReload) {
            val storedData = getStoredData() ?: return
            viewModel.load(
                activity,
                storedData.url,
                storedData.apiName,
                storedData.showFillers,
                storedData.dubStatus,
                storedData.start
            )
        }
    }

    override fun onResume() {
        activity?.setNavigationBarColorCompat(R.attr.primaryBlackBackground)
        afterPluginsLoadedEvent += ::reloadViewModel
        super.onResume()
    }

    override fun onStop() {
        afterPluginsLoadedEvent -= ::reloadViewModel
        super.onStop()
    }

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(view, padTop = false)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindingCreated(binding: FragmentResultTvBinding) {
        // ===== setup =====
        val storedData = getStoredData() ?: return
        activity?.window?.decorView?.clearFocus()
        activity?.loadCache()
        hideKeyboard()
        if (storedData.restart || !viewModel.hasLoaded())
            viewModel.load(
                activity,
                storedData.url,
                storedData.apiName,
                storedData.showFillers,
                storedData.dubStatus,
                storedData.start
            )
        // ===== ===== =====
        var comingSoon = false

        binding.resultComposeView.setContent {
            CloneflixTheme {
                val page = composePageState
                if (page is Resource.Success) {
                    val d = page.value
                    val title = d.titleText.asStringNull(context) ?: d.title
                    val plot = d.plotText.asStringNull(context) ?: ""
                    val year = d.yearText?.asStringNull(context)
                    val duration = d.durationText?.asStringNull(context)
                    val rating = d.ratingText?.asStringNull(context)
                    val contentRating = d.contentRatingText?.asStringNull(context)
                    val genres = d.tags
                    val actors = composeActorsState.ifEmpty { d.actors ?: emptyList() }

                    CloneflixMovieDetailsComposeScreen(
                        title = title,
                        backdropUrl = d.posterBackgroundImage ?: d.posterImage,
                        posterUrl = d.posterImage,
                        logoUrl = d.logoUrl,
                        matchScore = rating,
                        releaseYear = year,
                        seasonsCount = duration,
                        maturityRating = contentRating,
                        synopsis = plot,
                        genres = genres,
                        dynamicActors = actors,
                        dynamicEpisodes = composeEpisodesState,
                        dynamicRecommendations = composeRecommendationsState,
                        dynamicSeasons = composeSeasonsState,
                        selectedSeasonIndex = composeSelectedSeasonIndexState,
                        isInWatchList = composeWatchStatusState != WatchType.NONE,
                        hasTrailers = composeHasTrailersState,
                        onPlayClick = {
                            val resume = composeResumeWatchingState
                            if (resume != null) {
                                viewModel.handleAction(
                                    EpisodeClickEvent(
                                        storedData.playerAction,
                                        resume.result
                                    )
                                )
                            } else {
                                val ep = composeEpisodesState.firstOrNull()
                                if (ep != null) {
                                    viewModel.handleAction(
                                        EpisodeClickEvent(
                                            storedData.playerAction,
                                            ep
                                        )
                                    )
                                } else {
                                    (viewModel.movie.value as? Resource.Success)?.value?.let { (_, movieEp) ->
                                        viewModel.handleAction(
                                            EpisodeClickEvent(ACTION_CLICK_DEFAULT, movieEp)
                                        )
                                    }
                                }
                            }
                        },
                        onEpisodeClick = { ep ->
                            viewModel.handleAction(
                                EpisodeClickEvent(
                                    storedData.playerAction,
                                    ep
                                )
                            )
                        },
                        onEpisodeDownloadClick = { ep ->
                            viewModel.handleAction(
                                EpisodeClickEvent(
                                    ACTION_DOWNLOAD_EPISODE,
                                    ep
                                )
                            )
                        },
                        onSeasonSelect = { idx ->
                            viewModel.changeSeason(idx)
                        },
                        onAddToListClick = {
                            val curStatus = composeWatchStatusState
                            activity?.showBottomDialog(
                                WatchType.entries.map { getString(it.stringRes) }.toList(),
                                curStatus.ordinal,
                                getString(R.string.action_add_to_bookmarks),
                                showApply = false,
                                {}
                            ) {
                                viewModel.updateWatchStatus(WatchType.entries[it], context)
                            }
                        },
                        onLikeClick = {
                            viewModel.toggleFavoriteStatus(context) { newStatus: Boolean? ->
                                if (newStatus == null) return@toggleFavoriteStatus
                                val message = if (newStatus) R.string.favorite_added else R.string.favorite_removed
                                val name = (viewModel.page.value as? Resource.Success)?.value?.title
                                    ?: txt(R.string.no_data).asStringNull(context) ?: ""
                                CommonActivity.showToast(txt(message, name), Toast.LENGTH_SHORT)
                            }
                        },
                        onTrailerClick = {
                            val trailersLinks = viewModel.trailers.value ?: emptyList()
                            val extractedTrailerLinks = trailersLinks.flatMap { it.mirros }
                                .map { (extractedTrailerLink, _) -> extractedTrailerLink }
                            if (extractedTrailerLinks.isNotEmpty()) {
                                activity.navigate(
                                    R.id.global_to_navigation_player,
                                    GeneratorPlayer.newInstance(
                                        ExtractorLinkGenerator(
                                            extractedTrailerLinks,
                                            emptyList()
                                        ),
                                        0
                                    )
                                )
                            }
                        },
                        onActorClick = { actorName ->
                            QuickSearchFragment.pushSearch(activity, actorName)
                        },
                        onRecommendationClick = { rec ->
                            SearchHelper.handleSearchClickCallback(
                                SearchClickCallback(
                                    SEARCH_ACTION_LOAD,
                                    binding.root,
                                    0,
                                    rec
                                )
                            )
                        },
                        onCloseClick = {
                            activity?.popCurrentPage()
                        }
                    )
                }
            }
        }

        observeNullable(viewModel.resumeWatching) { resume ->
            composeResumeWatchingState = resume
        }

        observe(viewModel.trailers) { trailersLinks ->
            val extractedTrailerLinks = trailersLinks.flatMap { it.mirros }
                .map { (extractedTrailerLink, _) -> extractedTrailerLink }
            composeHasTrailersState = extractedTrailerLinks.isNotEmpty()
        }

        observe(viewModel.watchStatus) { watchType ->
            composeWatchStatusState = watchType
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
                builder.setCanceledOnTouchOutside(true)
                builder.show()
                builder
            }
            loadingDialog?.findViewById<MaterialButton>(R.id.overlay_loading_skip_button)?.apply {
                if (load.linksLoaded <= 0) {
                    isInvisible = true
                } else {
                    setOnClickListener {
                        viewModel.skipLoading()
                    }
                    isVisible = true
                    text = "${context.getString(R.string.skip_loading)} (${load.linksLoaded})"
                }
            }
        }

        observe(viewModel.selectedSeasonIndex) { selected ->
            composeSelectedSeasonIndexState = selected
        }

        observe(viewModel.seasonSelections) {
            composeSeasonsState = it.map { s -> s.first?.asStringNull(context) ?: "" }
        }

        observe(viewModel.recommendations) { recommendations ->
            composeRecommendationsState = recommendations
        }

        observeNullable(viewModel.episodes) { episodes ->
            if (episodes == null) return@observeNullable
            if (episodes is Resource.Success) {
                composeEpisodesState = episodes.value
            }
        }

        observeNullable(viewModel.page) { data ->
            if (data == null) return@observeNullable
            composePageState = data
            binding.apply {
                when (data) {
                    is Resource.Success -> {
                        val d = data.value
                        composeActorsState = d.actors ?: emptyList()
                        resultComposeView.isVisible = true
                        resultLoading.isGone = true
                        resultLoadingError.isGone = true
                    }

                    is Resource.Loading -> {
                        resultComposeView.isGone = true
                        resultLoading.isVisible = true
                        resultLoadingError.isGone = true
                    }

                    is Resource.Failure -> {
                        resultComposeView.isGone = true
                        resultLoading.isGone = true
                        resultLoadingError.isVisible = true
                        resultErrorText.text = storedData.url.plus("\n") + data.errorString
                    }
                }
            }
        }
    }
}
