package com.lagradost.cloudstream3.ui.player

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Matrix
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.CommonActivity.keyEventListener
import com.lagradost.cloudstream3.CommonActivity.screenHeightWithOrientation
import com.lagradost.cloudstream3.CommonActivity.screenWidthWithOrientation
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.Vector2
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Handles all gesture, volume, brightness, speed-up, zoom, and hardware-key-event input for a
 * [PlayerView].  Keeps these separate from the player-view setup and lifecycle
 * code in [PlayerView] itself.
 *
 * Instantiated and owned by [PlayerView]; accessed from host fragments via the delegate
 * properties [PlayerView] exposes.
 */
@OptIn(UnstableApi::class)
class PlayerGestureHelper(private val playerView: PlayerView) {

    companion object {
        /** Swipe-seek constants */
        const val MINIMUM_SEEK_TIME        = 7000L
        const val MINIMUM_VERTICAL_SWIPE   = 2.0f   // % of screen height
        const val MINIMUM_HORIZONTAL_SWIPE = 2.0f  // % of screen height
        const val VERTICAL_MULTIPLIER      = 2.0f
        const val HORIZONTAL_MULTIPLIER    = 2.0f

        /** Double-tap constants */
        /** Maximum finger-hold time (ms) for a tap to qualify as a double-tap seek. */
        const val DOUBLE_TAP_MAXIMUM_HOLD_TIME = 200L
        /** Time window (ms) between taps to count as a double-tap.
         *  Also determines how long a single-tap is delayed before firing. */
        const val DOUBLE_TAP_MINIMUM_TIME_BETWEEN = 200L
        /** Fraction of view width on each side that counts as "left" / "right" seek zone. */
        const val DOUBLE_TAP_PAUSE_PERCENTAGE = 0.15

        /** Zoom constants */
        /** Minimum zoom; allows zooming out past 100% but snaps back. */
        const val MINIMUM_ZOOM = 0.95f
        /** Sensitivity for the auto-snap to 100% at the minimum zoom boundary. */
        const val ZOOM_SNAP_SENSITIVITY = 0.07f
        /** Maximum zoom to prevent the user from getting lost. */
        const val MAXIMUM_ZOOM = 4.0f

        /** Extracts translation and uniform scale from a matrix with no rotation. */
        fun matrixToTranslationAndScale(matrix: Matrix): Triple<Float, Float, Float> {
            val points = floatArrayOf(0f, 0f, 1f, 1f)
            matrix.mapPoints(points)
            val translationX = points[0]
            val translationY = points[1]
            val scale = points[2] - translationX
            return Triple(translationX, translationY, scale)
        }
    }

    private val context: Context get() = playerView.context

    /** Set true by the host when the player occupies the full screen.
     *  Controls whether hardware volume-key overrides are active (phones/emulators only). */
    var isFullScreen: Boolean = false

    /** Volume state */
    var currentRequestedVolume: Float = 0.0f
    var isVolumeLocked: Boolean = false
    var hasShownVolumeToast: Boolean = false
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var progressBarLeftHideRunnable: Runnable? = null

    /** Brightness state */
    var currentRequestedBrightness: Float = 1.0f
    var currentExtraBrightness: Float = 0.0f
    var isBrightnessLocked: Boolean = false
    var hasShownBrightnessToast: Boolean = false
    /** When true, read/write system brightness via [Settings.System.SCREEN_BRIGHTNESS].
     *  Automatically falls back to window-attribute brightness if the permission is missing. */
    var useTrueSystemBrightness: Boolean = true
    /** White overlay inflated into exo_content_frame; alpha encodes extra brightness (0–1). */
    var brightnessOverlay: View? = null
    private var progressBarRightHideRunnable: Runnable? = null

    /** Gesture settings (read from prefs in initialize) */
    var swipeVerticalEnabled: Boolean = true
    var swipeHorizontalEnabled: Boolean = false
    var extraBrightnessEnabled: Boolean = false
    var speedupEnabled: Boolean = false

    /** Hold / speed-up */
    val holdHandler = Handler(Looper.getMainLooper())
    var hasTriggeredSpeedUp = false
    val holdRunnable = Runnable {
        playerView.player.setPlaybackSpeed(2.0f)
        showOrHideSpeedUp(true)
        playerView.callbacks?.onHoldSpeedUp(true)
        hasTriggeredSpeedUp = true
    }

    enum class TouchAction { Brightness, Volume, Time }

    /** Mirrors the host's lock state; suppresses gesture interactions when true. */
    var isLocked: Boolean = false

    /** Touch tracking */
    var isCurrentTouchValid = false
        private set
    private var currentTouchStart: Vector2? = null
    private var currentTouchLast: Vector2? = null
    /** Current in-progress swipe action, null when no swipe is active. */
    var currentTouchAction: TouchAction? = null
    /** Action from the previous touch sequence; guards against mis-detected double-taps after swipes. */
    var currentLastTouchAction: TouchAction? = null
    private var currentTouchStartPlayerTime: Long? = null
    private var currentTouchStartTime: Long? = null
    /** Whether the player UI was visible when the current swipe gesture began. */
    var uiShowingBeforeGesture: Boolean = false

    /** Icons */
    private val brightnessIcons = listOf(
        R.drawable.sun_1, R.drawable.sun_2, R.drawable.sun_3,
        R.drawable.sun_4, R.drawable.sun_5, R.drawable.sun_6, R.drawable.sun_7,
    )
    private val volumeIcons = listOf(
        R.drawable.ic_baseline_volume_mute_24,
        R.drawable.ic_baseline_volume_down_24,
        R.drawable.ic_baseline_volume_up_24,
    )

