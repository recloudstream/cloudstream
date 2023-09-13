package com.lagradost.cloudstream3.ui.result

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.lagradost.cloudstream3.CommonActivity.screenHeight
import com.lagradost.cloudstream3.CommonActivity.screenWidth
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.player.CSPlayerEvent
import com.lagradost.cloudstream3.ui.player.PlayerEventSource
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.utils.IOnBackPressed


open class ResultTrailerPlayer : ResultFragmentPhone(), IOnBackPressed {

    override var lockRotation = false
    override var isFullScreenPlayer = false
    override var hasPipModeSupport = false

    companion object {
        const val TAG = "RESULT_TRAILER"
    }

    var playerWidthHeight: Pair<Int, Int>? = null

    override fun nextEpisode() {}

    override fun prevEpisode() {}

    override fun playerPositionChanged(position: Long, duration : Long) {}

    override fun nextMirror() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        uiReset()
        fixPlayerSize()
    }

    private fun fixPlayerSize() {
        playerWidthHeight?.let { (w, h) ->
            if(w <= 0 || h <= 0) return@let

            val orientation = context?.resources?.configuration?.orientation ?: return

            val sw = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                screenWidth
            } else {
                screenHeight
            }

            //result_trailer_loading?.isVisible = false
            resultBinding?.resultSmallscreenHolder?.isVisible = !isFullScreenPlayer
            binding?.resultFullscreenHolder?.isVisible = isFullScreenPlayer

            val to = sw * h / w

            resultBinding?.fragmentTrailer?.playerBackground?.apply {
                isVisible = true
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        if (isFullScreenPlayer) FrameLayout.LayoutParams.MATCH_PARENT else to
                    )
            }

            playerBinding?.playerIntroPlay?.apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        resultBinding?.resultTopHolder?.measuredHeight
                            ?: FrameLayout.LayoutParams.MATCH_PARENT
                    )
            }

            if (playerBinding?.playerIntroPlay?.isGone == true) {
                resultBinding?.resultTopHolder?.apply {

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

    override fun playerDimensionsLoaded(width: Int, height : Int) {
        playerWidthHeight = width to height
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
    private fun updateFullscreen(fullscreen: Boolean) {
        isFullScreenPlayer = fullscreen
        lockRotation = fullscreen

        playerBinding?.playerFullscreen?.setImageResource(if (fullscreen) R.drawable.baseline_fullscreen_exit_24 else R.drawable.baseline_fullscreen_24)
        if (fullscreen) {
            enterFullscreen()
            binding?.apply {
                resultTopBar.isVisible = false
                resultFullscreenHolder.isVisible = true
                resultMainHolder.isVisible = false
            }

            resultBinding?.fragmentTrailer?.playerBackground?.let { view ->
                (view.parent as ViewGroup?)?.removeView(view)
                binding?.resultFullscreenHolder?.addView(view)
            }

        } else {
            binding?.apply {
                resultTopBar.isVisible = true
                resultFullscreenHolder.isVisible = false
                resultMainHolder.isVisible = true
                resultBinding?.fragmentTrailer?.playerBackground?.let { view ->
                    (view.parent as ViewGroup?)?.removeView(view)
                    resultBinding?.resultSmallscreenHolder?.addView(view)
                }
            }
            exitFullscreen()
        }
        fixPlayerSize()
        uiReset()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playerBinding?.playerFullscreen?.setOnClickListener {
            updateFullscreen(!isFullScreenPlayer)
        }
        updateFullscreen(isFullScreenPlayer)
        uiReset()

        playerBinding?.playerIntroPlay?.setOnClickListener {
            playerBinding?.playerIntroPlay?.isGone = true
            player.handleEvent(CSPlayerEvent.Play, PlayerEventSource.UI)
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