package com.lagradost.cloudstream3.ui.result

import android.animation.Animator
import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.APIHolder.updateHasTrailers
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.FragmentResultTvBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.services.SubscriptionWorkManager
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup
import com.lagradost.cloudstream3.ui.player.ExtractorLinkGenerator
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.result.ResultFragment.getStoredData
import com.lagradost.cloudstream3.ui.result.ResultFragment.updateUIEvent
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_FOCUSED
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isEmulatorSettings
import com.lagradost.cloudstream3.utils.AppUtils.getNameFull
import com.lagradost.cloudstream3.utils.AppUtils.html
import com.lagradost.cloudstream3.utils.AppUtils.isRtl
import com.lagradost.cloudstream3.utils.AppUtils.loadCache
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialogInstant
import com.lagradost.cloudstream3.utils.UIHelper
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.setImage

class ResultFragmentTv : Fragment() {
    protected lateinit var viewModel: ResultViewModel2
    private var binding: FragmentResultTvBinding? = null

    override fun onDestroyView() {
        binding = null
        updateUIEvent -= ::updateUI
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel =
            ViewModelProvider(this)[ResultViewModel2::class.java]
        viewModel.EPISODE_RANGE_SIZE = 50
        updateUIEvent += ::updateUI

        val localBinding = FragmentResultTvBinding.inflate(inflater, container, false)
        binding = localBinding
        return localBinding.root
    }

    private fun updateUI(id: Int?) {
        viewModel.reloadEpisodes()
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
        activity?.let {
            it.window?.navigationBarColor =
                it.colorFromAttribute(R.attr.primaryBlackBackground)
        }
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
            episodesShadow.fade(show)
            episodeHolderTv.fade(show)
            if (episodesShadow.isRtl()) {
                episodesShadowBackground.scaleX = -1f
            } else {
                episodesShadowBackground.scaleX = 1f
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        binding?.apply {
            //episodesShadow.rotationX = 180.0f//if(episodesShadow.isRtl()) 180.0f else 0.0f
            
            val leftListener: View.OnFocusChangeListener =
                View.OnFocusChangeListener { view, hasFocus ->
                    if (!hasFocus) return@OnFocusChangeListener
                    if (view?.tag == context?.getString(R.string.tv_no_focus_tag)){
                        resultFinishLoading.scrollTo(0,0)
                    }
                    toggleEpisodes(false)
                }

            val rightListener: View.OnFocusChangeListener =
                View.OnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) return@OnFocusChangeListener
                    toggleEpisodes(true)
                }

            resultPlayMovieButton.onFocusChangeListener = leftListener
            resultPlaySeriesButton.onFocusChangeListener = leftListener
            resultResumeSeriesButton.onFocusChangeListener = leftListener
            resultPlayTrailerButton.onFocusChangeListener = leftListener
            resultEpisodesShowButton.onFocusChangeListener = rightListener
            resultDescription.onFocusChangeListener = leftListener
            resultBookmarkButton.onFocusChangeListener = leftListener
            resultFavoriteButton.onFocusChangeListener = leftListener
            resultEpisodesShowButton.setOnClickListener {
                // toggle, to make it more touch accessable just in case someone thinks that a
                // tv layout is better but is using a touch device
                toggleEpisodes(!episodeHolderTv.isVisible)
            }

            //  resultEpisodes.onFocusChangeListener = leftListener

            redirectToPlay.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) return@setOnFocusChangeListener
                toggleEpisodes(false)

