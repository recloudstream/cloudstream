package com.lagradost.cloudstream3.ui.result

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.Intent.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.request.RequestOptions.bitmapTransform
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.UIHelper.checkWrite
import com.lagradost.cloudstream3.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.UIHelper.loadResult
import com.lagradost.cloudstream3.UIHelper.requestRW
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.player.PlayerData
import com.lagradost.cloudstream3.ui.player.PlayerFragment
import com.lagradost.cloudstream3.utils.ExtractorLink
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlinx.android.synthetic.main.fragment_result.*
import java.io.File


const val MAX_SYNO_LENGH = 600

data class ResultEpisode(
    val name: String?,
    val episode: Int,
    val data: Any,
    val apiName: String,
    val id: Int,
    val index: Int,
    val watchProgress: Float, // 0-1
)

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


    private lateinit var viewModel: ResultViewModel
    private var allEpisodes: HashMap<Int, ArrayList<ExtractorLink>> = HashMap()

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
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.fixPaddingStatusbar(result_scroll)
        activity?.fixPaddingStatusbar(result_barstatus)
        // activity?.fixPaddingStatusbar(result_toolbar)

        val url = arguments?.getString("url")
        val slug = arguments?.getString("slug")
        val apiName = arguments?.getString("apiName")

        result_scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (result_poster_blur == null) return@OnScrollChangeListener
            result_poster_blur.alpha = maxOf(0f, (0.3f - scrollY / 1000f))
            result_barstatus.alpha = scrollY / 200f
            result_barstatus.visibility = if (scrollY > 0) View.VISIBLE else View.GONE
        })

        result_toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
        result_toolbar.setNavigationOnClickListener {
            activity?.onBackPressed()
        }

        observe(viewModel.allEpisodes) {
            allEpisodes = it
        }

        observe(viewModel.episodes) { episodes ->
            (result_episodes.adapter as EpisodeAdapter).cardList = episodes
            (result_episodes.adapter as EpisodeAdapter).notifyDataSetChanged()
        }

        observe(viewModel.resultResponse) { data ->
            when (data) {
                is Resource.Success -> {
                    val d = data.value
                    if (d is LoadResponse) {
                        result_bookmark_button.text = "Watching"

                        if (d.year != null) {
                            result_year.visibility = View.VISIBLE
                            result_year.text = d.year.toString()
                        } else {
                            result_year.visibility = View.GONE
                        }

                        if (d.posterUrl != null) {
                            val glideUrl =
                                GlideUrl(d.posterUrl)
                            context!!.let {
                                /*
                            Glide.with(it)
                                .load(glideUrl)
                                .into(result_poster)*/

                                Glide.with(it)
                                    .load(glideUrl)
                                    .apply(bitmapTransform(BlurTransformation(10, 3)))
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

                        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = activity?.let { it ->
                            EpisodeAdapter(
                                it,
                                ArrayList(),
                                result_episodes,
                            ) { episodeClick ->
                                val id = episodeClick.data.id
                                val index = episodeClick.data.index
                                val buildInPlayer = true
                                if (buildInPlayer) {
                                    (requireActivity() as AppCompatActivity).supportFragmentManager.beginTransaction()
                                        .setCustomAnimations(R.anim.enter_anim,
                                            R.anim.exit_anim,
                                            R.anim.pop_enter,
                                            R.anim.pop_exit)
                                        .add(R.id.homeRoot, PlayerFragment.newInstance(PlayerData(index)))
                                        .commit()
                                } else {
                                    when (episodeClick.action) {

                                        /*
                                        ACTION_PLAY_EPISODE -> {
                                            if (allEpisodes.containsKey(id)) {
                                                playEpisode(allEpisodes[id], index)
                                            } else {
                                                viewModel.loadEpisode(episodeClick.data) { res ->
                                                    if (res is Resource.Success) {
                                                        playEpisode(allEpisodes[id], index)
                                                    }
                                                }
                                            }
                                        }
                                        ACTION_RELOAD_EPISODE -> viewModel.loadEpisode(episodeClick.data) { res ->
                                            if (res is Resource.Success) {
                                                playEpisode(allEpisodes[id], index)
                                            }
                                        }*/
                                    }
                                }
                            }
                        }

                        result_episodes.adapter = adapter
                        result_episodes.layoutManager = GridLayoutManager(context, 1)

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

                        when (d) {
                            is AnimeLoadResponse -> {
                                val preferEnglish = true
                                val titleName = (if (preferEnglish) d.engName else d.japName) ?: d.name
                                result_title.text = titleName
                                result_toolbar.title = titleName

                                result_tags.text = (d.tags ?: ArrayList()).joinToString(separator = " | ")
                            }
                            else -> result_title.text = d.name
                        }
                    }
                }
                is Resource.Failure -> {

                }
            }
        }

        if (viewModel.resultResponse.value == null && apiName != null && slug != null)
            viewModel.load(slug, apiName)
    }
}