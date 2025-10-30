package com.lagradost.cloudstream3.ui.settings.testing

import android.view.View
import androidx.fragment.app.activityViewModels
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentTestingBinding
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setSystemBarsPadding
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar

class TestFragment : BaseFragment<FragmentTestingBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentTestingBinding::inflate)
) {

    private val testViewModel: TestViewModel by activityViewModels()

    override fun fixPadding(view: View) {
        setSystemBarsPadding()
    }

    override fun onBindingCreated(binding: FragmentTestingBinding) {
        setUpToolbar(R.string.category_provider_test)
        setToolBarScrollFlags()

        binding.apply {
            providerTestRecyclerView.adapter = TestResultAdapter()

            testViewModel.init()
            if (testViewModel.isRunningTest) {
                providerTest.setState(TestView.TestState.Running)
            }

            observe(testViewModel.providerProgress) { (passed, failed, total) ->
                providerTest.setProgress(passed, failed, total)
            }

            observeNullable(testViewModel.providerResults) {
                safe {
                    val newItems = it.sortedBy { api -> api.first.name }
                    (providerTestRecyclerView.adapter as? TestResultAdapter)?.submitList(
                        newItems
                    )
                }
            }

            providerTest.setOnPlayButtonListener { state ->
                when (state) {
                    TestView.TestState.Stopped -> testViewModel.stopTest()
                    TestView.TestState.Running -> testViewModel.startTest()
                    TestView.TestState.None -> testViewModel.startTest()
                }
            }

            if (isLayout(TV)) {
                providerTest.playPauseButton?.isFocusableInTouchMode = true
                providerTest.playPauseButton?.requestFocus()
            }

            providerTest.playPauseButton?.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    providerTestAppbar.setExpanded(true, true)
                }
            }

            fun focusRecyclerView() {
                // Hack to make it possible to focus the recyclerview.
                if (isLayout(TV)) {
                    providerTestRecyclerView.requestFocus()
                    providerTestAppbar.setExpanded(false, true)
                }
            }

            providerTest.setOnMainClick {
                testViewModel.setFilterMethod(TestViewModel.ProviderFilter.All)
                focusRecyclerView()
            }
            providerTest.setOnFailedClick {
                testViewModel.setFilterMethod(TestViewModel.ProviderFilter.Failed)
                focusRecyclerView()
            }
            providerTest.setOnPassedClick {
                testViewModel.setFilterMethod(TestViewModel.ProviderFilter.Passed)
                focusRecyclerView()
            }
        }
    }
}