                binding?.apply {
                    val views = listOf(
                        resultPlayMovieButton,
                        resultPlaySeriesButton,
                        resultResumeSeriesButton,
                        resultPlayTrailerButton,
                        resultBookmarkButton,
                        resultFavoriteButton,
                        resultSubscribeButton
                    )
                    for (requestView in views) {
                        if (!requestView.isVisible) continue
                        if (requestView.requestFocus()) break
                    }
                }
            }

            // parallax on background
            resultFinishLoading.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { view, _, scrollY, _, oldScrollY ->
                backgroundPosterHolder.translationY = -scrollY.toFloat() * 0.8f
            })

            redirectToEpisodes.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) return@setOnFocusChangeListener
                toggleEpisodes(true)
                binding?.apply {
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

            //resultReloadConnectionOpenInBrowser.setOnClickListener {view ->
            //    view.context?.openBrowser(storedData?.url ?: return@setOnClickListener, fallbackWebview = true)
            //}

            resultSeasonSelection.setAdapter()
            resultRangeSelection.setAdapter()
            resultDubSelection.setAdapter()
            resultRecommendationsFilterSelection.setAdapter()

            resultCastItems.setOnFocusChangeListener { _, hasFocus ->
                // Always escape focus
                if (hasFocus) binding?.resultBookmarkButton?.requestFocus()
            }
            //resultBack.setOnClickListener {
            //    activity?.popCurrentPage()
            //}

            resultRecommendationsList.spanCount = 8
            resultRecommendationsList.adapter =
                SearchAdapter(
                    ArrayList(),
                    resultRecommendationsList,
                ) { callback ->
                    if (callback.action == SEARCH_ACTION_FOCUSED)
                        toggleEpisodes(false)
                    else
                        SearchHelper.handleSearchClickCallback(callback)
                }

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

            resultCastItems.layoutManager = object : LinearListLayout(view.context) {

                override fun onInterceptFocusSearch(focused: View, direction: Int): View? {
                    return super.onInterceptFocusSearch(focused, direction)
                }

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
                setHorizontal()
            }

            val aboveCast = listOf(
                binding?.resultEpisodesShow,
                binding?.resultBookmark,
                binding?.resultFavorite,
                binding?.resultSubscribe,
            ).firstOrNull {
                it?.isVisible == true
            }
            resultCastItems.adapter = ActorAdaptor(aboveCast?.id) {
                toggleEpisodes(false)
            }
        }

        observeNullable(viewModel.resumeWatching) { resume ->
            binding?.apply {

                // > resultResumeSeries is visible when not null
                if (resume == null) {
                    resultResumeSeries.isVisible = false
                    return@observeNullable
                }

                // show progress no matter if series or movie
                resume.progress?.let { progress ->
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

                resultPlayMovie.isVisible = false
                resultPlaySeries.isVisible = false
                resultResumeSeries.isVisible = true

                focusPlayButton()

                resultResumeSeriesText.text =
                    if (resume.isMovie) context?.getString(R.string.resume) else "${getString(R.string.season_short)}${resume.result.season}:${getString(R.string.episode_short)}${resume.result.episode}"

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
            val trailers = trailersLinks.flatMap { it.mirros }
            binding?.apply {
                resultPlayTrailer.isGone = trailers.isEmpty()
                resultPlayTrailerButton.setOnClickListener {
                    if (trailers.isEmpty()) return@setOnClickListener
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

        observe(viewModel.watchStatus) { watchType ->
            binding?.apply {
                resultBookmarkText.setText(watchType.stringRes)
                resultBookmarkButton.setOnClickListener { view ->
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

        observeNullable(viewModel.favoriteStatus) { isFavorite ->

            binding?.resultFavorite?.isVisible = isFavorite != null

            binding?.resultFavoriteButton?.apply {

                if (isFavorite == null) return@observeNullable

                val drawable = if (isFavorite) {
                    R.drawable.ic_baseline_favorite_24
                } else {
                    R.drawable.ic_baseline_favorite_border_24
                }

                setIconResource(drawable)

                setOnClickListener {
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
            }

            binding?.resultFavoriteText?.apply {
                val text = if (isFavorite == true) {
                    R.string.unfavorite
                } else {
                    R.string.favorite
                }
                setText(text)
            }
        }

        observeNullable(viewModel.subscribeStatus) { isSubscribed ->
            binding?.resultSubscribe?.isVisible = isSubscribed != null && requireContext().isEmulatorSettings()
            binding?.resultSubscribeButton?.apply {

                if (isSubscribed == null) return@observeNullable

                val drawable = if (isSubscribed) {
                    R.drawable.ic_baseline_notifications_active_24
                } else {
                    R.drawable.baseline_notifications_none_24
                }

                setIconResource(drawable)

                setOnClickListener {
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

                binding?.resultSubscribeText?.apply {
                    val text = if (isSubscribed) {
                        R.string.action_unsubscribe
                    } else {
                        R.string.action_subscribe
                    }
                    setText(text)
                }
            }
        }

        observeNullable(viewModel.movie) { data ->
            if (data == null) return@observeNullable

            binding?.apply {

                resultPlayMovie.isVisible = data is Resource.Success
                resultPlaySeries.isVisible = false
                resultEpisodesShow.isVisible = false

                (data as? Resource.Success)?.value?.let { (text, ep) ->
                    //resultPlayMovieText.setText(text)
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
                    //focusPlayButton()
                    resultPlayMovieButton.requestFocus()
                }
            }
            //focusPlayButton()
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

        // Used to request focus the first time the episodes are loaded.
        var hasLoadedEpisodesOnce = false
        observeNullable(viewModel.episodes) { episodes ->
            if (episodes == null) return@observeNullable

            binding?.apply {

                resultPlayMovie.isVisible = false
                resultPlaySeries.isVisible = true
                resultEpisodes.isVisible = true
                resultEpisodesShow.isVisible = true

                //    resultEpisodeLoading.isVisible = episodes is Resource.Loading
                if (episodes is Resource.Success) {
                    val first = episodes.value.firstOrNull()
                    if (first != null) {
                        resultPlaySeriesText.text = "${getString(R.string.season_short)}${first.season}:${getString(R.string.episode_short)}${first.episode}"

                        resultPlaySeriesButton.setOnClickListener {
                            viewModel.handleAction(
                                EpisodeClickEvent(
                                    ACTION_CLICK_DEFAULT,
                                    first
                                )
                            )
                        }
                        resultPlaySeriesButton.setOnLongClickListener {
                            viewModel.handleAction(
                                EpisodeClickEvent(ACTION_SHOW_OPTIONS, first)
                            )
                            return@setOnLongClickListener true
                        }
                        if (!hasLoadedEpisodesOnce) {
                            hasLoadedEpisodesOnce = true
                            focusPlayButton()
                            resultPlaySeries.requestFocus()
                        }
                    }

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
                    /*val focus = activity?.currentFocus

                    if (hasEpisodes) {
                        // Make it impossible to focus anywhere else!
                        temporaryNoFocus.isFocusable = true
                        temporaryNoFocus.requestFocus()
                    }*/

                    (resultEpisodes.adapter as? EpisodeAdapter)?.updateList(episodes.value)

                    /* if (hasEpisodes) main {

                         delay(500)
                         // This might make some people sad as it changes the focus when leaving an episode :(
                         if(focus?.requestFocus() == true) {
                             temporaryNoFocus.isFocusable = false
                             return@main
                         }
                         temporaryNoFocus.isFocusable = false
                         temporaryNoFocus.requestFocus()
                     }

                     if (hasNoFocus())
                         binding?.resultEpisodes?.requestFocus()*/
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
                        resultMetaContentRating.setText(d.contentRatingText)
                        resultCastText.setText(d.actorsText)
                        resultNextAiring.setText(d.nextAiringEpisode)
                        resultNextAiringTime.setText(d.nextAiringDate)
                        resultPoster.setImage(d.posterImage)
                        resultDescription.setTextHtml(d.plotText)
                        resultDescription.setOnClickListener { view ->
                            view.context?.let { ctx ->
                                val builder: AlertDialog.Builder =
                                    AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                                builder.setMessage(d.plotText.asString(ctx).html())
                                    .setTitle(d.plotHeaderText.asString(ctx))
                                    .show()
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
                        //Change poster crop area to 20% from Top
                        backgroundPoster.cropYCenterOffsetPct = 0.20F
                        
                        backgroundPoster.setImage(
                            d.posterBackgroundImage ?: UiImage.Drawable(error),
                            radius = 0,
                            errorImageDrawable = error
                        )
                        resultComingSoon.isVisible = d.comingSoon
                        resultDataHolder.isGone = d.comingSoon
                        UIHelper.populateChips(resultTag, d.tags)
                        resultCastItems.isGone = d.actors.isNullOrEmpty()
                        (resultCastItems.adapter as? ActorAdaptor)?.updateList(
                            d.actors ?: emptyList()
                        )

                        if (d.contentRatingText == null) {
                            // If there is no rating to display, we don't want an empty gap
                            resultMetaContentRating.width = 0
                        }
                    }

                    is Resource.Loading -> {

                    }

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