package com.lagradost.cloudstream3.ui.result

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.Intent.*
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.discord.panels.OverlappingPanelsLayout
import com.discord.panels.PanelsChildGestureRegionObserver
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getApiFromName
import com.lagradost.cloudstream3.APIHolder.updateHasTrailers
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.mvvm.*
import com.lagradost.cloudstream3.syncproviders.providers.Kitsu
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.player.CSPlayerEvent
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.AppUtils.html
import com.lagradost.cloudstream3.utils.AppUtils.isCastApiAvailable
import com.lagradost.cloudstream3.utils.AppUtils.loadCache
import com.lagradost.cloudstream3.utils.AppUtils.openBrowser
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialogInstant
import com.lagradost.cloudstream3.utils.UIHelper
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIcons
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import kotlinx.android.synthetic.main.fragment_result.*
import kotlinx.android.synthetic.main.fragment_result_swipe.*
import kotlinx.android.synthetic.main.fragment_trailer.*
import kotlinx.android.synthetic.main.result_recommendations.*
import kotlinx.android.synthetic.main.result_sync.*
import kotlinx.android.synthetic.main.trailer_custom_layout.*
import kotlinx.coroutines.runBlocking

const val START_ACTION_NORMAL = 0
const val START_ACTION_RESUME_LATEST = 1
const val START_ACTION_LOAD_EP = 2

const val START_VALUE_NORMAL = 0

data class ResultEpisode(
    val headerName: String,
    val name: String?,
    val poster: String?,
    val episode: Int,
    val seasonIndex: Int?,  // this is the "season" index used season names
    val season: Int?, // this is the display
    val data: String,
    val apiName: String,
    val id: Int,
    val index: Int,
    val position: Long, // time in MS
    val duration: Long, // duration in MS
    val rating: Int?,
    val description: String?,
    val isFiller: Boolean?,
    val tvType: TvType,
    val parentId: Int,
)

fun ResultEpisode.getRealPosition(): Long {
    if (duration <= 0) return 0
    val percentage = position * 100 / duration
    if (percentage <= 5 || percentage >= 95) return 0
    return position
}

fun ResultEpisode.getDisplayPosition(): Long {
    if (duration <= 0) return 0
    val percentage = position * 100 / duration
    if (percentage <= 1) return 0
    if (percentage <= 5) return 5 * duration / 100
    if (percentage >= 95) return duration
    return position
}

fun buildResultEpisode(
    headerName: String,
    name: String? = null,
    poster: String? = null,
    episode: Int,
    seasonIndex: Int? = null,
    season: Int? = null,
    data: String,
    apiName: String,
    id: Int,
    index: Int,
    rating: Int? = null,
    description: String? = null,
    isFiller: Boolean? = null,
    tvType: TvType,
    parentId: Int,
): ResultEpisode {
    val posDur = getViewPos(id)
    return ResultEpisode(
        headerName,
        name,
        poster,
        episode,
        seasonIndex,
        season,
        data,
        apiName,
        id,
        index,
        posDur?.position ?: 0,
        posDur?.duration ?: 0,
        rating,
        description,
        isFiller,
        tvType,
        parentId,
    )
}

/** 0f-1f */
fun ResultEpisode.getWatchProgress(): Float {
    return (getDisplayPosition() / 1000).toFloat() / (duration / 1000).toFloat()
}

class ResultFragment : ResultTrailerPlayer() {
    companion object {
        const val URL_BUNDLE = "url"
        const val API_NAME_BUNDLE = "apiName"
        const val SEASON_BUNDLE = "season"
        const val EPISODE_BUNDLE = "episode"
        const val START_ACTION_BUNDLE = "startAction"
        const val START_VALUE_BUNDLE = "startValue"
        const val RESTART_BUNDLE = "restart"
        fun newInstance(
            card: SearchResponse, startAction: Int = 0, startValue: Int? = null
        ): Bundle {
            return Bundle().apply {
                putString(URL_BUNDLE, card.url)
                putString(API_NAME_BUNDLE, card.apiName)
                if (card is DataStoreHelper.ResumeWatchingResult) {
//                    println("CARD::::: $card")
                    if (card.season != null)
                        putInt(SEASON_BUNDLE, card.season)
                    if (card.episode != null)
                        putInt(EPISODE_BUNDLE, card.episode)
                }
                putInt(START_ACTION_BUNDLE, startAction)
                if (startValue != null)
                    putInt(START_VALUE_BUNDLE, startValue)
                putBoolean(RESTART_BUNDLE, true)
            }
        }

        fun newInstance(
            url: String,
            apiName: String,
            startAction: Int = 0,
            startValue: Int = 0
        ): Bundle {
            return Bundle().apply {
                putString(URL_BUNDLE, url)
                putString(API_NAME_BUNDLE, apiName)
                putInt(START_ACTION_BUNDLE, startAction)
                putInt(START_VALUE_BUNDLE, startValue)
                putBoolean(RESTART_BUNDLE, true)
            }
        }

        fun updateUI() {
            updateUIListener?.invoke()
        }

        private var updateUIListener: (() -> Unit)? = null
    }

