package com.lagradost.cloudstream3.ui.result

import android.animation.Animator
import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Visibility
import com.discord.panels.OverlappingPanelsLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.FragmentResultTvBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.services.SubscriptionWorkManager
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup
import com.lagradost.cloudstream3.ui.player.ExtractorLinkGenerator
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.player.NEXT_WATCH_EPISODE_PERCENTAGE
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.result.ResultFragment.bindLogo
import com.lagradost.cloudstream3.ui.result.ResultFragment.getStoredData
import com.lagradost.cloudstream3.ui.result.ResultFragment.updateUIEvent
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_FOCUSED
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.getNameFull
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.AppContextUtils.isRtl
import com.lagradost.cloudstream3.utils.AppContextUtils.loadCache
import com.lagradost.cloudstream3.utils.AppContextUtils.updateHasTrailers
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialogInstant
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.populateChips
import com.lagradost.cloudstream3.utils.UIHelper.setListViewHeightBasedOnItems
import com.lagradost.cloudstream3.utils.UIHelper.setNavigationBarColorCompat
import com.lagradost.cloudstream3.utils.getImageFromDrawable
import com.lagradost.cloudstream3.utils.setText
import com.lagradost.cloudstream3.utils.setTextHtml
import com.lagradost.cloudstream3.utils.txt
import kotlin.math.roundToInt

