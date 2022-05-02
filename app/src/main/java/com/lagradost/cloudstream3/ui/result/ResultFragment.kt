package com.lagradost.cloudstream3.ui.result

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
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
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.discord.panels.OverlappingPanelsLayout
import com.discord.panels.PanelsChildGestureRegionObserver
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getApiDubstatusSettings
import com.lagradost.cloudstream3.APIHolder.getApiFromName
import com.lagradost.cloudstream3.APIHolder.getId
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.getCastSession
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.mvvm.*
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_NAVIGATE_TO
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.download.EasyDownloadButton
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.player.RepoLinkGenerator
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.ui.settings.SettingsFragment
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.getDownloadSubsLanguageISO639_1
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.isAppInstalled
import com.lagradost.cloudstream3.utils.AppUtils.isCastApiAvailable
import com.lagradost.cloudstream3.utils.AppUtils.isConnectedToChromecast
import com.lagradost.cloudstream3.utils.AppUtils.loadCache
import com.lagradost.cloudstream3.utils.AppUtils.openBrowser
import com.lagradost.cloudstream3.utils.CastHelper.startCast
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore.getFolderName
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.UIHelper.checkWrite
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIcons
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import com.lagradost.cloudstream3.utils.UIHelper.requestRW
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.UIHelper.setImageBlur
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getFileName
import com.lagradost.cloudstream3.utils.VideoDownloadManager.sanitizeFilename
import kotlinx.android.synthetic.main.fragment_result.*
import kotlinx.android.synthetic.main.fragment_result_swipe.*
import kotlinx.android.synthetic.main.result_recommendations.*
import kotlinx.android.synthetic.main.result_sync.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File

const val MAX_SYNO_LENGH = 1000

const val START_ACTION_NORMAL = 0
const val START_ACTION_RESUME_LATEST = 1
const val START_ACTION_LOAD_EP = 2

const val START_VALUE_NORMAL = 0

