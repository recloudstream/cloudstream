package com.lagradost.cloudstream3.ui.result

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.content.Intent.*
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.FileProvider
import androidx.core.text.color
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getApiFromName
import com.lagradost.cloudstream3.APIHolder.getId
import com.lagradost.cloudstream3.MainActivity.Companion.getCastSession
import com.lagradost.cloudstream3.MainActivity.Companion.showToast
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_NAVIGATE_TO
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.download.EasyDownloadButton
import com.lagradost.cloudstream3.ui.player.PlayerData
import com.lagradost.cloudstream3.ui.player.PlayerFragment
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.getDownloadSubsLanguageISO639_1
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.isAppInstalled
import com.lagradost.cloudstream3.utils.AppUtils.isCastApiAvailable
import com.lagradost.cloudstream3.utils.AppUtils.isConnectedToChromecast
import com.lagradost.cloudstream3.utils.CastHelper.startCast
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore.getFolderName
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.UIHelper.checkWrite
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.getStatusBarHeight
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIcons
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import com.lagradost.cloudstream3.utils.UIHelper.requestRW
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.VideoDownloadManager.sanitizeFilename
import kotlinx.android.synthetic.main.fragment_result.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File

const val MAX_SYNO_LENGH = 300

const val START_ACTION_NORMAL = 0
const val START_ACTION_RESUME_LATEST = 1
const val START_ACTION_LOAD_EP = 2

const val START_VALUE_NORMAL = 0

