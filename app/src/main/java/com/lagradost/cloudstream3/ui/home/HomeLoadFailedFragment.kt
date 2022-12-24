package com.lagradost.cloudstream3.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.selectHomepage
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import kotlinx.android.synthetic.main.fragment_home_load_failed.*

class HomeLoadFailedFragment : Fragment() {
    private val homeViewModel: HomeViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home_load_failed, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        home_reload_connectionerror.setOnClickListener {
            requireContext().selectHomepage(homeViewModel.apiNameString) { api ->
                homeViewModel.loadAndCancel(api)
            }
        }
        home_reload_connection_open_in_browser.setOnClickListener { button ->
            val validAPIs = APIHolder.apis//.filter { api -> api.hasMainPage }

            button.popupMenuNoIconsAndNoStringRes(validAPIs.mapIndexed { index, api ->
                Pair(
                    index,
                    api.name
                )
            }) {
                try {
                    val i = Intent(Intent.ACTION_VIEW)
                    i.data = Uri.parse(validAPIs[itemId].mainUrl)
                    startActivity(i)
                } catch (e: Exception) {
                    logError(e)
                }
            }
        }
        result_error_text.text = (homeViewModel.page.value as? Resource.Failure)?.errorString
    }
}