data class ResultEpisode(
    val headerName: String,
    val name: String?,
    val poster: String?,
    val episode: Int,
    val season: Int?,
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

class ResultFragment : Fragment(), PanelsChildGestureRegionObserver.GestureRegionsListener {
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
                    println("CARD::::: $card")
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

        private fun downloadSubtitle(
            context: Context?,
            link: SubtitleData,
            meta: VideoDownloadManager.DownloadEpisodeMetadata,
        ) {
            context?.let { ctx ->
                val fileName = getFileName(ctx, meta)
                val folder = getFolder(meta.type ?: return, meta.mainName)
                downloadSubtitle(
                    ctx,
                    ExtractorSubtitleLink(link.name, link.url, ""),
                    fileName,
                    folder
                )
            }
        }

        private fun downloadSubtitle(
            context: Context?,
            link: ExtractorSubtitleLink,
            fileName: String,
            folder: String
        ) {
            ioSafe {
                VideoDownloadManager.downloadThing(
                    context ?: return@ioSafe,
                    link,
                    "$fileName ${link.name}",
                    folder,
                    if (link.url.contains(".srt")) ".srt" else "vtt",
                    false,
                    null
                ) {
                    // no notification
                }
            }
        }

        private fun getMeta(
            episode: ResultEpisode,
            titleName: String,
            apiName: String,
            currentPoster: String,
            currentIsMovie: Boolean,
            tvType: TvType,
        ): VideoDownloadManager.DownloadEpisodeMetadata {
            return VideoDownloadManager.DownloadEpisodeMetadata(
                episode.id,
                sanitizeFilename(titleName),
                apiName,
                episode.poster ?: currentPoster,
                episode.name,
                if (currentIsMovie) null else episode.season,
                if (currentIsMovie) null else episode.episode,
                tvType,
            )
        }

        private fun getFolder(currentType: TvType, titleName: String): String {
            val sanitizedFileName = sanitizeFilename(titleName)
            return when (currentType) {
                TvType.Anime -> "Anime/$sanitizedFileName"
                TvType.Movie -> "Movies"
                TvType.AnimeMovie -> "Movies"
                TvType.TvSeries -> "TVSeries/$sanitizedFileName"
                TvType.OVA -> "OVA"
                TvType.Cartoon -> "Cartoons/$sanitizedFileName"
                TvType.Torrent -> "Torrent"
                TvType.Documentary -> "Documentaries"
                TvType.AsianDrama -> "AsianDrama"
            }
        }

        fun startDownload(
            context: Context?,
            episode: ResultEpisode,
            currentIsMovie: Boolean,
            currentHeaderName: String,
            currentType: TvType,
            currentPoster: String,
            apiName: String,
            parentId: Int,
            url: String,
            links: List<ExtractorLink>,
            subs: List<SubtitleData>?
        ) {
            try {
                if (context == null) return

                val meta =
                    getMeta(
                        episode,
                        currentHeaderName,
                        apiName,
                        currentPoster,
                        currentIsMovie,
                        currentType
                    )

                val folder = getFolder(currentType, currentHeaderName)

                val src = "$DOWNLOAD_NAVIGATE_TO/$parentId" // url ?: return@let

                // SET VISUAL KEYS
                setKey(
                    DOWNLOAD_HEADER_CACHE,
                    parentId.toString(),
                    VideoDownloadHelper.DownloadHeaderCached(
                        apiName,
                        url,
                        currentType,
                        currentHeaderName,
                        currentPoster,
                        parentId,
                        System.currentTimeMillis(),
                    )
                )

                setKey(
                    getFolderName(
                        DOWNLOAD_EPISODE_CACHE,
                        parentId.toString()
                    ), // 3 deep folder for faster acess
                    episode.id.toString(),
                    VideoDownloadHelper.DownloadEpisodeCached(
                        episode.name,
                        episode.poster,
                        episode.episode,
                        episode.season,
                        episode.id,
                        parentId,
                        episode.rating,
                        episode.description,
                        System.currentTimeMillis(),
                    )
                )

                // DOWNLOAD VIDEO
                VideoDownloadManager.downloadEpisodeUsingWorker(
                    context,
                    src,//url ?: return,
                    folder,
                    meta,
                    links
                )

                // 1. Checks if the lang should be downloaded
                // 2. Makes it into the download format
                // 3. Downloads it as a .vtt file
                val downloadList = getDownloadSubsLanguageISO639_1()
                subs?.let { subsList ->
                    subsList.filter {
                        downloadList.contains(
                            SubtitleHelper.fromLanguageToTwoLetters(
                                it.name,
                                true
                            )
                        )
                    }
                        .map { ExtractorSubtitleLink(it.name, it.url, "") }
                        .forEach { link ->
                            val fileName = getFileName(context, meta)
                            downloadSubtitle(context, link, fileName, folder)
                        }
                }
            } catch (e: Exception) {
                logError(e)
            }
        }

        suspend fun downloadEpisode(
            activity: Activity?,
            episode: ResultEpisode,
            currentIsMovie: Boolean,
            currentHeaderName: String,
            currentType: TvType,
            currentPoster: String,
            apiName: String,
            parentId: Int,
            url: String,
        ) {
            safeApiCall {
                val generator = RepoLinkGenerator(listOf(episode))
                val currentLinks = mutableSetOf<ExtractorLink>()
                val currentSubs = mutableSetOf<SubtitleData>()
                generator.generateLinks(clearCache = false, isCasting = false, callback = {
                    it.first?.let { link ->
                        currentLinks.add(link)
                    }
                }, subtitleCallback = { sub ->
                    currentSubs.add(sub)
                })

                if (currentLinks.isEmpty()) {
                    showToast(activity, R.string.no_links_found_toast, Toast.LENGTH_SHORT)
                    return@safeApiCall
                }

                startDownload(
                    activity,
                    episode,
                    currentIsMovie,
                    currentHeaderName,
                    currentType,
                    currentPoster,
                    apiName,
                    parentId,
                    url,
                    sortUrls(currentLinks),
                    sortSubs(currentSubs),
                )
            }
        }
    }

    private var currentLoadingCount =
        0 // THIS IS USED TO PREVENT LATE EVENTS, AFTER DISMISS WAS CLICKED
    private lateinit var viewModel: ResultViewModel //by activityViewModels()
    private lateinit var syncModel: SyncViewModel
    private var currentHeaderName: String? = null
    private var currentType: TvType? = null
    private var currentEpisodes: List<ResultEpisode>? = null
    private var downloadButton: EasyDownloadButton? = null
    private var syncdata: Map<String, String>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        viewModel =
            ViewModelProvider(this)[ResultViewModel::class.java]
        syncModel =
            ViewModelProvider(this)[SyncViewModel::class.java]

        return inflater.inflate(R.layout.fragment_result_swipe, container, false)
    }

    override fun onDestroyView() {
        (result_episodes?.adapter as EpisodeAdapter?)?.killAdapter()
        super.onDestroyView()
    }

    override fun onDestroy() {
        //requireActivity().viewModelStore.clear() // REMEMBER THE CLEAR
        downloadButton?.dispose()
        updateUIListener = null
        result_cast_items?.let {
            PanelsChildGestureRegionObserver.Provider.get().unregister(it)
        }

        super.onDestroy()
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
                result_reload_connection_open_in_browser?.isVisible = url != null
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

    private var currentPoster: String? = null
    private var currentId: Int? = null
    private var currentIsMovie: Boolean? = null
    private var episodeRanges: List<String>? = null
    private var dubRange: Set<DubStatus>? = null
    var url: String? = null

    private fun fromIndexToSeasonText(selection: Int?): String {
        return when (selection) {
            null -> getString(R.string.no_season)
            -2 -> getString(R.string.no_season)
            else -> "${getString(R.string.season)} $selection"
        }
    }

    var startAction: Int? = null
    private var startValue: Int? = null

    private fun setFormatText(textView: TextView?, @StringRes format: Int, arg: Any?) {
        // java.util.IllegalFormatConversionException: f != java.lang.Integer
        // This can fail with malformed formatting
        normalSafeApiCall {
            if (arg == null) {
                textView?.isVisible = false
            } else {
                val text = context?.getString(format)?.format(arg)
                if (text == null) {
                    textView?.isVisible = false
                } else {
                    textView?.isVisible = true
                    textView?.text = text
                }
            }
        }
    }

    private fun setDuration(duration: Int?) {
        setFormatText(result_meta_duration, R.string.duration_format, duration)
    }

    private fun setShow(showStatus: ShowStatus?) {
        val status = when (showStatus) {
            null -> null
            ShowStatus.Ongoing -> R.string.status_ongoing
            ShowStatus.Completed -> R.string.status_completed
        }

        if (status == null) {
            result_meta_status?.isVisible = false
        } else {
            context?.getString(status)?.let {
                result_meta_status?.text = it
            }
        }
    }

    private fun setYear(year: Int?) {
        setFormatText(result_meta_year, R.string.year_format, year)
    }

    private fun setRating(rating: Int?) {
        setFormatText(result_meta_rating, R.string.rating_format, rating?.div(1000f))
    }

    private fun setActors(actors: List<ActorData>?) {
        if (actors.isNullOrEmpty()) {
            result_cast_text?.isVisible = false
            result_cast_items?.isVisible = false
        } else {
            val isImage = actors.first().actor.image != null
            if (isImage) {
                (result_cast_items?.adapter as ActorAdaptor?)?.apply {
                    updateList(actors)
                }
                result_cast_text?.isVisible = false
                result_cast_items?.isVisible = true
            } else {
                result_cast_text?.isVisible = true
                result_cast_items?.isVisible = false
                setFormatText(result_cast_text, R.string.cast_format,
                    actors.joinToString { it.actor.name })
            }
        }
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

    private fun lateFixDownloadButton(show: Boolean) {
        if (!show || currentType?.isMovieType() == false) {
            result_movie_parent.visibility = GONE
            result_episodes_text.visibility = VISIBLE
            result_episodes.visibility = VISIBLE
        } else {
            result_movie_parent.visibility = VISIBLE
            result_episodes_text.visibility = GONE
            result_episodes.visibility = GONE
        }
    }

    private fun updateUI() {
        syncModel.updateUserData()
        viewModel.reloadEpisodes()
    }

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

        updateUIListener = ::updateUI

        val restart = arguments?.getBoolean(RESTART_BUNDLE) ?: false
        if (restart) {
            arguments?.putBoolean(RESTART_BUNDLE, false)
        }

        activity?.window?.decorView?.clearFocus()
        hideKeyboard()
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

        url = arguments?.getString(URL_BUNDLE)
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

                        castContext.addCastStateListener { state ->
                            media_route_button?.isGone = state == CastState.NO_DEVICES_AVAILABLE
                        }
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
            result_poster_blur_holder?.translationY = -scrollY.toFloat()
        })

        result_back.setOnClickListener {
            activity?.popCurrentPage()
        }

        fun handleAction(episodeClick: EpisodeClickEvent): Job = main {
            if (episodeClick.action == ACTION_DOWNLOAD_EPISODE) {
                val isMovie = currentIsMovie ?: return@main
                val headerName = currentHeaderName ?: return@main
                val tvType = currentType ?: return@main
                val poster = currentPoster ?: return@main
                val id = currentId ?: return@main
                val curl = url ?: return@main
                showToast(activity, R.string.download_started, Toast.LENGTH_SHORT)
                downloadEpisode(
                    activity,
                    episodeClick.data,
                    isMovie,
                    headerName,
                    tvType,
                    poster,
                    apiName,
                    id,
                    curl,
                )
                return@main
            }

            var currentLinks: Set<ExtractorLink>? = null
            var currentSubs: Set<SubtitleData>? = null

            //val id = episodeClick.data.id
            currentLoadingCount++

            val showTitle =
                episodeClick.data.name ?: context?.getString(R.string.episode_name_format)
                    ?.format(
                        getString(R.string.episode),
                        episodeClick.data.episode
                    )


            fun acquireSingleExtractorLink(
                links: List<ExtractorLink>,
                title: String,
                callback: (ExtractorLink) -> Unit
            ) {
                val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)

                builder.setTitle(title)
                builder.setItems(links.map { "${it.name} ${Qualities.getStringByInt(it.quality)}" }
                    .toTypedArray()) { dia, which ->
                    callback.invoke(links[which])
                    dia?.dismiss()
                }
                builder.create().show()
            }

            fun acquireSingleSubtitleLink(
                links: List<SubtitleData>,
                title: String,
                callback: (SubtitleData) -> Unit
            ) {
                val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)

                builder.setTitle(title)
                builder.setItems(links.map { it.name }.toTypedArray()) { dia, which ->
                    callback.invoke(links[which])
                    dia?.dismiss()
                }
                builder.create().show()
            }

            fun acquireSingeExtractorLink(title: String, callback: (ExtractorLink) -> Unit) {
                acquireSingleExtractorLink(sortUrls(currentLinks ?: return), title, callback)
            }

            fun startChromecast(startIndex: Int) {
                val eps = currentEpisodes ?: return
                activity?.getCastSession()?.startCast(
                    apiName,
                    currentIsMovie ?: return,
                    currentHeaderName,
                    currentPoster,
                    episodeClick.data.index,
                    eps,
                    sortUrls(currentLinks ?: return),
                    sortSubs(currentSubs ?: return),
                    startTime = episodeClick.data.getRealPosition(),
                    startIndex = startIndex
                )
            }


            suspend fun requireLinks(isCasting: Boolean, displayLoading: Boolean = true): Boolean {
                val skipLoading = getApiFromName(apiName).instantLinkLoading

                var loadingDialog: AlertDialog? = null
                val currentLoad = currentLoadingCount

                if (!skipLoading && displayLoading) {
                    val builder =
                        AlertDialog.Builder(requireContext(), R.style.AlertDialogCustomTransparent)
                    val customLayout = layoutInflater.inflate(R.layout.dialog_loading, null)
                    builder.setView(customLayout)

                    loadingDialog = builder.create()

                    loadingDialog.show()
                    loadingDialog.setOnDismissListener {
                        currentLoadingCount++
                    }
                }

                val data = viewModel.loadEpisode(episodeClick.data, isCasting)
                if (currentLoadingCount != currentLoad) return false
                loadingDialog?.dismissSafe(activity)

                when (data) {
                    is Resource.Success -> {
                        currentLinks = data.value.first
                        currentSubs = data.value.second
                        return true
                    }
                    is Resource.Failure -> {
                        showToast(
                            activity,
                            R.string.error_loading_links_toast,
                            Toast.LENGTH_SHORT
                        )
                    }
                    else -> Unit
                }
                return false
            }

            val isLoaded = when (episodeClick.action) {
                ACTION_PLAY_EPISODE_IN_PLAYER -> true
                ACTION_CLICK_DEFAULT -> true
                ACTION_SHOW_TOAST -> true
                ACTION_DOWNLOAD_EPISODE -> {
                    showToast(activity, R.string.download_started, Toast.LENGTH_SHORT)
                    requireLinks(false, false)
                }
                ACTION_CHROME_CAST_EPISODE -> requireLinks(true)
                ACTION_CHROME_CAST_MIRROR -> requireLinks(true)
                else -> requireLinks(false)
            }
            if (!isLoaded) return@main // CANT LOAD

            when (episodeClick.action) {
                ACTION_SHOW_TOAST -> {
                    showToast(activity, R.string.play_episode_toast, Toast.LENGTH_SHORT)
                }

                ACTION_CLICK_DEFAULT -> {
                    context?.let { ctx ->
                        if (ctx.isConnectedToChromecast()) {
                            handleAction(
                                EpisodeClickEvent(
                                    ACTION_CHROME_CAST_EPISODE,
                                    episodeClick.data
                                )
                            )
                        } else {
                            handleAction(
                                EpisodeClickEvent(
                                    ACTION_PLAY_EPISODE_IN_PLAYER,
                                    episodeClick.data
                                )
                            )
                        }
                    }
                }

                ACTION_DOWNLOAD_EPISODE_SUBTITLE -> {
                    acquireSingleSubtitleLink(
                        sortSubs(
                            currentSubs ?: return@main
                        ),//(currentLinks ?: return@main).filter { !it.isM3u8 },
                        getString(R.string.episode_action_download_subtitle)
                    ) { link ->
                        downloadSubtitle(
                            context,
                            link,
                            getMeta(
                                episodeClick.data,
                                currentHeaderName ?: return@acquireSingleSubtitleLink,
                                apiName,
                                currentPoster ?: return@acquireSingleSubtitleLink,
                                currentIsMovie ?: return@acquireSingleSubtitleLink,
                                currentType ?: return@acquireSingleSubtitleLink
                            )
                        )
                        showToast(activity, R.string.download_started, Toast.LENGTH_SHORT)
                    }
                }

                ACTION_SHOW_OPTIONS -> {
                    context?.let { ctx ->
                        val builder = AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                        var dialog: AlertDialog? = null
                        builder.setTitle(showTitle)
                        val options =
                            requireContext().resources.getStringArray(R.array.episode_long_click_options)
                        val optionsValues =
                            requireContext().resources.getIntArray(R.array.episode_long_click_options_values)

                        val verifiedOptions = ArrayList<String>()
                        val verifiedOptionsValues = ArrayList<Int>()

                        val hasDownloadSupport = api.hasDownloadSupport

                        for (i in options.indices) {
                            val opv = optionsValues[i]
                            val op = options[i]

                            val isConnected = ctx.isConnectedToChromecast()
                            val add = when (opv) {
                                ACTION_CHROME_CAST_EPISODE -> isConnected
                                ACTION_CHROME_CAST_MIRROR -> isConnected
                                ACTION_DOWNLOAD_EPISODE_SUBTITLE -> !currentSubs.isNullOrEmpty()
                                ACTION_DOWNLOAD_EPISODE -> hasDownloadSupport
                                ACTION_DOWNLOAD_MIRROR -> hasDownloadSupport
                                ACTION_PLAY_EPISODE_IN_VLC_PLAYER -> context?.isAppInstalled(
                                    VLC_PACKAGE
                                ) ?: false
                                else -> true
                            }
                            if (add) {
                                verifiedOptions.add(op)
                                verifiedOptionsValues.add(opv)
                            }
                        }

                        builder.setItems(
                            verifiedOptions.toTypedArray()
                        ) { _, which ->
                            handleAction(
                                EpisodeClickEvent(
                                    verifiedOptionsValues[which],
                                    episodeClick.data
                                )
                            )
                            dialog?.dismissSafe(activity)
                        }

                        dialog = builder.create()
                        dialog.show()
                    }
                }
                ACTION_COPY_LINK -> {
                    activity?.let { act ->
                        try {
                            acquireSingeExtractorLink(act.getString(R.string.episode_action_copy_link)) { link ->
                                val serviceClipboard =
                                    (act.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?)
                                        ?: return@acquireSingeExtractorLink
                                val clip = ClipData.newPlainText(link.name, link.url)
                                serviceClipboard.setPrimaryClip(clip)
                                showToast(act, R.string.copy_link_toast, Toast.LENGTH_SHORT)
                            }
                        } catch (e: Exception) {
                            showToast(act, e.toString(), Toast.LENGTH_LONG)
                            logError(e)
                        }
                    }
                }

                ACTION_PLAY_EPISODE_IN_BROWSER -> {
                    acquireSingeExtractorLink(getString(R.string.episode_action_play_in_browser)) { link ->
                        try {
                            val i = Intent(ACTION_VIEW)
                            i.data = Uri.parse(link.url)
                            startActivity(i)
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }
                }

                ACTION_CHROME_CAST_MIRROR -> {
                    acquireSingeExtractorLink(getString(R.string.episode_action_chromecast_mirror)) { link ->
                        val mirrorIndex = currentLinks?.indexOf(link) ?: -1
                        startChromecast(if (mirrorIndex == -1) 0 else mirrorIndex)
                    }
                }

                ACTION_CHROME_CAST_EPISODE -> {
                    startChromecast(0)
                }

                ACTION_PLAY_EPISODE_IN_VLC_PLAYER -> {
                    activity?.let { act ->
                        try {
                            if (!act.checkWrite()) {
                                act.requestRW()
                                if (act.checkWrite()) return@main
                            }
                            val data = currentLinks ?: return@main
                            val subs = currentSubs ?: return@main

                            val outputDir = act.cacheDir
                            val outputFile = withContext(Dispatchers.IO) {
                                File.createTempFile("mirrorlist", ".m3u8", outputDir)
                            }
                            var text = "#EXTM3U"
                            for (sub in sortSubs(subs)) {
                                text += "\n#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subs\",NAME=\"${sub.name}\",DEFAULT=NO,AUTOSELECT=NO,FORCED=NO,LANGUAGE=\"${sub.name}\",URI=\"${sub.url}\""
                            }
                            for (link in data.sortedBy { -it.quality }) {
                                text += "\n#EXTINF:, ${link.name}\n${link.url}"
                            }
                            outputFile.writeText(text)

                            val vlcIntent = Intent(VLC_INTENT_ACTION_RESULT)

                            vlcIntent.setPackage(VLC_PACKAGE)
                            vlcIntent.addFlags(FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                            vlcIntent.addFlags(FLAG_GRANT_PREFIX_URI_PERMISSION)
                            vlcIntent.addFlags(FLAG_GRANT_READ_URI_PERMISSION)
                            vlcIntent.addFlags(FLAG_GRANT_WRITE_URI_PERMISSION)

                            vlcIntent.setDataAndType(
                                FileProvider.getUriForFile(
                                    act,
                                    act.applicationContext.packageName + ".provider",
                                    outputFile
                                ), "video/*"
                            )

                            val startId = VLC_FROM_PROGRESS

                            var position = startId
                            if (startId == VLC_FROM_START) {
                                position = 1
                            } else if (startId == VLC_FROM_PROGRESS) {
                                position = 0
                            }

                            vlcIntent.putExtra("position", position)

                            vlcIntent.component = VLC_COMPONENT
                            act.setKey(VLC_LAST_ID_KEY, episodeClick.data.id)
                            act.startActivityForResult(vlcIntent, VLC_REQUEST_CODE)
                        } catch (e: Exception) {
                            logError(e)
                            showToast(act, e.toString(), Toast.LENGTH_LONG)
                        }
                    }
                }

                ACTION_PLAY_EPISODE_IN_PLAYER -> {
                    viewModel.getGenerator(episodeClick.data)
                        ?.let { generator ->
                            activity?.navigate(
                                R.id.global_to_navigation_player,
                                GeneratorPlayer.newInstance(
                                    generator, syncdata?.let { HashMap(it) }
                                )
                            )
                        }
                }

                ACTION_RELOAD_EPISODE -> {
                    viewModel.loadEpisode(episodeClick.data, false, clearCache = true)
                }

                ACTION_DOWNLOAD_MIRROR -> {
                    acquireSingleExtractorLink(
                        sortUrls(
                            currentLinks ?: return@main
                        ),//(currentLinks ?: return@main).filter { !it.isM3u8 },
                        getString(R.string.episode_action_download_mirror)
                    ) { link ->
                        startDownload(
                            context,
                            episodeClick.data,
                            currentIsMovie ?: return@acquireSingleExtractorLink,
                            currentHeaderName ?: return@acquireSingleExtractorLink,
                            currentType ?: return@acquireSingleExtractorLink,
                            currentPoster ?: return@acquireSingleExtractorLink,
                            apiName,
                            currentId ?: return@acquireSingleExtractorLink,
                            url ?: return@acquireSingleExtractorLink,
                            listOf(link),
                            sortSubs(currentSubs ?: return@acquireSingleExtractorLink),
                        )
                        showToast(activity, R.string.download_started, Toast.LENGTH_SHORT)
                    }
                }
            }
        }

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> =
            EpisodeAdapter(
                ArrayList(),
                api.hasDownloadSupport,
                { episodeClick ->
                    handleAction(episodeClick)
                },
                { downloadClickEvent ->
                    handleDownloadClick(activity, currentHeaderName, downloadClickEvent)
                }
            )

        result_episodes.adapter = adapter
        result_episodes.layoutManager = GridLayoutManager(context, 1)

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

        observe(viewModel.selectedSeason) { season ->
            result_season_button?.text = fromIndexToSeasonText(season)
        }

        observe(viewModel.seasonSelections) { seasonList ->
            result_season_button?.visibility = if (seasonList.size <= 1) GONE else VISIBLE.also {

                // If the season button is visible the result season button will be next focus down
                if (result_series_parent?.isVisible == true)
                    setFocusUpAndDown(result_resume_series_button, result_season_button)
                else
                    setFocusUpAndDown(result_bookmark_button, result_season_button)
            }

            result_season_button?.setOnClickListener {
                result_season_button?.popupMenuNoIconsAndNoStringRes(
                    items = seasonList
                        .map { Pair(it ?: -2, fromIndexToSeasonText(it)) },
                ) {
                    val id = this.itemId

                    viewModel.changeSeason(if (id == -2) null else id)
                }
            }
        }

        observe(viewModel.selectedRange) { range ->
            result_episode_select?.text = range
        }

        observe(viewModel.rangeOptions) { range ->
            episodeRanges = range
            result_episode_select?.visibility = if (range.size <= 1) GONE else VISIBLE.also {

                // If Season button is invisible then the bookmark button next focus is episode select
                if (result_season_button?.isVisible != true) {
                    if (result_series_parent?.isVisible == true)
                        setFocusUpAndDown(result_resume_series_button, result_episode_select)
                    else
                        setFocusUpAndDown(result_bookmark_button, result_episode_select)
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
        val imgAdapter = ImageAdapter(
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
        result_mini_sync?.adapter = imgAdapter

        observe(syncModel.synced) { list ->
            result_sync_names?.text =
                list.filter { it.isSynced && it.hasAccount }.joinToString { it.name }

            val newList = list.filter { it.isSynced && it.hasAccount }

            result_mini_sync?.isVisible = newList.isNotEmpty()
            (result_mini_sync?.adapter as? ImageAdapter?)?.updateList(newList.map { it.icon })
        }

        observe(syncModel.syncIds) {
            syncdata = it
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
                    viewModel.setMeta(d)
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
            context?.getString(if (isProgressVisible) R.string.resume else R.string.play_movie_button)
                ?.let {
                    result_play_movie?.text = it
                }
            println("startAction = $startAction")

            when (startAction) {
                START_ACTION_RESUME_LATEST -> {
                    for (ep in episodeList) {
                        println("WATCH STATUS::: S${ep.season} E ${ep.episode} - ${ep.getWatchProgress()}")
                        if (ep.getWatchProgress() > 0.90f) { // watched too much
                            continue
                        }
                        handleAction(EpisodeClickEvent(ACTION_PLAY_EPISODE_IN_PLAYER, ep))
                        break
                    }
                }
                START_ACTION_LOAD_EP -> {
                    if(episodeList.size == 1) {
                        handleAction(EpisodeClickEvent(ACTION_PLAY_EPISODE_IN_PLAYER, episodeList.first()))
                    } else {
                        var found = false
                        for (ep in episodeList) {
                            if (ep.id == startValue) { // watched too much
                                println("WATCH STATUS::: START_ACTION_LOAD_EP S${ep.season} E ${ep.episode} - ${ep.getWatchProgress()}")
                                handleAction(EpisodeClickEvent(ACTION_PLAY_EPISODE_IN_PLAYER, ep))
                                found = true
                                break
                            }
                        }
                        if (!found)
                            for (ep in episodeList) {
                                if (ep.episode == resumeEpisode && ep.season == resumeSeason) {
                                    println("WATCH STATUS::: START_ACTION_LOAD_EP S${ep.season} E ${ep.episode} - ${ep.getWatchProgress()}")
                                    handleAction(EpisodeClickEvent(ACTION_PLAY_EPISODE_IN_PLAYER, ep))
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

        observe(viewModel.publicEpisodes) { episodes ->
            when (episodes) {
                is Resource.Failure -> {
                    result_episode_loading?.isVisible = false
                    //result_episodes?.isVisible = false
                }
                is Resource.Loading -> {
                    result_episode_loading?.isVisible = true
                    // result_episodes?.isVisible = false
                }
                is Resource.Success -> {
                    //result_episodes?.isVisible = true
                    result_episode_loading?.isVisible = false
                    if (result_episodes == null || result_episodes.adapter == null) return@observe
                    currentEpisodes = episodes.value
                    (result_episodes?.adapter as? EpisodeAdapter?)?.cardList = episodes.value
                    (result_episodes?.adapter as? EpisodeAdapter?)?.updateLayout()
                    (result_episodes?.adapter as? EpisodeAdapter?)?.notifyDataSetChanged()
                }
            }
        }

        observe(viewModel.dubStatus) { status ->
            result_dub_select?.text = status.toString()
        }

        val preferDub = context?.getApiDubstatusSettings()?.all { it == DubStatus.Dubbed } == true

        observe(viewModel.dubSubSelections) { range ->
            dubRange = range

            if (preferDub && dubRange?.contains(DubStatus.Dubbed) == true) {
                viewModel.changeDubStatus(DubStatus.Dubbed)
            }

            result_dub_select?.visibility = if (range.size <= 1) GONE else VISIBLE

            if (result_season_button?.isVisible != true && result_episode_select?.isVisible != true) {
                if (result_series_parent?.isVisible == true)
                    setFocusUpAndDown(result_resume_series_button, result_dub_select)
                else
                    setFocusUpAndDown(result_bookmark_button, result_dub_select)
            }
        }

        result_cast_items?.setOnFocusChangeListener { v, hasFocus ->
            // Always escape focus
            if (hasFocus) result_bookmark_button?.requestFocus()
        }

        result_dub_select.setOnClickListener {
            val ranges = dubRange
            if (ranges != null) {
                it.popupMenuNoIconsAndNoStringRes(ranges
                    .map { status ->
                        Pair(
                            status.ordinal,
                            status.toString()
                        )
                    }
                    .toList()) {
                    viewModel.changeDubStatus(DubStatus.values()[itemId])
                }
            }
        }

        result_episode_select?.setOnClickListener {
            val ranges = episodeRanges
            if (ranges != null) {
                it.popupMenuNoIconsAndNoStringRes(ranges.mapIndexed { index, s -> Pair(index, s) }
                    .toList()) {
                    viewModel.changeRange(itemId)
                }
            }
        }

        result_sync_set_score?.setOnClickListener {
            syncModel.publishUserData()
        }

        observe(viewModel.publicEpisodesCount) { count ->
            if (count < 0) {
                result_episodes_text?.isVisible = false
            } else {
                // result_episodes_text?.isVisible = true
                result_episodes_text?.text =
                    "$count ${if (count == 1) getString(R.string.episode) else getString(R.string.episodes)}"
            }
        }

        observe(viewModel.id) {
            currentId = it
        }

        observe(viewModel.result) { data ->
            when (data) {
                is Resource.Success -> {
                    val d = data.value
                    if (d !is AnimeLoadResponse && result_episode_loading.isVisible) { // no episode loading when not anime
                        result_episode_loading.isVisible = false
                    }

                    updateVisStatus(2)

                    result_vpn?.text = when (api.vpnStatus) {
                        VPNStatus.MightBeNeeded -> getString(R.string.vpn_might_be_needed)
                        VPNStatus.Torrent -> getString(R.string.vpn_torrent)
                        else -> ""
                    }
                    result_vpn?.isGone = api.vpnStatus == VPNStatus.None

                    result_info?.text = when (api.providerType) {
                        ProviderType.MetaProvider -> getString(R.string.provider_info_meta)
                        else -> ""
                    }
                    result_info?.isVisible = api.providerType == ProviderType.MetaProvider

                    if (d.type.isEpisodeBased()) {
                        val ep = d as? TvSeriesLoadResponse
                        val epCount = ep?.episodes?.size ?: 1
                        if (epCount < 1) {
                            result_info?.text = getString(R.string.no_episodes_found)
                            result_info?.isVisible = true
                        }
                    }

                    currentHeaderName = d.name
                    currentType = d.type

                    currentPoster = d.posterUrl
                    currentIsMovie = !d.isEpisodeBased()

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
                        QuickSearchFragment.pushSearch(activity, d.name)
                    }

                    result_share?.setOnClickListener {
                        try {
                            val i = Intent(ACTION_SEND)
                            i.type = "text/plain"
                            i.putExtra(EXTRA_SUBJECT, d.name)
                            i.putExtra(EXTRA_TEXT, d.url)
                            startActivity(createChooser(i, d.name))
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }

                    val showStatus = when (d) {
                        is TvSeriesLoadResponse -> d.showStatus
                        is AnimeLoadResponse -> d.showStatus
                        else -> null
                    }

                    setShow(showStatus)
                    setDuration(d.duration)
                    setYear(d.year)
                    setRating(d.rating)
                    setRecommendations(d.recommendations, null)
                    setActors(d.actors)

                    if (SettingsFragment.accountEnabled) {
                        if (syncModel.addSyncs(d.syncData)) {
                            syncModel.updateMetaAndUser()
                            syncModel.updateSynced()
                        } else {
                            syncModel.addFromUrl(d.url)
                        }
                    }

                    result_meta_site?.text = d.apiName

                    val posterImageLink = d.posterUrl
                    if (!posterImageLink.isNullOrEmpty()) {
                        result_poster?.setImage(posterImageLink, d.posterHeaders)
                        result_poster_blur?.setImageBlur(posterImageLink, 10, 3, d.posterHeaders)
                        //Full screen view of Poster image
                        if (context?.isTrueTvSettings() == false) // Poster not clickable on tv
                            result_poster_holder?.setOnClickListener {
                                try {
                                    context?.let { ctx ->
                                        val bitmap = result_poster.drawable.toBitmap()
                                        val sourceBuilder = AlertDialog.Builder(ctx)
                                        sourceBuilder.setView(R.layout.result_poster)

                                        val sourceDialog = sourceBuilder.create()
                                        sourceDialog.show()

                                        sourceDialog.findViewById<ImageView?>(R.id.imgPoster)
                                            ?.apply {
                                                setImageBitmap(bitmap)
                                                setOnClickListener {
                                                    sourceDialog.dismissSafe()
                                                }
                                            }
                                    }
                                } catch (e: Exception) {
                                    logError(e)
                                }
                            }

                    } else {
                        result_poster?.setImageResource(R.drawable.default_cover)
                        result_poster_blur?.setImageResource(R.drawable.default_cover)
                    }

                    result_poster_holder?.visibility = VISIBLE

                    /*result_play_movie?.text =
                        if (d.type == TvType.Torrent) getString(R.string.play_torrent_button) else getString(
                            R.string.play_movie_button
                        )*/
                    //result_plot_header?.text =
                    //    if (d.type == TvType.Torrent) getString(R.string.torrent_plot) else getString(R.string.result_plot)
                    if (!d.plot.isNullOrEmpty()) {
                        var syno = d.plot!!
                        if (syno.length > MAX_SYNO_LENGH) {
                            syno = syno.substring(0, MAX_SYNO_LENGH) + "..."
                        }
                        result_description.setOnClickListener {
                            val builder: AlertDialog.Builder =
                                AlertDialog.Builder(requireContext())
                            builder.setMessage(d.plot)
                                .setTitle(if (d.type == TvType.Torrent) R.string.torrent_plot else R.string.result_plot)
                                .show()
                        }
                        result_description.text = syno
                    } else {
                        result_description.text =
                            if (d.type == TvType.Torrent) getString(R.string.torrent_no_plot) else getString(
                                R.string.normal_no_plot
                            )
                    }

                    result_tag?.removeAllViews()
                    //result_tag_holder?.visibility = GONE
                    // result_status.visibility = GONE

                    d.comingSoon.let { soon ->
                        result_coming_soon?.isVisible = soon
                        result_data_holder?.isGone = soon
                    }

                    val tags = d.tags
                    if (tags.isNullOrEmpty()) {
                        //result_tag_holder?.visibility = GONE
                    } else {
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

                    if (d.type.isMovieType()) {
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

//                            result_options.setOnClickListener {
//                                val card = currentEpisodes?.first() ?: return@setOnClickListener
//                                handleAction(EpisodeClickEvent(ACTION_SHOW_OPTIONS, card))
//                            }

                        result_download_movie?.visibility =
                            if (hasDownloadSupport) VISIBLE else GONE
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
                            }

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

                    context?.getString(
                        when (d.type) {
                            TvType.TvSeries -> R.string.tv_series_singular
                            TvType.Anime -> R.string.anime_singular
                            TvType.OVA -> R.string.ova_singular
                            TvType.AnimeMovie -> R.string.movies_singular
                            TvType.Cartoon -> R.string.cartoons_singular
                            TvType.Documentary -> R.string.documentaries_singular
                            TvType.Movie -> R.string.movies_singular
                            TvType.Torrent -> R.string.torrent_singular
                            TvType.AsianDrama -> R.string.asian_drama_singular
                        }
                    )?.let {
                        result_meta_type?.text = it
                    }

                    when (d) {
                        is AnimeLoadResponse -> {

                            // val preferEnglish = true
                            //val titleName = (if (preferEnglish) d.engName else d.japName) ?: d.name
                            val titleName = d.name
                            result_title.text = titleName
                            //result_toolbar.title = titleName
                        }
                        else -> result_title.text = d.name
                    }
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

        val recAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = activity?.let {
            SearchAdapter(
                ArrayList(),
                result_recommendations,
            ) { callback ->
                SearchHelper.handleSearchClickCallback(activity, callback)
            }
        }

        result_recommendations?.adapter = recAdapter

        context?.let { ctx ->
            result_bookmark_button?.isVisible = ctx.isTvSettings()

            val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)
            val showFillers =
                settingsManager.getBoolean(ctx.getString(R.string.show_fillers_key), false)

            val tempUrl = url
            if (tempUrl != null) {
                result_reload_connectionerror.setOnClickListener {
                    viewModel.load(tempUrl, apiName, showFillers)
                }

                result_reload_connection_open_in_browser?.setOnClickListener {
                    val i = Intent(ACTION_VIEW)
                    i.data = Uri.parse(tempUrl)
                    try {
                        startActivity(i)
                    } catch (e: Exception) {
                        logError(e)
                    }
                }

                result_open_in_browser?.setOnClickListener {
                    val i = Intent(ACTION_VIEW)
                    i.data = Uri.parse(tempUrl)
                    try {
                        startActivity(i)
                    } catch (e: Exception) {
                        logError(e)
                    }
                }

                // bloats the navigation on tv
                if (context?.isTrueTvSettings() == false) {
                    result_meta_site?.setOnClickListener {
                        it.context?.openBrowser(tempUrl)
                    }
                    result_meta_site?.isFocusable = true
                } else {
                    result_meta_site?.isFocusable = false
                }

                if (restart || viewModel.result.value == null) {
                    //viewModel.clear()
                    viewModel.load(tempUrl, apiName, showFillers)
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
