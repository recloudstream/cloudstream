package com.lagradost.cloudstream3.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.HOMEPAGE_API
import kotlinx.android.synthetic.main.fragment_child_downloads.*
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observe(homeViewModel.apiName) {
            context?.setKey(HOMEPAGE_API, it)
        }

        observe(homeViewModel.page) {
            when (it) {
                is Resource.Success -> {
                    val d = it.value
                    (home_master_recycler?.adapter as ParentItemAdapter?)?.itemList = d.items
                    home_master_recycler?.adapter?.notifyDataSetChanged()
                }
                is Resource.Failure -> {

                }
                is Resource.Loading -> {

                }
            }
        }

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> = ParentItemAdapter(listOf()) {

        }

        home_master_recycler.adapter = adapter
        home_master_recycler.layoutManager = GridLayoutManager(context, 1)

        homeViewModel.load(context?.getKey(HOMEPAGE_API))
    }
}