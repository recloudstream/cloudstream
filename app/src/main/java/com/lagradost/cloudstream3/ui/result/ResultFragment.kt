package com.lagradost.cloudstream3.ui.result

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.Intent.*
import android.content.res.ColorStateList
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
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.discord.panels.OverlappingPanelsLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.lagradost.cloudstream3.APIHolder.getApiDubstatusSettings
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.APIHolder.updateHasTrailers
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.*
import com.lagradost.cloudstream3.services.SubscriptionWorkManager
import com.lagradost.cloudstream3.syncproviders.providers.Kitsu
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.download.EasyDownloadButton
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.result.EpisodeAdapter.Companion.getPlayerAction
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.getNameFull
import com.lagradost.cloudstream3.utils.AppUtils.html
import com.lagradost.cloudstream3.utils.AppUtils.loadCache
import com.lagradost.cloudstream3.utils.AppUtils.openBrowser
import com.lagradost.cloudstream3.utils.Coroutines.ioWorkSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStoreHelper.getVideoWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import kotlinx.android.synthetic.main.fragment_result.*
import kotlinx.android.synthetic.main.fragment_result.result_cast_items
import kotlinx.android.synthetic.main.fragment_result.result_cast_text
import kotlinx.android.synthetic.main.fragment_result.result_coming_soon
import kotlinx.android.synthetic.main.fragment_result.result_data_holder
import kotlinx.android.synthetic.main.fragment_result.result_description
import kotlinx.android.synthetic.main.fragment_result.result_download_movie
import kotlinx.android.synthetic.main.fragment_result.result_episode_loading
import kotlinx.android.synthetic.main.fragment_result.result_episodes
import kotlinx.android.synthetic.main.fragment_result.result_error_text
import kotlinx.android.synthetic.main.fragment_result.result_finish_loading
import kotlinx.android.synthetic.main.fragment_result.result_info
import kotlinx.android.synthetic.main.fragment_result.result_loading
import kotlinx.android.synthetic.main.fragment_result.result_loading_error
import kotlinx.android.synthetic.main.fragment_result.result_meta_duration
import kotlinx.android.synthetic.main.fragment_result.result_meta_rating
import kotlinx.android.synthetic.main.fragment_result.result_meta_site
import kotlinx.android.synthetic.main.fragment_result.result_meta_type
import kotlinx.android.synthetic.main.fragment_result.result_meta_year
import kotlinx.android.synthetic.main.fragment_result.result_movie_download_icon
import kotlinx.android.synthetic.main.fragment_result.result_movie_download_text
import kotlinx.android.synthetic.main.fragment_result.result_movie_download_text_precentage
import kotlinx.android.synthetic.main.fragment_result.result_movie_progress_downloaded
import kotlinx.android.synthetic.main.fragment_result.result_movie_progress_downloaded_holder
import kotlinx.android.synthetic.main.fragment_result.result_next_airing
import kotlinx.android.synthetic.main.fragment_result.result_next_airing_time
import kotlinx.android.synthetic.main.fragment_result.result_no_episodes
import kotlinx.android.synthetic.main.fragment_result.result_play_movie
import kotlinx.android.synthetic.main.fragment_result.result_poster
import kotlinx.android.synthetic.main.fragment_result.result_poster_holder
import kotlinx.android.synthetic.main.fragment_result.result_reload_connection_open_in_browser
import kotlinx.android.synthetic.main.fragment_result.result_reload_connectionerror
import kotlinx.android.synthetic.main.fragment_result.result_resume_parent
import kotlinx.android.synthetic.main.fragment_result.result_resume_progress_holder
import kotlinx.android.synthetic.main.fragment_result.result_resume_series_progress
import kotlinx.android.synthetic.main.fragment_result.result_resume_series_progress_text
import kotlinx.android.synthetic.main.fragment_result.result_resume_series_title
import kotlinx.android.synthetic.main.fragment_result.result_tag
import kotlinx.android.synthetic.main.fragment_result.result_tag_holder
import kotlinx.android.synthetic.main.fragment_result.result_title
import kotlinx.android.synthetic.main.fragment_result.result_vpn
import kotlinx.android.synthetic.main.fragment_result_swipe.*
import kotlinx.android.synthetic.main.fragment_result_tv.*
import kotlinx.android.synthetic.main.result_sync.*
import kotlinx.android.synthetic.main.trailer_custom_layout.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

