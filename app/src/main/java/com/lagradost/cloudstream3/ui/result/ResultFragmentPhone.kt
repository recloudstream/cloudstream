package com.lagradost.cloudstream3.ui.result

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
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
import androidx.lifecycle.ViewModelProvider
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
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
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
import com.lagradost.cloudstream3.services.SubscriptionWorkManager
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_LONG_CLICK
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup
import com.lagradost.cloudstream3.ui.player.CSPlayerEvent
import com.lagradost.cloudstream3.ui.player.FullScreenPlayer
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.result.ResultFragment.getStoredData
import com.lagradost.cloudstream3.ui.result.ResultFragment.updateUIEvent
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.utils.AppUtils.getNameFull
import com.lagradost.cloudstream3.utils.AppUtils.html
import com.lagradost.cloudstream3.utils.AppUtils.isCastApiAvailable
import com.lagradost.cloudstream3.utils.AppUtils.loadCache
import com.lagradost.cloudstream3.utils.AppUtils.openBrowser
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialogInstant
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialogText
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.UIHelper
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.utils.UIHelper.populateChips
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.VideoDownloadHelper


open class ResultFragmentPhone : FullScreenPlayer() {
    private val gestureRegionsListener = object : PanelsChildGestureRegionObserver.GestureRegionsListener {
        override fun onGestureRegionsUpdate(gestureRegions: List<Rect>) {
            binding?.resultOverlappingPanels?.setChildGestureRegions(gestureRegions)
        }
    }

    protected lateinit var viewModel: ResultViewModel2
    protected lateinit var syncModel: SyncViewModel

    protected var binding: FragmentResultSwipeBinding? = null
    protected var resultBinding: FragmentResultBinding? = null
    protected var recommendationBinding: ResultRecommendationsBinding? = null
    protected var syncBinding: ResultSyncBinding? = null

    override var layout = R.layout.fragment_result_swipe

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel =
            ViewModelProvider(this)[ResultViewModel2::class.java]
        syncModel =
            ViewModelProvider(this)[SyncViewModel::class.java]
        updateUIEvent += ::updateUI

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

    override fun playerError(exception: Throwable) {
        if (player.getIsPlaying()) { // because we don't want random toasts in player
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
                        autoPlay = false,
                        preview = false
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

    private fun setTrailers(trailers: List<ExtractorLink>?) {
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

            obs.removeGestureRegionsUpdateListener(gestureRegionsListener)
        }

        updateUIEvent -= ::updateUI
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

    private fun setUrl(url: String?) {
        if (url == null) {
            binding?.resultOpenInBrowser?.isVisible = false
            return
        }

        val valid = url.startsWith("http")

        binding?.resultOpenInBrowser?.apply {
            isVisible = valid
            setOnClickListener {
                context?.openBrowser(url)
            }
        }

        resultBinding?.resultReloadConnectionOpenInBrowser?.setOnClickListener {
            view?.context?.openBrowser(url)
        }

        resultBinding?.resultMetaSite?.setOnClickListener {
            view?.context?.openBrowser(url)
        }
    }

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
        afterPluginsLoadedEvent += ::reloadViewModel
        activity?.let {
            it.window?.navigationBarColor =
                it.colorFromAttribute(R.attr.primaryBlackBackground)
        }
        super.onResume()
        PanelsChildGestureRegionObserver.Provider.get()
            .addGestureRegionsUpdateListener(gestureRegionsListener)
    }

    override fun onStop() {
        afterPluginsLoadedEvent -= ::reloadViewModel
        super.onStop()
    }

    private fun updateUI(id: Int?) {
        syncModel.updateUserData()
        viewModel.reloadEpisodes()
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ===== setup =====
        UIHelper.fixPaddingStatusbar(binding?.resultTopBar)
        val storedData = getStoredData() ?: return
        activity?.window?.decorView?.clearFocus()
        activity?.loadCache()
        context?.updateHasTrailers()
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

        setUrl(storedData.url)
        syncModel.addFromUrl(storedData.url)
        val api = APIHolder.getApiFromNameNull(storedData.apiName)

        PanelsChildGestureRegionObserver.Provider.get().apply {
            resultBinding?.resultCastItems?.let {
                register(it)
            }
            addGestureRegionsUpdateListener(gestureRegionsListener)
        }



        // ===== ===== =====

        resultBinding?.apply {
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

            resultCastItems.setLinearListLayout(
                isHorizontal = true,
                nextLeft = FOCUS_SELF,
                nextRight = FOCUS_SELF
            )
            /*resultCastItems.layoutManager = object : LinearListLayout(view.context) {
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
            }.apply {
                this.orientation = RecyclerView.HORIZONTAL
            }*/
            resultCastItems.adapter = ActorAdaptor()

            resultEpisodes.adapter =
                EpisodeAdapter(
                    api?.hasDownloadSupport == true,
                    { episodeClick ->
                        viewModel.handleAction(episodeClick)
                    },
                    { downloadClickEvent ->
                        DownloadButtonSetup.handleDownloadClick(downloadClickEvent)
                    }
                )


            resultScroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
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
            })
        }

