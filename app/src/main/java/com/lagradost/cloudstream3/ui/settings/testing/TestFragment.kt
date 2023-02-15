package com.lagradost.cloudstream3.ui.settings.testing

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.activityViewModels
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import kotlinx.android.synthetic.main.fragment_testing.*
import kotlinx.android.synthetic.main.view_test.*


class TestFragment : Fragment() {

    private val testViewModel: TestViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setUpToolbar(R.string.category_provider_test)
        super.onViewCreated(view, savedInstanceState)

        provider_test_recycler_view?.adapter = TestResultAdapter(
            mutableListOf()
        )

        testViewModel.init()
        if (testViewModel.isRunningTest) {
            provider_test?.setState(TestView.TestState.Running)
        }

        observe(testViewModel.providerProgress) { (passed, failed, total) ->
            provider_test?.setProgress(passed, failed, total)
        }

        observeNullable(testViewModel.providerResults) {
            normalSafeApiCall {
                val newItems = it.sortedBy { api -> api.first.name }
                (provider_test_recycler_view?.adapter as? TestResultAdapter)?.updateList(
                    newItems
                )
            }
        }

        provider_test?.setOnPlayButtonListener { state ->
            when (state) {
                TestView.TestState.Stopped -> testViewModel.stopTest()
                TestView.TestState.Running -> testViewModel.startTest()
                TestView.TestState.None -> testViewModel.startTest()
            }
        }

        if (isTrueTvSettings()) {
            tests_play_pause?.isFocusableInTouchMode = true
            tests_play_pause?.requestFocus()
        }

        provider_test?.playPauseButton?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                provider_test_appbar?.setExpanded(true, true)
            }
        }

        fun focusRecyclerView() {
            // Hack to make it possible to focus the recyclerview.
            if (isTrueTvSettings()) {
                provider_test_recycler_view?.requestFocus()
                provider_test_appbar?.setExpanded(false, true)
            }
        }

        provider_test?.setOnMainClick {
            testViewModel.setFilterMethod(TestViewModel.ProviderFilter.All)
            focusRecyclerView()
        }
        provider_test?.setOnFailedClick {
            testViewModel.setFilterMethod(TestViewModel.ProviderFilter.Failed)
            focusRecyclerView()
        }
        provider_test?.setOnPassedClick {
            testViewModel.setFilterMethod(TestViewModel.ProviderFilter.Passed)
            focusRecyclerView()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_testing, container, false)
    }
}