class ResultFragmentTv : BaseFragment<FragmentResultTvBinding>(
    BindingCreator.Inflate(FragmentResultTvBinding::inflate)
) {

    private lateinit var viewModel: ResultViewModel2
    private lateinit var syncModel: SyncViewModel

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

        syncModel = ViewModelProvider(this)[SyncViewModel::class.java]

        updateUIEvent += ::updateUI

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    private fun updateUI(id: Int?) {
        viewModel.reloadEpisodes()
        syncModel.updateUserData()
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
        (this?.adapter as? SelectAdaptor?)?.submitList(data)
        this?.isVisible = data.size > 1
    }

    private fun RecyclerView?.setAdapter() {
        this?.adapter = SelectAdaptor { data ->
            handleSelection(data)
        }
    }

//    private fun hasNoFocus(): Boolean {
//        val focus = activity?.currentFocus
//        if (focus == null || !focus.isVisible) return true
//        return focus == binding?.resultRoot
//    }

    /**
     * Force focus any play button.
     * Note that this will steal any focus if the episode loading is too slow (unlikely).
     */
    private fun focusPlayButton() {
        binding?.resultPlayMovieButton?.requestFocus()
        binding?.resultPlaySeriesButton?.requestFocus()
        binding?.resultResumeSeriesButton?.requestFocus()
    }

    private fun setRecommendations(rec: List<SearchResponse>?, validApiName: String?) {
        currentRecommendations = rec ?: emptyList()
        val isInvalid = rec.isNullOrEmpty()
        binding?.apply {
            resultRecommendationsList.isGone = isInvalid
            resultRecommendationsHolder.isGone = isInvalid
            val matchAgainst = validApiName ?: rec?.firstOrNull()?.apiName
            (resultRecommendationsList.adapter as? SearchAdapter)?.submitList(rec?.filter { it.apiName == matchAgainst }
                ?: emptyList())

            rec?.map { it.apiName }?.distinct()?.let { apiNames ->
                // very dirty selection
                resultRecommendationsFilterSelection.isVisible = apiNames.size > 1
                resultRecommendationsFilterSelection.update(apiNames.map {
                    txt(
                        it
                    ) to it
                })
                resultRecommendationsFilterSelection.select(apiNames.indexOf(matchAgainst))
            } ?: run {
                resultRecommendationsFilterSelection.isVisible = false
            }
        }
    }

    var loadingDialog: Dialog? = null
    var popupDialog: Dialog? = null

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

    private fun View.fade(turnVisible: Boolean) {
        if (turnVisible) {
            isVisible = true
        }

        this.animate().alpha(if (turnVisible) 0.97f else 0.0f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            setListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                }

                override fun onAnimationEnd(animation: Animator) {
                    this@fade.isVisible = turnVisible
                }

                override fun onAnimationCancel(animation: Animator) {
                }

                override fun onAnimationRepeat(animation: Animator) {
                }
            })
        }
        this.animate().translationX(if (turnVisible) 0f else if (isRtl()) -100.0f else 100f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
        }
    }

    private fun toggleEpisodes(show: Boolean) {
        binding?.apply {
            if (show) {
                activity?.attachBackPressedCallback(this@ResultFragmentTv.toString()) {
                    toggleEpisodes(false)
                }
            } else {
                activity?.detachBackPressedCallback(this@ResultFragmentTv.toString())
            }
            episodesShadow.fade(show)
            episodeHolderTv.fade(show)
            if (episodesShadow.isRtl()) {
                episodesShadowBackground.scaleX = -1f
            } else {
                episodesShadowBackground.scaleX = 1f
            }
        }
    }

    private fun toggleSync(show: Boolean) {
        binding?.apply {
            if (show) {
                activity?.attachBackPressedCallback(this@ResultFragmentTv.toString()) {
                    toggleSync(false)
                }
            } else {
                activity?.detachBackPressedCallback(this@ResultFragmentTv.toString())
            }
            syncShadow.fade(show)
            resultSyncUi.root.fade(show)
            if (syncShadow.isRtl()) {
                syncShadowBackground.scaleX = -1f
            } else {
                syncShadowBackground.scaleX = 1f
            }
        }
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

        //setUrl(storedData.url)
        syncModel.addFromUrl(storedData.url)

        // ===== ===== =====
        var comingSoon = false

        binding.apply {
            //episodesShadow.rotationX = 180.0f//if(episodesShadow.isRtl()) 180.0f else 0.0f

            // parallax on background
            resultFinishLoading.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { view, _, scrollY, _, oldScrollY ->
                backgroundPosterHolder.translationY = -scrollY.toFloat() * 0.8f
            })

            redirectToPlay.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) return@setOnFocusChangeListener
                toggleEpisodes(false)

                binding.apply {
                    val views = listOf(
                        resultPlayMovieButton,
                        resultPlaySeriesButton,
                        resultResumeSeriesButton,
                        resultPlayTrailerButton,
                        resultBookmarkButton,
                        resultFavoriteButton,
                        resultSubscribeButton,
                        resultSearchButton,
                        resultSyncButton
                    )
                    for (requestView in views) {
                        if (!requestView.isVisible) continue
                        if (requestView.requestFocus()) break
                    }
                }
            }

            redirectToEpisodes.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) return@setOnFocusChangeListener
                toggleEpisodes(true)
                binding.apply {
                    val views = listOf(
                        resultDubSelection,
                        resultSeasonSelection,
                        resultRangeSelection,
                        resultEpisodes,
                        resultPlayTrailerButton,
                    )
                    for (requestView in views) {
                        if (!requestView.isShown) continue
                        if (requestView.requestFocus()) break // View.FOCUS_RIGHT
                    }
                }
            }

            // Please add those buttons to [MainActivity.exceptionButtons] list to stop centering the screen of focus them
            mapOf(
                resultPlayMovieButton to resultPlayMovieText,
                resultPlaySeriesButton to resultPlaySeriesText,
                resultResumeSeriesButton to resultResumeSeriesText,
                resultPlayTrailerButton to resultPlayTrailerText,
                resultBookmarkButton to resultBookmarkText,
                resultFavoriteButton to resultFavoriteText,
                resultSubscribeButton to resultSubscribeText,
                resultSearchButton to resultSearchText,
                resultEpisodesShowButton to resultEpisodesShowText,
                resultSyncButton to resultSyncText
            ).forEach { (button, text) ->

                button.setOnFocusChangeListener { view, hasFocus ->
                    if (!hasFocus) {
                        text.isSelected = false
                        if (view.id == R.id.result_episodes_show_button) toggleEpisodes(false)
                        //if (view.id == R.id.result_sync_Button) toggleSync(false)
                        return@setOnFocusChangeListener
                    }

                    text.isSelected = true
                    if (button.tag == context?.getString(R.string.tv_no_focus_tag)) {
                        resultFinishLoading.scrollTo(0, 0)
                    }

                    when (button.id) {
                        R.id.result_episodes_show_button -> {
                            toggleEpisodes(true)
                        }

                        R.id.result_sync_Button -> {

                            toggleSync(false)
                            toggleEpisodes(false)

                            resultSyncButton.nextFocusRightId =
                                if (resultEpisodesShow.isVisible) R.id.result_episodes_show_button else R.id.result_sync_episodes_slider
                        }

                        else -> {
                            toggleEpisodes(false)
                        }
                    }
                }
            }

            resultEpisodesShowButton.setOnClickListener {
                // toggle, to make it more touch accessible just in case someone thinks that a
                // tv layout is better but is using a touch device
                toggleEpisodes(!episodeHolderTv.isVisible)
            }

            resultSyncButton.setOnClickListener {
                // toggle, to make it more touch accessible just in case someone thinks that a
                // tv layout is better but is using a touch device
                toggleSync(!resultSyncUi.root.isVisible)
                resultSyncButton.nextFocusRightId = R.id.result_sync_episodes_slider
            }

            resultEpisodes.setLinearListLayout(
                isHorizontal = false,
                nextUp = FOCUS_SELF,
                nextDown = FOCUS_SELF,
                nextRight = FOCUS_SELF,
            )
            resultDubSelection.setLinearListLayout(
                isHorizontal = false,
                nextUp = FOCUS_SELF,
                nextDown = FOCUS_SELF,
            )
            resultRangeSelection.setLinearListLayout(
                isHorizontal = false,
                nextUp = FOCUS_SELF,
                nextDown = FOCUS_SELF,
            )
            resultSeasonSelection.setLinearListLayout(
                isHorizontal = false,
                nextUp = FOCUS_SELF,
                nextDown = FOCUS_SELF,
            )

            /*.layoutManager =
                LinearListLayout(resultEpisodes.context, resultEpisodes.isRtl()).apply {
                    setVertical()
                }*/

            resultReloadConnectionerror.setOnClickListener {
                viewModel.load(
                    activity,
                    storedData.url,
                    storedData.apiName,
                    storedData.showFillers,
                    storedData.dubStatus,
                    storedData.start
                )

            }

            resultMetaSite.isFocusable = false

            resultSeasonSelection.setAdapter()
            resultRangeSelection.setAdapter()
            resultDubSelection.setAdapter()
            resultRecommendationsFilterSelection.setAdapter()

            resultCastItems.setOnFocusChangeListener { _, hasFocus ->
                // Always escape focus
                if (hasFocus) binding.resultBookmarkButton.requestFocus()
            }
            //resultBack.setOnClickListener {
            //    activity?.popCurrentPage()
            //}

            resultRecommendationsList.spanCount = 8
            resultRecommendationsList.setRecycledViewPool(SearchAdapter.sharedPool)
            resultRecommendationsList.adapter =
                SearchAdapter(
                    resultRecommendationsList,
                ) { callback ->
                    if (callback.action == SEARCH_ACTION_FOCUSED) {
                        toggleEpisodes(false)
                    } else SearchHelper.handleSearchClickCallback(callback)
                }

            resultEpisodes.setRecycledViewPool(EpisodeAdapter.sharedPool)
            resultEpisodes.adapter =
                EpisodeAdapter(
                    false,
                    { episodeClick ->
                        viewModel.handleAction(episodeClick)
                    },
                    { downloadClickEvent ->
                        DownloadButtonSetup.handleDownloadClick(downloadClickEvent)
                    }
                )

            resultCastItems.layoutManager = object : LinearListLayout(root.context) {
                override fun onRequestChildFocus(
                    parent: RecyclerView,
                    state: RecyclerView.State,
                    child: View,
                    focused: View?
                ): Boolean {
                    // Make the cast always focus the first visible item when focused
                    // from somewhere else. Otherwise it jumps to the last item.
                    return if (parent.focusedChild == null) {
                        scrollToPosition(this.findFirstCompletelyVisibleItemPosition())
                        true
                    } else {
                        super.onRequestChildFocus(parent, state, child, focused)
                    }
                }
            }.apply { setHorizontal() }

            val aboveCast = listOf(
                binding.resultEpisodesShow,
                binding.resultBookmark,
                binding.resultFavorite,
                binding.resultSubscribe,
            ).firstOrNull { it.isVisible }

            resultCastItems.setRecycledViewPool(ActorAdaptor.sharedPool)
            resultCastItems.adapter = ActorAdaptor(aboveCast?.id) {
                toggleEpisodes(false)
            }

            if (isLayout(EMULATOR)) {
                episodesShadow.setOnClickListener {
                    toggleEpisodes(false)
                }
            }
        }

        observeNullable(viewModel.resumeWatching) { resume ->
            binding.apply {
                if (resume == null) {
                    return@observeNullable
                }

                resultResumeSeries.isVisible = true
                resultPlayMovie.isVisible = false
                resultPlaySeries.isVisible = false

                // show progress no matter if series or movie
                resume.progress?.let { progress ->
                    resultResumeSeriesTitle.apply {
                        isVisible = !resume.isMovie
                        text =
                            if (resume.isMovie) null else context?.getNameFull(
                                resume.result.name,
                                resume.result.episode,
                                resume.result.season
                            )
                    }
                    resultResumeSeriesProgressText.setText(progress.progressLeft)
                    resultResumeSeriesProgress.apply {
                        isVisible = true
                        this.max = progress.maxProgress
                        this.progress = progress.progress
                    }
                    resultResumeProgressHolder.isVisible = true
                } ?: run {
                    resultResumeProgressHolder.isVisible = false
                }

                focusPlayButton()

                resultResumeSeriesText.text =
                    when {
                        resume.isMovie -> context?.getString(R.string.resume)
                        resume.result.season != null ->
                            "${getString(R.string.season_short)}${resume.result.season}:${
                                getString(
                                    R.string.episode_short
                                )
                            }${resume.result.episode}"

                        else -> "${getString(R.string.episode)} ${resume.result.episode}"
                    }

                resultResumeSeriesButton.setOnClickListener {
                    viewModel.handleAction(
                        EpisodeClickEvent(
                            storedData.playerAction, //?: ACTION_PLAY_EPISODE_IN_PLAYER,
                            resume.result
                        )
                    )
                }

                resultResumeSeriesButton.setOnLongClickListener {
                    viewModel.handleAction(
                        EpisodeClickEvent(ACTION_SHOW_OPTIONS, resume.result)
                    )
                    return@setOnLongClickListener true
                }

            }
        }

        observe(viewModel.trailers) { trailersLinks ->
            context?.updateHasTrailers()
            if (!LoadResponse.isTrailersEnabled) return@observe
            val extractedTrailerLinks = trailersLinks.flatMap { it.mirros }
                .map { (extractedTrailerLink, _) -> extractedTrailerLink }
            binding.apply {
                resultPlayTrailer.isGone = extractedTrailerLinks.isEmpty()
                resultPlayTrailerButton.setOnClickListener {
                    if (extractedTrailerLinks.isEmpty()) return@setOnClickListener
                    activity.navigate(
                        R.id.global_to_navigation_player, GeneratorPlayer.newInstance(
                            ExtractorLinkGenerator(
                                extractedTrailerLinks,
                                emptyList()
                            ), 0
                        )
                    )
                }
            }
        }

        observe(syncModel.synced) { list ->
            binding.resultSyncUi.resultSyncNames.text =
                list.filter { it.isSynced && it.hasAccount }.joinToString { it.name }

            val newList = list.filter { it.isSynced && it.hasAccount }

            binding.resultSync.isVisible = newList.isNotEmpty()
        }

        var currentSyncProgress = 0

        fun setSyncMaxEpisodes(totalEpisodes: Int?) {
            binding.resultSyncUi.resultSyncEpisodes.max = (totalEpisodes ?: 0) * 1000

            safe {
                val ctx = binding.resultSyncUi.resultSyncEpisodes.context
                binding.resultSyncUi.resultSyncMaxEpisodes.text =
                    totalEpisodes?.let { episodes ->
                        ctx?.getString(R.string.sync_total_episodes_some)?.format(episodes)
                    } ?: run {
                        ctx?.getString(R.string.sync_total_episodes_none)
                    }
            }
        }

        fun setSyncEpisodes(currentEpisodes: Int?, totalEpisodes: Int?) {

            Log.d("King", "current: $currentEpisodes, total: $totalEpisodes")

            safe {
                val ctx = binding.resultSyncUi.resultSyncEpisodes.context

                binding.resultSyncUi.resultSyncEpisodesText.text = totalEpisodes?.let { episodes ->

                    if (episodes != 0) {
                        binding.resultSyncUi.resultSyncEpisodesSlider.value = (currentEpisodes ?: 0).toFloat()
                        binding.resultSyncUi.resultSyncEpisodesSlider.valueTo = episodes.toFloat()
                    } else {
                        binding.resultSyncUi.resultSyncEpisodesHolder.isVisible = false
                    }

                    ctx?.getString(R.string.sync_total_episodes_format)?.format(currentEpisodes, episodes)

                } ?: run {
                    binding.resultSyncUi.resultSyncEpisodesHolder.isVisible = false
                    binding.resultSyncUi.resultSyncSetScore.isClickable = false

                    ctx?.getString(R.string.sync_total_episodes_format_none)
                }
            }
        }

        observe(syncModel.metadata) { meta ->
            when (meta) {
                is Resource.Success -> {
                    val d = meta.value
                    binding.resultSyncUi.resultSyncEpisodes.progress = currentSyncProgress * 1000
                    setSyncMaxEpisodes(d.totalEpisodes)

                    viewModel.setMeta(d, syncModel.getSyncs())
                }

                is Resource.Loading -> {
                    binding.resultSyncUi.resultSyncMaxEpisodes.text =
                        binding.resultSyncUi.resultSyncMaxEpisodes.context?.getString(R.string.sync_total_episodes_none)
                }

                else -> {}
            }
        }

        observe(syncModel.userData) { status ->
            var closed = false
            binding.resultSyncUi.apply {
                when (status) {
                    is Resource.Failure -> {
                        resultSyncLoadingShimmer.stopShimmer()
                        resultSyncLoadingShimmer.isVisible = false
                        resultSyncHolder.isVisible = false
                        closed = true
                    }

                    is Resource.Loading -> {
                        resultSyncLoadingShimmer.startShimmer()
                        resultSyncLoadingShimmer.isVisible = true
                        resultSyncHolder.isVisible = false
                    }

                    is Resource.Success -> {
                        resultSyncLoadingShimmer.stopShimmer()
                        resultSyncLoadingShimmer.isVisible = false
                        resultSyncHolder.isVisible = true

                        val d = status.value
                        val desiredScore = d.score?.toFloat(1) ?: 0.0f
                        val totalSteps = resultSyncRating.valueTo / resultSyncRating.stepSize
                        val desiredStep = (totalSteps * desiredScore).roundToInt()
                        resultSyncRating.value = desiredStep * resultSyncRating.stepSize

                        resultSyncCheck.setItemChecked(d.status.internalId + 1, true)

                        safe { // format might fail
                            val text = d.score?.toFloat(10)?.roundToInt()?.let {
                                context?.getString(R.string.sync_score_format)?.format(it)
                            } ?: "?"
                            resultSyncScoreText.text = text
                        }

                        val watchedEpisodes = d.watchedEpisodes ?: 0
                        currentSyncProgress = watchedEpisodes

                        Log.d("King", "Watched: $watchedEpisodes, Max: ${d.maxEpisodes}")

                        d.maxEpisodes?.let {
                            // don't directly call it because we don't want to override metadata observe
                            setSyncMaxEpisodes(it)
                            setSyncEpisodes(watchedEpisodes,it)
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            resultSyncEpisodes.setProgress(watchedEpisodes * 1000, true)
                        } else {
                            resultSyncEpisodes.progress = watchedEpisodes * 1000
                        }
                        resultSyncCurrentEpisodes.text =
                            Editable.Factory.getInstance()?.newEditable(watchedEpisodes.toString())
                    }

                    null -> {
                        closed = false
                    }
                }
            }
        }

        context?.let { ctx ->
            val arrayAdapter = ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
            /*
            -1 -> None
            0 -> Watching
            1 -> Completed
            2 -> OnHold
            3 -> Dropped
            4 -> PlanToWatch
            5 -> ReWatching
            */
            val items = listOf(
                R.string.none,
                R.string.type_watching,
                R.string.type_completed,
                R.string.type_on_hold,
                R.string.type_dropped,
                R.string.type_plan_to_watch,
                R.string.type_re_watching
            ).map { ctx.getString(it) }
            arrayAdapter.addAll(items)

            binding.resultSyncUi.apply {
                resultSyncCheck.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                resultSyncCheck.adapter = arrayAdapter
                setListViewHeightBasedOnItems(resultSyncCheck)

                resultSyncCheck.setOnItemClickListener { _, _, which, _ ->
                    syncModel.setStatus(which - 1)
                }

                resultSyncRating.addOnChangeListener { it, value, fromUser ->
                    if (fromUser) syncModel.setScore(Score.from(value, it.valueTo.roundToInt()))
                }

                resultSyncEpisodesSlider.addOnChangeListener { it, value, fromUser ->
                    if (fromUser) syncModel.setEpisodes(value.roundToInt())
                }

                resultSyncAddEpisode.setOnClickListener {
                    syncModel.setEpisodesDelta(1)
                }

                resultSyncSubEpisode.setOnClickListener {
                    syncModel.setEpisodesDelta(-1)
                }

                resultSyncCurrentEpisodes.doOnTextChanged { text, _, before, count ->
                    if (count == before) return@doOnTextChanged
                    text?.toString()?.toIntOrNull()?.let { ep ->
                        syncModel.setEpisodes(ep)
                    }
                }
            }
        }

        binding.resultSyncUi.resultSyncSetScore.setOnClickListener {
            syncModel.publishUserData()
        }

        observe(viewModel.watchStatus) { watchType ->
            binding.apply {
                resultBookmarkText.setText(watchType.stringRes)

                resultBookmarkButton.apply {
                    val drawable = if (watchType.stringRes == R.string.type_none) {
                        R.drawable.outline_bookmark_add_24
                    } else R.drawable.ic_baseline_bookmark_24
                    setIconResource(drawable)

                    setOnClickListener { view ->
                        activity?.showBottomDialog(
                            WatchType.entries.map { view.context.getString(it.stringRes) }.toList(),
                            watchType.ordinal,
                            view.context.getString(R.string.action_add_to_bookmarks),
                            showApply = false,
                            {}) {
                            viewModel.updateWatchStatus(WatchType.entries[it], context)
                        }
                    }
                }
            }
        }

        observeNullable(viewModel.favoriteStatus) { isFavorite ->
            binding.resultFavorite.isVisible = isFavorite != null
            binding.resultFavoriteButton.apply {
                if (isFavorite == null) return@observeNullable

                val drawable = if (isFavorite) {
                    R.drawable.ic_baseline_favorite_24
                } else R.drawable.ic_baseline_favorite_border_24
                setIconResource(drawable)

                setOnClickListener {
                    viewModel.toggleFavoriteStatus(context) { newStatus: Boolean? ->
                        if (newStatus == null) return@toggleFavoriteStatus

                        val message = if (newStatus) {
                            R.string.favorite_added
                        } else R.string.favorite_removed

                        val name = (viewModel.page.value as? Resource.Success)?.value?.title
                            ?: txt(R.string.no_data)
                                .asStringNull(context) ?: ""
                        CommonActivity.showToast(
                            txt(
                                message,
                                name
                            ), Toast.LENGTH_SHORT
                        )
                    }
                }
            }

            binding.resultFavoriteText.apply {
                val text = if (isFavorite == true) {
                    R.string.unfavorite
                } else R.string.favorite
                setText(text)
            }
        }

        observeNullable(viewModel.subscribeStatus) { isSubscribed ->
            binding.resultSubscribe.isVisible = isSubscribed != null && isLayout(EMULATOR)
            binding.resultSubscribeButton.apply {
                if (isSubscribed == null) return@observeNullable

                val drawable = if (isSubscribed) {
                    R.drawable.ic_baseline_notifications_active_24
                } else R.drawable.baseline_notifications_none_24
                setIconResource(drawable)

                setOnClickListener {
                    viewModel.toggleSubscriptionStatus(context) { newStatus: Boolean? ->
                        if (newStatus == null) return@toggleSubscriptionStatus

                        val message = if (newStatus) {
                            // Kinda icky to have this here, but it works.
                            SubscriptionWorkManager.enqueuePeriodicWork(context)
                            R.string.subscription_new
                        } else R.string.subscription_deleted

                        val name = (viewModel.page.value as? Resource.Success)?.value?.title
                            ?: txt(R.string.no_data)
                                .asStringNull(context) ?: ""
                        CommonActivity.showToast(
                            txt(
                                message,
                                name
                            ), Toast.LENGTH_SHORT
                        )
                    }
                }

                binding.resultSubscribeText.apply {
                    val text = if (isSubscribed) {
                        R.string.action_unsubscribe
                    } else R.string.action_subscribe
                    setText(text)
                }
            }
        }

        observeNullable(viewModel.movie) { data ->
            if (data == null) {
                return@observeNullable
            }

            binding.apply {
                (data as? Resource.Success)?.value?.let { (_, ep) ->
                    resultPlayMovieButton.setOnClickListener {
                        viewModel.handleAction(
                            EpisodeClickEvent(ACTION_CLICK_DEFAULT, ep)
                        )
                    }
                    resultPlayMovieButton.setOnLongClickListener {
                        viewModel.handleAction(
                            EpisodeClickEvent(ACTION_SHOW_OPTIONS, ep)
                        )
                        return@setOnLongClickListener true
                    }

                    resultPlayMovie.isVisible = !comingSoon && resultResumeSeries.isGone
                    if (comingSoon) {
                        resultBookmarkButton.requestFocus()
                    } else resultPlayMovieButton.requestFocus()
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


        observeNullable(viewModel.episodesCountText) { count ->
            binding.resultEpisodesText.setText(count)
        }

        observe(viewModel.selectedRangeIndex) { selected ->
            binding.resultRangeSelection.select(selected)
        }
        observe(viewModel.selectedSeasonIndex) { selected ->
            binding.resultSeasonSelection.select(selected)
        }
        observe(viewModel.selectedDubStatusIndex) { selected ->
            binding.resultDubSelection.select(selected)
        }
        observe(viewModel.rangeSelections) {
            binding.resultRangeSelection.update(it)
        }
        observe(viewModel.dubSubSelections) {
            binding.resultDubSelection.update(it)
        }
        observe(viewModel.seasonSelections) {
            binding.resultSeasonSelection.update(it)
        }
        observe(viewModel.recommendations) { recommendations ->
            setRecommendations(recommendations, null)
        }

        if (isLayout(TV)) {
            observe(viewModel.episodeSynopsis) { description ->
                context?.let { ctx ->
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
        }

        // Used to request focus the first time the episodes are loaded.
        var hasLoadedEpisodesOnce = false
        observeNullable(viewModel.episodes) { episodes ->
            if (episodes == null) return@observeNullable
            binding.apply {
                if (comingSoon) resultBookmarkButton.requestFocus()

                //    resultEpisodeLoading.isVisible = episodes is Resource.Loading
                if (episodes is Resource.Success) {
                    val lastWatchedIndex = episodes.value.indexOfLast { ep ->
                        ep.getWatchProgress() >= NEXT_WATCH_EPISODE_PERCENTAGE.toFloat() / 100.0f || ep.videoWatchState == VideoWatchState.Watched
                    }

                    val firstUnwatched =
                        episodes.value.getOrElse(lastWatchedIndex + 1) { episodes.value.firstOrNull() }

                    if (firstUnwatched != null) {
                        resultPlaySeriesText.text =
                            when {
                                firstUnwatched.season != null ->
                                    "${getString(R.string.season_short)}${firstUnwatched.season}:${
                                        getString(
                                            R.string.episode_short
                                        )
                                    }${firstUnwatched.episode}"

                                else -> "${getString(R.string.episode)} ${firstUnwatched.episode}"
                            }
                        resultPlaySeriesButton.setOnClickListener {
                            viewModel.handleAction(
                                EpisodeClickEvent(
                                    ACTION_CLICK_DEFAULT,
                                    firstUnwatched
                                )
                            )
                        }
                        resultPlaySeriesButton.setOnLongClickListener {
                            viewModel.handleAction(
                                EpisodeClickEvent(ACTION_SHOW_OPTIONS, firstUnwatched)
                            )
                            return@setOnLongClickListener true
                        }
                        if (!hasLoadedEpisodesOnce) {
                            hasLoadedEpisodesOnce = true
                            resultPlaySeries.isVisible = resultResumeSeries.isGone && !comingSoon
                            resultEpisodesShow.isVisible = true && !comingSoon
                            //resultSyncUi.resultSyncEpisodeHolder.isVisible = true
                            //resultSyncUi.resultSyncEpisodes.isVisible = true
                            resultSyncUi.resultSyncEpisodesCounterHolder.isVisible = true
                            resultSyncUi.resultSyncEpisodesSlider.isVisible = true


                            resultPlaySeriesButton.requestFocus()
                        }
                    }


                    (resultEpisodes.adapter as? EpisodeAdapter)?.submitList(episodes.value)
                }
            }
        }

        observeNullable(viewModel.page) { data ->
            if (data == null) return@observeNullable
            binding.apply {
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
                        resultMetaStatus.setText(d.onGoingText)
                        resultMetaContentRating.setText(d.contentRatingText)
                        resultCastText.setText(d.actorsText)
                        resultNextAiring.setText(d.nextAiringEpisode)
                        resultNextAiringTime.setText(d.nextAiringDate)
                        resultPoster.loadImage(d.posterImage)

                        var isExpanded = false
                        resultDescription.apply {
                            setTextHtml(d.plotText)
                            setOnClickListener {
                                if (isLayout(EMULATOR)) {
                                    isExpanded = !isExpanded
                                    maxLines = if (isExpanded) {
                                        Integer.MAX_VALUE
                                    } else 10
                                } else {
                                    context?.let { ctx ->
                                        val builder: AlertDialog.Builder =
                                            AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                                        builder.setMessage(d.plotText.asString(ctx).html())
                                            .setTitle(d.plotHeaderText.asString(ctx))
                                            .show()
                                    }
                                }
                            }
                        }

                        val error = listOf(
                            R.drawable.profile_bg_dark_blue,
                            R.drawable.profile_bg_blue,
                            R.drawable.profile_bg_orange,
                            R.drawable.profile_bg_pink,
                            R.drawable.profile_bg_purple,
                            R.drawable.profile_bg_red,
                            R.drawable.profile_bg_teal
                        ).random()

                        backgroundPoster.loadImage(d.posterBackgroundImage) {
                            error { getImageFromDrawable(context ?: return@error null, error) }
                        }

                        bindLogo(
                            url = d.logoUrl,
                            headers = d.posterHeaders,
                            titleView = resultTitle,
                            logoView = backgroundPosterWatermarkBadgeHolder
                        )

                        comingSoon = d.comingSoon
                        resultTvComingSoon.isVisible = d.comingSoon

                        populateChips(resultTag, d.tags)
                        val prefs =
                            androidx.preference.PreferenceManager.getDefaultSharedPreferences(root.context)
                        val showCast = prefs.getBoolean(
                            root.context.getString(R.string.show_cast_in_details_key),
                            true
                        )

                        resultCastItems.isGone = !showCast || d.actors.isNullOrEmpty()
                        (resultCastItems.adapter as? ActorAdaptor)?.submitList(if (showCast) d.actors else emptyList())

                        if (d.contentRatingText == null) {
                            // If there is no rating to display, we don't want an empty gap
                            resultMetaContentRating.width = 0
                        }

                        if (syncModel.addSyncs(d.syncData)) {
                            syncModel.updateMetaAndUser()
                            syncModel.updateSynced()
                        } else {
                            syncModel.addFromUrl(d.url)
                        }

                        resultSearchButton.setOnClickListener {
                            QuickSearchFragment.pushSearch(activity, d.title)
                        }
                    }

                    is Resource.Loading -> {}

                    is Resource.Failure -> {
                        resultErrorText.text =
                            storedData.url.plus("\n") + data.errorString
                    }
                }

                resultFinishLoading.isVisible = data is Resource.Success

                resultLoading.isVisible = data is Resource.Loading

                resultLoadingError.isVisible = data is Resource.Failure
                //resultReloadConnectionOpenInBrowser.isVisible = data is Resource.Failure
            }
        }
    }
}