        binding?.apply {
            resultOverlappingPanels.setStartPanelLockState(OverlappingPanelsLayout.LockState.CLOSE)
            resultOverlappingPanels.setEndPanelLockState(OverlappingPanelsLayout.LockState.CLOSE)
            resultBack.setOnClickListener {
                activity?.popCurrentPage()
            }


            resultMiniSync.adapter = ImageAdapter(
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
            resultSubscribe.setOnClickListener {
                viewModel.toggleSubscriptionStatus(context) { newStatus: Boolean? ->
                    if (newStatus == null) return@toggleSubscriptionStatus

                    val message = if (newStatus) {
                            // Kinda icky to have this here, but it works.
                            SubscriptionWorkManager.enqueuePeriodicWork(context)
                            R.string.subscription_new
                        } else {
                            R.string.subscription_deleted
                        }

                        val name = (viewModel.page.value as? Resource.Success)?.value?.title
                            ?: txt(R.string.no_data).asStringNull(context) ?: ""
                        CommonActivity.showToast(txt(message, name), Toast.LENGTH_SHORT)
                }
            }
            resultFavorite.setOnClickListener {
                viewModel.toggleFavoriteStatus(context) { newStatus: Boolean? ->
                    if (newStatus == null) return@toggleFavoriteStatus

                    val message = if (newStatus) {
                        R.string.favorite_added
                    } else {
                        R.string.favorite_removed
                    }

                    val name = (viewModel.page.value as? Resource.Success)?.value?.title
                        ?: txt(R.string.no_data).asStringNull(context) ?: ""
                    CommonActivity.showToast(txt(message, name), Toast.LENGTH_SHORT)
                }
            }
            mediaRouteButton.apply {
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
        }

        playerBinding?.apply {
            playerOpenSource.setOnClickListener {
                currentTrailers.getOrNull(currentTrailerIndex)?.let {
                    context?.openBrowser(it.url)
                }
            }
        }

        recommendationBinding?.apply {
            resultRecommendationsList.apply {
                spanCount = 3
                adapter =
                    SearchAdapter(
                        ArrayList(),
                        this,
                    ) { callback ->
                        SearchHelper.handleSearchClickCallback(callback)
                    }
            }
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

        observeNullable(viewModel.resumeWatching) { resume ->
            resultBinding?.apply {
                if (resume == null) {
                    resultResumeParent.isVisible = false
                    return@observeNullable
                }
                resultResumeParent.isVisible = true
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
                    resultResumeSeriesProgress.isVisible = false
                    resultResumeSeriesTitle.isVisible = false
                    resultResumeSeriesProgressText.isVisible = false
                }

                resultResumeSeriesButton.isVisible = !resume.isMovie
                resultResumeSeriesButton.setOnClickListener {
                    viewModel.handleAction(
                        EpisodeClickEvent(
                            storedData.playerAction, //?: ACTION_PLAY_EPISODE_IN_PLAYER,
                            resume.result
                        )
                    )
                }
            }
        }

        observeNullable(viewModel.subscribeStatus) { isSubscribed ->
            binding?.resultSubscribe?.isVisible = isSubscribed != null
            if (isSubscribed == null) return@observeNullable

            val drawable = if (isSubscribed) {
                R.drawable.ic_baseline_notifications_active_24
            } else {
                R.drawable.baseline_notifications_none_24
            }

            binding?.resultSubscribe?.setImageResource(drawable)
        }

        observeNullable(viewModel.favoriteStatus) { isFavorite ->
            binding?.resultFavorite?.isVisible = isFavorite != null
            if (isFavorite == null) return@observeNullable

            val drawable = if (isFavorite) {
                R.drawable.ic_baseline_favorite_24
            } else {
                R.drawable.ic_baseline_favorite_border_24
            }

            binding?.resultFavorite?.setImageResource(drawable)
        }

        observe(viewModel.trailers) { trailers ->
            setTrailers(trailers.flatMap { it.mirros }) // I dont care about subtitles yet!
        }

        observeNullable(viewModel.episodes) { episodes ->
            resultBinding?.apply {
                // no failure?
                resultEpisodeLoading.isVisible = episodes is Resource.Loading
                resultEpisodes.isVisible = episodes is Resource.Success
                if (episodes is Resource.Success) {
                    (resultEpisodes.adapter as? EpisodeAdapter)?.updateList(episodes.value)
                }
            }
        }

        observeNullable(viewModel.movie) { data ->
            resultBinding?.apply {
                resultPlayMovie.isVisible = data is Resource.Success
                downloadButton.isVisible =
                    data is Resource.Success && viewModel.currentRepo?.api?.hasDownloadSupport == true

                (data as? Resource.Success)?.value?.let { (text, ep) ->
                    resultPlayMovie.setText(text)
                    resultPlayMovie.setOnClickListener {
                        viewModel.handleAction(
                            EpisodeClickEvent(ACTION_CLICK_DEFAULT, ep)
                        )
                    }
                    resultPlayMovie.setOnLongClickListener {
                        viewModel.handleAction(
                            EpisodeClickEvent(ACTION_SHOW_OPTIONS, ep)
                        )
                        return@setOnLongClickListener true
                    }
                    downloadButton.setDefaultClickListener(
                        VideoDownloadHelper.DownloadEpisodeCached(
                            ep.name,
                            ep.poster,
                            0,
                            null,
                            ep.id,
                            ep.id,
                            null,
                            null,
                            System.currentTimeMillis(),
                        ),
                        null
                    ) { click ->
                        when (click.action) {
                            DOWNLOAD_ACTION_DOWNLOAD -> {
                                viewModel.handleAction(
                                    EpisodeClickEvent(ACTION_DOWNLOAD_EPISODE, ep)
                                )
                            }

                            DOWNLOAD_ACTION_LONG_CLICK -> {
                                viewModel.handleAction(
                                    EpisodeClickEvent(
                                        ACTION_DOWNLOAD_MIRROR,
                                        ep
                                    )
                                )
                            }

                            else -> DownloadButtonSetup.handleDownloadClick(click)
                        }
                    }
                }
            }
        }

        observe(viewModel.page) { data ->
            if (data == null) return@observe
            resultBinding?.apply {
                (data as? Resource.Success)?.value?.let { d ->
                    resultVpn.setText(d.vpnText)
                    resultInfo.setText(d.metaText)
                    resultNoEpisodes.setText(d.noEpisodesFoundText)
                    resultTitle.setText(d.titleText)
                    resultMetaSite.setText(d.apiName)
                    resultMetaType.setText(d.typeText)
                    resultMetaYear.setText(d.yearText)
                    resultMetaDuration.setText(d.durationText)
                    resultMetaRating.setText(d.ratingText)
                    resultMetaContentRating.setText(d.contentRatingText)
                    resultCastText.setText(d.actorsText)
                    resultNextAiring.setText(d.nextAiringEpisode)
                    resultNextAiringTime.setText(d.nextAiringDate)
                    resultPoster.setImage(d.posterImage)
                    resultPosterBackground.setImage(d.posterBackgroundImage)
                    resultDescription.setTextHtml(d.plotText)
                    resultDescription.setOnClickListener {
                        activity?.let { activity ->
                            activity.showBottomDialogText(
                                d.titleText.asString(activity),
                                d.plotText.asString(activity).html(),
                                {}
                            )
                        }
                    }

                    populateChips(resultTag, d.tags)

                    resultComingSoon.isVisible = d.comingSoon
                    resultDataHolder.isGone = d.comingSoon

                    resultCastItems.isGone = d.actors.isNullOrEmpty()
                    (resultCastItems.adapter as? ActorAdaptor)?.updateList(d.actors ?: emptyList())

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

                    binding?.apply {
                        resultSearch.setOnClickListener {
                            QuickSearchFragment.pushSearch(activity, d.title)
                        }

                        resultShare.setOnClickListener {
                            try {
                                val i = Intent(Intent.ACTION_SEND)
                                i.type = "text/plain"
                                i.putExtra(Intent.EXTRA_SUBJECT, d.title)
                                i.putExtra(Intent.EXTRA_TEXT, d.url)
                                startActivity(Intent.createChooser(i, d.title))
                            } catch (e: Exception) {
                                logError(e)
                            }
                        }

                        setUrl(d.url)
                        resultBookmarkFab.apply {
                            isVisible = true
                            extend()
                        }
                    }
                }

                (data as? Resource.Failure)?.let { data ->
                    resultErrorText.text = storedData.url.plus("\n") + data.errorString
                }

                binding?.resultBookmarkFab?.isVisible = data is Resource.Success
                resultFinishLoading.isVisible = data is Resource.Success

                resultLoading.isVisible = data is Resource.Loading

                resultLoadingError.isVisible = data is Resource.Failure
                resultErrorText.isVisible = data is Resource.Failure
                resultReloadConnectionOpenInBrowser.isVisible = data is Resource.Failure
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
                        resultSyncCheck.setItemChecked(d.status.internalId + 1, true)
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
        observe(viewModel.recommendations) { recommendations ->
            setRecommendations(recommendations, null)
        }
        observe(viewModel.episodeSynopsis) { description ->
            activity?.let { activity ->
                activity.showBottomDialogText(
                    activity.getString(R.string.synopsis),
                    description.html()
                ) { viewModel.releaseEpisodeSynopsis() }
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
                setText(watchType.stringRes)
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
                        viewModel.updateWatchStatus(WatchType.values()[it], context)
                    }
                }
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
        PanelsChildGestureRegionObserver.Provider.get().addGestureRegionsUpdateListener(gestureRegionsListener)
    }

    private fun setRecommendations(rec: List<SearchResponse>?, validApiName: String?) {
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
