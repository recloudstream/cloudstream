package com.lagradost.cloudstream3.ui.result

import android.annotation.SuppressLint
import android.content.*
import android.content.Context.CLIPBOARD_SERVICE
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
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.text.color
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.request.RequestOptions.bitmapTransform
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getApiFromName
import com.lagradost.cloudstream3.UIHelper.checkWrite
import com.lagradost.cloudstream3.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.UIHelper.getStatusBarHeight
import com.lagradost.cloudstream3.UIHelper.isAppInstalled
import com.lagradost.cloudstream3.UIHelper.isCastApiAvailable
import com.lagradost.cloudstream3.UIHelper.isConnectedToChromecast
import com.lagradost.cloudstream3.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.UIHelper.popupMenuNoIcons
import com.lagradost.cloudstream3.UIHelper.popupMenuNoIconsAndNoStringres
import com.lagradost.cloudstream3.UIHelper.requestRW
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.player.PlayerData
import com.lagradost.cloudstream3.ui.player.PlayerFragment
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.CastHelper.startCast
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore.getFolderName
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos

import com.lagradost.cloudstream3.utils.VideoDownloadManager.sanitizeFilename
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlinx.android.synthetic.main.fragment_result.*
import kotlinx.coroutines.Job
import java.io.File


const val MAX_SYNO_LENGH = 300

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
    )
}

fun ResultEpisode.getWatchProgress(): Float {
    return getDisplayPosition().toFloat() / duration
}

class ResultFragment : Fragment() {
    companion object {
        fun newInstance(url: String, slug: String, apiName: String) =
            ResultFragment().apply {
                arguments = Bundle().apply {
                    putString("url", url)
                    putString("slug", slug)
                    putString("apiName", apiName)
                }
            }
    }