    /** Double-tap / tap state */

    /** Whether double-tapping left/right seeks backward/forward. */
    var doubleTapEnabled: Boolean = false

    /** Whether double-tapping the center of the screen pauses (left/right still seeks if [doubleTapEnabled]). */
    var doubleTapPauseEnabled: Boolean = false

    /** Seek distance (ms) for each double-tap seek. Read from prefs in [initialize]. */
    var fastForwardTime: Long = 10_000L

    /** Monotonically-incremented token; cancels any pending single-tap runnable when a double-tap arrives. */
    private var doubleTapToken = 0

    /** Number of consecutive taps in the current double-tap window. */
    private var tapCount = 0

    /** System time of the most-recent touch end.  Updated by callers at the end of every ACTION_UP. */
    var lastTouchEndTime: Long = 0L

    /** Zoom state */

    /** Optional view for showing the snap-hint outline during zoom (set by FullScreenPlayer). */
    var videoOutline: View? = null

    /** Current zoom+pan matrix, or null when no zoom is active. */
    var zoomMatrix: Matrix? = null

    /** The matrix the zoom will animate to after the user lifts fingers. */
    var desiredMatrix: Matrix? = null

    /** Running snap-back animation, or null. */
    var matrixAnimation: ValueAnimator? = null

    private var scaleGestureDetector: ScaleGestureDetector? = null

    /** Midpoint of the two-finger pan, null when no pan is active. */
    var lastPan: Vector2? = null

    private var overlayLayoutListener: View.OnLayoutChangeListener? = null

    /** Called from [PlayerView.initialize] after views are bound. */
    fun initialize() {
        try {
            val sm = PreferenceManager.getDefaultSharedPreferences(context)
            swipeVerticalEnabled   = sm.getBoolean(context.getString(R.string.swipe_vertical_enabled_key), true)
            swipeHorizontalEnabled = sm.getBoolean(context.getString(R.string.swipe_enabled_key), true)
            extraBrightnessEnabled = sm.getBoolean(context.getString(R.string.extra_brightness_key), false)
            speedupEnabled         = sm.getBoolean(context.getString(R.string.speedup_key), false)
            doubleTapEnabled       = sm.getBoolean(context.getString(R.string.double_tap_enabled_key), false)
            doubleTapPauseEnabled  = sm.getBoolean(context.getString(R.string.double_tap_pause_enabled_key), false)
            fastForwardTime        = sm.getInt(context.getString(R.string.double_tap_seek_time_key), 10).toLong() * 1000L
        } catch (_: Exception) { }

        // Inject the brightness overlay into the ExoPlayer content frame so it sits
        // directly on top of the video surface.  Alpha is set by handleBrightnessAdjustment.
        safe {
            val pkg = context.packageName
            @SuppressLint("DiscouragedApi")
            val contentId = context.resources.getIdentifier("exo_content_frame", "id", pkg)
            val contentFrame = playerView.exoPlayerView?.findViewById<ViewGroup>(contentId)
            if (contentFrame != null) {
                brightnessOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
                brightnessOverlay = LayoutInflater.from(context)
                    .inflate(R.layout.extra_brightness_overlay, contentFrame, false)
                contentFrame.addView(brightnessOverlay)
            }
        }

        setupTouchGestures()
    }

