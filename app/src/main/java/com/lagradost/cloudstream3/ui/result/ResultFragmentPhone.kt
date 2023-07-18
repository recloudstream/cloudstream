package com.lagradost.cloudstream3.ui.result

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doOnTextChanged
import com.discord.panels.OverlappingPanelsLayout
import com.discord.panels.PanelsChildGestureRegionObserver
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.updateHasTrailers
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.FragmentResultBinding
import com.lagradost.cloudstream3.databinding.FragmentResultSwipeBinding
import com.lagradost.cloudstream3.databinding.ResultRecommendationsBinding
import com.lagradost.cloudstream3.databinding.ResultSyncBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.player.CSPlayerEvent
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.utils.AppUtils.isCastApiAvailable
import com.lagradost.cloudstream3.utils.AppUtils.openBrowser
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialogInstant
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.UIHelper
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes


class ResultFragmentPhone : ResultFragment() {
    private var binding: FragmentResultSwipeBinding? = null
    private var resultBinding: FragmentResultBinding? = null
    private var recommendationBinding: ResultRecommendationsBinding? = null
    private var syncBinding: ResultSyncBinding? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = super.onCreateView(inflater, container, savedInstanceState) ?: return null
        FragmentResultSwipeBinding.bind(root).let { bind ->
            resultBinding =
                bind.fragmentResult//FragmentResultBinding.bind(binding.root.findViewById(R.id.fragment_result))
            recommendationBinding = bind.resultRecommendations
            syncBinding = bind.resultSync
            binding = bind
        }