    private lateinit var viewModel: ResultViewModel2 //by activityViewModels()
    private lateinit var syncModel: SyncViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        viewModel =
            ViewModelProvider(this)[ResultViewModel2::class.java]
        syncModel =
            ViewModelProvider(this)[SyncViewModel::class.java]

        return inflater.inflate(R.layout.fragment_result_swipe, container, false)
    }

    override fun onDestroyView() {
        updateUIListener = null
        (result_episodes?.adapter as EpisodeAdapter?)?.killAdapter()
        //downloadButton?.dispose() //TODO READD
        //somehow this still leaks and I dont know why????
        // todo look at https://github.com/discord/OverlappingPanels/blob/70b4a7cf43c6771873b1e091029d332896d41a1a/sample_app/src/main/java/com/discord/sampleapp/MainActivity.kt
        PanelsChildGestureRegionObserver.Provider.get().removeGestureRegionsUpdateListener(this)
        result_cast_items?.let {
            PanelsChildGestureRegionObserver.Provider.get().unregister(it)
        }
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        activity?.let {
            it.window?.navigationBarColor =
                it.colorFromAttribute(R.attr.primaryBlackBackground)
        }
    }

    /// 0 = LOADING, 1 = ERROR LOADING, 2 = LOADED
    private fun updateVisStatus(state: Int) {
        when (state) {
            0 -> {
                result_bookmark_fab?.isGone = true
                result_loading?.isVisible = true
                result_finish_loading?.isVisible = false
                result_loading_error?.isVisible = false
            }
            1 -> {
                result_bookmark_fab?.isGone = true
                result_loading?.isVisible = false
                result_finish_loading?.isVisible = false
                result_loading_error?.isVisible = true
                result_reload_connection_open_in_browser?.isVisible = true
            }
            2 -> {
                result_bookmark_fab?.isGone = result_bookmark_fab?.context?.isTvSettings() == true
                result_bookmark_fab?.extend()
                if (result_bookmark_button?.context?.isTrueTvSettings() == true) {
                    when {
                        result_play_movie?.isVisible == true -> {
                            result_play_movie?.requestFocus()
                        }
                        result_resume_series_button?.isVisible == true -> {
                            result_resume_series_button?.requestFocus()
                        }
                        else -> {
                            result_bookmark_button?.requestFocus()
                        }
                    }
                }

                result_loading?.isVisible = false
                result_finish_loading?.isVisible = true
                result_loading_error?.isVisible = false
            }
        }
    }

    private fun fromIndexToSeasonText(selection: Int?): String {
        return when (selection) {
            null -> getString(R.string.no_season)
            -2 -> getString(R.string.no_season)
            else -> "${getString(R.string.season)} $selection"
        }
    }

    var startAction: Int? = null
    private var startValue: Int? = null

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
        result_trailer_loading?.isVisible = isSuccess
        result_smallscreen_holder?.isVisible = !isSuccess && !isFullScreenPlayer

        // We don't want the trailer to be focusable if it's not visible
        result_smallscreen_holder?.descendantFocusability = if (isSuccess) {
            ViewGroup.FOCUS_AFTER_DESCENDANTS
        } else {
            ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        result_fullscreen_holder?.isVisible = !isSuccess && isFullScreenPlayer
    }

    private fun setTrailers(trailers: List<ExtractorLink>?) {
        context?.updateHasTrailers()
        if (!LoadResponse.isTrailersEnabled) return
        currentTrailers = trailers?.sortedBy { -it.quality } ?: emptyList()
        loadTrailer()
    }

    private fun setRecommendations(rec: List<SearchResponse>?, validApiName: String?) {
        val isInvalid = rec.isNullOrEmpty()
        result_recommendations?.isGone = isInvalid
        result_recommendations_btt?.isGone = isInvalid
        result_recommendations_btt?.setOnClickListener {
            val nextFocusDown = if (result_overlapping_panels?.getSelectedPanel()?.ordinal == 1) {
                result_overlapping_panels?.openEndPanel()
                R.id.result_recommendations
            } else {
                result_overlapping_panels?.closePanels()
                R.id.result_description
            }

            result_recommendations_btt?.nextFocusDownId = nextFocusDown
            result_search?.nextFocusDownId = nextFocusDown
            result_open_in_browser?.nextFocusDownId = nextFocusDown
            result_share?.nextFocusDownId = nextFocusDown
        }
        result_overlapping_panels?.setEndPanelLockState(if (isInvalid) OverlappingPanelsLayout.LockState.CLOSE else OverlappingPanelsLayout.LockState.UNLOCKED)

        val matchAgainst = validApiName ?: rec?.firstOrNull()?.apiName
        rec?.map { it.apiName }?.distinct()?.let { apiNames ->
            // very dirty selection
            result_recommendations_filter_button?.isVisible = apiNames.size > 1
            result_recommendations_filter_button?.text = matchAgainst
            result_recommendations_filter_button?.setOnClickListener { _ ->
                activity?.showBottomDialog(
                    apiNames,
                    apiNames.indexOf(matchAgainst),
                    getString(R.string.home_change_provider_img_des), false, {}
                ) {
                    setRecommendations(rec, apiNames[it])
                }
            }
        } ?: run {
            result_recommendations_filter_button?.isVisible = false
        }

        result_recommendations?.post {
            rec?.let { list ->
                (result_recommendations?.adapter as SearchAdapter?)?.updateList(list.filter { it.apiName == matchAgainst })
            }
        }
    }

    private fun fixGrid() {
        activity?.getSpanCount()?.let { _ ->
            //result_recommendations?.spanCount = count // this is due to discord not changing size with rotation
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fixGrid()
    }

    private fun updateUI() {
        syncModel.updateUserData()
        viewModel.reloadEpisodes()
    }

    var loadingDialog: Dialog? = null
    var popupDialog: Dialog? = null

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        result_cast_items?.let {
            PanelsChildGestureRegionObserver.Provider.get().register(it)
        }
        result_cast_items?.adapter = ActorAdaptor()
        fixGrid()
        result_recommendations?.spanCount = 3
        result_overlapping_panels?.setStartPanelLockState(OverlappingPanelsLayout.LockState.CLOSE)
        result_overlapping_panels?.setEndPanelLockState(OverlappingPanelsLayout.LockState.CLOSE)

        player_open_source?.setOnClickListener {
            currentTrailers.getOrNull(currentTrailerIndex)?.let {
                context?.openBrowser(it.url)
            }
        }

        updateUIListener = ::updateUI

        val restart = arguments?.getBoolean(RESTART_BUNDLE) ?: false
        if (restart) {
            arguments?.putBoolean(RESTART_BUNDLE, false)
        }

        activity?.window?.decorView?.clearFocus()
        hideKeyboard()
        context?.updateHasTrailers()
        activity?.loadCache()

        activity?.fixPaddingStatusbar(result_top_bar)
        //activity?.fixPaddingStatusbar(result_barstatus)

        /* val backParameter = result_back.layoutParams as FrameLayout.LayoutParams
         backParameter.setMargins(
             backParameter.leftMargin,
             backParameter.topMargin + requireContext().getStatusBarHeight(),
             backParameter.rightMargin,
             backParameter.bottomMargin
         )
         result_back.layoutParams = backParameter*/

        // activity?.fixPaddingStatusbar(result_toolbar)

        val url = arguments?.getString(URL_BUNDLE)
        val apiName = arguments?.getString(API_NAME_BUNDLE) ?: return
        startAction = arguments?.getInt(START_ACTION_BUNDLE) ?: START_ACTION_NORMAL
        startValue = arguments?.getInt(START_VALUE_BUNDLE)
        val resumeEpisode = arguments?.getInt(EPISODE_BUNDLE)
        val resumeSeason = arguments?.getInt(SEASON_BUNDLE)
        syncModel.addFromUrl(url)

        val api = getApiFromName(apiName)
        if (media_route_button != null) {
            val chromecastSupport = api.hasChromecastSupport
            media_route_button?.alpha = if (chromecastSupport) 1f else 0.3f
            if (!chromecastSupport) {
                media_route_button?.setOnClickListener {
                    showToast(activity, R.string.no_chromecast_support_toast, Toast.LENGTH_LONG)
                }
            }
            activity?.let { act ->
                if (act.isCastApiAvailable()) {
                    try {
                        CastButtonFactory.setUpMediaRouteButton(act, media_route_button)
                        val castContext = CastContext.getSharedInstance(act.applicationContext)
                        media_route_button?.isGone =
                            castContext.castState == CastState.NO_DEVICES_AVAILABLE
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

        result_scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val dy = scrollY - oldScrollY
            if (dy > 0) { //check for scroll down
                result_bookmark_fab?.shrink()
            } else if (dy < -5) {
                result_bookmark_fab?.extend()
            }
            if (!isFullScreenPlayer && player.getIsPlaying()) {
                if (scrollY > (player_background?.height ?: scrollY)) {
                    player.handleEvent(CSPlayerEvent.Pause)
                }
            }
            //result_poster_blur_holder?.translationY = -scrollY.toFloat()
        })

        result_back.setOnClickListener {
            activity?.popCurrentPage()
        }

        result_episodes.adapter =
            EpisodeAdapter(
                ArrayList(),
                api.hasDownloadSupport,
                { episodeClick ->
                    viewModel.handleAction(activity, episodeClick)
                },
                { downloadClickEvent ->
                    handleDownloadClick(activity, downloadClickEvent)
                }
            )

        result_bookmark_button.setOnClickListener {
            it.popupMenuNoIcons(
                items = WatchType.values()
                    .map { watchType -> Pair(watchType.internalId, watchType.stringRes) },
                //.map { watchType -> Triple(watchType.internalId, watchType.iconRes, watchType.stringRes) },
            ) {
                viewModel.updateWatchStatus(WatchType.fromInternalId(this.itemId))
            }
        }

        observe(viewModel.watchStatus) { watchType ->
            result_bookmark_button?.text = getString(watchType.stringRes)
            result_bookmark_fab?.text = getString(watchType.stringRes)

            if (watchType == WatchType.NONE) {
                result_bookmark_fab?.context?.colorFromAttribute(R.attr.white)
            } else {
                result_bookmark_fab?.context?.colorFromAttribute(R.attr.colorPrimary)
            }?.let {
                val colorState = ColorStateList.valueOf(it)
                result_bookmark_fab?.iconTint = colorState
                result_bookmark_fab?.setTextColor(colorState)
            }

            result_bookmark_fab?.setOnClickListener { fab ->
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

        /**
         * Sets next focus to allow navigation up and down between 2 views
         * if either of them is null nothing happens.
         **/
        fun setFocusUpAndDown(upper: View?, down: View?) {
            if (upper == null || down == null) return
            upper.nextFocusDownId = down.id
            down.nextFocusUpId = upper.id
        }

        // This is to band-aid FireTV navigation
        result_season_button?.isFocusableInTouchMode = context?.isTvSettings() == true
        result_episode_select?.isFocusableInTouchMode = context?.isTvSettings() == true
        result_dub_select?.isFocusableInTouchMode = context?.isTvSettings() == true



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
            result_sync_check?.choiceMode = AbsListView.CHOICE_MODE_SINGLE
            result_sync_check?.adapter = arrayAdapter
            UIHelper.setListViewHeightBasedOnItems(result_sync_check)

            result_sync_check?.setOnItemClickListener { _, _, which, _ ->
                syncModel.setStatus(which - 1)
            }

            result_sync_rating?.addOnChangeListener { _, value, _ ->
                syncModel.setScore(value.toInt())
            }

            result_sync_add_episode?.setOnClickListener {
                syncModel.setEpisodesDelta(1)
            }

            result_sync_sub_episode?.setOnClickListener {
                syncModel.setEpisodesDelta(-1)
            }

            result_sync_current_episodes?.doOnTextChanged { text, _, before, count ->
                if (count == before) return@doOnTextChanged
                text?.toString()?.toIntOrNull()?.let { ep ->
                    syncModel.setEpisodes(ep)
                }
            }
        }

        result_mini_sync?.adapter = ImageAdapter(
            R.layout.result_mini_image,
            nextFocusDown = R.id.result_sync_set_score,
            clickCallback = { action ->
                if (action == IMAGE_CLICK || action == IMAGE_LONG_CLICK) {
                    if (result_overlapping_panels?.getSelectedPanel()?.ordinal == 1) {
                        result_overlapping_panels?.openStartPanel()
                    } else {
                        result_overlapping_panels?.closePanels()
                    }
                }
            })

        observe(syncModel.synced) { list ->
            result_sync_names?.text =
                list.filter { it.isSynced && it.hasAccount }.joinToString { it.name }

            val newList = list.filter { it.isSynced && it.hasAccount }

            result_mini_sync?.isVisible = newList.isNotEmpty()
            (result_mini_sync?.adapter as? ImageAdapter?)?.updateList(newList.mapNotNull { it.icon })
        }

        var currentSyncProgress = 0

        fun setSyncMaxEpisodes(totalEpisodes: Int?) {
            result_sync_episodes?.max = (totalEpisodes ?: 0) * 1000

            normalSafeApiCall {
                val ctx = result_sync_max_episodes?.context
                result_sync_max_episodes?.text =
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
                    result_sync_episodes?.progress = currentSyncProgress * 1000
                    setSyncMaxEpisodes(d.totalEpisodes)

                    viewModel.setMeta(d, syncModel.getSyncs())
                }
                is Resource.Loading -> {
                    result_sync_max_episodes?.text =
                        result_sync_max_episodes?.context?.getString(R.string.sync_total_episodes_none)
                }
                else -> {}
            }
        }

        observe(syncModel.userData) { status ->
            var closed = false
            when (status) {
                is Resource.Failure -> {
                    result_sync_loading_shimmer?.stopShimmer()
                    result_sync_loading_shimmer?.isVisible = false
                    result_sync_holder?.isVisible = false
                    closed = true
                }
                is Resource.Loading -> {
                    result_sync_loading_shimmer?.startShimmer()
                    result_sync_loading_shimmer?.isVisible = true
                    result_sync_holder?.isVisible = false
                }
                is Resource.Success -> {
                    result_sync_loading_shimmer?.stopShimmer()
                    result_sync_loading_shimmer?.isVisible = false
                    result_sync_holder?.isVisible = true

                    val d = status.value
                    result_sync_rating?.value = d.score?.toFloat() ?: 0.0f
                    result_sync_check?.setItemChecked(d.status + 1, true)
                    val watchedEpisodes = d.watchedEpisodes ?: 0
                    currentSyncProgress = watchedEpisodes

                    d.maxEpisodes?.let {
                        // don't directly call it because we don't want to override metadata observe
                        setSyncMaxEpisodes(it)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        result_sync_episodes?.setProgress(watchedEpisodes * 1000, true)
                    } else {
                        result_sync_episodes?.progress = watchedEpisodes * 1000
                    }
                    result_sync_current_episodes?.text =
                        Editable.Factory.getInstance()?.newEditable(watchedEpisodes.toString())
                    normalSafeApiCall { // format might fail
                        context?.getString(R.string.sync_score_format)?.format(d.score ?: 0)?.let {
                            result_sync_score_text?.text = it
                        }
                    }
                }
                null -> {
                    closed = false
                }
            }
            result_overlapping_panels?.setStartPanelLockState(if (closed) OverlappingPanelsLayout.LockState.CLOSE else OverlappingPanelsLayout.LockState.UNLOCKED)
        }

        /*
        observe(viewModel.episodes) { episodeList ->
            lateFixDownloadButton(episodeList.size <= 1) // movies can have multible parts but still be *movies* this will fix this
            var isSeriesVisible = false
            var isProgressVisible = false
            DataStoreHelper.getLastWatched(currentId)?.let { resume ->
                if (currentIsMovie == false) {
                    isSeriesVisible = true

                    result_resume_series_button?.setOnClickListener {
                        episodeList.firstOrNull { it.id == resume.episodeId }?.let {
                            handleAction(EpisodeClickEvent(ACTION_PLAY_EPISODE_IN_PLAYER, it))
                        }
                    }

                    result_resume_series_title?.text =
                        if (resume.season == null)
                            "${getString(R.string.episode)} ${resume.episode}"
                        else
                            " \"${getString(R.string.season_short)}${resume.season}:${getString(R.string.episode_short)}${resume.episode}\""
                }

                getViewPos(resume.episodeId)?.let { viewPos ->
                    if (viewPos.position > 30_000L || currentIsMovie == false) { // first 30s will not show for movies
                        result_resume_series_progress?.apply {
                            max = (viewPos.duration / 1000).toInt()
                            progress = (viewPos.position / 1000).toInt()
                        }
                        result_resume_series_progress_text?.text =
                            getString(R.string.resume_time_left).format((viewPos.duration - viewPos.position) / (60_000))
                        isProgressVisible = true
                    } else {
                        isProgressVisible = false
                        isSeriesVisible = false
                    }
                } ?: run {
                    isProgressVisible = false
                    isSeriesVisible = false
                }
            }

            result_series_parent?.isVisible = isSeriesVisible
            if (isSeriesVisible && activity?.currentFocus?.id == R.id.result_back && context?.isTrueTvSettings() == true) {
                result_resume_series_button?.requestFocus()
            }

            if (isSeriesVisible) {
                val down = when {
                    result_season_button?.isVisible == true -> result_season_button
                    result_episode_select?.isVisible == true -> result_episode_select
                    result_dub_select?.isVisible == true -> result_dub_select
                    else -> null
                }
                setFocusUpAndDown(result_resume_series_button, down)
                setFocusUpAndDown(result_bookmark_button, result_resume_series_button)
            }

            result_resume_progress_holder?.isVisible = isProgressVisible
            context?.getString(
                when {
                    currentType?.isLiveStream() == true -> R.string.play_livestream_button
                    isProgressVisible -> R.string.resume
                    else -> R.string.play_movie_button
                }
            )?.let {
                result_play_movie?.text = it
            }
            //println("startAction = $startAction")

            when (startAction) {
                START_ACTION_RESUME_LATEST -> {
                    for (ep in episodeList) {
                        //println("WATCH STATUS::: S${ep.season} E ${ep.episode} - ${ep.getWatchProgress()}")
                        if (ep.getWatchProgress() > 0.90f) { // watched too much
                            continue
                        }
                        handleAction(EpisodeClickEvent(ACTION_PLAY_EPISODE_IN_PLAYER, ep))
                        break
                    }
                }
                START_ACTION_LOAD_EP -> {
                    if (episodeList.size == 1) {
                        handleAction(
                            EpisodeClickEvent(
                                ACTION_PLAY_EPISODE_IN_PLAYER,
                                episodeList.first()
                            )
                        )
                    } else {
                        var found = false
                        for (ep in episodeList) {
                            if (ep.id == startValue) { // watched too much
                                //println("WATCH STATUS::: START_ACTION_LOAD_EP S${ep.season} E ${ep.episode} - ${ep.getWatchProgress()}")
                                handleAction(EpisodeClickEvent(ACTION_PLAY_EPISODE_IN_PLAYER, ep))
                                found = true
                                break
                            }
                        }
                        if (!found)
                            for (ep in episodeList) {
                                if (ep.episode == resumeEpisode && ep.season == resumeSeason) {
                                    //println("WATCH STATUS::: START_ACTION_LOAD_EP S${ep.season} E ${ep.episode} - ${ep.getWatchProgress()}")
                                    handleAction(
                                        EpisodeClickEvent(
                                            ACTION_PLAY_EPISODE_IN_PLAYER,
                                            ep
                                        )
                                    )
                                    break
                                }
                            }
                    }

                }
                else -> Unit
            }
            arguments?.remove("startValue")
            arguments?.remove("startAction")
            startAction = null
            startValue = null
        }
*/
        observe(viewModel.episodes) { episodes ->
            when (episodes) {
                is ResourceSome.None -> {
                    result_episode_loading?.isVisible = false
                    result_episodes?.isVisible = false
                }
                is ResourceSome.Loading -> {
                    result_episode_loading?.isVisible = true
                    result_episodes?.isVisible = false
                }
                is ResourceSome.Success -> {
                    result_episodes?.isVisible = true
                    result_episode_loading?.isVisible = false
                    (result_episodes?.adapter as? EpisodeAdapter?)?.updateList(episodes.value)
                }
            }
        }

        observe(viewModel.selectedSeason) { text ->
            result_season_button.setText(text)

            // If the season button is visible the result season button will be next focus down
            if (result_season_button?.isVisible == true)
                if (result_series_parent?.isVisible == true)
                    setFocusUpAndDown(result_resume_series_button, result_season_button)
                else
                    setFocusUpAndDown(result_bookmark_button, result_season_button)
        }

        observe(viewModel.selectedDubStatus) { status ->
            result_dub_select?.setText(status)

            if (result_dub_select?.isVisible == true)
                if (result_season_button?.isVisible != true && result_episode_select?.isVisible != true) {
                    if (result_series_parent?.isVisible == true)
                        setFocusUpAndDown(result_resume_series_button, result_dub_select)
                    else
                        setFocusUpAndDown(result_bookmark_button, result_dub_select)
                }
        }

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

            //showBottomDialogInstant
        }

        observe(viewModel.loadedLinks) { load ->

            when (load) {
                is Some.Success -> {
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
                is Some.None -> {
                    loadingDialog?.dismissSafe(activity)
                    loadingDialog = null
                }
            }
        }

        observe(viewModel.selectedRange) { range ->
            result_episode_select.setText(range)

            // If Season button is invisible then the bookmark button next focus is episode select
            if (result_episode_select?.isVisible == true)
                if (result_season_button?.isVisible != true) {
                    if (result_series_parent?.isVisible == true)
                        setFocusUpAndDown(result_resume_series_button, result_episode_select)
                    else
                        setFocusUpAndDown(result_bookmark_button, result_episode_select)
                }
        }

//        val preferDub = context?.getApiDubstatusSettings()?.all { it == DubStatus.Dubbed } == true

        observe(viewModel.dubSubSelections) { range ->
            result_dub_select.setOnClickListener { view ->
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
            result_episode_select?.setOnClickListener { view ->
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
            result_season_button?.setOnClickListener { view ->
                view?.context?.let { ctx ->
                    val names = seasonList
                        .mapNotNull { (text, r) ->
                            r to (text?.asStringNull(ctx) ?: return@mapNotNull null)
                        }

                    view.popupMenuNoIconsAndNoStringRes(names.mapIndexed { index, (_, name) ->
                        index to name
                    }) {
                        viewModel.changeSeason(names[itemId].first)
                    }
                }
            }
        }

        result_cast_items?.setOnFocusChangeListener { _, hasFocus ->
            // Always escape focus
            if (hasFocus) result_bookmark_button?.requestFocus()
        }

        result_sync_set_score?.setOnClickListener {
            syncModel.publishUserData()
        }

        observe(viewModel.episodesCountText) { count ->
            result_episodes_text.setText(count)
        }

        observe(viewModel.trailers) { trailers ->
            setTrailers(trailers.flatMap { it.mirros }) // I dont care about subtitles yet!
        }

        observe(viewModel.recommendations) { recommendations ->
            setRecommendations(recommendations, null)
        }

        observe(viewModel.movie) { data ->
            when (data) {
                is ResourceSome.Success -> {
                    data.value.let { (text, ep) ->
                        result_play_movie.setText(text)
                        result_play_movie?.setOnClickListener {
                            viewModel.handleAction(
                                activity,
                                EpisodeClickEvent(ACTION_CLICK_DEFAULT, ep)
                            )
                        }
                        result_play_movie?.setOnLongClickListener {
                            viewModel.handleAction(
                                activity,
                                EpisodeClickEvent(ACTION_SHOW_OPTIONS, ep)
                            )
                            return@setOnLongClickListener true
                        }
                    }
                }
                else -> {
                    result_play_movie?.isVisible = false

                }
            }
        }

        observe(viewModel.page) { data ->
            when (data) {
                is Resource.Success -> {
                    val d = data.value

                    updateVisStatus(2)

                    result_vpn.setText(d.vpnText)
                    result_info.setText(d.metaText)
                    result_no_episodes.setText(d.noEpisodesFoundText)
                    result_title.setText(d.titleText)
                    result_meta_site.setText(d.apiName)
                    result_meta_type.setText(d.typeText)
                    result_meta_year.setText(d.yearText)
                    result_meta_duration.setText(d.durationText)
                    result_meta_rating.setText(d.ratingText)
                    result_cast_text.setText(d.actorsText)
                    result_next_airing.setText(d.nextAiringEpisode)
                    result_next_airing_time.setText(d.nextAiringDate)
                    result_poster.setImage(d.posterImage)

                    if (d.posterImage != null && context?.isTrueTvSettings() == false)
                        result_poster_holder?.setOnClickListener {
                            try {
                                context?.let { ctx ->
                                    runBlocking {
                                        val sourceBuilder = AlertDialog.Builder(ctx)
                                        sourceBuilder.setView(R.layout.result_poster)

                                        val sourceDialog = sourceBuilder.create()
                                        sourceDialog.show()

                                        sourceDialog.findViewById<ImageView?>(R.id.imgPoster)
                                            ?.apply {
                                                setImage(d.posterImage)
                                                setOnClickListener {
                                                    sourceDialog.dismissSafe()
                                                }
                                            }
                                    }
                                }
                            } catch (e: Exception) {
                                logError(e)
                            }
                        }


                    result_cast_items?.isVisible = d.actors != null
                    (result_cast_items?.adapter as ActorAdaptor?)?.apply {
                        updateList(d.actors ?: emptyList())
                    }

                    result_open_in_browser?.isGone = d.url.isBlank()
                    result_open_in_browser?.setOnClickListener {
                        val i = Intent(ACTION_VIEW)
                        i.data = Uri.parse(d.url)
                        try {
                            startActivity(i)
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }

                    result_search?.setOnClickListener {
                        QuickSearchFragment.pushSearch(activity, d.title)
                    }

                    result_share?.setOnClickListener {
                        try {
                            val i = Intent(ACTION_SEND)
                            i.type = "text/plain"
                            i.putExtra(EXTRA_SUBJECT, d.title)
                            i.putExtra(EXTRA_TEXT, d.url)
                            startActivity(createChooser(i, d.title))
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }

                    if (syncModel.addSyncs(d.syncData)) {
                        syncModel.updateMetaAndUser()
                        syncModel.updateSynced()
                    } else {
                        syncModel.addFromUrl(d.url)
                    }

                    result_description.setTextHtml(d.plotText)
                    result_description?.setOnClickListener { view ->
                        view.context?.let { ctx ->
                            val builder: AlertDialog.Builder =
                                AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                            builder.setMessage(d.plotText.asString(ctx).html())
                                .setTitle(d.plotHeaderText.asString(ctx))
                                .show()
                        }
                    }


                    result_tag?.removeAllViews()

                    d.comingSoon.let { soon ->
                        result_coming_soon?.isVisible = soon
                        result_data_holder?.isGone = soon
                    }

                    val tags = d.tags
                    result_tag_holder?.isVisible = tags.isNotEmpty()
                    if (tags.isNotEmpty()) {
                        //result_tag_holder?.visibility = VISIBLE
                        val isOnTv = context?.isTrueTvSettings() == true
                        for ((index, tag) in tags.withIndex()) {
                            val viewBtt = layoutInflater.inflate(R.layout.result_tag, null)
                            val btt = viewBtt.findViewById<MaterialButton>(R.id.result_tag_card)
                            btt.text = tag
                            btt.isFocusable = !isOnTv
                            btt.isClickable = !isOnTv
                            result_tag?.addView(viewBtt, index)
                        }
                    }

                    //TODO FIX
                    /*
                    if (d.typeText.isMovieType()) {
                        val hasDownloadSupport = api.hasDownloadSupport
                        lateFixDownloadButton(true)

                        result_play_movie?.setOnClickListener {
                            val card =
                                currentEpisodes?.firstOrNull() ?: return@setOnClickListener
                            handleAction(EpisodeClickEvent(ACTION_CLICK_DEFAULT, card))
                        }

                        result_play_movie?.setOnLongClickListener {
                            val card = currentEpisodes?.firstOrNull()
                                ?: return@setOnLongClickListener true
                            handleAction(EpisodeClickEvent(ACTION_SHOW_OPTIONS, card))
                            return@setOnLongClickListener true
                        }

                        result_download_movie?.setOnLongClickListener {
                            val card = currentEpisodes?.firstOrNull()
                                ?: return@setOnLongClickListener true
                            handleAction(EpisodeClickEvent(ACTION_SHOW_OPTIONS, card))
                            return@setOnLongClickListener true
                        }

                        result_movie_progress_downloaded_holder?.isVisible = hasDownloadSupport
                        if (hasDownloadSupport) {
                            val localId = d.getId()

                            val file =
                                VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(
                                    requireContext(),
                                    localId
                                )
                            downloadButton?.dispose()
                            downloadButton = EasyDownloadButton()
                            downloadButton?.setUpMoreButton(
                                file?.fileLength,
                                file?.totalBytes,
                                result_movie_progress_downloaded,
                                result_movie_download_icon,
                                result_movie_download_text,
                                result_movie_download_text_precentage,
                                result_download_movie,
                                true,
                                VideoDownloadHelper.DownloadEpisodeCached(
                                    d.name,
                                    d.posterUrl,
                                    0,
                                    null,
                                    localId,
                                    localId,
                                    d.rating,
                                    d.plot,
                                    System.currentTimeMillis(),
                                ),
                                ::handleDownloadButton
                            )

                            result_download_movie?.setOnLongClickListener {
                                val card =
                                    currentEpisodes?.firstOrNull()
                                        ?: return@setOnLongClickListener false
                                handleAction(EpisodeClickEvent(ACTION_DOWNLOAD_MIRROR, card))
                                return@setOnLongClickListener true
                            }

                            /*downloadButton?.setUpMaterialButton(
                                file?.fileLength,
                                file?.totalBytes,
                                result_movie_progress_downloaded,
                                result_download_movie,
                                null, //result_movie_text_progress
                                VideoDownloadHelper.DownloadEpisodeCached(
                                    d.name,
                                    d.posterUrl,
                                    0,
                                    null,
                                    localId,
                                    localId,
                                    d.rating,
                                    d.plot,
                                    System.currentTimeMillis(),
                                )
                            ) { downloadClickEvent ->
                                if (downloadClickEvent.action == DOWNLOAD_ACTION_DOWNLOAD) {
                                    currentEpisodes?.firstOrNull()?.let { episode ->
                                        handleAction(
                                            EpisodeClickEvent(
                                                ACTION_DOWNLOAD_EPISODE,
                                                ResultEpisode(
                                                    d.name,
                                                    d.name,
                                                    null,
                                                    0,
                                                    null,
                                                    episode.data,
                                                    d.apiName,
                                                    localId,
                                                    0,
                                                    0L,
                                                    0L,
                                                    null,
                                                    null,
                                                    null,
                                                    d.type,
                                                    localId,
                                                )
                                            )
                                        )
                                    }
                                } else {
                                    handleDownloadClick(
                                        activity,
                                        currentHeaderName,
                                        downloadClickEvent
                                    )
                                }
                            }*/
                        }
                    } else {
                        lateFixDownloadButton(false)
                    }
                    */
                }
                is Resource.Failure -> {
                    result_error_text.text = url?.plus("\n") + data.errorString
                    updateVisStatus(1)
                }
                is Resource.Loading -> {
                    updateVisStatus(0)
                }
            }
        }

        result_recommendations?.adapter =
            SearchAdapter(
                ArrayList(),
                result_recommendations,
            ) { callback ->
                SearchHelper.handleSearchClickCallback(activity, callback)
            }


        context?.let { ctx ->
            result_bookmark_button?.isVisible = ctx.isTvSettings()

            val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)
            val showFillers =
                settingsManager.getBoolean(ctx.getString(R.string.show_fillers_key), false)

            Kitsu.isEnabled =
                settingsManager.getBoolean(ctx.getString(R.string.show_kitsu_posters_key), true)

            if (url != null) {
                result_reload_connectionerror.setOnClickListener {
                    viewModel.load(url, apiName, showFillers, DubStatus.Dubbed, 0, 0) //TODO FIX
                }

                result_reload_connection_open_in_browser?.setOnClickListener {
                    val i = Intent(ACTION_VIEW)
                    i.data = Uri.parse(url)
                    try {
                        startActivity(i)
                    } catch (e: Exception) {
                        logError(e)
                    }
                }

                result_open_in_browser?.setOnClickListener {
                    val i = Intent(ACTION_VIEW)
                    i.data = Uri.parse(url)
                    try {
                        startActivity(i)
                    } catch (e: Exception) {
                        logError(e)
                    }
                }

                // bloats the navigation on tv
                if (context?.isTrueTvSettings() == false) {
                    result_meta_site?.setOnClickListener {
                        it.context?.openBrowser(url)
                    }
                    result_meta_site?.isFocusable = true
                } else {
                    result_meta_site?.isFocusable = false
                }

                if (restart || !viewModel.hasLoaded()) {
                    //viewModel.clear()
                    viewModel.load(url, apiName, showFillers, DubStatus.Dubbed, 0, 0) //TODO FIX
                }
            }
        }

        PanelsChildGestureRegionObserver.Provider.get().addGestureRegionsUpdateListener(this)
    }

    override fun onPause() {
        super.onPause()
        PanelsChildGestureRegionObserver.Provider.get().addGestureRegionsUpdateListener(this)
    }

    override fun onGestureRegionsUpdate(gestureRegions: List<Rect>) {
        result_overlapping_panels?.setChildGestureRegions(gestureRegions)
    }
}