    /** Called from [PlayerView.release]. */
    fun release() {
        safe { brightnessOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) } }
        brightnessOverlay = null
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        holdHandler.removeCallbacksAndMessages(null)
        clearZoomState()
        releaseOverlayLayoutListener()
    }

    /** Key-event listener */

    /**
     * Registers the basic volume-key listener on [keyEventListener].
     * Called from [PlayerView.initialize] and from the host fragment's onResume.
     */
    fun setupKeyEventListener() {
        keyEventListener = { (event, _) ->
            if (event != null && event.action == KeyEvent.ACTION_DOWN)
                handleVolumeKey(event.keyCode)
            else false
        }
    }

    /** Nulls [keyEventListener]. Called from the host fragment's onPause. */
    fun releaseKeyEventListener() {
        keyEventListener = null
    }

    /** Speed-up */

    fun showOrHideSpeedUp(show: Boolean) {
        playerView.playerSpeedupButton?.let { btn ->
            btn.clearAnimation()
            btn.alpha = if (show) 0f else 1f
            btn.isVisible = show
            btn.animate()
                .alpha(if (show) 1f else 0f)
                .setDuration(200L)
                .withEndAction { if (!show) btn.isVisible = false }
                .start()
        }
    }

    /** Volume helpers */

    /**
     * Syncs [currentRequestedVolume] with the current system stream volume.
     *
     * This is here to make returning to the player less jarring, if we change the volume outside
     * the app. Note that this will make it a bit wierd when using loudness in PiP, then returning
     * however that is the cost of correctness.
     */
    fun verifyVolume() {
        ((context as? Activity)?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.let { am ->
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (cur < max || currentRequestedVolume <= 1.0f) {
                currentRequestedVolume = cur.toFloat() / max.toFloat()
                loudnessEnhancer?.release()
                loudnessEnhancer = null
            }
        }
    }

    /**
     * Handles a hardware volume key press.
     * Only active on phones/emulators when [isFullScreen] is true.
     *
     * @return true if the key was consumed (suppresses the system volume UI).
     */
    fun handleVolumeKey(keyCode: Int): Boolean {
        if (!isLayout(PHONE or EMULATOR) || !isFullScreen) return false
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        verifyVolume()
        if (currentRequestedVolume <= 1.0f) hasShownVolumeToast = false
        isVolumeLocked = currentRequestedVolume < 1.0f
        handleVolumeAdjustment(if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 0.05f else -0.05f, fromButton = true)
        return true
    }

    fun handleVolumeAdjustment(delta: Float, fromButton: Boolean) {
        val am = (context as? Activity)?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val curStep = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxStep = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        val cur    = currentRequestedVolume
        val locked = isVolumeLocked
        val next   = (cur + delta).coerceIn(0.0f, if (locked) 1.0f else 2.0f)
        val nextStep = (next * maxStep.toFloat()).roundToInt().coerceIn(0, maxStep)

        if (fromButton) {
            if (cur <= 1.0f && next > 1.0f && !hasShownVolumeToast) {
                showToast(R.string.volume_exceeded_100)
                hasShownVolumeToast = true
            }
        } else {
            val raw = cur + delta
            if (raw > 1.0 && locked && !hasShownVolumeToast) {
                showToast(R.string.slide_up_again_to_exceed_100)
                hasShownVolumeToast = true
            }
        }

        if (nextStep != curStep) am.setStreamVolume(AudioManager.STREAM_MUSIC, nextStep, 0)

        var hasBoostError = false
        if (next > 1.0f) {
            val boost = ((next - 1.0f) * 1000).toInt()
            val existing = loudnessEnhancer
            if (existing != null) {
                existing.setTargetGain(boost)
            } else {
                val sessionId = (playerView.exoPlayerView?.player as? ExoPlayer)?.audioSessionId
                if (sessionId != null && sessionId != AudioManager.ERROR) {
                    try {
                        loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                            setTargetGain(boost); enabled = true
                        }
                    } catch (t: Throwable) { logError(t); hasBoostError = true }
                }
            }
        } else {
            loudnessEnhancer?.release(); loudnessEnhancer = null
        }

        currentRequestedVolume = next

        val leftHolder = playerView.playerProgressbarLeftHolder ?: return
        val level1    = playerView.playerProgressbarLeftLevel1  ?: return
        val level2    = playerView.playerProgressbarLeftLevel2  ?: return
        val icon      = playerView.playerProgressbarLeftIcon    ?: return

        if (next > 1.0f) {
            level2.progressTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, if (hasBoostError) R.color.colorPrimaryRed else R.color.colorPrimaryOrange)
            )
        }
        level1.max      = 100_000
        level1.progress = (next * 100_000f).toInt().coerceIn(2_000, 100_000)
        level2.max      = 100_000
        level2.progress = if (next > 1.0f) ((next - 1.0) * 100_000f).toInt().coerceIn(2_000, 100_000) else 0
        level2.isVisible = next > 1.0f
        val iconIdx = (next * volumeIcons.lastIndex).roundToInt().coerceIn(0, volumeIcons.lastIndex)
        icon.setImageResource(volumeIcons[iconIdx])

        if (!leftHolder.isVisible || leftHolder.alpha < 1f) {
            leftHolder.animate().cancel(); leftHolder.alpha = 1f; leftHolder.isVisible = true
        }
        progressBarLeftHideRunnable?.let { leftHolder.removeCallbacks(it) }
        progressBarLeftHideRunnable = Runnable {
            leftHolder.animate().cancel()
            leftHolder.animate().alpha(0f).setDuration(300).withEndAction { leftHolder.isVisible = false }.start()
        }
        leftHolder.postDelayed(progressBarLeftHideRunnable, 1500)
    }

    /** Brightness helpers */

    /**
     * Reads from [Settings.System.SCREEN_BRIGHTNESS], falling back to the window
     * attribute if the permission is absent.
     */
    fun getBrightness(): Float? {
        return if (useTrueSystemBrightness) {
            try {
                Settings.System.getInt(
                    (context as? Activity)?.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                ) / 255f
            } catch (_: Exception) {
                // Permission not granted — fall back to window-attribute mode permanently.
                useTrueSystemBrightness = false
                getBrightness()
            }
        } else {
            try {
                (context as? Activity)?.window?.attributes?.screenBrightness?.takeIf { it >= 0f }
            } catch (e: Exception) {
                logError(e)
                null
            }
        }
    }

    /**
     * Sets [Settings.System.SCREEN_BRIGHTNESS], falling back to the window
     * attribute if the permission is absent.
     */
    fun setBrightness(brightness: Float) {
        if (useTrueSystemBrightness) {
            try {
                Settings.System.putInt(
                    (context as? Activity)?.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(
                    (context as? Activity)?.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    min(1, (brightness.coerceIn(0.0f, 1.0f) * 255).toInt())
                )
            } catch (_: Exception) {
                useTrueSystemBrightness = false
                setBrightness(brightness)
            }
        } else {
            try {
                val lp = (context as? Activity)?.window?.attributes ?: return
                // Use 0.004f instead of 0: on some devices a value too close to 0 causes the
                // system to override with its own brightness, making fine-tuning impossible.
                lp.screenBrightness = brightness.coerceIn(0.004f, 1.0f)
                (context as? Activity)?.window?.attributes = lp
            } catch (e: Exception) {
                logError(e)
            }
        }
    }

    fun handleBrightnessAdjustment(verticalAddition: Float) {
        val lastBrightness = currentRequestedBrightness
        val raw  = currentRequestedBrightness + verticalAddition
        val next = raw.coerceIn(0.0f, if (extraBrightnessEnabled && !isBrightnessLocked) 2.0f else 1.0f)

        if (extraBrightnessEnabled && isBrightnessLocked && raw > 1.0f && !hasShownBrightnessToast) {
            showToast(R.string.slide_up_again_to_exceed_100)
            hasShownBrightnessToast = true
        }

        currentRequestedBrightness = next
        if (lastBrightness != currentRequestedBrightness) setBrightness(currentRequestedBrightness)

        currentExtraBrightness = if (extraBrightnessEnabled && next > 1.0f) min(2.0f, next) - 1.0f else 0.0f
        brightnessOverlay?.alpha = currentExtraBrightness
        playerView.callbacks?.onBrightnessExtra(currentExtraBrightness)

        val rightHolder = playerView.playerProgressbarRightHolder ?: return
        val level1      = playerView.playerProgressbarRightLevel1 ?: return
        val level2      = playerView.playerProgressbarRightLevel2 ?: return
        val icon        = playerView.playerProgressbarRightIcon   ?: return

        level1.max      = 100_000
        level1.progress = max(2_000, (min(1.0f, next) * 100_000f).toInt())

        if (extraBrightnessEnabled) {
            level2.max      = 100_000
            level2.progress = (currentExtraBrightness * 100_000f).toInt().coerceIn(2_000, 100_000)
            level2.isVisible = next > 1.0f
        }

        icon.setImageResource(
            brightnessIcons[min(brightnessIcons.lastIndex, max(0, round(next * brightnessIcons.lastIndex).toInt()))]
        )

        if (!rightHolder.isVisible || rightHolder.alpha < 1f) {
            rightHolder.animate().cancel(); rightHolder.alpha = 1f; rightHolder.isVisible = true
        }
        progressBarRightHideRunnable?.let { rightHolder.removeCallbacks(it) }
        progressBarRightHideRunnable = Runnable {
            rightHolder.animate().cancel()
            rightHolder.animate().alpha(0f).setDuration(300).withEndAction { rightHolder.isVisible = false }.start()
        }
        rightHolder.postDelayed(progressBarRightHideRunnable, 1500)
    }

    /** Zoom helpers */

    /**
     * Returns the current zoom matrix, accounting for RESIZE_MODE_ZOOM which already has
     * an implicit zoom applied.
     *
     * This is different from `zoomMatrix ?: Matrix()`
     * because it allows used to start zooming at different resizeModes.
     *
     * The main issue is that RESIZE_MODE_FIT = 100% zoom, but if you are in RESIZE_MODE_ZOOM
     * 100% will make the zoom snap to less zoomed in then you already are.
     */
    fun currentZoomMatrix(): Matrix {
        val current = zoomMatrix
        if (current != null) return current

        val exoView = playerView.exoPlayerView
        val videoView = exoView?.videoSurfaceView

        if (exoView == null || videoView == null ||
            exoView.resizeMode != AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
            return Matrix()
        }

        val videoWidth  = videoView.width.toFloat()
        val videoHeight = videoView.height.toFloat()
        val playerWidth  = screenWidthWithOrientation.toFloat()
        val playerHeight = screenHeightWithOrientation.toFloat()

        if (videoWidth <= 1f || videoHeight <= 1f || playerWidth <= 1f || playerHeight <= 1f) {
            return Matrix()
        }

        val initAspect = (playerHeight * videoWidth) / (playerWidth * videoHeight)
        val aspect = max(initAspect, 1f / initAspect)
        return Matrix().apply { postScale(aspect, aspect) }
    }

    /** Applies [newMatrix] (scale + translation only) to the video surface view. */
    fun applyZoomMatrix(newMatrix: Matrix, animation: Boolean) {
        val exoView = playerView.exoPlayerView ?: return
        if (!animation) {
            matrixAnimation?.cancel()
            matrixAnimation = null
        }
        val (translationX, translationY, scale) = matrixToTranslationAndScale(newMatrix)

        if (exoView.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
            exoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }

        val videoView   = exoView.videoSurfaceView ?: return
        val videoWidth  = videoView.width.toFloat()
        val videoHeight = videoView.height.toFloat()
        val playerWidth  = screenWidthWithOrientation.toFloat()
        val playerHeight = screenHeightWithOrientation.toFloat()

        if (videoWidth <= 1f || videoHeight <= 1f || playerWidth <= 1f || playerHeight <= 1f || scale <= 0.01f) return

        val initAspect  = (playerHeight * videoWidth) / (playerWidth * videoHeight)
        val aspect      = min(initAspect, 1f / initAspect)
        val scaledAspect = scale * aspect

        val maxTransX = max(0f, videoWidth  * scaledAspect - playerWidth)  * 0.5f
        val maxTransY = max(0f, videoHeight * scaledAspect - playerHeight) * 0.5f

        val expectedTranslationX = translationX.coerceIn(-maxTransX, maxTransX)
        val expectedTranslationY = translationY.coerceIn(-maxTransY, maxTransY)

        newMatrix.postTranslate(
            expectedTranslationX - translationX,
            expectedTranslationY - translationY
        )
        zoomMatrix = newMatrix

        if (!animation) {
            if ((scaledAspect - 1f).absoluteValue < ZOOM_SNAP_SENSITIVITY) {
                videoOutline?.isVisible = true
                val desired = Matrix()
                desired.setScale(1f / aspect, 1f / aspect)
                desiredMatrix = desired
            } else if (scale < 1f) {
                videoOutline?.isVisible = false
                desiredMatrix = Matrix()
            } else {
                videoOutline?.isVisible = false
                desiredMatrix = null
            }
        }

        videoView.scaleX       = scaledAspect
        videoView.scaleY       = scaledAspect
        videoView.translationX = expectedTranslationX
        videoView.translationY = expectedTranslationY
        updateBrightnessOverlayBounds()
    }

    /**
     * Clears all zoom state and resets the video surface view to 1:1 scale.
     * Does NOT change the ExoPlayer resize mode - call [PlayerView.resize] separately.
     */
    fun clearZoomState() {
        matrixAnimation?.cancel()
        matrixAnimation = null
        zoomMatrix = null
        desiredMatrix = null
        scaleGestureDetector = null
        lastPan = null
        playerView.exoPlayerView?.videoSurfaceView?.apply {
            scaleX = 1f
            scaleY = 1f
            translationX = 0f
            translationY = 0f
        }
    }

    /**
     * Resets zoom to fit mode if any zoom is currently active.
     * Calls [PlayerView.resize] to update the ExoPlayer resize mode.
     */
    fun resetZoomToDefault() {
        if (zoomMatrix != null) {
            clearZoomState()
            playerView.resize(PlayerResize.Fit, false)
        }
    }

    private fun createScaleGestureDetector(ctx: Context) {
        scaleGestureDetector = ScaleGestureDetector(
            ctx,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val matrix = currentZoomMatrix()
                    val (_, _, scale) = matrixToTranslationAndScale(matrix)
                    // Clamp scale of the zoom, do it here as it is easier than doing it within applyZoomMatrix.
                    val newScale = (scale * detector.scaleFactor).coerceIn(MINIMUM_ZOOM, MAXIMUM_ZOOM)
                    // This is how much we should scale it with to prevent infinite scaling.
                    val actualScaleFactor = newScale / scale
                    // Scale around the focus point, this is more natural than just zoom.
                    val pivotX = detector.focusX - screenWidthWithOrientation.toFloat() * 0.5f
                    val pivotY = detector.focusY - screenHeightWithOrientation.toFloat() * 0.5f
                    matrix.postScale(actualScaleFactor, actualScaleFactor, pivotX, pivotY)
                    applyZoomMatrix(matrix, false)
                    return true
                }
            }
        )
    }

    /**
     * Processes a two-finger zoom/pan gesture event.
     * Handles scale detection, panning, and the snap-back animation after finger lift.
     *
     * @param event              The motion event (should have pointerCount >= 2 or [lastPan] != null).
     * @param ctx                Context used to create the [ScaleGestureDetector] on first call.
     * @param onFirstPointerDown Called on [MotionEvent.ACTION_POINTER_DOWN] (e.g. hide player UI).
     * @param onGestureEnd       Called when the gesture ends (e.g. reset caller touch state).
     * @return Always true (event consumed).
     */
    fun handleZoomPanGesture(
        event: MotionEvent,
        ctx: Context,
        onFirstPointerDown: () -> Unit,
        onGestureEnd: () -> Unit
    ): Boolean {
        if (scaleGestureDetector == null) createScaleGestureDetector(ctx)
        scaleGestureDetector?.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                onFirstPointerDown()
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val newPan = Vector2(
                        (event.getX(0) + event.getX(1)) / 2f,
                        (event.getY(0) + event.getY(1)) / 2f
                    )
                    val oldPan = lastPan
                    if (oldPan != null) {
                        val matrix = currentZoomMatrix()
                        matrix.postTranslate(newPan.x - oldPan.x, newPan.y - oldPan.y)
                        applyZoomMatrix(matrix, false)
                    }
                    lastPan = newPan
                }
            }

            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP -> {
                lastPan = null
                videoOutline?.isVisible = false
                matrixAnimation?.cancel()
                matrixAnimation = null

                // Snap to desired matrix after zoom gesture ends
                matrixAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
                    startDelay = 0
                    duration = 200
                    val startMatrix = currentZoomMatrix()
                    val endMatrix = desiredMatrix ?: return@apply
                    val (startX, startY, startScale) = matrixToTranslationAndScale(startMatrix)
                    val (endX, endY, endScale)       = matrixToTranslationAndScale(endMatrix)
                    addUpdateListener { anim ->
                        val v    = anim.animatedValue as Float
                        val vInv = 1f - v
                        val m = Matrix()
                        m.setScale(startScale * vInv + endScale * v, startScale * vInv + endScale * v)
                        m.postTranslate(startX * vInv + endX * v, startY * vInv + endY * v)
                        applyZoomMatrix(m, true)
                    }
                    start()
                }

                onGestureEnd()
            }
        }
        return true
    }

    /**
     * Resizes and repositions [brightnessOverlay] to exactly match the visible video surface,
     * accounting for zoom scale and translation.
     */
    fun updateBrightnessOverlayBounds() {
        val overlay = brightnessOverlay ?: return
        val pv      = playerView.exoPlayerView ?: return
        val video   = pv.videoSurfaceView ?: return

        val vw = video.width.toFloat()
        val vh = video.height.toFloat()
        val sx = video.scaleX
        val sy = video.scaleY
        if (vw <= 0f || vh <= 0f) return

        val pivotX = if (video.pivotX != 0f) video.pivotX else vw * 0.5f
        val pivotY = if (video.pivotY != 0f) video.pivotY else vh * 0.5f
        val tx = video.x
        val ty = video.y

        fun transform(lx: Float, ly: Float): Pair<Float, Float> {
            val gx = tx + pivotX + (lx - pivotX) * sx
            val gy = ty + pivotY + (ly - pivotY) * sy
            return Pair(gx, gy)
        }

        val p0 = transform(0f, 0f);  val p1 = transform(vw, 0f)
        val p2 = transform(0f, vh); val p3 = transform(vw, vh)

        val minX = min(min(p0.first,  p1.first),  min(p2.first,  p3.first))
        val maxX = max(max(p0.first,  p1.first),  max(p2.first,  p3.first))
        val minY = min(min(p0.second, p1.second), min(p2.second, p3.second))
        val maxY = max(max(p0.second, p1.second), max(p2.second, p3.second))

        val newW = ceil(maxX - minX).toInt().coerceAtLeast(0)
        val newH = ceil(maxY - minY).toInt().coerceAtLeast(0)

        val lp = overlay.layoutParams
        if (lp == null) {
            overlay.layoutParams = ViewGroup.LayoutParams(newW, newH)
        } else if (lp.width != newW || lp.height != newH) {
            lp.width = newW; lp.height = newH
            overlay.layoutParams = lp
        }

        overlay.scaleX = 1f; overlay.scaleY = 1f
        overlay.x = minX;    overlay.y = minY
    }

    /**
     * Attaches a persistent layout-change listener to the ExoPlayer view so
     * [updateBrightnessOverlayBounds] is called on every layout pass (orientation change,
     * aspect-ratio change, zoom, PiP transition, etc.).
     */
    fun requestUpdateBrightnessOverlayOnNextLayout() {
        val exoView = playerView.exoPlayerView ?: return
        overlayLayoutListener?.let { exoView.removeOnLayoutChangeListener(it) }
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            safe { updateBrightnessOverlayBounds() }
        }
        overlayLayoutListener = listener
        exoView.addOnLayoutChangeListener(listener)
    }

    /** Removes the overlay layout listener registered by [requestUpdateBrightnessOverlayOnNextLayout]. */
    fun releaseOverlayLayoutListener() {
        overlayLayoutListener?.let { playerView.exoPlayerView?.removeOnLayoutChangeListener(it) }
        overlayLayoutListener = null
    }

    /** Rewind / fast-forward animations */

    /** Resets the rewind button label to the standard "–Xs" format. */
    fun resetRewindText() {
        playerView.exoRewText?.text = context.getString(R.string.rew_text_regular_format)
            .format(fastForwardTime / 1000)
    }

    /** Resets the fast-forward button label to the standard "+Xs" format. */
    fun resetFastForwardText() {
        playerView.exoFfwdText?.text = context.getString(R.string.ffw_text_regular_format)
            .format(fastForwardTime / 1000)
    }

    /**
     * Fades playerRewHolder, playerFfwdHolder, and playerPausePlay to [fadeTo] (0f or 1f).
     * Always resets the holder alphas to 1f first so any stale fillAfter state is cleared.
     * Called from host fragments' show/hide control animations so both GeneratorPlayer and trailer share
     * the same fade logic.
     */
    fun animateCenterControls(fadeTo: Float) {
        val from = if (fadeTo > 0.5f) 0f else 1f
        fun makeAnim() = AlphaAnimation(from, fadeTo).apply { duration = 100; fillAfter = true }
        // Each view needs its own Animation instance; sharing one causes fillAfter to
        // not hold reliably across all views once any of them restarts the animation.
        playerView.playerRewHolder?.let  { it.alpha = 1f; it.startAnimation(makeAnim()) }
        playerView.playerFfwdHolder?.let { it.alpha = 1f; it.startAnimation(makeAnim()) }
        playerView.playerPausePlay?.startAnimation(makeAnim())
    }

    /** Plays the rewind animation and seeks back by [fastForwardTime]. */
    fun rewind() {
        try {
            val rewHolder  = playerView.playerRewHolder ?: return
            val rew        = playerView.playerRew
            val rewText    = playerView.exoRewText
            val wasShowing = playerView.callbacks?.isUIShowing() ?: false

            // Only expose the parent chain when controls are currently hidden.
            val prevCenterMenuGone     = playerView.playerCenterMenu?.isGone ?: false
            val prevVideoHolderVisible = playerView.playerVideoHolder?.isVisible ?: true
            if (!wasShowing) {
                playerView.playerCenterMenu?.isGone = false
                playerView.playerVideoHolder?.isVisible = true
            }
            // Always clear any stale fillAfter alpha so the button is visible during animation.
            rewHolder.alpha = 1f

            rew?.startAnimation(AnimationUtils.loadAnimation(context, R.anim.rotate_left))
            val goLeft = AnimationUtils.loadAnimation(context, R.anim.go_left)
            goLeft.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    rewText?.post {
                        resetRewindText()
                        // Restore parent chain only if we changed it and controls are still hidden.
                        if (!wasShowing && !(playerView.callbacks?.isUIShowing() ?: false)) {
                            playerView.playerCenterMenu?.isGone = prevCenterMenuGone
                            playerView.playerVideoHolder?.isVisible = prevVideoHolderVisible
                            rewHolder.alpha = 0f
                        }
                    }
                }
            })
            rewText?.startAnimation(goLeft)
            rewText?.text = context.getString(R.string.rew_text_format).format(fastForwardTime / 1000)
            playerView.player.seekTime(-fastForwardTime)
        } catch (e: Exception) { logError(e) }
    }

    /** Plays the fast-forward animation and seeks forward by [fastForwardTime]. */
    fun fastForward() {
        try {
            val ffwdHolder = playerView.playerFfwdHolder ?: return
            val ffwd       = playerView.playerFfwd
            val ffwdText   = playerView.exoFfwdText
            val wasShowing = playerView.callbacks?.isUIShowing() ?: false

            val prevCenterMenuGone     = playerView.playerCenterMenu?.isGone ?: false
            val prevVideoHolderVisible = playerView.playerVideoHolder?.isVisible ?: true
            if (!wasShowing) {
                playerView.playerCenterMenu?.isGone = false
                playerView.playerVideoHolder?.isVisible = true
            }
            // Always clear any stale fillAfter alpha so the button is visible during animation.
            ffwdHolder.alpha = 1f

            ffwd?.startAnimation(AnimationUtils.loadAnimation(context, R.anim.rotate_right))
            val goRight = AnimationUtils.loadAnimation(context, R.anim.go_right)
            goRight.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    ffwdText?.post {
                        resetFastForwardText()
                        if (!wasShowing && !(playerView.callbacks?.isUIShowing() ?: false)) {
                            playerView.playerCenterMenu?.isGone = prevCenterMenuGone
                            playerView.playerVideoHolder?.isVisible = prevVideoHolderVisible
                            ffwdHolder.alpha = 0f
                        }
                    }
                }
            })
            ffwdText?.startAnimation(goRight)
            ffwdText?.text = context.getString(R.string.ffw_text_format).format(fastForwardTime / 1000)
            playerView.player.seekTime(fastForwardTime)
        } catch (e: Exception) { logError(e) }
    }

    /** Double-tap detection */

    /**
     * Call when a valid tap is detected (short hold, minimal movement, valid touch area).
     * Routes to double-tap seeking/pausing or schedules a delayed single-tap callback.
     *
     * Updates [lastTouchEndTime] when a confirmed tap (single or double) is recorded.
     *
     * @param x           X coordinate of the tap in the view's coordinate space.
     * @param viewWidth   Width of the view (used to compute left/center/right zones).
     * @param isLocked    Whether player controls are locked (suppresses double-tap seek).
     * @param onSingleTap Invoked when it is determined to be a single tap; may be deferred.
     * @return true if a double-tap action was performed.
     */
    fun onTapDetected(x: Float, viewWidth: Int, isLocked: Boolean, onSingleTap: () -> Unit): Boolean {
        val anyDoubleTap = doubleTapEnabled || doubleTapPauseEnabled
        if (!anyDoubleTap) {
            onSingleTap()
            return false
        }

        val timeSinceLast = System.currentTimeMillis() - lastTouchEndTime
        return if (!isLocked && timeSinceLast < DOUBLE_TAP_MINIMUM_TIME_BETWEEN) {
            /** Double-tap */
            tapCount++
            doubleTapToken++ // cancel any pending single-tap runnable
            if (doubleTapPauseEnabled) {
                when {
                    x < viewWidth / 2f - (DOUBLE_TAP_PAUSE_PERCENTAGE * viewWidth) -> {
                        if (doubleTapEnabled) rewind()
                    }
                    x > viewWidth / 2f + (DOUBLE_TAP_PAUSE_PERCENTAGE * viewWidth) -> {
                        if (doubleTapEnabled) fastForward()
                    }
                    else -> {
                        playerView.player.handleEvent(CSPlayerEvent.PlayPauseToggle, PlayerEventSource.UI)
                    }
                }
            } else if (doubleTapEnabled) {
                if (x < viewWidth / 2f) rewind() else fastForward()
            }
            true
        } else {
            /** Single tap (first tap, or too slow for double-tap) */
            tapCount = 0
            val token = ++doubleTapToken
            playerView.playerHolder?.postDelayed({
                if (token == doubleTapToken) {
                    onSingleTap()
                }
            }, DOUBLE_TAP_MINIMUM_TIME_BETWEEN)
            false
        }
    }

    /** Seek time helpers */

    private fun calculateNewTime(startTime: Long?, touchStart: Vector2?, touchEnd: Vector2?): Long? {
        if (touchStart == null || touchEnd == null || startTime == null) return null
        val diffX = (touchEnd.x - touchStart.x) * HORIZONTAL_MULTIPLIER / screenWidthWithOrientation.toFloat()
        val duration = playerView.player.getDuration() ?: return null
        return max(min(startTime + ((duration * (diffX * diffX)) * (if (diffX < 0) -1 else 1)).toLong(), duration), 0)
    }

    private fun forceLetters(inp: Long, letters: Int = 2): String {
        val added = letters - inp.toString().length
        return if (added > 0) "0".repeat(added) + inp.toString() else inp.toString()
    }

    private fun convertTimeToString(sec: Long): String {
        val rsec = sec % 60L
        val min = ceil((sec - rsec) / 60.0).toInt()
        val rmin = min % 60L
        val h = ceil((min - rmin) / 60.0).toLong()
        return (if (h > 0) forceLetters(h) + ":" else "") +
               (if (rmin >= 0 || h >= 0) forceLetters(rmin) + ":" else "") +
               forceLetters(rsec)
    }

    /** Touch gestures */

    fun setupTouchGestures() {
        val holder = playerView.playerHolder ?: return
        @SuppressLint("ClickableViewAccessibility")
        holder.setOnTouchListener(::handleGesture)
    }

    private fun handleGesture(view: View, event: MotionEvent): Boolean {
        val currentTouch = Vector2(event.x, event.y)
        val startTouch = currentTouchStart

        /** Two-finger zoom/pan (fullscreen, unlocked) */
        if ((event.pointerCount >= 2 || lastPan != null) && isFullScreen && !isLocked
                && !hasTriggeredSpeedUp && currentTouchAction == null) {
            holdHandler.removeCallbacks(holdRunnable)
            isCurrentTouchValid = false
            return handleZoomPanGesture(
                event = event,
                ctx = view.context,
                onFirstPointerDown = {
                    uiShowingBeforeGesture = playerView.callbacks?.isUIShowing() ?: false
                    playerView.callbacks?.onHidePlayerUI()
                },
                onGestureEnd = {
                    currentTouchStart = null
                    currentLastTouchAction = null
                    currentTouchAction = null
                    currentTouchStartPlayerTime = null
                    currentTouchLast = null
                    currentTouchStartTime = null
                }
            )
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isCurrentTouchValid = playerView.callbacks?.isValidTouch(event.rawX, event.rawY) ?: true
                if (isCurrentTouchValid) {
                    playerView.callbacks?.onTouchDown()
                    hasTriggeredSpeedUp = false
                    if (speedupEnabled && playerView.player.getIsPlaying() && !isLocked) {
                        holdHandler.postDelayed(holdRunnable, 500)
                    }
                    isVolumeLocked = currentRequestedVolume < 1.0f
                    if (currentRequestedVolume <= 1.0f) hasShownVolumeToast = false
                    isBrightnessLocked = currentRequestedBrightness < 1.0f
                    if (currentRequestedBrightness <= 1.0f) hasShownBrightnessToast = false
                    currentTouchStartTime = System.currentTimeMillis()
                    currentTouchStart = currentTouch
                    currentTouchLast = currentTouch
                    currentTouchStartPlayerTime = playerView.player.getPosition()
                    getBrightness()?.let { currentRequestedBrightness = it + currentExtraBrightness }
                    verifyVolume()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (hasTriggeredSpeedUp) return true
                if (!isCurrentTouchValid) return true

                if (currentTouchAction == null && startTouch != null) {
                    val diffFromStart = startTouch - currentTouch
                    if (swipeVerticalEnabled) {
                        if (abs(diffFromStart.y * 100 / screenHeightWithOrientation) > MINIMUM_VERTICAL_SWIPE) {
                            holdHandler.removeCallbacks(holdRunnable)
                            uiShowingBeforeGesture = playerView.callbacks?.isUIShowing() ?: false
                            playerView.callbacks?.onHidePlayerUI()
                            currentTouchAction = if ((startTouch.x) >= view.width / 2f)
                                TouchAction.Volume else TouchAction.Brightness
                        }
                    }
                    if (swipeHorizontalEnabled && !isLocked) {
                        if (abs(diffFromStart.x * 100 / screenHeightWithOrientation) > MINIMUM_HORIZONTAL_SWIPE) {
                            holdHandler.removeCallbacks(holdRunnable)
                            currentTouchAction = TouchAction.Time
                        }
                    }
                }

                val lastTouch = currentTouchLast
                if (lastTouch != null) {
                    val diffFromLast = lastTouch - currentTouch
                    val verticalAddition = diffFromLast.y * VERTICAL_MULTIPLIER / view.height.toFloat()
                    when (currentTouchAction) {
                        TouchAction.Time -> {
                            val startTime = currentTouchStartPlayerTime?.div(1000L)?.times(1000L)
                            if (startTime != null) {
                                calculateNewTime(startTime, startTouch, currentTouch)?.let { newMs ->
                                    val skipMs = newMs - startTime
                                    playerView.callbacks?.onSeekPreviewText(
                                        "${convertTimeToString(newMs / 1000)} [${
                                            if (abs(skipMs) < 1000) "" else if (skipMs > 0) "+" else "-"
                                        }${convertTimeToString(abs(skipMs / 1000))}]"
                                    )
                                }
                            }
                        }
                        TouchAction.Brightness -> if (!isLocked) handleBrightnessAdjustment(verticalAddition)
                        TouchAction.Volume     -> if (!isLocked) handleVolumeAdjustment(verticalAddition, false)
                        null -> Unit
                    }
                    if (currentTouchAction != TouchAction.Time) {
                        playerView.callbacks?.onSeekPreviewText(null)
                    }
                }
                currentTouchLast = currentTouch
                return true
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                holdHandler.removeCallbacks(holdRunnable)
                if (hasTriggeredSpeedUp) {
                    playerView.player.setPlaybackSpeed(DataStoreHelper.playBackSpeed)
                    showOrHideSpeedUp(false)
                    playerView.callbacks?.onHoldSpeedUp(false)
                    hasTriggeredSpeedUp = false
                }

                if (isCurrentTouchValid) {
                    // Horizontal seek on release
                    if (swipeHorizontalEnabled && currentTouchAction == TouchAction.Time && !isLocked) {
                        val startTime = currentTouchStartPlayerTime
                        if (startTime != null) {
                            calculateNewTime(startTime, startTouch, currentTouch)?.let { seekTo ->
                                if (abs(seekTo - startTime) > MINIMUM_SEEK_TIME) {
                                    playerView.player.seekTo(seekTo, PlayerEventSource.UI)
                                }
                            }
                        }
                    }
                    // Tap detection: only fire if the finger was held briefly (not a long-press).
                    val holdTime = currentTouchStartTime?.let { System.currentTimeMillis() - it }
                    if (currentTouchAction == null && currentLastTouchAction == null
                        && !hasTriggeredSpeedUp
                        && (holdTime == null || holdTime < DOUBLE_TAP_MAXIMUM_HOLD_TIME)) {
                        onTapDetected(
                            x = currentTouch.x,
                            viewWidth = view.width,
                            isLocked = isLocked,
                            onSingleTap = { playerView.callbacks?.onSingleTap() }
                        )
                    }
                }

                playerView.callbacks?.onSeekPreviewText(null)
                val hadSwipe = currentTouchAction != null || currentLastTouchAction != null
                playerView.callbacks?.onGestureEnd(hadSwipe, uiShowingBeforeGesture)

                lastTouchEndTime = System.currentTimeMillis()
                isCurrentTouchValid = false
                currentTouchStart = null
                currentLastTouchAction = currentTouchAction
                currentTouchAction = null
                currentTouchStartPlayerTime = null
                currentTouchLast = null
                currentTouchStartTime = null
                uiShowingBeforeGesture = false
                return true
            }
        }
        return false
    }
}
