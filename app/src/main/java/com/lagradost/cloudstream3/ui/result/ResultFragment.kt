package com.lagradost.cloudstream3.ui.result

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
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
import com.lagradost.cloudstream3.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.UIHelper.getStatusBarHeight
import com.lagradost.cloudstream3.UIHelper.isCastApiAvailable
import com.lagradost.cloudstream3.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.UIHelper.popupMenuNoIcons
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.player.PlayerData
import com.lagradost.cloudstream3.ui.player.PlayerFragment
import com.lagradost.cloudstream3.utils.CastHelper.startCast
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.ExtractorLink
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlinx.android.synthetic.main.fragment_result.*


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
): ResultEpisode {
    val posDur = getViewPos(id)
    return ResultEpisode(name,
        poster,
        episode,
        season,
        data,
        apiName,
        id,
        index,
        posDur?.position ?: 0,
        posDur?.duration ?: 0)
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
    private var currentHeaderName: String? = null
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
    fun updateVisStatus(state: Int) {
        when (state) {
            0 -> {
                result_loading.visibility = VISIBLE
                result_finish_loading.visibility = GONE
                result_reload_connectionerror.visibility = GONE
                result_reload_connection_open_in_browser.visibility = GONE
            }
            1 -> {
                result_loading.visibility = GONE
                result_finish_loading.visibility = GONE
                result_reload_connectionerror.visibility = VISIBLE
                result_reload_connection_open_in_browser.visibility = if (url == null) GONE else VISIBLE
            }
            2 -> {
                result_loading.visibility = GONE
                result_finish_loading.visibility = VISIBLE
                result_reload_connectionerror.visibility = GONE
                result_reload_connection_open_in_browser.visibility = GONE
            }
        }
    }

    private var currentPoster: String? = null
    private var currentIsMovie: Boolean? = null

    var url: String? = null

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.fixPaddingStatusbar(result_scroll)
        activity?.fixPaddingStatusbar(result_barstatus)

        val backParameter = result_back.layoutParams as CoordinatorLayout.LayoutParams
        backParameter.setMargins(backParameter.leftMargin,
            backParameter.topMargin + requireContext().getStatusBarHeight(),
            backParameter.rightMargin,
            backParameter.bottomMargin)
        result_back.layoutParams = backParameter

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
        // activity?.fixPaddingStatusbar(result_toolbar)

        url = arguments?.getString("url")
        val slug = arguments?.getString("slug")
        val apiName = arguments?.getString("apiName")

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

        fun handleAction(episodeClick: EpisodeClickEvent) {
            //val id = episodeClick.data.id
            val index = episodeClick.data.index
            val buildInPlayer = true
            currentLoadingCount++
            when (episodeClick.action) {
                ACTION_CHROME_CAST_EPISODE -> {

                    val skipLoading = if (apiName != null) {
                        getApiFromName(apiName).instantLinkLoading
                    } else false

                    var dialog: AlertDialog? = null
                    val currentLoad = currentLoadingCount

                    if (!skipLoading) {
                        val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustomTransparent)
                        val customLayout = layoutInflater.inflate(R.layout.dialog_loading, null)
                        builder.setView(customLayout)

                        dialog = builder.create()

                        dialog.show()
                        dialog.setOnDismissListener {
                            currentLoadingCount++
                        }
                    }

                    // Toast.makeText(activity, "Loading links", Toast.LENGTH_SHORT).show()

                    viewModel.loadEpisode(episodeClick.data, true) { data ->
                        if (currentLoadingCount != currentLoad) return@loadEpisode
                        dialog?.dismiss()

                        when (data) {
                            is Resource.Failure -> {
                                Toast.makeText(activity, "Failed to load links", Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Success -> {
                                val eps = currentEpisodes ?: return@loadEpisode
                                context?.startCast(
                                    apiName ?: return@loadEpisode,
                                    currentIsMovie ?: return@loadEpisode,
                                    currentHeaderName,
                                    currentPoster,
                                    episodeClick.data.index,
                                    eps,
                                    sortUrls(data.value),
                                    startTime = episodeClick.data.getRealPosition()
                                )
                            }
                        }
                    }
                }

                ACTION_PLAY_EPISODE_IN_PLAYER -> {
                    if (buildInPlayer) {
                        (requireActivity() as AppCompatActivity).supportFragmentManager.beginTransaction()
                            .setCustomAnimations(R.anim.enter_anim,
                                R.anim.exit_anim,
                                R.anim.pop_enter,
                                R.anim.pop_exit)
                            .add(R.id.homeRoot,
                                PlayerFragment.newInstance(PlayerData(index, null, 0),
                                    episodeClick.data.getRealPosition())
                            )
                            .commit()
                    }
                }
                ACTION_RELOAD_EPISODE -> {
                    /*viewModel.load(episodeClick.data) { res ->
                        if (res is Resource.Success) {
                            playEpisode(allEpisodes[id], index)
                        }
                    }*/
                }

            }
        }

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = activity?.let { it ->
            EpisodeAdapter(
                it,
                ArrayList(),
                result_episodes,
            ) { episodeClick ->
                handleAction(episodeClick)
            }
        }

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

        observe(viewModel.episodes) { episodes ->
            if (result_episodes == null || result_episodes.adapter == null) return@observe
            result_episodes_text.text = "${episodes.size} Episode${if (episodes.size == 1) "" else "s"}"
            currentEpisodes = episodes
            (result_episodes.adapter as EpisodeAdapter).cardList = episodes
            (result_episodes.adapter as EpisodeAdapter).notifyDataSetChanged()
        }

        observe(viewModel.resultResponse) { data ->
            when (data) {
                is Resource.Success -> {
                    val d = data.value
                    if (d is LoadResponse) {
                        updateVisStatus(2)

                        result_bookmark_button.text = "Watching"

                        currentHeaderName = d.name

                        currentPoster = d.posterUrl
                        currentIsMovie = !d.isEpisodeBased()

                        result_openinbrower.setOnClickListener {
                            val i = Intent(Intent.ACTION_VIEW)
                            i.data = Uri.parse(d.url)
                            startActivity(i)
                        }

                        result_share.setOnClickListener {
                            val i = Intent(Intent.ACTION_SEND)
                            i.type = "text/plain"
                            i.putExtra(Intent.EXTRA_SUBJECT, d.name)
                            i.putExtra(Intent.EXTRA_TEXT, d.url)
                            startActivity(Intent.createChooser(i, d.name))
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
                        if (rating != null) metadataInfoArray.add(Pair("Rating",
                            "%.2f/10.0".format(rating.toFloat() / 10f).replace(",", ".")))
                        val duration = d.duration
                        if (duration != null) metadataInfoArray.add(Pair("Duration", duration))

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

                        fun playEpisode(data: ArrayList<ExtractorLink>?, episodeIndex: Int) {
                            if (data != null) {

/*
if (activity?.checkWrite() != true) {
    activity?.requestRW()
    if (activity?.checkWrite() == true) return
}

val outputDir = context!!.cacheDir
val outputFile = File.createTempFile("mirrorlist", ".m3u8", outputDir)
var text = "#EXTM3U";
for (link in data.sortedBy { -it.quality }) {
    text += "\n#EXTINF:, ${link.name}\n${link.url}"
}
outputFile.writeText(text)
val VLC_PACKAGE = "org.videolan.vlc"
val VLC_INTENT_ACTION_RESULT = "org.videolan.vlc.player.result"
val VLC_COMPONENT: ComponentName =
    ComponentName(VLC_PACKAGE, "org.videolan.vlc.gui.video.VideoPlayerActivity")
val REQUEST_CODE = 42

val FROM_START = -1
val FROM_PROGRESS = -2

val vlcIntent = Intent(VLC_INTENT_ACTION_RESULT)

vlcIntent.setPackage(VLC_PACKAGE)
vlcIntent.addFlags(FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
vlcIntent.addFlags(FLAG_GRANT_PREFIX_URI_PERMISSION)
vlcIntent.addFlags(FLAG_GRANT_READ_URI_PERMISSION)
vlcIntent.addFlags(FLAG_GRANT_WRITE_URI_PERMISSION)

vlcIntent.setDataAndType(FileProvider.getUriForFile(activity!!,
    activity!!.applicationContext.packageName + ".provider",
    outputFile), "video/*")

val startId = FROM_PROGRESS

var position = startId
if (startId == FROM_START) {
    position = 1
} else if (startId == FROM_PROGRESS) {
    position = 0
}

vlcIntent.putExtra("position", position)
//vlcIntent.putExtra("title", episodeName)

vlcIntent.setComponent(VLC_COMPONENT)

activity?.startActivityForResult(vlcIntent, REQUEST_CODE)
*/
 */
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

                        when (d.type) {
                            TvType.Movie -> {
                                result_play_movie.visibility = VISIBLE
                                result_episodes_text.visibility = GONE
                                result_episodes.visibility = GONE

                                result_play_movie.setOnClickListener {
                                    val card = currentEpisodes?.first() ?: return@setOnClickListener
                                    if (requireContext().isCastApiAvailable()) {
                                        val castContext = CastContext.getSharedInstance(requireContext())

                                        if (castContext.castState == CastState.CONNECTED) {
                                            handleAction(EpisodeClickEvent(ACTION_CHROME_CAST_EPISODE, card))
                                        } else {
                                            handleAction(EpisodeClickEvent(ACTION_PLAY_EPISODE_IN_PLAYER, card))
                                        }
                                    } else {
                                        handleAction(EpisodeClickEvent(ACTION_PLAY_EPISODE_IN_PLAYER, card))
                                    }
                                }
                            }
                            else -> {
                                result_play_movie.visibility = GONE
                                result_episodes_text.visibility = VISIBLE
                                result_episodes.visibility = VISIBLE
                            }
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
                    val i = Intent(Intent.ACTION_VIEW)
                    i.data = Uri.parse(url)
                    startActivity(i)
                }
            }

            if (viewModel.resultResponse.value == null)
                viewModel.load(requireContext(), slug, apiName)
        }
    }
}