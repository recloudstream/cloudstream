package com.lagradost.cloudstream3.ui.result

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.discord.panels.PanelsChildGestureRegionObserver
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.player.CSPlayerEvent
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.utils.IOnBackPressed
import kotlinx.android.synthetic.main.fragment_result.*
import kotlinx.android.synthetic.main.fragment_result.result_smallscreen_holder
import kotlinx.android.synthetic.main.fragment_result_swipe.*
import kotlinx.android.synthetic.main.fragment_result_tv.*
import kotlinx.android.synthetic.main.fragment_trailer.*
import kotlinx.android.synthetic.main.trailer_custom_layout.*


open class ResultTrailerPlayer : com.lagradost.cloudstream3.ui.player.FullScreenPlayer(),
    PanelsChildGestureRegionObserver.GestureRegionsListener, IOnBackPressed {

    override var lockRotation = false
    override var isFullScreenPlayer = false
    override var hasPipModeSupport = false

    companion object {
        const val TAG = "RESULT_TRAILER"
    }

    var playerWidthHeight: Pair<Int, Int>? = null

    override fun nextEpisode() {}

    override fun prevEpisode() {}

    override fun playerPositionChanged(posDur: Pair<Long, Long>) {}

    override fun nextMirror() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        uiReset()
        fixPlayerSize()
    }

    private fun fixPlayerSize() {
        playerWidthHeight?.let { (w, h) ->
            val orientation = context?.resources?.configuration?.orientation ?: return

            val sw = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                screenWidth
            } else {
                screenHeight
            }

            result_trailer_loading?.isVisible = false
            result_smallscreen_holder?.isVisible = !isFullScreenPlayer
            result_fullscreen_holder?.isVisible = isFullScreenPlayer

            val to = sw * h / w

            player_background?.apply {
                isVisible = true
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        if (isFullScreenPlayer) FrameLayout.LayoutParams.MATCH_PARENT else to
                    )
            }

            player_intro_play?.apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        result_top_holder?.measuredHeight ?: FrameLayout.LayoutParams.MATCH_PARENT
                    )
            }

            if (player_intro_play?.isGone == true) {
                result_top_holder?.apply {

                    val anim = ValueAnimator.ofInt(
                        measuredHeight,
                        if (isFullScreenPlayer) ViewGroup.LayoutParams.MATCH_PARENT else to
                    )
                    anim.addUpdateListener { valueAnimator ->
                        val `val` = valueAnimator.animatedValue as Int
                        val layoutParams: ViewGroup.LayoutParams =
                            layoutParams
                        layoutParams.height = `val`
                        setLayoutParams(layoutParams)
                    }
                    anim.duration = 200
                    anim.start()
                }
            }
        }
    }

    override fun playerDimensionsLoaded(widthHeight: Pair<Int, Int>) {
        playerWidthHeight = widthHeight
        fixPlayerSize()
    }

    override fun showMirrorsDialogue() {}
    override fun showTracksDialogue() {}

    override fun openOnlineSubPicker(
        context: Context,
        imdbId: Long?,
        dismissCallback: () -> Unit
    ) {
    }

    override fun subtitlesChanged() {}

    override fun embeddedSubtitlesFetched(subtitles: List<SubtitleData>) {}
    override fun onTracksInfoChanged() {}

    override fun exitedPipMode() {}

    override fun onGestureRegionsUpdate(gestureRegions: List<Rect>) {}

    private fun updateFullscreen(fullscreen: Boolean) {
        isFullScreenPlayer = fullscreen
        lockRotation = fullscreen
        player_fullscreen?.setImageResource(if (fullscreen) R.drawable.baseline_fullscreen_exit_24 else R.drawable.baseline_fullscreen_24)
        if (fullscreen) {
            enterFullscreen()
            result_top_bar?.isVisible = false
            result_fullscreen_holder?.isVisible = true
            result_main_holder?.isVisible = false
            player_background?.let { view ->
                (view.parent as ViewGroup?)?.removeView(view)
                result_fullscreen_holder?.addView(view)
            }
        } else {
            result_top_bar?.isVisible = true
            result_fullscreen_holder?.isVisible = false
            result_main_holder?.isVisible = true
            player_background?.let { view ->
                (view.parent as ViewGroup?)?.removeView(view)
                result_smallscreen_holder?.addView(view)
            }
            exitFullscreen()
        }
        fixPlayerSize()
        uiReset()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        player_fullscreen?.setOnClickListener {
            updateFullscreen(!isFullScreenPlayer)
        }
        updateFullscreen(isFullScreenPlayer)
        uiReset()

        player_intro_play?.setOnClickListener {
            player_intro_play?.isGone = true
            player.handleEvent(CSPlayerEvent.Play)
            updateUIVisibility()
            fixPlayerSize()
        }
    }

    override fun onBackPressed(): Boolean {
        return if (isFullScreenPlayer) {
            updateFullscreen(false)
            false
        } else {
            true
        }
    }
}