    private var currentLoadingCount = 0 // THIS IS USED TO PREVENT LATE EVENTS, AFTER DISMISS WAS CLICKED
    private lateinit var viewModel: ResultViewModel
    private var allEpisodes: HashMap<Int, ArrayList<ExtractorLink>> = HashMap()
    private var allEpisodesSubs: HashMap<Int, ArrayList<SubtitleFile>> = HashMap()
    private var currentHeaderName: String? = null
    private var currentType: TvType? = null
    private var currentEpisodes: List<ResultEpisode>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        viewModel =
            ViewModelProvider(requireActivity()).get(ResultViewModel::class.java)
        return inflater.inflate(R.layout.fragment_result, container, false)
    }

    override fun onDestroy() {
        //requireActivity().viewModelStore.clear() // REMEMBER THE CLEAR
        super.onDestroy()
        activity?.let {
            it.window?.navigationBarColor =
                it.colorFromAttribute(R.attr.darkBackground)
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.let {
            it.window?.navigationBarColor =
                it.colorFromAttribute(R.attr.bitDarkerGrayBackground)
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
            }
        }
    }

    private var currentPoster: String? = null
    private var currentId: Int? = null
    private var currentIsMovie: Boolean? = null

    var url: String? = null

    private fun fromIndexToSeasonText(selection: Int?): String {
        return when (selection) {
            null -> "No Season"
            -2 -> "No Season"
            else -> "Season $selection"
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.fixPaddingStatusbar(result_scroll)
        activity?.fixPaddingStatusbar(result_barstatus)

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
        val slug = arguments?.getString("slug")
        val apiName = arguments?.getString("apiName") ?: return

        val api = getApiFromName(apiName)
        if (media_route_button != null) {
            val chromecastSupport = api.hasChromecastSupport

            media_route_button?.alpha = if (chromecastSupport) 1f else 0.3f
            if (!chromecastSupport) {
                media_route_button.setOnClickListener {
                    Toast.makeText(it.context, "This provider has no chromecast support", Toast.LENGTH_LONG).show()
                }
            }

            if (activity?.isCastApiAvailable() == true) {
                CastButtonFactory.setUpMediaRouteButton(activity, media_route_button)
                val castContext = CastContext.getSharedInstance(requireActivity().applicationContext)

                if (castContext.castState != CastState.NO_DEVICES_AVAILABLE) media_route_button.visibility = VISIBLE
                castContext.addCastStateListener { state ->
                    if (media_route_button != null) {
                        if (state == CastState.NO_DEVICES_AVAILABLE) media_route_button.visibility = GONE else {
                            if (media_route_button.visibility == GONE) media_route_button.visibility = VISIBLE
                        }
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

        result_toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
        result_toolbar.setNavigationOnClickListener {
            activity?.onBackPressed()
        }

        result_back.setOnClickListener {
            requireActivity().popCurrentPage()
        }

        fun handleAction(episodeClick: EpisodeClickEvent): Job = main {
            //val id = episodeClick.data.id
            val index = episodeClick.data.index
            val buildInPlayer = true
            currentLoadingCount++
            var currentLinks: ArrayList<ExtractorLink>? = null
            var currentSubs: ArrayList<SubtitleFile>? = null

            val showTitle = episodeClick.data.name ?: "Episode ${episodeClick.data.episode}"

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

                val skipLoading = if (apiName != null) {
                    getApiFromName(apiName).instantLinkLoading
                } else false

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
                        Toast.makeText(requireContext(), R.string.error_loading_links, Toast.LENGTH_SHORT).show()
                    }
                    else -> {

                    }
                }
                return false
            }

            fun aquireSingeExtractorLink(links: List<ExtractorLink>, title: String, callback: (ExtractorLink) -> Unit) {
                val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)

                builder.setTitle(title)
                builder.setItems(links.map { it.name }.toTypedArray()) { dia, which ->
                    callback.invoke(links[which])
                    dia?.dismiss()
                }
                builder.create().show()
            }

            fun aquireSingeExtractorLink(title: String, callback: (ExtractorLink) -> Unit) {
                aquireSingeExtractorLink(currentLinks ?: return, title, callback)
            }

            fun startChromecast(startIndex: Int) {
                val eps = currentEpisodes ?: return
                context?.startCast(
                    apiName ?: return,
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

            fun startDownload(links: List<ExtractorLink>) {
                val isMovie = currentIsMovie ?: return
                val titleName = sanitizeFilename(currentHeaderName ?: return)

                val meta = VideoDownloadManager.DownloadEpisodeMetadata(
                    episodeClick.data.id,
                    titleName,
                    apiName ?: return,
                    episodeClick.data.poster ?: currentPoster,
                    episodeClick.data.name,
                    if (isMovie) null else episodeClick.data.season,
                    if (isMovie) null else episodeClick.data.episode
                )

                val folder = when (currentType) {
                    TvType.Anime -> "Anime/$titleName"
                    TvType.Movie -> "Movies"
                    TvType.TvSeries -> "TVSeries/$titleName"
                    TvType.ONA -> "ONA"
                    else -> null
                }

                context?.let { ctx ->
                    // SET VISUAL KEYS
                    ctx.setKey(
                        DOWNLOAD_HEADER_CACHE, (currentId ?: return@let).toString(),
                        VideoDownloadHelper.DownloadHeaderCached(
                            apiName,
                            url ?: return@let,
                            currentType ?: return@let,
                            currentHeaderName ?: return@let,
                            currentPoster ?: return@let,
                            currentId ?: return@let
                        )
                    )

                    val epData = episodeClick.data
                    ctx.setKey(
                        getFolderName(
                            DOWNLOAD_EPISODE_CACHE,
                            (currentId ?: return@let).toString()
                        ), // 3 deep folder for faster acess
                        epData.id.toString(),
                        VideoDownloadHelper.DownloadEpisodeCached(
                            epData.name,
                            epData.poster,
                            epData.episode,
                            epData.season,
                            epData.id,
                            currentId ?: return@let,
                            epData.rating,
                            epData.descript
                        )
                    )

                    // DOWNLOAD VIDEO
                    VideoDownloadManager.downloadEpisode(
                        ctx,
                        url ?: return,
                        folder,
                        meta,
                        links
                    )
                }
            }

            val isLoaded = when (episodeClick.action) {
                ACTION_PLAY_EPISODE_IN_PLAYER -> true
                ACTION_CHROME_CAST_EPISODE -> requireLinks(true)
                ACTION_CHROME_CAST_MIRROR -> requireLinks(true)
                else -> requireLinks(false)
            }
            if (!isLoaded) return@main // CANT LOAD

            when (episodeClick.action) {
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
                    val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
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

                        val isConnected = requireContext().isConnectedToChromecast()
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
                ACTION_COPY_LINK -> {
                    aquireSingeExtractorLink("Copy Link") { link ->
                        val serviceClipboard =
                            (requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?)
                                ?: return@aquireSingeExtractorLink
                        val clip = ClipData.newPlainText(link.name, link.url)
                        serviceClipboard.setPrimaryClip(clip)
                        Toast.makeText(requireContext(), "Text Copied", Toast.LENGTH_SHORT).show()
                    }
                }

                ACTION_PLAY_EPISODE_IN_BROWSER -> {
                    aquireSingeExtractorLink("Play in Browser") { link ->
                        val i = Intent(ACTION_VIEW)
                        i.data = Uri.parse(link.url)
                        startActivity(i)
                    }
                }

                ACTION_CHROME_CAST_MIRROR -> {
                    aquireSingeExtractorLink("Cast Mirror") { link ->
                        val mirrorIndex = currentLinks?.indexOf(link) ?: -1
                        startChromecast(if (mirrorIndex == -1) 0 else mirrorIndex)
                    }
                }

                ACTION_CHROME_CAST_EPISODE -> {
                    startChromecast(0)
                }

                ACTION_PLAY_EPISODE_IN_VLC_PLAYER -> {
                    if (activity?.checkWrite() != true) {
                        activity?.requestRW()
                        if (activity?.checkWrite() == true) return@main
                    }
                    val data = currentLinks ?: return@main
                    val subs = currentSubs

                    val outputDir = requireContext().cacheDir
                    val outputFile = File.createTempFile("mirrorlist", ".m3u8", outputDir)
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
                            requireActivity(),
                            requireActivity().applicationContext.packageName + ".provider",
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
                    requireContext().setKey(VLC_LAST_ID_KEY, episodeClick.data.id)
                    activity?.startActivityForResult(vlcIntent, VLC_REQUEST_CODE)
                }

                ACTION_PLAY_EPISODE_IN_PLAYER -> {
                    if (buildInPlayer) {
                        (requireActivity() as AppCompatActivity).supportFragmentManager.beginTransaction()
                            .setCustomAnimations(
                                R.anim.enter_anim,
                                R.anim.exit_anim,
                                R.anim.pop_enter,
                                R.anim.pop_exit
                            )
                            .add(
                                R.id.homeRoot,
                                PlayerFragment.newInstance(
                                    PlayerData(index, null, 0),
                                    episodeClick.data.getRealPosition()
                                )
                            )
                            .commit()
                    }
                }

                ACTION_RELOAD_EPISODE -> {
                    viewModel.loadEpisode(episodeClick.data, false)
                }

                ACTION_DOWNLOAD_EPISODE -> {
                    startDownload(currentLinks ?: return@main)
                }

                ACTION_DOWNLOAD_MIRROR -> {
                    aquireSingeExtractorLink(
                        (currentLinks ?: return@main).filter { !it.isM3u8 },
                        "Download Mirror"
                    ) { link ->
                        startDownload(listOf(link))
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
                result_season_button?.popupMenuNoIconsAndNoStringres(
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
            if (result_episodes == null || result_episodes.adapter == null) return@observe
            result_episodes_text.text = "${episodes.size} Episode${if (episodes.size == 1) "" else "s"}"
            currentEpisodes = episodes
            activity?.runOnUiThread {
                (result_episodes.adapter as EpisodeAdapter).cardList = episodes
                (result_episodes.adapter as EpisodeAdapter).updateLayout()
                (result_episodes.adapter as EpisodeAdapter).notifyDataSetChanged()
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
                        updateVisStatus(2)

                        result_bookmark_button.text = "Watching"

                        currentHeaderName = d.name
                        currentType = d.type

                        currentPoster = d.posterUrl
                        currentIsMovie = !d.isEpisodeBased()

                        result_openinbrower.setOnClickListener {
                            val i = Intent(ACTION_VIEW)
                            i.data = Uri.parse(d.url)
                            startActivity(i)
                        }

                        result_share.setOnClickListener {
                            val i = Intent(ACTION_SEND)
                            i.type = "text/plain"
                            i.putExtra(EXTRA_SUBJECT, d.name)
                            i.putExtra(EXTRA_TEXT, d.url)
                            startActivity(createChooser(i, d.name))
                        }

                        val metadataInfoArray = ArrayList<Pair<String, String>>()
                        if (d is AnimeLoadResponse) {
                            val status = when (d.showStatus) {
                                null -> null
                                ShowStatus.Ongoing -> "Ongoing"
                                ShowStatus.Completed -> "Completed"
                            }
                            if (status != null) {
                                metadataInfoArray.add(Pair("Status", status))
                            }
                        }
                        if (d.year != null) metadataInfoArray.add(Pair("Year", d.year.toString()))
                        val rating = d.rating
                        if (rating != null) metadataInfoArray.add(
                            Pair(
                                "Rating",
                                "%.1f/10.0".format(rating.toFloat() / 10f).replace(",", ".")
                            )
                        )
                        val duration = d.duration
                        if (duration != null) metadataInfoArray.add(Pair("Duration", duration))

                        metadataInfoArray.add(Pair("Site", d.apiName))

                        if (metadataInfoArray.size > 0) {
                            result_metadata.visibility = VISIBLE
                            val text = SpannableStringBuilder()
                            val grayColor = ContextCompat.getColor(requireContext(), R.color.grayTextColor)
                            val textColor = ContextCompat.getColor(requireContext(), R.color.textColor)
                            for (meta in metadataInfoArray) {
                                text.color(grayColor) { append("${meta.first}: ") }
                                    .color(textColor) { append("${meta.second}\n") }
                            }
                            result_metadata.text = text
                        } else {
                            result_metadata.visibility = GONE
                        }

                        if (d.posterUrl != null) {
                            val glideUrl =
                                GlideUrl(d.posterUrl)
                            requireContext().let {

                                Glide.with(it)
                                    .load(glideUrl)
                                    .into(result_poster)

                                Glide.with(it)
                                    .load(glideUrl)
                                    .apply(bitmapTransform(BlurTransformation(80, 3)))
                                    .into(result_poster_blur)
                            }
                        }

                        if (d.plot != null) {
                            var syno = d.plot!!
                            if (syno.length > MAX_SYNO_LENGH) {
                                syno = syno.substring(0, MAX_SYNO_LENGH) + "..."
                            }
                            result_descript.setOnClickListener {
                                val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
                                builder.setMessage(d.plot).setTitle("Synopsis")
                                    .show()
                            }
                            result_descript.text = syno
                        } else {
                            result_descript.text = "No Plot found"
                        }

                        result_tag.removeAllViews()
                        result_tag_holder.visibility = GONE
                        // result_status.visibility = GONE

                        val tags = d.tags
                        if (tags == null) {
                            result_tag_holder.visibility = GONE
                        } else {
                            result_tag_holder.visibility = VISIBLE

                            for ((index, tag) in tags.withIndex()) {
                                val viewBtt = layoutInflater.inflate(R.layout.result_tag, null)
                                val btt = viewBtt.findViewById<MaterialButton>(R.id.result_tag_card)
                                btt.text = tag

                                result_tag.addView(viewBtt, index)
                            }
                        }

                        if (d.type == TvType.Movie && d is MovieLoadResponse) {
                            result_movie_parent.visibility = VISIBLE
                            result_episodes_text.visibility = GONE
                            result_episodes.visibility = GONE

                            result_play_movie.setOnClickListener {
                                val card = currentEpisodes?.first() ?: return@setOnClickListener
                                handleAction(EpisodeClickEvent(ACTION_CLICK_DEFAULT, card))
                            }
                            result_options.setOnClickListener {
                                val card = currentEpisodes?.first() ?: return@setOnClickListener
                                handleAction(EpisodeClickEvent(ACTION_SHOW_OPTIONS, card))
                            }
                        } else {
                            result_movie_parent.visibility = GONE
                            result_episodes_text.visibility = VISIBLE
                            result_episodes.visibility = VISIBLE
                        }

                        when (d) {
                            is AnimeLoadResponse -> {

                                // val preferEnglish = true
                                //val titleName = (if (preferEnglish) d.engName else d.japName) ?: d.name
                                val titleName = d.name
                                result_title.text = titleName
                                result_toolbar.title = titleName
                            }
                            else -> result_title.text = d.name
                        }
                    } else {
                        updateVisStatus(1)
                    }
                }
                is Resource.Failure -> {
                    result_error_text.text = data.errorString
                    updateVisStatus(1)
                }
                is Resource.Loading -> {
                    updateVisStatus(0)
                }
            }
        }

        if (apiName != null && slug != null) {
            result_reload_connectionerror.setOnClickListener {
                viewModel.load(requireContext(), slug, apiName)
            }

            if (url != null) {
                result_reload_connection_open_in_browser.setOnClickListener {
                    val i = Intent(ACTION_VIEW)
                    i.data = Uri.parse(url)
                    startActivity(i)
                }
            }

            if (viewModel.resultResponse.value == null)
                viewModel.load(requireContext(), slug, apiName)
        }
    }
}