data class ResultEpisode(
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
    val descript: String?,
    val isFiller: Boolean?,
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

fun Context.buildResultEpisode(
    name: String?,
    poster: String?,
    episode: Int,
    season: Int?,
    data: String,
    apiName: String,
    id: Int,
    index: Int,
    rating: Int?,
    descript: String?,
    isFiller: Boolean?,
): ResultEpisode {
    val posDur = getViewPos(id)
    return ResultEpisode(
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
        descript,
        isFiller
    )
}

/** 0f-1f */
fun ResultEpisode.getWatchProgress(): Float {
    return (getDisplayPosition() / 1000).toFloat() / (duration / 1000).toFloat()
}

class ResultFragment : Fragment() {
    companion object {
        fun newInstance(url: String, apiName: String, startAction: Int = 0, startValue: Int = 0): Bundle {
            return Bundle().apply {
                putString("url", url)
                putString("apiName", apiName)
                putInt("startAction", startAction)
                putInt("startValue", startValue)
                putBoolean("restart", true)
            }
        }
    }

    private var currentLoadingCount = 0 // THIS IS USED TO PREVENT LATE EVENTS, AFTER DISMISS WAS CLICKED
    private val viewModel: ResultViewModel by activityViewModels()
    private var allEpisodes: HashMap<Int, ArrayList<ExtractorLink>> = HashMap()
    private var allEpisodesSubs: HashMap<Int, ArrayList<SubtitleFile>> = HashMap()
    private var currentHeaderName: String? = null
    private var currentType: TvType? = null
    private var currentEpisodes: List<ResultEpisode>? = null
    var downloadButton: EasyDownloadButton? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
       // viewModel =
       //     ViewModelProvider(activity ?: this).get(ResultViewModel::class.java)
        return inflater.inflate(R.layout.fragment_result, container, false)
    }

    override fun onDestroyView() {
        (result_episodes?.adapter as EpisodeAdapter?)?.killAdapter()
        super.onDestroyView()
    }

    override fun onDestroy() {
        //requireActivity().viewModelStore.clear() // REMEMBER THE CLEAR
        downloadButton?.dispose()

        super.onDestroy()
        activity?.let {
            it.window?.navigationBarColor =
                it.colorFromAttribute(R.attr.primaryGrayBackground)
        }
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
                result_loading.visibility = VISIBLE
                result_finish_loading.visibility = GONE
                result_loading_error.visibility = GONE
            }
            1 -> {
                result_loading.visibility = GONE
                result_finish_loading.visibility = GONE
                result_loading_error.visibility = VISIBLE
                result_reload_connection_open_in_browser.visibility = if (url == null) GONE else VISIBLE
            }
            2 -> {
                result_loading.visibility = GONE
                result_finish_loading.visibility = VISIBLE
                result_loading_error.visibility = GONE

                result_bookmark_button.requestFocus()
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
    var startValue: Int? = null

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

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val restart = arguments?.getBoolean("restart") ?: false
        if (restart) {
            arguments?.putBoolean("restart", false)
        }

        activity?.window?.decorView?.clearFocus()
        hideKeyboard()

        activity?.fixPaddingStatusbar(result_scroll)
        //activity?.fixPaddingStatusbar(result_barstatus)

        val backParameter = result_back.layoutParams as CoordinatorLayout.LayoutParams
        backParameter.setMargins(
            backParameter.leftMargin,
            backParameter.topMargin + requireContext().getStatusBarHeight(),
            backParameter.rightMargin,
            backParameter.bottomMargin
        )
        result_back.layoutParams = backParameter

        // activity?.fixPaddingStatusbar(result_toolbar)

        url = arguments?.getString("url")
        val apiName = arguments?.getString("apiName") ?: return
        startAction = arguments?.getInt("startAction") ?: START_ACTION_NORMAL
        startValue = arguments?.getInt("startValue") ?: START_VALUE_NORMAL


        val api = getApiFromName(apiName)
        if (media_route_button != null) {
            val chromecastSupport = api.hasChromecastSupport

            media_route_button?.alpha = if (chromecastSupport) 1f else 0.3f
            if (!chromecastSupport) {
                media_route_button.setOnClickListener {
                    showToast(activity, R.string.no_chomecast_support_toast, Toast.LENGTH_LONG)
                }
            }

            activity?.let {
                if (it.isCastApiAvailable()) {
                    try {
                        CastButtonFactory.setUpMediaRouteButton(it, media_route_button)
                        val castContext = CastContext.getSharedInstance(it.applicationContext)

                        if (castContext.castState != CastState.NO_DEVICES_AVAILABLE) media_route_button.visibility =
                            VISIBLE
                        castContext.addCastStateListener { state ->
                            if (media_route_button != null) {
                                if (state == CastState.NO_DEVICES_AVAILABLE) media_route_button.visibility = GONE else {
                                    if (media_route_button.visibility == GONE) media_route_button.visibility = VISIBLE
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logError(e)
                    }
                }
            }
        }
        result_scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
            if (result_poster_blur == null) return@OnScrollChangeListener
            result_poster_blur.alpha = maxOf(0f, (0.7f - scrollY / 1000f))
            val setAlpha = 1f - scrollY / 200f
            result_back.alpha = setAlpha
            result_poster_blur_holder.translationY = -scrollY.toFloat()
            // result_back.translationY = -scrollY.toFloat()
            //result_barstatus.alpha = scrollY / 200f
            //result_barstatus.visibility = if (scrollY > 0) View.VISIBLE else View.GONE§
            result_back.visibility = if (setAlpha > 0) VISIBLE else GONE
        })

        //  result_toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
        // result_toolbar.setNavigationOnClickListener {
        //     activity?.onBackPressed()
        // }

        result_back.setOnClickListener {
            activity?.popCurrentPage()
        }

        fun handleAction(episodeClick: EpisodeClickEvent): Job = main {
            //val id = episodeClick.data.id
            val index = episodeClick.data.index
            val buildInPlayer = true
            currentLoadingCount++
            var currentLinks: ArrayList<ExtractorLink>? = null
            var currentSubs: ArrayList<SubtitleFile>? = null

            val showTitle =
                episodeClick.data.name ?: getString(R.string.episode_name_format).format(
                    getString(R.string.episode),
                    episodeClick.data.episode
                )

            suspend fun requireLinks(isCasting: Boolean): Boolean {
                val currentLinksTemp =
                    if (allEpisodes.containsKey(episodeClick.data.id)) allEpisodes[episodeClick.data.id] else null
                val currentSubsTemp =
                    if (allEpisodesSubs.containsKey(episodeClick.data.id)) allEpisodesSubs[episodeClick.data.id] else null
                if (currentLinksTemp != null && currentLinksTemp.size > 0) {
                    currentLinks = currentLinksTemp
                    currentSubs = currentSubsTemp
                    return true
                }

                val skipLoading = getApiFromName(apiName).instantLinkLoading

                var loadingDialog: AlertDialog? = null
                val currentLoad = currentLoadingCount

                if (!skipLoading) {
                    val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustomTransparent)
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
                loadingDialog?.dismiss()

                when (data) {
                    is Resource.Success -> {
                        currentLinks = data.value.links
                        currentSubs = data.value.subs
                        return true
                    }
                    is Resource.Failure -> {
                        showToast(
                            activity,
                            R.string.error_loading_links_toast,
                            Toast.LENGTH_SHORT
                        )
                    }
                    else -> {

                    }
                }
                return false
            }

            fun acquireSingeExtractorLink(
                links: List<ExtractorLink>,
                title: String,
                callback: (ExtractorLink) -> Unit
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
                acquireSingeExtractorLink(currentLinks ?: return, title, callback)
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
                    currentSubs ?: ArrayList(),
                    startTime = episodeClick.data.getRealPosition(),
                    startIndex = startIndex
                )
            }

            fun startDownload(links: List<ExtractorLink>, subs: List<SubtitleFile>?) {
                val isMovie = currentIsMovie ?: return
                val titleName = sanitizeFilename(currentHeaderName ?: return)

                val meta = VideoDownloadManager.DownloadEpisodeMetadata(
                    episodeClick.data.id,
                    titleName,
                    apiName,
                    episodeClick.data.poster ?: currentPoster,
                    episodeClick.data.name,
                    if (isMovie) null else episodeClick.data.season,
                    if (isMovie) null else episodeClick.data.episode
                )


                val folder = when (currentType) {
                    TvType.Anime -> "Anime/$titleName"
                    TvType.Movie -> "Movies"
                    TvType.AnimeMovie -> "Movies"
                    TvType.TvSeries -> "TVSeries/$titleName"
                    TvType.ONA -> "ONA"
                    TvType.Cartoon -> "Cartoons/$titleName"
                    TvType.Torrent -> "Torrent"
                    else -> null
                }

                context?.let { ctx ->
                    val parentId = currentId ?: return@let
                    val src = "$DOWNLOAD_NAVIGATE_TO/$parentId" // url ?: return@let

                    // SET VISUAL KEYS
                    ctx.setKey(
                        DOWNLOAD_HEADER_CACHE,
                        parentId.toString(),
                        VideoDownloadHelper.DownloadHeaderCached(
                            apiName,
                            url ?: return@let,
                            currentType ?: return@let,
                            currentHeaderName ?: return@let,
                            currentPoster,
                            currentId ?: return@let,
                            System.currentTimeMillis(),
                        )
                    )

                    val epData = episodeClick.data
                    ctx.setKey(
                        getFolderName(
                            DOWNLOAD_EPISODE_CACHE,
                            parentId.toString()
                        ), // 3 deep folder for faster acess
                        epData.id.toString(),
                        VideoDownloadHelper.DownloadEpisodeCached(
                            epData.name,
                            epData.poster,
                            epData.episode,
                            epData.season,
                            epData.id,
                            parentId,
                            epData.rating,
                            epData.descript,
                            System.currentTimeMillis(),
                        )
                    )

                    // DOWNLOAD VIDEO
                    VideoDownloadManager.downloadEpisodeUsingWorker(
                        ctx,
                        src,//url ?: return,
                        folder,
                        meta,
                        links
                    )
                    // 1. Checks if the lang should be downloaded
                    // 2. Makes it into the download format
                    // 3. Downloads it as a .vtt file
                    val downloadList = ctx.getDownloadSubsLanguageISO639_1()
                    main {
                        subs?.let { subsList ->
                            subsList.filter { downloadList.contains(SubtitleHelper.fromLanguageToTwoLetters(it.lang)) }
                                .map { ExtractorSubtitleLink(it.lang, it.url, "") }
                                .forEach { link ->
                                    val epName = meta.name ?: "${context?.getString(R.string.episode)} ${meta.episode}"
                                    val fileName =
                                        sanitizeFilename(epName + if (downloadList.size > 1) " ${link.name}" else "")
                                    val topFolder = "$folder"

                                    withContext(Dispatchers.IO) {
                                        normalSafeApiCall {
                                            VideoDownloadManager.downloadThing(
                                                ctx,
                                                link,
                                                fileName,
                                                topFolder,
                                                "vtt",
                                                false,
                                                null
                                            ) {
                                                // no notification
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
            }

            val isLoaded = when (episodeClick.action) {
                ACTION_PLAY_EPISODE_IN_PLAYER -> true
                ACTION_CLICK_DEFAULT -> true
                ACTION_SHOW_TOAST -> true
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
                            handleAction(EpisodeClickEvent(ACTION_CHROME_CAST_EPISODE, episodeClick.data))
                        } else {
                            handleAction(EpisodeClickEvent(ACTION_PLAY_EPISODE_IN_PLAYER, episodeClick.data))
                        }
                    }
                }

                ACTION_SHOW_OPTIONS -> {
                    context?.let { ctx ->
                        val builder = AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                        var dialog: AlertDialog? = null
                        builder.setTitle(showTitle)
                        val options = requireContext().resources.getStringArray(R.array.episode_long_click_options)
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
                                ACTION_DOWNLOAD_EPISODE -> hasDownloadSupport
                                ACTION_DOWNLOAD_MIRROR -> hasDownloadSupport
                                ACTION_PLAY_EPISODE_IN_VLC_PLAYER -> context?.isAppInstalled(VLC_PACKAGE) ?: false
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
                            handleAction(EpisodeClickEvent(verifiedOptionsValues[which], episodeClick.data))
                            dialog?.dismiss()
                        }

                        dialog = builder.create()
                        dialog.show()
                    }
                }
                ACTION_COPY_LINK -> {
                    activity?.let { act ->
                        acquireSingeExtractorLink(act.getString(R.string.episode_action_copy_link)) { link ->
                            val serviceClipboard =
                                (act.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?)
                                    ?: return@acquireSingeExtractorLink
                            val clip = ClipData.newPlainText(link.name, link.url)
                            serviceClipboard.setPrimaryClip(clip)
                            showToast(act, R.string.copy_link_toast, Toast.LENGTH_SHORT)
                        }
                    }
                }

                ACTION_PLAY_EPISODE_IN_BROWSER -> {
                    acquireSingeExtractorLink(getString(R.string.episode_action_play_in_browser)) { link ->
                        val i = Intent(ACTION_VIEW)
                        i.data = Uri.parse(link.url)
                        startActivity(i)
                    }
                }

                ACTION_CHROME_CAST_MIRROR -> {
                    acquireSingeExtractorLink(getString(R.string.episode_action_chomecast_mirror)) { link ->
                        val mirrorIndex = currentLinks?.indexOf(link) ?: -1
                        startChromecast(if (mirrorIndex == -1) 0 else mirrorIndex)
                    }
                }

                ACTION_CHROME_CAST_EPISODE -> {
                    startChromecast(0)
                }

                ACTION_PLAY_EPISODE_IN_VLC_PLAYER -> {
                    activity?.let { act ->
                        if (!act.checkWrite()) {
                            act.requestRW()
                            if (act.checkWrite()) return@main
                        }
                        val data = currentLinks ?: return@main
                        val subs = currentSubs

                        val outputDir = act.cacheDir
                        val outputFile = withContext(Dispatchers.IO) {
                            File.createTempFile("mirrorlist", ".m3u8", outputDir)
                        }
                        var text = "#EXTM3U"
                        if (subs != null) {
                            for (sub in subs) {
                                text += "\n#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subs\",NAME=\"${sub.lang}\",DEFAULT=NO,AUTOSELECT=NO,FORCED=NO,LANGUAGE=\"${sub.lang}\",URI=\"${sub.url}\""
                            }
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
                    }
                }

                ACTION_PLAY_EPISODE_IN_PLAYER -> {
                    if (buildInPlayer) {
                        activity.navigate(
                            R.id.global_to_navigation_player, PlayerFragment.newInstance(
                                PlayerData(index, null, 0),
                                episodeClick.data.getRealPosition()
                            )
                        )
                    }
                }

                ACTION_RELOAD_EPISODE -> {
                    viewModel.loadEpisode(episodeClick.data, false)
                }

                ACTION_DOWNLOAD_EPISODE -> {
                    startDownload(currentLinks ?: return@main, currentSubs)
                }

                ACTION_DOWNLOAD_MIRROR -> {
                    currentLinks?.let { links ->
                        acquireSingeExtractorLink(
                            links,//(currentLinks ?: return@main).filter { !it.isM3u8 },
                            getString(R.string.episode_action_download_mirror)
                        ) { link ->
                            startDownload(listOf(link), currentSubs)
                        }
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
                context?.let { localContext ->
                    viewModel.updateWatchStatus(localContext, WatchType.fromInternalId(this.itemId))
                }
            }
        }

        observe(viewModel.watchStatus) {
            //result_bookmark_button.setIconResource(it.iconRes)
            result_bookmark_button.text = getString(it.stringRes)
        }

        observe(viewModel.episodes) { episodeList ->
            lateFixDownloadButton(episodeList.size <= 1) // movies can have multible parts but still be *movies* this will fix this

            when (startAction) {
                START_ACTION_RESUME_LATEST -> {
                    for (ep in episodeList) {
                        if (ep.getWatchProgress() > 0.90f) { // watched too much
                            continue
                        }
                        handleAction(EpisodeClickEvent(ACTION_PLAY_EPISODE_IN_PLAYER, ep))
                        break
                    }
                }
                START_ACTION_LOAD_EP -> {
                    for (ep in episodeList) {
                        if (ep.id == startValue) { // watched too much
                            handleAction(EpisodeClickEvent(ACTION_PLAY_EPISODE_IN_PLAYER, ep))
                            break
                        }
                    }
                }
                else -> {
                }
            }
            arguments?.remove("startValue")
            arguments?.remove("startAction")
            startAction = null
            startValue = null
        }

        observe(viewModel.allEpisodes) {
            allEpisodes = it
        }

        observe(viewModel.allEpisodesSubs) {
            allEpisodesSubs = it
        }

        observe(viewModel.selectedSeason) { season ->
            result_season_button?.text = fromIndexToSeasonText(season)
        }

        observe(viewModel.seasonSelections) { seasonList ->
            result_season_button?.visibility = if (seasonList.size <= 1) GONE else VISIBLE
            result_season_button?.setOnClickListener {
                result_season_button?.popupMenuNoIconsAndNoStringRes(
                    items = seasonList
                        .map { Pair(it ?: -2, fromIndexToSeasonText(it)) },
                ) {
                    val id = this.itemId
                    context?.let {
                        viewModel.changeSeason(it, if (id == -2) null else id)
                    }
                }
            }
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
                    (result_episodes?.adapter as EpisodeAdapter?)?.cardList = episodes.value
                    (result_episodes?.adapter as EpisodeAdapter?)?.updateLayout()
                    (result_episodes?.adapter as EpisodeAdapter?)?.notifyDataSetChanged()
                }
            }
        }

        observe(viewModel.dubStatus) { status ->
            result_dub_select?.text = status.toString()
        }

        observe(viewModel.dubSubSelections) { range ->
            dubRange = range
            result_dub_select?.visibility = if (range.size <= 1) GONE else VISIBLE
        }

        result_dub_select.setOnClickListener {
            val ranges = dubRange
            if (ranges != null) {
                it.popupMenuNoIconsAndNoStringRes(ranges.map { status -> Pair(status.ordinal, status.toString()) }
                    .toList()) {
                    viewModel.changeDubStatus(requireContext(), DubStatus.values()[itemId])
                }
            }
        }

        observe(viewModel.selectedRange) { range ->
            result_episode_select?.text = range
        }

        observe(viewModel.rangeOptions) { range ->
            episodeRanges = range
            result_episode_select?.visibility = if (range.size <= 1) GONE else VISIBLE
        }

        result_episode_select.setOnClickListener {
            val ranges = episodeRanges
            if (ranges != null) {
                it.popupMenuNoIconsAndNoStringRes(ranges.mapIndexed { index, s -> Pair(index, s) }.toList()) {
                    viewModel.changeRange(requireContext(), itemId)
                }
            }
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

        observe(viewModel.resultResponse) { data ->
            when (data) {
                is Resource.Success -> {
                    val d = data.value
                    if (d is LoadResponse) {
                        if (d !is AnimeLoadResponse && result_episode_loading.isVisible) { // no episode loading when not anime
                            result_episode_loading.isVisible = false
                        }

                        updateVisStatus(2)

                        result_vpn?.text = when (api.vpnStatus) {
                            VPNStatus.MightBeNeeded -> getString(R.string.vpn_might_be_needed)
                            VPNStatus.Torrent -> getString(R.string.vpn_torrent)
                            else -> ""
                        }
                        result_vpn?.visibility = if (api.vpnStatus == VPNStatus.None) GONE else VISIBLE

                        //result_bookmark_button.text = getString(R.string.type_watching)

                        currentHeaderName = d.name
                        currentType = d.type

                        currentPoster = d.posterUrl
                        currentIsMovie = !d.isEpisodeBased()

                        result_openinbrower?.setOnClickListener {
                            val i = Intent(ACTION_VIEW)
                            i.data = Uri.parse(d.url)
                            try {
                                startActivity(i)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        result_search?.setOnClickListener {
                            QuickSearchFragment.push(activity,true, d.name)
                        }

                        result_share?.setOnClickListener {
                            val i = Intent(ACTION_SEND)
                            i.type = "text/plain"
                            i.putExtra(EXTRA_SUBJECT, d.name)
                            i.putExtra(EXTRA_TEXT, d.url)
                            startActivity(createChooser(i, d.name))
                        }

                        val metadataInfoArray = ArrayList<Pair<Int, String>>()
                        if (d is AnimeLoadResponse) {
                            val status = when (d.showStatus) {
                                null -> null
                                ShowStatus.Ongoing -> R.string.status_ongoing
                                ShowStatus.Completed -> R.string.status_completed
                            }
                            if (status != null) {
                                metadataInfoArray.add(Pair(R.string.status, getString(status)))
                            }
                        }
                        if (d.year != null) metadataInfoArray.add(Pair(R.string.year, d.year.toString()))
                        val rating = d.rating
                        if (rating != null) metadataInfoArray.add(
                            Pair(
                                R.string.rating,
                                "%.1f/10.0".format(rating.toFloat() / 10f).replace(",", ".")
                            )
                        )
                        val duration = d.duration
                        if (duration != null) metadataInfoArray.add(Pair(R.string.duration, duration))

                        metadataInfoArray.add(Pair(R.string.site, d.apiName))

                        context?.let { ctx ->
                            if (metadataInfoArray.size > 0) {
                                result_metadata.visibility = VISIBLE
                                val text = SpannableStringBuilder()
                                val grayColor =
                                    ctx.colorFromAttribute(R.attr.grayTextColor) //ContextCompat.getColor(requireContext(), R.color.grayTextColor)
                                val textColor =
                                    ctx.colorFromAttribute(R.attr.textColor) //ContextCompat.getColor(requireContext(), R.color.textColor)
                                for (meta in metadataInfoArray) {
                                    text.color(grayColor) { append(getString(meta.first) + ": ") }
                                        .color(textColor) { append("${meta.second}\n") }
                                }
                                result_metadata.text = text
                            } else {
                                result_metadata.visibility = GONE
                            }
                        }


                        result_poster?.setImage(d.posterUrl)
                        result_poster_holder?.visibility = if (d.posterUrl.isNullOrBlank()) GONE else VISIBLE

                        result_play_movie?.text =
                            if (d.type == TvType.Torrent) getString(R.string.play_torrent_button) else getString(R.string.play_movie_button)
                        result_plot_header?.text =
                            if (d.type == TvType.Torrent) getString(R.string.torrent_plot) else getString(R.string.result_plot)
                        if (!d.plot.isNullOrEmpty()) {
                            var syno = d.plot!!
                            if (syno.length > MAX_SYNO_LENGH) {
                                syno = syno.substring(0, MAX_SYNO_LENGH) + "..."
                            }
                            result_descript.setOnClickListener {
                                val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
                                builder.setMessage(d.plot)
                                    .setTitle(if (d.type == TvType.Torrent) R.string.torrent_plot else R.string.result_plot)
                                    .show()
                            }
                            result_descript.text = syno
                        } else {
                            result_descript.text =
                                if (d.type == TvType.Torrent) getString(R.string.torrent_no_plot) else getString(R.string.normal_no_plot)
                        }

                        result_tag?.removeAllViews()
                        result_tag_holder?.visibility = GONE
                        // result_status.visibility = GONE

                        val tags = d.tags
                        if (tags.isNullOrEmpty()) {
                            result_tag_holder?.visibility = GONE
                        } else {
                            result_tag_holder?.visibility = VISIBLE

                            for ((index, tag) in tags.withIndex()) {
                                val viewBtt = layoutInflater.inflate(R.layout.result_tag, null)
                                val btt = viewBtt.findViewById<MaterialButton>(R.id.result_tag_card)
                                btt.text = tag

                                result_tag?.addView(viewBtt, index)
                            }
                        }

                        if (d.type.isMovieType()) {
                            val hasDownloadSupport = api.hasDownloadSupport
                            lateFixDownloadButton(true)

                            result_play_movie?.setOnClickListener {
                                val card = currentEpisodes?.firstOrNull() ?: return@setOnClickListener
                                handleAction(EpisodeClickEvent(ACTION_CLICK_DEFAULT, card))
                            }

                            result_play_movie?.setOnLongClickListener {
                                val card = currentEpisodes?.firstOrNull() ?: return@setOnLongClickListener true
                                handleAction(EpisodeClickEvent(ACTION_SHOW_OPTIONS, card))
                                return@setOnLongClickListener true
                            }

//                            result_options.setOnClickListener {
//                                val card = currentEpisodes?.first() ?: return@setOnClickListener
//                                handleAction(EpisodeClickEvent(ACTION_SHOW_OPTIONS, card))
//                            }

                            result_download_movie?.visibility = if (hasDownloadSupport) VISIBLE else GONE
                            if (hasDownloadSupport) {
                                val localId = d.getId()
                                val file =
                                    VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(requireContext(), localId)
                                downloadButton?.dispose()
                                downloadButton = EasyDownloadButton()
                                downloadButton?.setUpMaterialButton(
                                    file?.fileLength,
                                    file?.totalBytes,
                                    result_movie_progress_downloaded,
                                    result_download_movie,
                                    result_movie_text_progress,
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
                                                    )
                                                )
                                            )
                                        }
                                    } else {
                                        handleDownloadClick(activity, currentHeaderName, downloadClickEvent)
                                    }
                                }
                            }
                        } else {
                            lateFixDownloadButton(false)
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
                    } else {
                        updateVisStatus(1)
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

        context?.let { ctx ->
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)
            val showFillers = settingsManager.getBoolean(ctx.getString(R.string.show_fillers_key), true)

            val tempUrl = url
            if (tempUrl != null) {
                result_reload_connectionerror.setOnClickListener {
                    viewModel.load(it.context, tempUrl, apiName, showFillers)
                }

                result_reload_connection_open_in_browser.setOnClickListener {
                    val i = Intent(ACTION_VIEW)
                    i.data = Uri.parse(tempUrl)
                    try {
                        startActivity(i)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (restart || viewModel.resultResponse.value == null) {
                    viewModel.clear()
                    viewModel.load(ctx, tempUrl, apiName, showFillers)
                }
            }
        }
    }
}