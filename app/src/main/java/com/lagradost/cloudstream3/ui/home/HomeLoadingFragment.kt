package com.lagradost.cloudstream3.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.selectHomepage
import com.lagradost.cloudstream3.ui.settings.SettingsFragment
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import kotlinx.android.synthetic.main.fragment_home_loading.*

class HomeLoadingFragment : Fragment() {
    private val homeViewModel: HomeViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home_loading, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        home_loading_shimmer?.startShimmer()
        requireContext().fixPaddingStatusbar(home_loading_statusbar)
        home_change_api_loading.setOnClickListener {
            requireContext().selectHomepage(homeViewModel.apiNameString) { api ->
                homeViewModel.loadAndCancel(api)
            }
        }
        if (SettingsFragment.isTrueTvSettings()) {
            home_change_api_loading?.isVisible = true
            home_change_api_loading?.isFocusable = true
            home_change_api_loading?.isFocusableInTouchMode = true
        } else {
            home_change_api_loading?.isVisible = false
        }
    }
}