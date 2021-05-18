package com.lagradost.cloudstream3.ui.result

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.marginTop
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.request.RequestOptions.bitmapTransform
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.UIHelper.getStatusBarHeight
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlinx.android.synthetic.main.fragment_result.*
import kotlinx.android.synthetic.main.fragment_search.*


const val MAX_SYNO_LENGH = 600

data class ResultEpisode(
    val name: String?,
    val episode: Int,
    val data: Any,
    val apiName: String,
    val id: Int,
    val watchProgress: Float, // 0-1
)

class ResultFragment : Fragment() {
    fun newInstance(url: String, slug: String, apiName: String) =
        ResultFragment().apply {
            arguments = Bundle().apply {
                putString("url", url)
                putString("slug", slug)
                putString("apiName", apiName)
            }
        }

    private lateinit var viewModel: ResultViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        viewModel =
            ViewModelProvider(this).get(ResultViewModel::class.java)

        return inflater.inflate(R.layout.fragment_result, container, false)
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
            if(result_poster_blur == null) return@OnScrollChangeListener
            result_poster_blur.alpha = maxOf(0f, (0.3f - scrollY / 1000f))
            result_barstatus.alpha = scrollY / 200f
            result_barstatus.visibility = if(scrollY > 0) View.VISIBLE else View.GONE
        })

        result_toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
        result_toolbar.setNavigationOnClickListener {
            activity?.onBackPressed()
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

                        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = activity?.let {
                            EpisodeAdapter(
                                it,
                                ArrayList(),
                                result_episodes,
                            )
                        }

                        result_episodes.adapter = adapter
                        result_episodes.layoutManager = GridLayoutManager(context, 1)

                        if (d is AnimeLoadResponse) {
                            val preferEnglish = true
                            val titleName = (if (preferEnglish) d.engName else d.japName) ?: d.name
                            result_title.text = titleName
                            result_toolbar.title = titleName

                            if (d.plot != null) {
                                var syno = d.plot
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

                            result_tags.text = (d.tags ?: ArrayList()).joinToString(separator = " | ")
                            val isDub = d.dubEpisodes != null && d.dubEpisodes.size > 0
                            val dataList = (if (isDub) d.dubEpisodes else d.subEpisodes)
                            if (dataList != null && apiName != null) {
                                val episodes = ArrayList<ResultEpisode>()
                                for ((index, i) in dataList.withIndex()) {
                                    episodes.add(ResultEpisode(
                                        null, // TODO ADD NAMES
                                        index + 1, //TODO MAKE ABLE TO NOT HAVE SOME EPISODE
                                        i,
                                        apiName,
                                        (slug + index).hashCode(),
                                        (index * 0.1f),//TODO TEST; REMOVE
                                    ))
                                }
                                (result_episodes.adapter as EpisodeAdapter).cardList = episodes
                                (result_episodes.adapter as EpisodeAdapter).notifyDataSetChanged()
                            }
                        } else {
                            result_title.text = d.name
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