        return root
    }

    var currentTrailers: List<ExtractorLink> = emptyList()
    var currentTrailerIndex = 0

    override fun nextMirror() {
        currentTrailerIndex++
        loadTrailer()
    }

    override fun hasNextMirror(): Boolean {
        return currentTrailerIndex + 1 < currentTrailers.size
    }

    override fun playerError(exception: Exception) {
        if (player.getIsPlaying()) { // because we dont want random toasts in player
            super.playerError(exception)
        } else {
            nextMirror()
        }
    }

    private fun loadTrailer(index: Int? = null) {
        val isSuccess =
            currentTrailers.getOrNull(index ?: currentTrailerIndex)?.let { trailer ->
                context?.let { ctx ->
                    player.onPause()
                    player.loadPlayer(
                        ctx,
                        false,
                        trailer,
                        null,
                        startPosition = 0L,
                        subtitles = emptySet(),
                        subtitle = null,
                        autoPlay = false
                    )
                    true
                } ?: run {
                    false
                }
            } ?: run {
                false
            }
        //result_trailer_thumbnail?.setImageBitmap(result_poster_background?.drawable?.toBitmap())


        // result_trailer_loading?.isVisible = isSuccess
        val turnVis = !isSuccess && !isFullScreenPlayer
        resultBinding?.apply {
            resultSmallscreenHolder.isVisible = turnVis
            resultPosterBackgroundHolder.apply {
                val fadeIn: Animation = AlphaAnimation(alpha, if (turnVis) 1.0f else 0.0f).apply {
                    interpolator = DecelerateInterpolator()
                    duration = 200
                    fillAfter = true
                }
                clearAnimation()
                startAnimation(fadeIn)
            }

            // We don't want the trailer to be focusable if it's not visible
            resultSmallscreenHolder.descendantFocusability = if (isSuccess) {
                ViewGroup.FOCUS_AFTER_DESCENDANTS
            } else {
                ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
            binding?.resultFullscreenHolder?.isVisible = !isSuccess && isFullScreenPlayer
        }


        //player_view?.apply {
        //alpha = 0.0f
        //ObjectAnimator.ofFloat(player_view, "alpha", 1f).apply {
        //    duration = 200
        //    start()
        //}

        //val fadeIn: Animation = AlphaAnimation(0.0f, 1f).apply {
        //    interpolator = DecelerateInterpolator()
        //    duration = 2000
        //    fillAfter = true
        //}
        //startAnimation(fadeIn)
        // }


    }

    override fun setTrailers(trailers: List<ExtractorLink>?) {
        context?.updateHasTrailers()
        if (!LoadResponse.isTrailersEnabled) return
        currentTrailers = trailers?.sortedBy { -it.quality } ?: emptyList()
        loadTrailer()
    }

    override fun onDestroyView() {
        //somehow this still leaks and I dont know why????
        // todo look at https://github.com/discord/OverlappingPanels/blob/70b4a7cf43c6771873b1e091029d332896d41a1a/sample_app/src/main/java/com/discord/sampleapp/MainActivity.kt
        PanelsChildGestureRegionObserver.Provider.get().let { obs ->
            resultBinding?.resultCastItems?.let {
                obs.unregister(it)
            }
            obs.removeGestureRegionsUpdateListener(this)
        }
        binding = null
        resultBinding = null
        syncBinding = null
        recommendationBinding = null
        super.onDestroyView()
    }

    var loadingDialog: Dialog? = null
    var popupDialog: Dialog? = null

    /**
     * Sets next focus to allow navigation up and down between 2 views
     * if either of them is null nothing happens.
     **/
    private fun setFocusUpAndDown(upper: View?, down: View?) {
        if (upper == null || down == null) return
        upper.nextFocusDownId = down.id
        down.nextFocusUpId = upper.id
    }

    var selectSeason: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val apiName = arguments?.getString(API_NAME_BUNDLE) ?: return

        super.onViewCreated(view, savedInstanceState)



        playerBinding?.playerOpenSource?.setOnClickListener {
            currentTrailers.getOrNull(currentTrailerIndex)?.let {
                context?.openBrowser(it.url)
            }
        }
        binding?.resultOverlappingPanels?.setStartPanelLockState(OverlappingPanelsLayout.LockState.CLOSE)
        binding?.resultOverlappingPanels?.setEndPanelLockState(OverlappingPanelsLayout.LockState.CLOSE)

        recommendationBinding?.resultRecommendationsList?.apply {
            spanCount = 3
            adapter =
                SearchAdapter(
                    ArrayList(),
                    this,
                ) { callback ->
                    SearchHelper.handleSearchClickCallback(callback)
                }
        }

        PanelsChildGestureRegionObserver.Provider.get().addGestureRegionsUpdateListener(this)

        resultBinding?.resultCastItems?.let {
            PanelsChildGestureRegionObserver.Provider.get().register(it)
        }


        binding?.resultBack?.setOnClickListener {
            activity?.popCurrentPage()
        }

        /*
        result_bookmark_button?.setOnClickListener {
            it.popupMenuNoIcons(
                items = WatchType.values()
                    .map { watchType -> Pair(watchType.internalId, watchType.stringRes) },
                //.map { watchType -> Triple(watchType.internalId, watchType.iconRes, watchType.stringRes) },
            ) {
                viewModel.updateWatchStatus(WatchType.fromInternalId(this.itemId))
            }
        }*/

        binding?.resultMiniSync?.adapter = ImageAdapter(
            nextFocusDown = R.id.result_sync_set_score,
            clickCallback = { action ->
                if (action == IMAGE_CLICK || action == IMAGE_LONG_CLICK) {
                    if (binding?.resultOverlappingPanels?.getSelectedPanel()?.ordinal == 1) {
                        binding?.resultOverlappingPanels?.openStartPanel()
                    } else {
                        binding?.resultOverlappingPanels?.closePanels()
                    }
                }
            })


        resultBinding?.resultScroll?.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val dy = scrollY - oldScrollY
            if (dy > 0) { //check for scroll down
                binding?.resultBookmarkFab?.shrink()
            } else if (dy < -5) {
                binding?.resultBookmarkFab?.extend()
            }
            if (!isFullScreenPlayer && player.getIsPlaying()) {
                if (scrollY > (resultBinding?.fragmentTrailer?.playerBackground?.height
                        ?: scrollY)
                ) {
                    player.handleEvent(CSPlayerEvent.Pause)
                }
            }
            //result_poster_blur_holder?.translationY = -scrollY.toFloat()
        })
        val api = APIHolder.getApiFromNameNull(apiName)

        binding?.mediaRouteButton?.apply {
            val chromecastSupport = api?.hasChromecastSupport == true
            alpha = if (chromecastSupport) 1f else 0.3f
            if (!chromecastSupport) {
                setOnClickListener {
                    CommonActivity.showToast(
                        R.string.no_chromecast_support_toast,
                        Toast.LENGTH_LONG
                    )
                }
            }
            activity?.let { act ->
                if (act.isCastApiAvailable()) {
                    try {
                        CastButtonFactory.setUpMediaRouteButton(act, this)
                        val castContext = CastContext.getSharedInstance(act.applicationContext)
                        isGone = castContext.castState == CastState.NO_DEVICES_AVAILABLE
                        // this shit leaks for some reason
                        //castContext.addCastStateListener { state ->
                        //    media_route_button?.isGone = state == CastState.NO_DEVICES_AVAILABLE
                        //}
                    } catch (e: Exception) {
                        logError(e)
                    }
                }
            }
        }

        observeNullable(viewModel.episodesCountText) { count ->
            resultBinding?.resultEpisodesText.setText(count)
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

        observe(syncModel.synced) { list ->
            syncBinding?.resultSyncNames?.text =
                list.filter { it.isSynced && it.hasAccount }.joinToString { it.name }

            val newList = list.filter { it.isSynced && it.hasAccount }

            binding?.resultMiniSync?.isVisible = newList.isNotEmpty()
            (binding?.resultMiniSync?.adapter as? ImageAdapter)?.updateList(newList.mapNotNull { it.icon })
        }

        var currentSyncProgress = 0
        fun setSyncMaxEpisodes(totalEpisodes: Int?) {
            syncBinding?.resultSyncEpisodes?.max = (totalEpisodes ?: 0) * 1000

            normalSafeApiCall {
                val ctx = syncBinding?.resultSyncEpisodes?.context
                syncBinding?.resultSyncMaxEpisodes?.text =
                    totalEpisodes?.let { episodes ->
                        ctx?.getString(R.string.sync_total_episodes_some)?.format(episodes)
                    } ?: run {
                        ctx?.getString(R.string.sync_total_episodes_none)
                    }
            }
        }
        observe(syncModel.metadata) { meta ->
            when (meta) {
                is Resource.Success -> {
                    val d = meta.value
                    syncBinding?.resultSyncEpisodes?.progress = currentSyncProgress * 1000
                    setSyncMaxEpisodes(d.totalEpisodes)

                    viewModel.setMeta(d, syncModel.getSyncs())
                }

                is Resource.Loading -> {
                    syncBinding?.resultSyncMaxEpisodes?.text =
                        syncBinding?.resultSyncMaxEpisodes?.context?.getString(R.string.sync_total_episodes_none)
                }

                else -> {}
            }
        }


        observe(syncModel.userData) { status ->
            var closed = false
            syncBinding?.apply {
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
                        resultSyncRating.value = d.score?.toFloat() ?: 0.0f
                        resultSyncCheck.setItemChecked(d.status + 1, true)
                        val watchedEpisodes = d.watchedEpisodes ?: 0
                        currentSyncProgress = watchedEpisodes

                        d.maxEpisodes?.let {
                            // don't directly call it because we don't want to override metadata observe
                            setSyncMaxEpisodes(it)
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            resultSyncEpisodes.setProgress(watchedEpisodes * 1000, true)
                        } else {
                            resultSyncEpisodes.progress = watchedEpisodes * 1000
                        }
                        resultSyncCurrentEpisodes.text =
                            Editable.Factory.getInstance()?.newEditable(watchedEpisodes.toString())
                        normalSafeApiCall { // format might fail
                            context?.getString(R.string.sync_score_format)?.format(d.score ?: 0)
                                ?.let {
                                    resultSyncScoreText.text = it
                                }
                        }
                    }

                    null -> {
                        closed = false
                    }
                }
            }
            binding?.resultOverlappingPanels?.setStartPanelLockState(if (closed) OverlappingPanelsLayout.LockState.CLOSE else OverlappingPanelsLayout.LockState.UNLOCKED)
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
            syncBinding?.apply {
                resultSyncCheck.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                resultSyncCheck.adapter = arrayAdapter
                UIHelper.setListViewHeightBasedOnItems(resultSyncCheck)

                resultSyncCheck.setOnItemClickListener { _, _, which, _ ->
                    syncModel.setStatus(which - 1)
                }

                resultSyncRating.addOnChangeListener { _, value, _ ->
                    syncModel.setScore(value.toInt())
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

        syncBinding?.resultSyncSetScore?.setOnClickListener {
            syncModel.publishUserData()
        }

        observe(viewModel.watchStatus) { watchType ->
            binding?.resultBookmarkFab?.apply {
                if (watchType == WatchType.NONE) {
                    context?.colorFromAttribute(R.attr.white)
                } else {
                    context?.colorFromAttribute(R.attr.colorPrimary)
                }?.let {
                    val colorState = ColorStateList.valueOf(it)
                    iconTint = colorState
                    setTextColor(colorState)
                }

                setOnClickListener { fab ->
                    activity?.showBottomDialog(
                        WatchType.values().map { fab.context.getString(it.stringRes) }.toList(),
                        watchType.ordinal,
                        fab.context.getString(R.string.action_add_to_bookmarks),
                        showApply = false,
                        {}) {
                        viewModel.updateWatchStatus(WatchType.values()[it])
                    }
                }
            }
        }


        observe(viewModel.loadedLinks) { load ->
            if (load == null) {
                loadingDialog?.dismissSafe(activity)
                loadingDialog = null
                return@observe
            }
            if (loadingDialog?.isShowing != true) {
                loadingDialog?.dismissSafe(activity)
                loadingDialog = null
            }
            loadingDialog = loadingDialog ?: context?.let { ctx ->
                val builder =
                    BottomSheetDialog(ctx)
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

        observeNullable(viewModel.selectedSeason) { text ->
            resultBinding?.apply {
                resultSeasonButton.setText(text)

                selectSeason =
                    text?.asStringNull(resultSeasonButton.context)
                // If the season button is visible the result season button will be next focus down
                if (resultSeasonButton.isVisible && resultResumeParent.isVisible) {
                    setFocusUpAndDown(resultResumeSeriesButton, resultSeasonButton)
                }
            }
        }

        observeNullable(viewModel.selectedDubStatus) { status ->
            resultBinding?.apply {
                resultDubSelect.setText(status)

                if (resultDubSelect.isVisible && !resultSeasonButton.isVisible && !resultEpisodeSelect.isVisible && resultResumeParent.isVisible) {
                    setFocusUpAndDown(resultResumeSeriesButton, resultDubSelect)
                }
            }
        }
        observeNullable(viewModel.selectedRange) { range ->
            resultBinding?.apply {
                resultEpisodeSelect.setText(range)
                // If Season button is invisible then the bookmark button next focus is episode select
                if (resultEpisodeSelect.isVisible && !resultSeasonButton.isVisible && resultResumeParent.isVisible) {
                    setFocusUpAndDown(resultResumeSeriesButton, resultEpisodeSelect)
                }
            }
        }

//        val preferDub = context?.getApiDubstatusSettings()?.all { it == DubStatus.Dubbed } == true

        observe(viewModel.dubSubSelections) { range ->
            resultBinding?.resultDubSelect?.setOnClickListener { view ->
                view?.context?.let { ctx ->
                    view.popupMenuNoIconsAndNoStringRes(range
                        .mapNotNull { (text, status) ->
                            Pair(
                                status.ordinal,
                                text?.asStringNull(ctx) ?: return@mapNotNull null
                            )
                        }) {
                        viewModel.changeDubStatus(DubStatus.values()[itemId])
                    }
                }
            }
        }

        observe(viewModel.rangeSelections) { range ->
            resultBinding?.resultEpisodeSelect?.setOnClickListener { view ->
                view?.context?.let { ctx ->
                    val names = range
                        .mapNotNull { (text, r) ->
                            r to (text?.asStringNull(ctx) ?: return@mapNotNull null)
                        }

                    view.popupMenuNoIconsAndNoStringRes(names.mapIndexed { index, (_, name) ->
                        index to name
                    }) {
                        viewModel.changeRange(names[itemId].first)
                    }
                }
            }
        }

        observe(viewModel.seasonSelections) { seasonList ->
            resultBinding?.resultSeasonButton?.setOnClickListener { view ->

                view?.context?.let { ctx ->
                    val names = seasonList
                        .mapNotNull { (text, r) ->
                            r to (text?.asStringNull(ctx) ?: return@mapNotNull null)
                        }

                    activity?.showDialog(
                        names.map { it.second },
                        names.indexOfFirst { it.second == selectSeason },
                        "",
                        false,
                        {}) { itemId ->
                        viewModel.changeSeason(names[itemId].first)
                    }


                    //view.popupMenuNoIconsAndNoStringRes(names.mapIndexed { index, (_, name) ->
                    //    index to name
                    //}) {
                    //    viewModel.changeSeason(names[itemId].first)
                    //}
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        PanelsChildGestureRegionObserver.Provider.get().addGestureRegionsUpdateListener(this)
    }

    override fun onGestureRegionsUpdate(gestureRegions: List<Rect>) {
        binding?.resultOverlappingPanels?.setChildGestureRegions(gestureRegions)
    }

    override fun setRecommendations(rec: List<SearchResponse>?, validApiName: String?) {
        val isInvalid = rec.isNullOrEmpty()
        val matchAgainst = validApiName ?: rec?.firstOrNull()?.apiName

        recommendationBinding?.apply {
            root.isGone = isInvalid
            root.post {
                rec?.let { list ->
                    (resultRecommendationsList.adapter as? SearchAdapter)?.updateList(list.filter { it.apiName == matchAgainst })
                }
            }
        }

        binding?.apply {
            resultRecommendationsBtt.isGone = isInvalid
            resultRecommendationsBtt.setOnClickListener {
                val nextFocusDown = if (resultOverlappingPanels.getSelectedPanel().ordinal == 1) {
                    resultOverlappingPanels.openEndPanel()
                    R.id.result_recommendations
                } else {
                    resultOverlappingPanels.closePanels()
                    R.id.result_description
                }
                resultBinding?.apply {
                    resultRecommendationsBtt.nextFocusDownId = nextFocusDown
                    resultSearch.nextFocusDownId = nextFocusDown
                    resultOpenInBrowser.nextFocusDownId = nextFocusDown
                    resultShare.nextFocusDownId = nextFocusDown
                }
            }
            resultOverlappingPanels.setEndPanelLockState(if (isInvalid) OverlappingPanelsLayout.LockState.CLOSE else OverlappingPanelsLayout.LockState.UNLOCKED)

            rec?.map { it.apiName }?.distinct()?.let { apiNames ->
                // very dirty selection
                recommendationBinding?.resultRecommendationsFilterButton?.apply {
                    isVisible = apiNames.size > 1
                    text = matchAgainst
                    setOnClickListener { _ ->
                        activity?.showBottomDialog(
                            apiNames,
                            apiNames.indexOf(matchAgainst),
                            getString(R.string.home_change_provider_img_des), false, {}
                        ) {
                            setRecommendations(rec, apiNames[it])
                        }
                    }
                }
            } ?: run {
                recommendationBinding?.resultRecommendationsFilterButton?.isVisible = false
            }
        }
    }
}