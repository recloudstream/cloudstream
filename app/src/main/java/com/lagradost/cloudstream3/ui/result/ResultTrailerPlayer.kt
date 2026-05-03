package com.lagradost.cloudstream3.ui.result

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.lagradost.cloudstream3.CommonActivity.screenHeight
import com.lagradost.cloudstream3.CommonActivity.screenWidth
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentResultSwipeBinding
import com.lagradost.cloudstream3.ui.player.CSPlayerEvent
import com.lagradost.cloudstream3.ui.player.CSPlayerLoading
import com.lagradost.cloudstream3.ui.player.PlayerEventSource
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding

class ResultTrailerPlayer : ResultFragmentPhone() {

    override var lockRotation = false
    override var isFullScreenPlayer = false
    override var hasPipModeSupport = false

    companion object {
        const val TAG = "ResultTrailerPlayer"
    }

    private var playerWidthHeight: Pair<Int, Int>? = null
    private var introVisible = true

    // Single-tap on empty player area: toggle controls.
    override fun onSingleTap() {
        if (introVisible) return
        if (isShowing) uiReset() else showControls()
    }

    private fun showControls() {
        if (introVisible) return
        isShowing = true
        updateUIVisibility()
        playerHostView?.scheduleAutoHide()
    }

    override fun isUIShowing(): Boolean = isShowing

    override fun onAutoHideUI() {
        if (player.getIsPlaying()) uiReset()
    }

    override fun onHidePlayerUI() = uiReset()

    // When the hold-speedup gesture fires, hide controls so the video is unobstructed.
    // The speedup button show/hide and speed change are handled by PlayerView.
    override fun onHoldSpeedUp(show: Boolean) {
        if (show && isShowing) uiReset()
    }

    override fun nextEpisode() {}
    override fun prevEpisode() {}
    override fun playerPositionChanged(position: Long, duration: Long) {}
    override fun nextMirror() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        uiReset()
        fixPlayerSize()
    }

    private fun fixPlayerSize() {
        binding?.apply {
            if (isFullScreenPlayer) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ViewCompat.setOnApplyWindowInsetsListener(root, null)
                    root.overlay.clear()
                }
                root.setPadding(0, 0, 0, 0)
            } else {
                fixSystemBarsPadding(root)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ViewCompat.requestApplyInsets(root)
                }
            }
        }

        playerWidthHeight?.let { (w, h) ->
            if (w <= 0 || h <= 0) return@let

            val orientation = context?.resources?.configuration?.orientation ?: return

            val sw = if (orientation == Configuration.ORIENTATION_LANDSCAPE) screenWidth else screenHeight

            resultBinding?.resultSmallscreenHolder?.isVisible = !isFullScreenPlayer
            binding?.resultFullscreenHolder?.isVisible = isFullScreenPlayer

            val to = sw * h / w

            resultBinding?.fragmentTrailer?.playerBackground?.apply {
                isVisible = true
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    if (isFullScreenPlayer) FrameLayout.LayoutParams.MATCH_PARENT else to
                )
            }

            playerBinding?.playerIntroPlay?.apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    resultBinding?.resultTopHolder?.measuredHeight ?: FrameLayout.LayoutParams.MATCH_PARENT
                )
            }

            if (playerBinding?.playerIntroPlay?.isGone == true) {
                resultBinding?.resultTopHolder?.apply {
                    val anim = ValueAnimator.ofInt(
                        measuredHeight,
                        if (isFullScreenPlayer) ViewGroup.LayoutParams.MATCH_PARENT else to
                    )
                    anim.addUpdateListener { va ->
                        val v = va.animatedValue as Int
                        val lp: ViewGroup.LayoutParams = layoutParams
                        lp.height = v
                        layoutParams = lp
                    }
                    anim.duration = 200
                    anim.start()
                }
            }
        }
    }

    override fun playerDimensionsLoaded(width: Int, height: Int) {
        playerWidthHeight = width to height
        fixPlayerSize()
        // Apply autorotation when fullscreen (lockRotation = true).
        // PlayerView already set isVerticalOrientation before this callback fires.
        if (lockRotation) {
            activity?.requestedOrientation = playerHostView?.dynamicOrientation() ?: return
        }
    }

    override fun showMirrorsDialogue() {}
    override fun showTracksDialogue() {}

    override fun openOnlineSubPicker(
        context: Context,
        loadResponse: LoadResponse?,
        dismissCallback: () -> Unit
    ) {}

    override fun subtitlesChanged() {}
    override fun embeddedSubtitlesFetched(subtitles: List<SubtitleData>) {}
    override fun onTracksInfoChanged() {}
    override fun exitedPipMode() {}

    override fun onSeekPreviewText(text: String?) {
        playerBinding?.playerTimeText?.apply {
            isVisible = text != null
            if (text != null) this.text = text
        }
    }

    private fun updateFullscreen(fullscreen: Boolean) {
        isFullScreenPlayer = fullscreen
        lockRotation = fullscreen
        playerHostView?.isFullScreen = fullscreen

        playerBinding?.playerFullscreen?.setImageResource(
            if (fullscreen) R.drawable.baseline_fullscreen_exit_24 else R.drawable.baseline_fullscreen_24
        )
        if (fullscreen) {
            playerHostView?.enterFullscreen()
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
            playerHostView?.exitFullscreen()
        }
        fixPlayerSize()
        uiReset()

        if (isFullScreenPlayer) {
            activity?.attachBackPressedCallback("ResultTrailerPlayer") { updateFullscreen(false) }
        } else {
            activity?.detachBackPressedCallback("ResultTrailerPlayer")
        }
    }

    override fun updateUIVisibility() {
        super.updateUIVisibility()
        playerBinding?.apply {
            playerGoBackHolder.isVisible = false
            val controlsVisible = isShowing && !introVisible
            playerTopHolder.isVisible = controlsVisible
            playerVideoHolder.isVisible = controlsVisible
            shadowOverlay.isVisible = controlsVisible
            playerPausePlayHolderHolder.isVisible =
                controlsVisible && playerHostView?.currentPlayerStatus != CSPlayerLoading.IsBuffering

            playerPausePlay.isClickable = controlsVisible
            playerRew.isClickable = controlsVisible
            playerFfwd.isClickable = controlsVisible
        }
        // Fade center controls in/out; also resets stale fillAfter alpha from seek animations.
        playerHostView?.gestureHelper?.animateCenterControls(if (isShowing && !introVisible) 1f else 0f)
    }

    override fun playerStatusChanged() {
        if (introVisible) {
            playerBinding?.playerPausePlayHolderHolder?.isVisible = false
        }
    }

    override fun onBindingCreated(binding: FragmentResultSwipeBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        playerHostView?.videoOutline = playerBinding?.videoOutline
        playerHostView?.requestUpdateBrightnessOverlayOnNextLayout()

        playerBinding?.playerFullscreen?.setOnClickListener { updateFullscreen(!isFullScreenPlayer) }
        updateFullscreen(isFullScreenPlayer)
        uiReset()

        playerBinding?.playerIntroPlay?.setOnClickListener {
            playerBinding?.playerIntroPlay?.isGone = true
            introVisible = false
            player.handleEvent(CSPlayerEvent.Play, PlayerEventSource.UI)
            fixPlayerSize()
            showControls()
        }
    }
}
