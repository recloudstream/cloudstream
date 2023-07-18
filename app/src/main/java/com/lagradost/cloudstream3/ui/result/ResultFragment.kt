package com.lagradost.cloudstream3.ui.result

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.Intent.*
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.lagradost.cloudstream3.APIHolder.getApiDubstatusSettings
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.APIHolder.updateHasTrailers
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.*
import com.lagradost.cloudstream3.syncproviders.providers.Kitsu
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.player.FullScreenPlayer
import com.lagradost.cloudstream3.ui.result.EpisodeAdapter.Companion.getPlayerAction
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.getNameFull
import com.lagradost.cloudstream3.utils.AppUtils.html
import com.lagradost.cloudstream3.utils.AppUtils.loadCache
import com.lagradost.cloudstream3.utils.AppUtils.openBrowser
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStoreHelper.getVideoWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import kotlinx.android.synthetic.main.fragment_result.download_button
import kotlinx.android.synthetic.main.fragment_result.result_cast_items
import kotlinx.android.synthetic.main.fragment_result.result_cast_text
import kotlinx.android.synthetic.main.fragment_result.result_coming_soon
import kotlinx.android.synthetic.main.fragment_result.result_data_holder
import kotlinx.android.synthetic.main.fragment_result.result_description
import kotlinx.android.synthetic.main.fragment_result.result_dub_select
import kotlinx.android.synthetic.main.fragment_result.result_episode_loading
import kotlinx.android.synthetic.main.fragment_result.result_episode_select
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
import kotlinx.android.synthetic.main.fragment_result.result_next_airing
import kotlinx.android.synthetic.main.fragment_result.result_next_airing_time
import kotlinx.android.synthetic.main.fragment_result.result_no_episodes
import kotlinx.android.synthetic.main.fragment_result.result_play_movie
import kotlinx.android.synthetic.main.fragment_result.result_poster
import kotlinx.android.synthetic.main.fragment_result.result_poster_background
import kotlinx.android.synthetic.main.fragment_result.result_poster_holder
import kotlinx.android.synthetic.main.fragment_result.result_reload_connection_open_in_browser
import kotlinx.android.synthetic.main.fragment_result.result_reload_connectionerror
import kotlinx.android.synthetic.main.fragment_result.result_resume_parent
import kotlinx.android.synthetic.main.fragment_result.result_resume_progress_holder
import kotlinx.android.synthetic.main.fragment_result.result_resume_series_button
import kotlinx.android.synthetic.main.fragment_result.result_resume_series_progress
import kotlinx.android.synthetic.main.fragment_result.result_resume_series_progress_text
import kotlinx.android.synthetic.main.fragment_result.result_resume_series_title
import kotlinx.android.synthetic.main.fragment_result.result_season_button
import kotlinx.android.synthetic.main.fragment_result.result_tag
import kotlinx.android.synthetic.main.fragment_result.result_tag_holder
import kotlinx.android.synthetic.main.fragment_result.result_title
import kotlinx.android.synthetic.main.fragment_result.result_vpn
import kotlinx.android.synthetic.main.fragment_result_tv.result_resume_series_button_play
import kotlinx.android.synthetic.main.fragment_result_tv.temporary_no_focus
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

open class ResultFragment : FullScreenPlayer() {
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
    //protected open val resultLayout = R.layout.fragment_result_swipe

    override var layout = R.layout.fragment_result_swipe

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        viewModel =
            ViewModelProvider(this)[ResultViewModel2::class.java]
        syncModel =
            ViewModelProvider(this)[SyncViewModel::class.java]

        return super.onCreateView(inflater, container, savedInstanceState)
        //return inflater.inflate(resultLayout, container, false)
    }

    override fun onDestroyView() {
        updateUIListener = null
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


    private fun updateUI() {
        syncModel.updateUserData()
        viewModel.reloadEpisodes()
    }

    data class StoredData(
        val url: String?,
        val apiName: String,
        val showFillers: Boolean,
        val dubStatus: DubStatus,
        val start: AutoResume?,
        val playerAction: Int
    )

    fun getStoredData(context: Context): StoredData? {
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

        // This is to band-aid FireTV navigation
        val isTv = isTvSettings()
        result_season_button?.isFocusableInTouchMode = isTv
        result_episode_select?.isFocusableInTouchMode = isTv
        result_dub_select?.isFocusableInTouchMode = isTv


        observeNullable(viewModel.resumeWatching) { resume ->
            if (resume == null) {
                result_resume_parent?.isVisible = false
                return@observeNullable
            }
            result_resume_parent?.isVisible = true
            resume.progress?.let { progress ->
                result_resume_series_title?.apply {
                    isVisible = !resume.isMovie
                    text =
                        if (resume.isMovie) null else activity?.getNameFull(
                            resume.result.name,
                            resume.result.episode,
                            resume.result.season
                        )
                }
                result_resume_series_progress_text?.setText(progress.progressLeft)
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

            result_resume_series_button?.isVisible = !resume.isMovie
            result_resume_series_button_play?.isVisible = !resume.isMovie

            val click = View.OnClickListener {
                viewModel.handleAction(
                    activity,
                    EpisodeClickEvent(
                        storedData?.playerAction ?: ACTION_PLAY_EPISODE_IN_PLAYER,
                        resume.result
                    )
                )
            }

            result_resume_series_button?.setOnClickListener(click)
            result_resume_series_button_play?.setOnClickListener(click)
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
