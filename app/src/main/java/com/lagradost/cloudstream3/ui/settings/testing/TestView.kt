package com.lagradost.cloudstream3.ui.settings.testing

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ContentLoadingProgressBar
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.AppUtils.animateProgressTo

class TestView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs) {
    enum class TestState(@StringRes val stringRes: Int, @DrawableRes val icon: Int) {
        None(R.string.start, R.drawable.ic_baseline_play_arrow_24),

        //        Paused(R.string.resume, R.drawable.ic_baseline_play_arrow_24),
        Stopped(R.string.restart, R.drawable.ic_baseline_play_arrow_24),
        Running(R.string.stop, R.drawable.pause_to_play),
    }

    var mainSection: View? = null
    var testsPassedSection: View? = null
    var testsFailedSection: View? = null

    var mainSectionText: TextView? = null
    var mainSectionHeader: TextView? = null
    var testsPassedSectionText: TextView? = null
    var testsFailedSectionText: TextView? = null
    var totalProgressBar: ContentLoadingProgressBar? = null

    var playPauseButton: MaterialButton? = null
    var stateListener: (TestState) -> Unit = {}

    private var state = TestState.None

    init {
        LayoutInflater.from(context).inflate(R.layout.view_test, this, true)

        mainSection = findViewById(R.id.main_test_section)
        testsPassedSection = findViewById(R.id.passed_test_section)
        testsFailedSection = findViewById(R.id.failed_test_section)

        mainSectionHeader = findViewById(R.id.main_test_header)
        mainSectionText = findViewById(R.id.main_test_section_progress)
        testsPassedSectionText = findViewById(R.id.passed_test_section_progress)
        testsFailedSectionText = findViewById(R.id.failed_test_section_progress)

        totalProgressBar = findViewById(R.id.test_total_progress)
        playPauseButton = findViewById(R.id.tests_play_pause)

        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.TestView)
            val headerText = typedArray.getString(R.styleable.TestView_header_text)
            mainSectionHeader?.text = headerText
            typedArray.recycle()
        }

        playPauseButton?.setOnClickListener {
            val newState = when (state) {
                TestState.None -> TestState.Running
                TestState.Running -> TestState.Stopped
                TestState.Stopped -> TestState.Running
            }
            setState(newState)
        }
    }

    fun setOnPlayButtonListener(listener: (TestState) -> Unit) {
        stateListener = listener
    }

    fun setState(newState: TestState) {
        state = newState
        stateListener.invoke(newState)
        playPauseButton?.setText(newState.stringRes)
        playPauseButton?.icon = ContextCompat.getDrawable(context, newState.icon)
    }

    fun setProgress(passed: Int, failed: Int, total: Int?) {
        val totalProgress = passed + failed
        mainSectionText?.text = "$totalProgress / ${total?.toString() ?: "?"}"
        testsPassedSectionText?.text = passed.toString()
        testsFailedSectionText?.text = failed.toString()

        totalProgressBar?.max = (total ?: 0) * 1000
        totalProgressBar?.animateProgressTo(totalProgress * 1000)

        totalProgressBar?.isVisible = !(totalProgress == 0 || (total ?: 0) == 0)
        if (totalProgress == total) {
            setState(TestState.Stopped)
        }
    }

    fun setMainHeader(@StringRes header: Int) {
        mainSectionHeader?.setText(header)
    }

    fun setOnMainClick(listener: OnClickListener) {
        mainSection?.setOnClickListener(listener)
    }

    fun setOnPassedClick(listener: OnClickListener) {
        testsPassedSection?.setOnClickListener(listener)
    }

    fun setOnFailedClick(listener: OnClickListener) {
        testsFailedSection?.setOnClickListener(listener)
    }
}