const val START_ACTION_RESUME_LATEST = 1
const val START_ACTION_LOAD_EP = 2

/**
 * Future proofed way to mark episodes as watched
 **/
enum class VideoWatchState {
    /** Default value when no key is set */
    None,
    Watched
}

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
    /**
     * Conveys if the episode itself is marked as watched
     **/
    val videoWatchState: VideoWatchState
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
    val videoWatchState = getVideoWatchState(id) ?: VideoWatchState.None
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
        videoWatchState
    )
}

/** 0f-1f */
fun ResultEpisode.getWatchProgress(): Float {
    return (getDisplayPosition() / 1000).toFloat() / (duration / 1000).toFloat()
}

open class ResultFragment : ResultTrailerPlayer() {
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

    open fun setTrailers(trailers: List<ExtractorLink>?) {}

    protected lateinit var viewModel: ResultViewModel2 //by activityViewModels()
    protected lateinit var syncModel: SyncViewModel
    protected open val resultLayout = R.layout.fragment_result_swipe

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        viewModel =
            ViewModelProvider(this)[ResultViewModel2::class.java]
        syncModel =
            ViewModelProvider(this)[SyncViewModel::class.java]

        return inflater.inflate(resultLayout, container, false)
    }

    private var downloadButton: EasyDownloadButton? = null
    override fun onDestroyView() {
        updateUIListener = null
        (result_episodes?.adapter as? EpisodeAdapter)?.killAdapter()
        downloadButton?.dispose()

        super.onDestroyView()
    }

    override fun onResume() {
        afterPluginsLoadedEvent += ::reloadViewModel
        super.onResume()
        activity?.let {
            it.window?.navigationBarColor =
                it.colorFromAttribute(R.attr.primaryBlackBackground)
        }
    }

    override fun onDestroy() {
        afterPluginsLoadedEvent -= ::reloadViewModel
        super.onDestroy()
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
                result_bookmark_fab?.isGone = isTrueTvSettings()
                result_bookmark_fab?.extend()
                //if (result_bookmark_button?.context?.isTrueTvSettings() == true) {
                //    when {
                //        result_play_movie?.isVisible == true -> {
                //            result_play_movie?.requestFocus()
                //        }
                //        result_resume_series_button?.isVisible == true -> {
                //            result_resume_series_button?.requestFocus()
                //        }
                //        else -> {
                //            result_bookmark_button?.requestFocus()
                //        }
                //    }
                //}

                result_loading?.isVisible = false
                result_finish_loading?.isVisible = true
                result_loading_error?.isVisible = false
            }
        }
    }

    open fun setRecommendations(rec: List<SearchResponse>?, validApiName: String?) {

    }

    private fun updateUI() {
        syncModel.updateUserData()
        viewModel.reloadEpisodes()
    }

    open fun updateMovie(data: ResourceSome<Pair<UiText, ResultEpisode>>) {
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

                    main {
                        val file =
                            ioWorkSafe {
                                context?.let {
                                    VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(
                                        it,
                                        ep.id
                                    )
                                }
                            }

                        downloadButton?.dispose()
                        downloadButton = EasyDownloadButton()
                        downloadButton?.setUpMoreButton(
                            file?.fileLength,
                            file?.totalBytes,
                            result_movie_progress_downloaded ?: return@main,
                            result_movie_download_icon ?: return@main,
                            result_movie_download_text ?: return@main,
                            result_movie_download_text_precentage ?: return@main,
                            result_download_movie ?: return@main,
                            true,
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
                            )
                        ) { click ->
                            when (click.action) {
                                DOWNLOAD_ACTION_DOWNLOAD -> {
                                    viewModel.handleAction(
                                        activity,
                                        EpisodeClickEvent(ACTION_DOWNLOAD_EPISODE, ep)
                                    )
                                }
                                else -> handleDownloadClick(activity, click)
                            }
                        }
                        result_movie_progress_downloaded_holder?.isVisible = true
                    }
                }
            }
            else -> {
                result_movie_progress_downloaded_holder?.isVisible = false
                result_play_movie?.isVisible = false
            }
        }
    }

    open fun updateEpisodes(episodes: ResourceSome<List<ResultEpisode>>) {
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

                // Do not use this.isTv, that is the player
                val isTv = isTvSettings()
                val hasEpisodes =
                    !(result_episodes?.adapter as? EpisodeAdapter?)?.cardList.isNullOrEmpty()

                if (isTv && hasEpisodes) {
                    // Make it impossible to focus anywhere else!
                    temporary_no_focus?.isFocusable = true
                    temporary_no_focus?.requestFocus()
                }

                (result_episodes?.adapter as? EpisodeAdapter)?.updateList(episodes.value)

                if (isTv && hasEpisodes) main {
                    delay(500)
                    temporary_no_focus?.isFocusable = false
                    // This might make some people sad as it changes the focus when leaving an episode :(
                    result_episodes?.requestFocus()
                }
            }
        }
    }

    data class StoredData(
        val url: String?,
        val apiName: String,
        val showFillers: Boolean,
        val dubStatus: DubStatus,
        val start: AutoResume?,
        val playerAction: Int
    )

    private fun getStoredData(context: Context): StoredData? {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
        val url = arguments?.getString(URL_BUNDLE)
        val apiName = arguments?.getString(API_NAME_BUNDLE) ?: return null
        val showFillers =
            settingsManager.getBoolean(context.getString(R.string.show_fillers_key), false)
        val dubStatus = if (context.getApiDubstatusSettings()
                .contains(DubStatus.Dubbed)
        ) DubStatus.Dubbed else DubStatus.Subbed
        val startAction = arguments?.getInt(START_ACTION_BUNDLE)

        val playerAction = getPlayerAction(context)

        val start = startAction?.let { action ->
            val startValue = arguments?.getInt(START_VALUE_BUNDLE)
            val resumeEpisode = arguments?.getInt(EPISODE_BUNDLE)
            val resumeSeason = arguments?.getInt(SEASON_BUNDLE)

            arguments?.remove(START_VALUE_BUNDLE)
            arguments?.remove(START_ACTION_BUNDLE)
            AutoResume(
                startAction = action,
                id = startValue,
                episode = resumeEpisode,
                season = resumeSeason
            )
        }
        return StoredData(url, apiName, showFillers, dubStatus, start, playerAction)
    }

    private fun reloadViewModel(forceReload: Boolean) {
        if (!viewModel.hasLoaded() || forceReload) {
            val storedData = getStoredData(activity ?: context ?: return) ?: return

            viewModel.load(
                activity,
                storedData.url ?: return,
                storedData.apiName,
                storedData.showFillers,
                storedData.dubStatus,
                storedData.start
            )
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        result_cast_items?.layoutManager = object : LinearListLayout(view.context) {
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
        }
        result_cast_items?.adapter = ActorAdaptor()

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

        val storedData = (activity ?: context)?.let {
            getStoredData(it)
        }
        syncModel.addFromUrl(storedData?.url)

        val api = getApiFromNameNull(storedData?.apiName)

        result_episodes?.adapter =
            EpisodeAdapter(
                api?.hasDownloadSupport == true,
                { episodeClick ->
                    viewModel.handleAction(activity, episodeClick)
                },
                { downloadClickEvent ->
                    handleDownloadClick(activity, downloadClickEvent)
                }
            )


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

            result_bookmark_button?.setOnClickListener { fab ->
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

        // This is to band-aid FireTV navigation
        val isTv = isTvSettings()
        result_season_button?.isFocusableInTouchMode = isTv
        result_episode_select?.isFocusableInTouchMode = isTv
        result_dub_select?.isFocusableInTouchMode = isTv

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

        observe(syncModel.synced) { list ->
            result_sync_names?.text =
                list.filter { it.isSynced && it.hasAccount }.joinToString { it.name }

            val newList = list.filter { it.isSynced && it.hasAccount }

            result_mini_sync?.isVisible = newList.isNotEmpty()
            (result_mini_sync?.adapter as? ImageAdapter)?.updateList(newList.mapNotNull { it.icon })
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

        observe(viewModel.resumeWatching) { resume ->
            when (resume) {
                is Some.Success -> {
                    result_resume_parent?.isVisible = true
                    val value = resume.value
                    value.progress?.let { progress ->
                        result_resume_series_title?.apply {
                            isVisible = !value.isMovie
                            text =
                                if (value.isMovie) null else activity?.getNameFull(
                                    value.result.name,
                                    value.result.episode,
                                    value.result.season
                                )
                        }
                        result_resume_series_progress_text.setText(progress.progressLeft)
                        result_resume_series_progress?.apply {
                            isVisible = true
                            this.max = progress.maxProgress
                            this.progress = progress.progress
                        }
                        result_resume_progress_holder?.isVisible = true
                    } ?: run {
                        result_resume_progress_holder?.isVisible = false
                        result_resume_series_progress?.isVisible = false
                        result_resume_series_title?.isVisible = false
                        result_resume_series_progress_text?.isVisible = false
                    }

                    result_resume_series_button?.isVisible = !value.isMovie
                    result_resume_series_button_play?.isVisible = !value.isMovie

                    val click = View.OnClickListener {
                        viewModel.handleAction(
                            activity,
                            EpisodeClickEvent(
                                storedData?.playerAction ?: ACTION_PLAY_EPISODE_IN_PLAYER,
                                value.result
                            )
                        )
                    }

                    result_resume_series_button?.setOnClickListener(click)
                    result_resume_series_button_play?.setOnClickListener(click)
                }
                is Some.None -> {
                    result_resume_parent?.isVisible = false
                }
            }
        }

        observe(viewModel.episodes) { episodes ->
            updateEpisodes(episodes)
        }

        result_cast_items?.setOnFocusChangeListener { _, hasFocus ->
            // Always escape focus
            if (hasFocus) result_bookmark_button?.requestFocus()
        }

        result_sync_set_score?.setOnClickListener {
            syncModel.publishUserData()
        }

        observe(viewModel.trailers) { trailers ->
            setTrailers(trailers.flatMap { it.mirros }) // I dont care about subtitles yet!
        }

        observe(viewModel.recommendations) { recommendations ->
            setRecommendations(recommendations, null)
        }

        observe(viewModel.movie) { data ->
            updateMovie(data)
        }

        observe(viewModel.page) { data ->
            if (data == null) return@observe
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
                    result_poster_background.setImage(d.posterBackgroundImage)
                    //result_trailer_thumbnail.setImage(d.posterBackgroundImage, fadeIn = false)

                    if (d.posterImage != null && !isTrueTvSettings())
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
                    (result_cast_items?.adapter as? ActorAdaptor)?.apply {
                        updateList(d.actors ?: emptyList())
                    }

                    observeNullable(viewModel.subscribeStatus) { isSubscribed ->
                        result_subscribe?.isVisible = isSubscribed != null
                        if (isSubscribed == null) return@observeNullable

                        val drawable = if (isSubscribed) {
                            R.drawable.ic_baseline_notifications_active_24
                        } else {
                            R.drawable.baseline_notifications_none_24
                        }

                        result_subscribe?.setImageResource(drawable)
                    }

                    result_subscribe?.setOnClickListener {
                        val isSubscribed =
                            viewModel.toggleSubscriptionStatus() ?: return@setOnClickListener

                        val message = if (isSubscribed) {
                            // Kinda icky to have this here, but it works.
                            SubscriptionWorkManager.enqueuePeriodicWork(context)
                            R.string.subscription_new
                        } else {
                            R.string.subscription_deleted
                        }

                        val name = (viewModel.page.value as? Resource.Success)?.value?.title
                            ?: txt(R.string.no_data).asStringNull(context) ?: ""
                        showToast(activity, txt(message, name), Toast.LENGTH_SHORT)
                    }

                    result_open_in_browser?.isVisible = d.url.startsWith("http")
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
                    if (this !is ResultFragmentTv) // dont want this clickable on tv layout
                        result_description?.setOnClickListener { view ->
                            view.context?.let { ctx ->
                                val builder: AlertDialog.Builder =
                                    AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                                builder.setMessage(d.plotText.asString(ctx).html())
                                    .setTitle(d.plotHeaderText.asString(ctx))
                                    .show()
                            }
                        }


                    d.comingSoon.let { soon ->
                        result_coming_soon?.isVisible = soon
                        result_data_holder?.isGone = soon
                    }

                    val tags = d.tags
                    result_tag_holder?.isVisible = tags.isNotEmpty()
                    result_tag?.apply {
                        removeAllViews()
                        tags.forEach { tag ->
                            val chip = Chip(context)
                            val chipDrawable = ChipDrawable.createFromAttributes(
                                context,
                                null,
                                0,
                                R.style.ChipFilled
                            )
                            chip.setChipDrawable(chipDrawable)
                            chip.text = tag
                            chip.isChecked = false
                            chip.isCheckable = false
                            chip.isFocusable = false
                            chip.isClickable = false
                            chip.setTextColor(context.colorFromAttribute(R.attr.textColor))
                            addView(chip)
                        }
                    }
                    // if (tags.isNotEmpty()) {
                    //result_tag_holder?.visibility = VISIBLE
                    //val isOnTv = isTrueTvSettings()


                    /*for ((index, tag) in tags.withIndex()) {
                            val viewBtt = layoutInflater.inflate(R.layout.result_tag, null)
                            val btt = viewBtt.findViewById<MaterialButton>(R.id.result_tag_card)
                            btt.text = tag
                            btt.isFocusable = !isOnTv
                            btt.isClickable = !isOnTv
                            result_tag?.addView(viewBtt, index)
                        }*/
                    //}
                }
                is Resource.Failure -> {
                    result_error_text.text = storedData?.url?.plus("\n") + data.errorString
                    updateVisStatus(1)
                }
                is Resource.Loading -> {
                    updateVisStatus(0)
                }
            }
        }

        context?.let { ctx ->

            //result_bookmark_button?.isVisible = ctx.isTvSettings()

            val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)


            Kitsu.isEnabled =
                settingsManager.getBoolean(ctx.getString(R.string.show_kitsu_posters_key), true)

            if (storedData?.url != null) {
                result_reload_connectionerror.setOnClickListener {
                    viewModel.load(
                        activity,
                        storedData.url,
                        storedData.apiName,
                        storedData.showFillers,
                        storedData.dubStatus,
                        storedData.start
                    )
                }

                result_reload_connection_open_in_browser?.setOnClickListener {
                    val i = Intent(ACTION_VIEW)
                    i.data = Uri.parse(storedData.url)
                    try {
                        startActivity(i)
                    } catch (e: Exception) {
                        logError(e)
                    }
                }

                result_open_in_browser?.isVisible = storedData.url.startsWith("http")
                result_open_in_browser?.setOnClickListener {
                    val i = Intent(ACTION_VIEW)
                    i.data = Uri.parse(storedData.url)
                    try {
                        startActivity(i)
                    } catch (e: Exception) {
                        logError(e)
                    }
                }

                // bloats the navigation on tv
                if (!isTrueTvSettings()) {
                    result_meta_site?.setOnClickListener {
                        it.context?.openBrowser(storedData.url)
                    }
                    result_meta_site?.isFocusable = true
                } else {
                    result_meta_site?.isFocusable = false
                }

                if (restart || !viewModel.hasLoaded()) {
                    //viewModel.clear()
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
        }
    }
}
