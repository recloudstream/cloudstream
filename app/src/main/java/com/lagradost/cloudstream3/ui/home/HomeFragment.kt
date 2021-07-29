package com.lagradost.cloudstream3.ui.home

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.UIHelper.getGridIsCompact
import com.lagradost.cloudstream3.UIHelper.loadResult
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

        observe(homeViewModel.apiName) {
            context?.setKey(HOMEPAGE_API, it)
        }

        observe(homeViewModel.page) {
            when (it) {
                is Resource.Success -> {
                    val d = it.value
                    (home_master_recycler?.adapter as ParentItemAdapter?)?.items = d.items
                    home_master_recycler?.adapter?.notifyDataSetChanged()
                }
                is Resource.Failure -> {

                }
                is Resource.Loading -> {

                }
            }
        }

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> = ParentItemAdapter(listOf(), { card ->
            (activity as AppCompatActivity).loadResult(card.url, card.slug, card.apiName)
        }, { item ->
            val bottomSheetDialogBuilder = BottomSheetDialog(view.context)
            bottomSheetDialogBuilder.setContentView(R.layout.home_episodes_expanded)
            val title = bottomSheetDialogBuilder.findViewById<TextView>(R.id.home_expanded_text)!!
            title.text = item.name
            val recycle = bottomSheetDialogBuilder.findViewById<AutofitRecyclerView>(R.id.home_expanded_recycler)!!

            // Span settings
            recycle.spanCount = currentSpan

            recycle.adapter = SearchAdapter(item.list, recycle) { card ->
                bottomSheetDialogBuilder.dismiss()
                (activity as AppCompatActivity).loadResult(card.url, card.slug, card.apiName)
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