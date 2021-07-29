package com.lagradost.cloudstream3.ui.home

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.UIHelper.getGridIsCompact
import com.lagradost.cloudstream3.UIHelper.loadSearchResult
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.HOMEPAGE_API
import kotlinx.android.synthetic.main.fragment_home.*

class HomeFragment : Fragment() {
    private lateinit var homeViewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    private val configEvent = Event<Int>()
    private var currentSpan = 1
    private var currentHomePage: HomePageResponse? = null
    var currentMainIndex = 0
    var currentMainList: ArrayList<SearchResponse> = ArrayList()

    private fun toggleMainVisibility(visible: Boolean) {
        home_main_holder.visibility = if (visible) View.VISIBLE else View.GONE
    }

    @SuppressLint("SetTextI18n")
    private fun chooseRandomMainPage(item: SearchResponse? = null): SearchResponse? {
        val home = currentHomePage
        if (home != null && home.items.isNotEmpty()) {
            var random: SearchResponse? = item

            var breakCount = 0
            val MAX_BREAK_COUNT = 10

            while (random?.posterUrl == null) {
                random = home.items.random().list.random()
                breakCount++
                if (breakCount > MAX_BREAK_COUNT) {
                    break
                }
            }

            if (random?.posterUrl != null) {
                home_main_poster.setOnClickListener {
                    activity.loadSearchResult(random)
                }
                home_main_play.setOnClickListener {
                    activity.loadSearchResult(random)
                }
                home_main_info.setOnClickListener {
                    activity.loadSearchResult(random)
                }

                home_main_text.text = random.name + if (random is AnimeSearchResponse) {
                    random.dubStatus?.joinToString(prefix = " • ", separator = " | ") { it.name }
                } else ""
                val glideUrl =
                    GlideUrl(random.posterUrl)
                requireContext().let {
                    Glide.with(it)
                        .load(glideUrl)
                        .into(home_main_poster)
/*
                    Glide.with(it)
                        .load(glideUrl)
                        .apply(RequestOptions.bitmapTransform(BlurTransformation(80, 3)))
                        .into(result_poster_blur)*/
                }

                toggleMainVisibility(true)
                return random
            } else {
                toggleMainVisibility(false)
                return null
            }
        }
        return null
    }

    private fun fixGrid() {
        val compactView = activity?.getGridIsCompact() ?: false
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation

        currentSpan = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            spanCountLandscape
        } else {
            spanCountPortrait
        }
        configEvent.invoke(currentSpan)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fixGrid()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fixGrid()

        home_reroll_next.setOnClickListener {
            currentMainIndex++
            if (currentMainIndex >= currentMainList.size) {
                val newItem = chooseRandomMainPage()
                if (newItem != null) {
                    currentMainList.add(newItem)
                }
                currentMainIndex = currentMainList.size - 1
            }
            chooseRandomMainPage(currentMainList[currentMainIndex])
        }

        home_reroll_prev.setOnClickListener {
            currentMainIndex--
            if (currentMainIndex < 0) {
                val newItem = chooseRandomMainPage()
                if (newItem != null) {
                    currentMainList.add(0, newItem)
                }
                currentMainIndex = 0
            }
            chooseRandomMainPage(currentMainList[currentMainIndex])
        }

        observe(homeViewModel.apiName) {
            context?.setKey(HOMEPAGE_API, it)
        }

        observe(homeViewModel.page) {
            when (it) {
                is Resource.Success -> {
                    val d = it.value
                    currentHomePage = d
                    (home_master_recycler?.adapter as ParentItemAdapter?)?.items = d.items
                    home_master_recycler?.adapter?.notifyDataSetChanged()
                    currentMainList.clear()
                    chooseRandomMainPage()?.let { response ->
                        currentMainList.add(response)
                    }
                    currentMainIndex = 0
                }
                is Resource.Failure -> {

                }
                is Resource.Loading -> {

                }
            }
        }

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> = ParentItemAdapter(listOf(), { card ->
            activity.loadSearchResult(card)
        }, { item ->
            val bottomSheetDialogBuilder = BottomSheetDialog(view.context)
            bottomSheetDialogBuilder.setContentView(R.layout.home_episodes_expanded)
            val title = bottomSheetDialogBuilder.findViewById<TextView>(R.id.home_expanded_text)!!
            title.text = item.name
            val recycle = bottomSheetDialogBuilder.findViewById<AutofitRecyclerView>(R.id.home_expanded_recycler)!!
            val titleHolder = bottomSheetDialogBuilder.findViewById<FrameLayout>(R.id.home_expanded_drag_down)!!

            titleHolder.setOnClickListener {
                bottomSheetDialogBuilder.dismiss()
            }

            // Span settings
            recycle.spanCount = currentSpan

            recycle.adapter = SearchAdapter(item.list, recycle) { card ->
                bottomSheetDialogBuilder.dismiss()
                activity.loadSearchResult(card)
            }

            val spanListener = { span: Int ->
                recycle.spanCount = span
                (recycle.adapter as SearchAdapter).notifyDataSetChanged()
            }

            configEvent += spanListener

            bottomSheetDialogBuilder.setOnDismissListener {
                configEvent -= spanListener
            }

            (recycle.adapter as SearchAdapter).notifyDataSetChanged()

            bottomSheetDialogBuilder.show()
        })

        context?.fixPaddingStatusbar(home_root)

        home_master_recycler.adapter = adapter
        home_master_recycler.layoutManager = GridLayoutManager(context, 1)

        homeViewModel.load(context?.getKey(HOMEPAGE_API))
    }
}