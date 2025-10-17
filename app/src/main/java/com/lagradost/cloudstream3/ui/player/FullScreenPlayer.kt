package com.lagradost.cloudstream3.ui.player

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.format.DateUtils
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.CommonActivity.keyEventListener
import com.lagradost.cloudstream3.CommonActivity.playerEventListener
import com.lagradost.cloudstream3.CommonActivity.screenHeightWithOrientation
import com.lagradost.cloudstream3.CommonActivity.screenWidthWithOrientation
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.PlayerCustomLayoutBinding
import com.lagradost.cloudstream3.databinding.SpeedDialogBinding
import com.lagradost.cloudstream3.databinding.SubtitleOffsetBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer.Companion.subsProvidersIsActive
import com.lagradost.cloudstream3.ui.player.source_priority.QualityDataHelper
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.isUsingMobileData
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.getNavigationBarHeight
import com.lagradost.cloudstream3.utils.UIHelper.getStatusBarHeight
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.utils.UIHelper.showSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.UserPreferenceDelegate
import com.lagradost.cloudstream3.utils.Vector2
import com.lagradost.cloudstream3.utils.setText
import com.lagradost.cloudstream3.utils.txt
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt


const val MINIMUM_SEEK_TIME = 7000L         // when swipe seeking
const val MINIMUM_VERTICAL_SWIPE = 2.0f     // in percentage
const val MINIMUM_HORIZONTAL_SWIPE = 2.0f   // in percentage
const val VERTICAL_MULTIPLIER = 2.0f
const val HORIZONTAL_MULTIPLIER = 2.0f
const val DOUBLE_TAB_MAXIMUM_HOLD_TIME = 200L
const val DOUBLE_TAB_MINIMUM_TIME_BETWEEN = 200L    // this also affects the UI show response time
const val DOUBLE_TAB_PAUSE_PERCENTAGE = 0.15        // in both directions
private const val SUBTITLE_DELAY_BUNDLE_KEY = "subtitle_delay"

// All the UI Logic for the player
open class FullScreenPlayer : AbstractPlayerFragment() {
    private var isVerticalOrientation: Boolean = false
    protected open var lockRotation = true
    protected open var isFullScreenPlayer = true
    protected var playerBinding: PlayerCustomLayoutBinding? = null

    private var durationMode: Boolean by UserPreferenceDelegate("duration_mode", false)

    // state of player UI
    protected var isShowing = false
    private var uiShowingBeforeGesture = false
    protected var isLocked = false

    protected var hasEpisodes = false
        private set
    //protected val hasEpisodes
    //    get() = episodes.isNotEmpty()

    // options for player

    /**
     * Default profile 1
     * Decides how links should be sorted based on a priority system.
     * This will be set in runtime based on settings.
     **/
    protected var currentQualityProfile = 1

    //    protected var currentPrefQuality =
//        Qualities.P2160.value // preferred maximum quality, used for ppl w bad internet or on cell
    protected var fastForwardTime = 10000L
    protected var androidTVInterfaceOffSeekTime = 10000L
    protected var androidTVInterfaceOnSeekTime = 30000L
    protected var swipeHorizontalEnabled = false
    protected var swipeVerticalEnabled = false
    protected var playBackSpeedEnabled = false
    protected var playerResizeEnabled = false
    protected var doubleTapEnabled = false
    protected var doubleTapPauseEnabled = true
    protected var playerRotateEnabled = false
    protected var autoPlayerRotateEnabled = false
    private var hideControlsNames = false
    protected var speedupEnabled = false
    protected var subtitleDelay
        set(value) = try {
            player.setSubtitleOffset(-value)
        } catch (e: Exception) {
            logError(e)
        }
        get() = try {
            -player.getSubtitleOffset()
        } catch (e: Exception) {
            logError(e)
            0L
        }

    //private var useSystemBrightness = false
    protected var useTrueSystemBrightness = true
    private val fullscreenNotch = true //TODO SETTING

    private var statusBarHeight: Int? = null
    private var navigationBarHeight: Int? = null

    private val brightnessIcons = listOf(
        R.drawable.sun_1,
        R.drawable.sun_2,
        R.drawable.sun_3,
        R.drawable.sun_4,
        R.drawable.sun_5,
        R.drawable.sun_6,
        //R.drawable.sun_7,
        // R.drawable.ic_baseline_brightness_1_24,
        // R.drawable.ic_baseline_brightness_2_24,
        // R.drawable.ic_baseline_brightness_3_24,
        // R.drawable.ic_baseline_brightness_4_24,
        // R.drawable.ic_baseline_brightness_5_24,
        // R.drawable.ic_baseline_brightness_6_24,
        // R.drawable.ic_baseline_brightness_7_24,
    )

    private val volumeIcons = listOf(
        R.drawable.ic_baseline_volume_mute_24,
        R.drawable.ic_baseline_volume_down_24,
        R.drawable.ic_baseline_volume_up_24,
    )

    private var isShowingEpisodeOverlay: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = super.onCreateView(inflater, container, savedInstanceState) ?: return null
        playerBinding = PlayerCustomLayoutBinding.bind(root.findViewById(R.id.player_holder))
        return root
    }

    override fun onDestroyView() {
        playerBinding = null
        super.onDestroyView()
    }

    open fun showMirrorsDialogue() {
        throw NotImplementedError()
    }

    open fun showTracksDialogue() {
        throw NotImplementedError()
    }

    open fun openOnlineSubPicker(
        context: Context,
        loadResponse: LoadResponse?,
        dismissCallback: (() -> Unit)
    ) {
        throw NotImplementedError()
    }

    open fun showEpisodesOverlay() {
        throw NotImplementedError()
    }

    open fun isThereEpisodes(): Boolean {
        return false
    }

    /**
     * [isValidTouch] should be called on a [View] spanning across the screen for reliable results.
     *
     * Android has supported gesture navigation properly since API-30. We get the absolute screen dimens using
     * [WindowManager.getCurrentWindowMetrics] and remove the stable insets
     * {[WindowInsets.getInsetsIgnoringVisibility]} to get a safe perimeter.
     * This approach supports any and all types of necessary system insets.
     *
     * @return false if the touch is on the status bar or navigation bar
     * */
    private fun View.isValidTouch(rawX: Float, rawY: Float): Boolean {
        // NOTE: screenWidth is without the navbar width when 3button nav is turned on.
        if (Build.VERSION.SDK_INT >= 30) {
            // real = absolute dimen without any default deductions like navbar width
            val windowMetrics =
                (context?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.currentWindowMetrics
            val realScreenHeight =
                windowMetrics?.let { windowMetrics.bounds.bottom - windowMetrics.bounds.top }
                    ?: screenHeightWithOrientation
            val realScreenWidth =
                windowMetrics?.let { windowMetrics.bounds.right - windowMetrics.bounds.left }
                    ?: screenWidthWithOrientation

            val insets =
                rootWindowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            val isOutsideHeight = rawY < insets.top || rawY > (realScreenHeight - insets.bottom)
            val isOutsideWidth = if (windowMetrics == null) {
                rawX < screenWidthWithOrientation
            } else rawX < insets.left || rawX > realScreenWidth - insets.right

            return !(isOutsideWidth || isOutsideHeight)
        } else {
            val statusHeight = statusBarHeight ?: 0
            return rawY > statusHeight && rawX < screenWidthWithOrientation
        }
    }

    override fun exitedPipMode() {
        animateLayoutChanges()
    }

    private fun animateLayoutChangesForSubtitles() =
        // Post here as bottomPlayerBar is gone the first frame => bottomPlayerBar.height = 0
        playerBinding?.bottomPlayerBar?.post {
            @OptIn(UnstableApi::class)
            val sView = subView ?: return@post
            val sStyle = CustomDecoder.style
            val binding = playerBinding ?: return@post

            val move = if (isShowing) minOf(
                // We do not want to drag down subtitles if the subtitle elevation is large
                -sStyle.elevation.toPx,
                // The lib uses Invisible instead of Gone for no reason
                binding.previewFrameLayout.height - binding.bottomPlayerBar.height
            ) else -sStyle.elevation.toPx
            ObjectAnimator.ofFloat(sView, "translationY", move.toFloat()).apply {
                duration = 200
                start()
            }
        }

    protected fun animateLayoutChanges() {
        if (isLayout(PHONE)) { // isEnabled also disables the onKeyDown
            playerBinding?.exoProgress?.isEnabled = isShowing // Prevent accidental clicks/drags
        }

        if (isShowing) {
            updateUIVisibility()
        } else {
            toggleEpisodesOverlay(false)
            playerBinding?.playerHolder?.postDelayed({ updateUIVisibility() }, 200)
        }

        val titleMove = if (isShowing) 0f else -50.toPx.toFloat()
        playerBinding?.playerVideoTitle?.let {
            ObjectAnimator.ofFloat(it, "translationY", titleMove).apply {
                duration = 200
                start()
            }
        }
        playerBinding?.playerVideoTitleRez?.let {
            ObjectAnimator.ofFloat(it, "translationY", titleMove).apply {
                duration = 200
                start()
            }
        }
        val playerBarMove = if (isShowing) 0f else 50.toPx.toFloat()
        playerBinding?.bottomPlayerBar?.let {
            ObjectAnimator.ofFloat(it, "translationY", playerBarMove).apply {
                duration = 200
                start()
            }
        }
        if (isLayout(PHONE)) {
            playerBinding?.playerEpisodesButton?.let {
                ObjectAnimator.ofFloat(it, "translationX", if (isShowing) 0f else 50.toPx.toFloat())
                    .apply {
                        duration = 200
                        start()
                    }
            }
        }
        val fadeTo = if (isShowing) 1f else 0f
        val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo)

        fadeAnimation.duration = 100
        fadeAnimation.fillAfter = true

        animateLayoutChangesForSubtitles()

        val playerSourceMove = if (isShowing) 0f else -50.toPx.toFloat()

        playerBinding?.apply {
            playerOpenSource.let {
                ObjectAnimator.ofFloat(it, "translationY", playerSourceMove).apply {
                    duration = 200
                    start()
                }
            }

            if (!isLocked) {
                playerFfwdHolder.alpha = 1f
                playerRewHolder.alpha = 1f
                // player_pause_play_holder?.alpha = 1f
                shadowOverlay.isVisible = true
                shadowOverlay.startAnimation(fadeAnimation)
                playerFfwdHolder.startAnimation(fadeAnimation)
                playerRewHolder.startAnimation(fadeAnimation)
                playerPausePlay.startAnimation(fadeAnimation)
                downloadBothHeader.startAnimation(fadeAnimation)

                /*if (isBuffering) {
                        player_pause_play?.isVisible = false
                        player_pause_play_holder?.isVisible = false
                    } else {
                        player_pause_play?.isVisible = true
                        player_pause_play_holder?.startAnimation(fadeAnimation)
                        player_pause_play?.startAnimation(fadeAnimation)
                    }*/
                //player_buffering?.startAnimation(fadeAnimation)
            }

            bottomPlayerBar.startAnimation(fadeAnimation)
            playerOpenSource.startAnimation(fadeAnimation)
            playerTopHolder.startAnimation(fadeAnimation)
        }
    }

    @OptIn(UnstableApi::class)
    override fun subtitlesChanged() {
        val tracks = player.getVideoTracks()
        val isBuiltinSubtitles = tracks.currentTextTracks.all { track ->
            track.mimeType == MimeTypes.APPLICATION_MEDIA3_CUES
        }
        // Subtitle offset is not possible on built-in media3 tracks
        playerBinding?.playerSubtitleOffsetBtt?.isGone =
            isBuiltinSubtitles || tracks.currentTextTracks.isEmpty()
    }

    private fun restoreOrientationWithSensor(activity: Activity) {
        val currentOrientation = activity.resources.configuration.orientation
        val orientation = when (currentOrientation) {
            Configuration.ORIENTATION_LANDSCAPE ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

            Configuration.ORIENTATION_PORTRAIT ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

            else -> dynamicOrientation()
        }
        activity.requestedOrientation = orientation
    }

    private fun toggleOrientationWithSensor(activity: Activity) {
        val currentOrientation = activity.resources.configuration.orientation
        val orientation: Int = when (currentOrientation) {
            Configuration.ORIENTATION_LANDSCAPE ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

            Configuration.ORIENTATION_PORTRAIT ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

            else -> dynamicOrientation()
        }
        activity.requestedOrientation = orientation
    }

    open fun lockOrientation(activity: Activity) {
        @Suppress("DEPRECATION")
        val display = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        else activity.display!!
        val rotation = display.rotation
        val currentOrientation = activity.resources.configuration.orientation
        val orientation: Int
        when (currentOrientation) {
            Configuration.ORIENTATION_LANDSCAPE ->
                orientation =
                    if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90)
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE

            Configuration.ORIENTATION_PORTRAIT ->
                orientation =
                    if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_270)
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT

            else -> orientation = dynamicOrientation()
        }
        activity.requestedOrientation = orientation
    }

    private fun updateOrientation(ignoreDynamicOrientation: Boolean = false) {
        activity?.apply {
            if (lockRotation) {
                if (isLocked) {
                    lockOrientation(this)
                } else {
                    if (ignoreDynamicOrientation) {
                        // restore when lock is disabled
                        restoreOrientationWithSensor(this)
                    } else {
                        this.requestedOrientation = dynamicOrientation()
                    }
                }
            }
        }
    }

    protected fun enterFullscreen() {
        if (isFullScreenPlayer) {
            activity?.hideSystemUI()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && fullscreenNotch) {
                val params = activity?.window?.attributes
                params?.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                activity?.window?.attributes = params
            }
        }
        updateOrientation()
    }

    protected fun exitFullscreen() {
        //if (lockRotation)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER

        // simply resets brightness and notch settings that might have been overridden
        val lp = activity?.window?.attributes
        lp?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp?.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
        activity?.window?.attributes = lp
        activity?.showSystemUI()
    }

    override fun onResume() {
        enterFullscreen()
        verifyVolume()
        activity?.attachBackPressedCallback("FullScreenPlayer") {
            if (isShowingEpisodeOverlay) {
                // isShowingEpisodeOverlay pauses, so this makes it easier to unpause
                if (isLayout(TV or EMULATOR)) {
                    playerPausePlay?.requestFocus()
                }
                toggleEpisodesOverlay(show = false)
                return@attachBackPressedCallback
            } else if (isShowing && isLayout(TV or EMULATOR)) {
                // netflix capture back and hide ~monke
                onClickChange()
            } else {
                activity?.popCurrentPage("FullScreenPlayer")
            }
        }
        super.onResume()
    }

    override fun onStop() {
        activity?.detachBackPressedCallback("FullScreenPlayer")
        super.onStop()
    }

    override fun onDestroy() {
        exitFullscreen()
        player.release()
        player.releaseCallbacks()
        player = CS3IPlayer()
        super.onDestroy()
    }

    private fun setPlayBackSpeed(speed: Float) {
        try {
            DataStoreHelper.playBackSpeed = speed
            playerBinding?.playerSpeedBtt?.text =
                getString(R.string.player_speed_text_format).format(speed)
                    .replace(".0x", "x")
        } catch (e: Exception) {
            // the format string was wrong
            logError(e)
        }

        player.setPlaybackSpeed(speed)
    }

    private fun skipOp() {
        player.seekTime(85000) // skip 85s
    }

    private fun showSubtitleOffsetDialog() {
        val ctx = context ?: return
        // Pause player because the subtitles cannot be continuously updated to follow playback.
        player.handleEvent(
            CSPlayerEvent.Pause,
            PlayerEventSource.UI
        )

        val binding = SubtitleOffsetBinding.inflate(LayoutInflater.from(ctx), null, false)

        // Use dialog as opposed to alertdialog to get fullscreen
        val dialog = Dialog(ctx, R.style.AlertDialogCustomBlack).apply {
            setContentView(binding.root)
        }
        dialog.show()

        val beforeOffset = subtitleDelay

        binding.apply {
            var subtitleAdapter: SubtitleOffsetItemAdapter? = null

            subtitleOffsetInput.doOnTextChanged { text, _, _, _ ->
                text?.toString()?.toLongOrNull()?.let { time ->
                    subtitleDelay = time

                    // Scroll to the first active subtitle
                    val playerPosition = player.getPosition() ?: 0
                    val totalPosition = playerPosition - subtitleDelay
                    subtitleAdapter?.updateTime(totalPosition)

                    subtitleAdapter?.getLatestActiveItem(totalPosition)
                        ?.let { subtitlePos ->
                            subtitleOffsetRecyclerview.scrollToPosition(subtitlePos)
                        }

                    val str = when {
                        time > 0L -> {
                            txt(R.string.subtitle_offset_extra_hint_later_format, time)
                        }

                        time < 0L -> {
                            txt(R.string.subtitle_offset_extra_hint_before_format, -time)
                        }

                        else -> {
                            txt(R.string.subtitle_offset_extra_hint_none_format)
                        }
                    }
                    subtitleOffsetSubTitle.setText(str)
                }
            }
            subtitleOffsetInput.text =
                Editable.Factory.getInstance()?.newEditable(beforeOffset.toString())

            val subtitles = player.getSubtitleCues().toMutableList()

            subtitleOffsetRecyclerview.isVisible = subtitles.isNotEmpty()
            noSubtitlesLoadedNotice.isVisible = subtitles.isEmpty()

            val initialSubtitlePosition = (player.getPosition() ?: 0) - subtitleDelay
            subtitleAdapter =
                SubtitleOffsetItemAdapter(initialSubtitlePosition, subtitles) { subtitleCue ->
                    val playerPosition = player.getPosition() ?: 0
                    subtitleOffsetInput.text = Editable.Factory.getInstance()
                        ?.newEditable((playerPosition - subtitleCue.startTimeMs).toString())
                }

            subtitleOffsetRecyclerview.adapter = subtitleAdapter
            // Prevent flashing changes when changing items
            (subtitleOffsetRecyclerview.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations =
                false

            val firstSubtitle = subtitleAdapter.getLatestActiveItem(initialSubtitlePosition)
            subtitleOffsetRecyclerview.scrollToPosition(firstSubtitle)

            val buttonChange = 100L
            val buttonChangeMore = 1000L

            fun changeBy(by: Long) {
                val current = (subtitleOffsetInput.text?.toString()?.toLongOrNull() ?: 0) + by
                subtitleOffsetInput.text =
                    Editable.Factory.getInstance()?.newEditable(current.toString())
            }

            subtitleOffsetAdd.setOnClickListener {
                changeBy(buttonChange)
            }
            subtitleOffsetAddMore.setOnClickListener {
                changeBy(buttonChangeMore)
            }
            subtitleOffsetSubtract.setOnClickListener {
                changeBy(-buttonChange)
            }
            subtitleOffsetSubtractMore.setOnClickListener {
                changeBy(-buttonChangeMore)
            }

            dialog.setOnDismissListener {
                if (isFullScreenPlayer)
                    activity?.hideSystemUI()
            }
            applyBtt.setOnClickListener {
                dialog.dismissSafe(activity)
                player.seekTime(1L)
            }
            resetBtt.setOnClickListener {
                subtitleDelay = 0
                dialog.dismissSafe(activity)
                player.seekTime(1L)
            }
            cancelBtt.setOnClickListener {
                subtitleDelay = beforeOffset
                dialog.dismissSafe(activity)
            }
        }
    }


    @SuppressLint("SetTextI18n")
    fun updateSpeedDialogBinding(binding: SpeedDialogBinding) {
        val speed = player.getPlaybackSpeed()
        binding.speedText.text = "%.2fx".format(speed).replace(".0x", "x")
        // Android crashes if you don't round to an exact step size
        binding.speedBar.value = (speed.coerceIn(0.1f, 2.0f) / binding.speedBar.stepSize).roundToInt().toFloat() * binding.speedBar.stepSize
    }

    private fun showSpeedDialog() {
        val act = activity ?: return
        val isPlaying = player.getIsPlaying()
        player.handleEvent(CSPlayerEvent.Pause, PlayerEventSource.UI)

        val binding: SpeedDialogBinding = SpeedDialogBinding.inflate(
            LayoutInflater.from(act)
        )

        updateSpeedDialogBinding(binding)
        for ((view, speed) in arrayOf(
            binding.speed25 to 0.25f,
            binding.speed100 to 1.0f,
            binding.speed125 to 1.25f,
            binding.speed150 to 1.5f,
            binding.speed200 to 2.0f,
        )) {
            view.setOnClickListener {
                setPlayBackSpeed(speed)
                updateSpeedDialogBinding(binding)
            }
        }

        binding.speedMinus.setOnClickListener {
            setPlayBackSpeed(maxOf((player.getPlaybackSpeed() - 0.1f), 0.1f))
            updateSpeedDialogBinding(binding)
        }

        binding.speedPlus.setOnClickListener {
            setPlayBackSpeed(minOf((player.getPlaybackSpeed() + 0.1f), 2.0f))
            updateSpeedDialogBinding(binding)
        }

        binding.speedBar.addOnChangeListener { slider, value, fromUser ->
            if (fromUser) {
                setPlayBackSpeed(value)
                updateSpeedDialogBinding(binding)
            }
        }

        val dismiss = DialogInterface.OnDismissListener {
            if (isFullScreenPlayer)
                activity?.hideSystemUI()
            if (isPlaying) {
                player.handleEvent(CSPlayerEvent.Play, PlayerEventSource.UI)
            }
        }

        //if (isLayout(PHONE)) {
        //    val builder =
        //        BottomSheetDialog(act, R.style.AlertDialogCustom)
        //    builder.setContentView(binding.root)
        //    builder.setOnDismissListener(dismiss)
        //    builder.show()
        //} else {
        val builder =
            AlertDialog.Builder(act, R.style.AlertDialogCustom)
                .setView(binding.root)
        builder.setOnDismissListener(dismiss)
        val dialog = builder.create()
        dialog.show()
        //}
    }

    fun resetRewindText() {
        playerBinding?.exoRewText?.text =
            getString(R.string.rew_text_regular_format).format(fastForwardTime / 1000)
    }

    fun resetFastForwardText() {
        playerBinding?.exoFfwdText?.text =
            getString(R.string.ffw_text_regular_format).format(fastForwardTime / 1000)
    }

    private fun rewind() {
        try {
            playerBinding?.apply {
                playerCenterMenu.isGone = false
                playerRewHolder.alpha = 1f

                val rotateLeft = AnimationUtils.loadAnimation(context, R.anim.rotate_left)
                playerRew.startAnimation(rotateLeft)

                val goLeft = AnimationUtils.loadAnimation(context, R.anim.go_left)
                goLeft.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}

                    override fun onAnimationRepeat(animation: Animation?) {}

                    override fun onAnimationEnd(animation: Animation?) {
                        exoRewText.post {
                            resetRewindText()
                            playerCenterMenu.isGone = !isShowing
                            playerRewHolder.alpha = if (isShowing) 1f else 0f
                        }
                    }
                })
                exoRewText.startAnimation(goLeft)
                exoRewText.text =
                    getString(R.string.rew_text_format).format(fastForwardTime / 1000)
            }
            player.seekTime(-fastForwardTime)
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun fastForward() {
        try {
            playerBinding?.apply {
                playerCenterMenu.isGone = false
                playerFfwdHolder.alpha = 1f

                val rotateRight = AnimationUtils.loadAnimation(context, R.anim.rotate_right)
                playerFfwd.startAnimation(rotateRight)

                val goRight = AnimationUtils.loadAnimation(context, R.anim.go_right)
                goRight.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}

                    override fun onAnimationRepeat(animation: Animation?) {}

                    override fun onAnimationEnd(animation: Animation?) {
                        exoFfwdText.post {
                            resetFastForwardText()
                            playerCenterMenu.isGone = !isShowing
                            playerFfwdHolder.alpha = if (isShowing) 1f else 0f
                        }
                    }
                })
                exoFfwdText.startAnimation(goRight)
                exoFfwdText.text =
                    getString(R.string.ffw_text_format).format(fastForwardTime / 1000)
            }
            player.seekTime(fastForwardTime)
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun onClickChange() {
        isShowing = !isShowing
        if (isShowing) {
            playerBinding?.playerIntroPlay?.isGone = true
            autoHide()
        }
        if (isFullScreenPlayer)
            activity?.hideSystemUI()
        animateLayoutChanges()
        if (playerBinding?.playerEpisodeOverlay?.isGone == true) playerBinding?.playerPausePlay?.requestFocus()
    }

    private fun toggleLock() {
        if (!isShowing) {
            onClickChange()
        }

        isLocked = !isLocked
        updateOrientation(true) // set true to ignore auto rotate to stay in current orientation

        if (isLocked && isShowing) {
            playerBinding?.playerHolder?.postDelayed({
                if (isLocked && isShowing) {
                    onClickChange()
                }
            }, 200)
        }

        val fadeTo = if (isLocked) 0f else 1f
        playerBinding?.apply {
            val fadeAnimation = AlphaAnimation(playerVideoTitle.alpha, fadeTo).apply {
                duration = 100
                fillAfter = true
            }

            updateUIVisibility()
            // MENUS
            //centerMenu.startAnimation(fadeAnimation)
            playerPausePlay.startAnimation(fadeAnimation)
            playerFfwdHolder.startAnimation(fadeAnimation)
            playerRewHolder.startAnimation(fadeAnimation)
            downloadBothHeader.startAnimation(fadeAnimation)

            if (hasEpisodes)
                playerEpisodesButton.startAnimation(fadeAnimation)
            //player_media_route_button?.startAnimation(fadeAnimation)
            //video_bar.startAnimation(fadeAnimation)

            //TITLE
            playerVideoTitleRez.startAnimation(fadeAnimation)
            playerEpisodeFiller.startAnimation(fadeAnimation)
            playerVideoTitle.startAnimation(fadeAnimation)
            playerTopHolder.startAnimation(fadeAnimation)
            // BOTTOM
            playerLockHolder.startAnimation(fadeAnimation)
            //player_go_back_holder?.startAnimation(fadeAnimation)

            shadowOverlay.isVisible = true
            shadowOverlay.startAnimation(fadeAnimation)
        }
        updateLockUI()
    }

    open fun updateUIVisibility() {
        val isGone = isLocked || !isShowing
        var togglePlayerTitleGone = isGone
        context?.let {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(it)
            val limitTitle = settingsManager.getInt(getString(R.string.prefer_limit_title_key), 0)
            if (limitTitle < 0) {
                togglePlayerTitleGone = true
            }
        }
        playerBinding?.apply {

            playerLockHolder.isGone = isGone
            playerVideoBar.isGone = isGone

            playerPausePlay.isGone = isGone
            //player_buffering?.isGone = isGone
            playerTopHolder.isGone = isGone
            val showPlayerEpisodes = !isGone && isThereEpisodes()
            playerEpisodesButtonRoot.isVisible = showPlayerEpisodes
            playerEpisodesButton.isVisible = showPlayerEpisodes
            playerVideoTitle.isGone = togglePlayerTitleGone
//        player_video_title_rez?.isGone = isGone
            playerEpisodeFiller.isGone = isGone
            playerCenterMenu.isGone = isGone
            playerLock.isGone = !isShowing
            //player_media_route_button?.isClickable = !isGone
            playerGoBackHolder.isGone = isGone
            playerSourcesBtt.isGone = isGone
            playerSkipEpisode.isClickable = !isGone
        }
    }

    private fun updateLockUI() {
        playerBinding?.apply {
            playerLock.setIconResource(if (isLocked) R.drawable.video_locked else R.drawable.video_unlocked)
            if (layout == R.layout.fragment_player) {
                val color = if (isLocked) context?.colorFromAttribute(R.attr.colorPrimary)
                else Color.WHITE
                if (color != null) {
                    playerLock.setTextColor(color)
                    playerLock.iconTint = ColorStateList.valueOf(color)
                    playerLock.rippleColor =
                        ColorStateList.valueOf(Color.argb(50, color.red, color.green, color.blue))
                }
            }
        }
    }

    private var currentTapIndex = 0
    protected fun autoHide() {
        currentTapIndex++
        delayHide()
    }

    protected fun hidePlayerUI() {
        if (isShowing) {
            isShowing = false
            animateLayoutChanges()
        }
    }

    override fun playerStatusChanged() {
        super.playerStatusChanged()
        delayHide()
    }

    private fun delayHide() {
        val index = currentTapIndex
        playerBinding?.playerHolder?.postDelayed({
            if (!isCurrentTouchValid && isShowing && index == currentTapIndex && player.getIsPlaying()) {
                onClickChange()
            }
        }, 2000)
    }

    // this is used because you don't want to hide UI when double tap seeking
    private var currentDoubleTapIndex = 0
    private fun toggleShowDelayed() {
        if (doubleTapEnabled || doubleTapPauseEnabled) {
            val index = currentDoubleTapIndex
            playerBinding?.playerHolder?.postDelayed({
                if (index == currentDoubleTapIndex) {
                    onClickChange()
                }
            }, DOUBLE_TAB_MINIMUM_TIME_BETWEEN)
        } else {
            onClickChange()
        }
    }

    private var isCurrentTouchValid = false
    private var currentTouchStart: Vector2? = null
    private var currentTouchLast: Vector2? = null
    private var currentTouchAction: TouchAction? = null
    private var currentLastTouchAction: TouchAction? = null
    private var currentTouchStartPlayerTime: Long? =
        null // the time in the player when you first click
    private var currentTouchStartTime: Long? = null // the system time when you first click
    private var currentLastTouchEndTime: Long = 0 // the system time when you released your finger
    private var currentClickCount: Int =
        0 // amount of times you have double clicked, will reset when other action is taken

    // requested volume and brightness is used to make swiping smoother
    // to make it not jump between values,
    // this value is within the range [0,2] where 1+ is loudness
    private var currentRequestedVolume: Float = 0.0f

    // this value is within the range [0,1]
    private var currentRequestedBrightness: Float = 1.0f

    enum class TouchAction {
        Brightness,
        Volume,
        Time,
    }

    companion object {
        private fun forceLetters(inp: Long, letters: Int = 2): String {
            val added: Int = letters - inp.toString().length
            return if (added > 0) {
                "0".repeat(added) + inp.toString()
            } else {
                inp.toString()
            }
        }

        private fun convertTimeToString(sec: Long): String {
            val rsec = sec % 60L
            val min = ceil((sec - rsec) / 60.0).toInt()
            val rmin = min % 60L
            val h = ceil((min - rmin) / 60.0).toLong()
            //int rh = h;// h % 24;
            return (if (h > 0) forceLetters(h) + ":" else "") + (if (rmin >= 0 || h >= 0) forceLetters(
                rmin
            ) + ":" else "") + forceLetters(
                rsec
            )
        }
    }

    private fun calculateNewTime(
        startTime: Long?,
        touchStart: Vector2?,
        touchEnd: Vector2?
    ): Long? {
        if (touchStart == null || touchEnd == null || startTime == null) return null
        val diffX =
            (touchEnd.x - touchStart.x) * HORIZONTAL_MULTIPLIER / screenWidthWithOrientation.toFloat()
        val duration = player.getDuration() ?: return null
        return max(
            min(
                startTime + ((duration * (diffX * diffX)) * (if (diffX < 0) -1 else 1)).toLong(),
                duration
            ), 0
        )
    }

    private fun getBrightness(): Float? {
        return if (useTrueSystemBrightness) {
            try {
                Settings.System.getInt(
                    context?.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                ) / 255f
            } catch (e: Exception) {
                // because true system brightness requires
                // permission, this is a lazy way to check
                // as it will throw an error if we do not have it
                useTrueSystemBrightness = false
                return getBrightness()
            }
        } else {
            try {
                activity?.window?.attributes?.screenBrightness
            } catch (e: Exception) {
                logError(e)
                null
            }
        }
    }

    private fun setBrightness(brightness: Float) {
        if (useTrueSystemBrightness) {
            try {
                Settings.System.putInt(
                    context?.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )

                Settings.System.putInt(
                    context?.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS, (brightness * 255).toInt()
                )
            } catch (e: Exception) {
                useTrueSystemBrightness = false
                setBrightness(brightness)
            }
        } else {
            try {
                val lp = activity?.window?.attributes
                lp?.screenBrightness = brightness
                activity?.window?.attributes = lp
            } catch (e: Exception) {
                logError(e)
            }
        }
    }

    private var isVolumeLocked: Boolean = false
    private var hasShownVolumeToast: Boolean = false

    private var progressBarLeftHideRunnable: Runnable? = null
    private var progressBarRightHideRunnable: Runnable? = null

    // Verifies that the currentRequestedVolume matches the system volume
    // if not, then it removes changes currentRequestedVolume and removes the loudnessEnhancer
    // if the real volume is less than 100%
    //
    // This is here to make returning to the player less jarring, if we change the volume outside
    // the app. Note that this will make it a bit wierd when using loudness in PiP, then returning
    // however that is the cost of correctness.
    private fun verifyVolume() {
        (activity?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.let { audioManager ->
            val currentVolumeStep =
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolumeStep =
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

            // if we can set the volume directly then do it
            if (currentVolumeStep < maxVolumeStep || currentRequestedVolume <= 1.0f) {
                currentRequestedVolume =
                    currentVolumeStep.toFloat() / maxVolumeStep.toFloat()

                loudnessEnhancer?.release()
                loudnessEnhancer = null
            }
        }
    }

    val holdhandler = Handler(Looper.getMainLooper())
    var hasTriggeredSpeedUp = false
    val holdRunnable = Runnable {
        if (isShowing) {
            onClickChange()
        }
        player.setPlaybackSpeed(2.0f)
        showOrHideSpeedUp(true)
        hasTriggeredSpeedUp = true
    }

    private fun showOrHideSpeedUp(show: Boolean) {
        playerBinding?.playerSpeedupButton?.let { button ->
            button.clearAnimation()
            button.alpha = if (show) 0f else 1f
            button.isVisible = show
            button.animate()
                .alpha(if (show) 1f else 0f)
                .setDuration(200L)
                .start()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun handleMotionEvent(view: View?, event: MotionEvent?): Boolean {
        if (event == null || view == null) return false
        val currentTouch = Vector2(event.x, event.y)
        val startTouch = currentTouchStart

        playerBinding?.apply {
            playerIntroPlay.isGone = true

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // validates if the touch is inside of the player area
                    isCurrentTouchValid = view.isValidTouch(currentTouch.x, currentTouch.y)
                    if (isCurrentTouchValid && isShowingEpisodeOverlay) {
                        toggleEpisodesOverlay(show = false)
                    } else if (isCurrentTouchValid) {
                        if (speedupEnabled) {
                            hasTriggeredSpeedUp = false
                            if (player.getIsPlaying() && !isLocked && isFullScreenPlayer) {
                                holdhandler.postDelayed(holdRunnable, 500)
                            }
                        }
                        isVolumeLocked = currentRequestedVolume < 1.0f
                        if (currentRequestedVolume <= 1.0f) {
                            hasShownVolumeToast = false
                        }

                        currentTouchStartTime = System.currentTimeMillis()
                        currentTouchStart = currentTouch
                        currentTouchLast = currentTouch
                        currentTouchStartPlayerTime = player.getPosition()

                        getBrightness()?.let {
                            currentRequestedBrightness = it
                        }
                        verifyVolume()
                    }
                }

                MotionEvent.ACTION_UP -> {
                    holdhandler.removeCallbacks(holdRunnable)
                    if (hasTriggeredSpeedUp) {
                        player.setPlaybackSpeed(DataStoreHelper.playBackSpeed)
                        showOrHideSpeedUp(false)
                    }
                    if (isCurrentTouchValid && !isLocked && isFullScreenPlayer) {
                        // seek time
                        if (swipeHorizontalEnabled && currentTouchAction == TouchAction.Time) {
                            val startTime = currentTouchStartPlayerTime
                            if (startTime != null) {
                                calculateNewTime(
                                    startTime,
                                    startTouch,
                                    currentTouch
                                )?.let { seekTo ->
                                    if (abs(seekTo - startTime) > MINIMUM_SEEK_TIME) {
                                        player.seekTo(seekTo, PlayerEventSource.UI)
                                    }
                                }
                            }
                        }
                    }

                    // see if click is eligible for seek 10s
                    val holdTime = currentTouchStartTime?.minus(System.currentTimeMillis())
                    if (isCurrentTouchValid // is valid
                        && currentTouchAction == null // no other action like swiping is taking place
                        && currentLastTouchAction == null // last action was none, this prevents mis input random seek
                        && holdTime != null
                        && holdTime < DOUBLE_TAB_MAXIMUM_HOLD_TIME // it is a click not a long hold
                    ) {
                        if (!isLocked
                            && (System.currentTimeMillis() - currentLastTouchEndTime) < DOUBLE_TAB_MINIMUM_TIME_BETWEEN // the time since the last action is short
                        ) {
                            currentClickCount++

                            if (currentClickCount >= 1) { // have double clicked
                                currentDoubleTapIndex++
                                if (doubleTapPauseEnabled && isFullScreenPlayer) { // you can pause if your tap is in the middle of the screen
                                    when {
                                        currentTouch.x < screenWidthWithOrientation / 2 - (DOUBLE_TAB_PAUSE_PERCENTAGE * screenWidthWithOrientation) -> {
                                            if (doubleTapEnabled)
                                                rewind()
                                        }

                                        currentTouch.x > screenWidthWithOrientation / 2 + (DOUBLE_TAB_PAUSE_PERCENTAGE * screenWidthWithOrientation) -> {
                                            if (doubleTapEnabled)
                                                fastForward()
                                        }

                                        else -> {
                                            player.handleEvent(
                                                CSPlayerEvent.PlayPauseToggle,
                                                PlayerEventSource.UI
                                            )
                                        }
                                    }
                                } else if (doubleTapEnabled && isFullScreenPlayer) {
                                    if (currentTouch.x < screenWidthWithOrientation / 2) {
                                        rewind()
                                    } else {
                                        fastForward()
                                    }
                                }
                            }
                        } else {
                            // is a valid click but not fast enough for seek
                            currentClickCount = 0
                            if (!hasTriggeredSpeedUp) {
                                toggleShowDelayed()
                            }
                            //onClickChange()
                        }
                    } else {
                        currentClickCount = 0
                    }

                    // If we hid the UI for a gesture and playback is paused, show it again
                    if (!player.getIsPlaying()) {
                        val didGesture =
                            currentTouchAction != null || currentLastTouchAction != null
                        if (didGesture && uiShowingBeforeGesture && !isShowing) {
                            isShowing = true
                            animateLayoutChanges()
                        }
                    }

                    // call auto hide as it wont hide when you have your finger down
                    autoHide()

                    // reset variables
                    isCurrentTouchValid = false
                    currentTouchStart = null
                    currentLastTouchAction = currentTouchAction
                    currentTouchAction = null
                    currentTouchStartPlayerTime = null
                    currentTouchLast = null
                    currentTouchStartTime = null
                    uiShowingBeforeGesture = false

                    // resets UI
                    playerTimeText.isVisible = false

                    currentLastTouchEndTime = System.currentTimeMillis()
                }

                MotionEvent.ACTION_MOVE -> {
                    // if current touch is valid

                    if (hasTriggeredSpeedUp) {
                        return true
                    }
                    if (startTouch != null && isCurrentTouchValid && !isLocked && isFullScreenPlayer) {
                        // action is unassigned and can therefore be assigned

                        if (currentTouchAction == null) {
                            val diffFromStart = startTouch - currentTouch
                            if (swipeVerticalEnabled) {
                                if (abs(diffFromStart.y * 100 / screenHeightWithOrientation) > MINIMUM_VERTICAL_SWIPE) {
                                    // left = Brightness, right = Volume, but the UI is reversed to show the UI better
                                    uiShowingBeforeGesture = isShowing
                                    currentTouchAction =
                                        if (startTouch.x < screenWidthWithOrientation / 2) {
                                            // hide the UI if you hold brightness to show screen better, better UX
                                            hidePlayerUI()
                                            TouchAction.Brightness
                                        } else {
                                            // hide the UI if you hold volume to show screen better, better UX
                                            hidePlayerUI()
                                            TouchAction.Volume
                                        }
                                }
                            }
                            if (swipeHorizontalEnabled) {
                                if (abs(diffFromStart.x * 100 / screenHeightWithOrientation) > MINIMUM_HORIZONTAL_SWIPE) {
                                    currentTouchAction = TouchAction.Time
                                }
                            }
                        }

                        // display action
                        val lastTouch = currentTouchLast
                        if (lastTouch != null) {
                            val diffFromLast = lastTouch - currentTouch
                            val verticalAddition =
                                diffFromLast.y * VERTICAL_MULTIPLIER / screenHeightWithOrientation.toFloat()

                            // update UI
                            playerTimeText.isVisible = false

                            when (currentTouchAction) {
                                TouchAction.Time -> {
                                    holdhandler.removeCallbacks(holdRunnable)
                                    // this simply updates UI as the seek logic happens on release
                                    // startTime is rounded to make the UI sync in a nice way
                                    val startTime =
                                        currentTouchStartPlayerTime?.div(1000L)?.times(1000L)
                                    if (startTime != null) {
                                        calculateNewTime(
                                            startTime,
                                            startTouch,
                                            currentTouch
                                        )?.let { newMs ->
                                            val skipMs = newMs - startTime
                                            playerTimeText.apply {
                                                text =
                                                    "${convertTimeToString(newMs / 1000)} [${
                                                        (if (abs(skipMs) < 1000) "" else (if (skipMs > 0) "+" else "-"))
                                                    }${convertTimeToString(abs(skipMs / 1000))}]"
                                                isVisible = true
                                            }
                                        }
                                    }
                                }

                                TouchAction.Brightness -> {
                                    holdhandler.removeCallbacks(holdRunnable)
                                    playerBinding?.playerProgressbarRightHolder?.apply {
                                        if (!isVisible || alpha < 1f) {
                                            alpha = 1f
                                            isVisible = true
                                        }

                                        progressBarRightHideRunnable?.let { removeCallbacks(it) }
                                        progressBarRightHideRunnable = Runnable {
                                            // Fade out the progress bar
                                            animate().cancel()
                                            animate()
                                                .alpha(0f)
                                                .setDuration(300)
                                                .withEndAction { isVisible = false }
                                                .start()
                                        }
                                        // Show the progress bar for 1.5 seconds
                                        postDelayed(progressBarRightHideRunnable, 1500)
                                    }

                                    val lastRequested = currentRequestedBrightness
                                    currentRequestedBrightness =
                                        min(
                                            1.0f,
                                            max(currentRequestedBrightness + verticalAddition, 0.0f)
                                        )

                                    // this is to not spam request it, just in case it fucks over someone
                                    if (lastRequested != currentRequestedBrightness)
                                        setBrightness(currentRequestedBrightness)

                                    // max is set high to make it smooth
                                    playerProgressbarRight.max = 100_000
                                    playerProgressbarRight.progress =
                                        max(2_000, (currentRequestedBrightness * 100_000f).toInt())

                                    playerProgressbarRightIcon.setImageResource(
                                        brightnessIcons[min( // clamp the value just in case
                                            brightnessIcons.size - 1,
                                            max(
                                                0,
                                                round(currentRequestedBrightness * (brightnessIcons.size - 1)).toInt()
                                            )
                                        )]
                                    )
                                }

                                TouchAction.Volume -> {
                                    holdhandler.removeCallbacks(holdRunnable)
                                    handleVolumeAdjustment(
                                        verticalAddition,
                                        false
                                    )
                                }

                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
        currentTouchLast = currentTouch
        return true
    }

    @SuppressLint("GestureBackNavigation")
    private fun handleKeyEvent(event: KeyEvent, hasNavigated: Boolean): Boolean {
        if (hasNavigated) {
            autoHide()
            return false
        }
        val keyCode = event.keyCode

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER -> {
                    if (!isShowing) {
                        if (!isLocked) player.handleEvent(CSPlayerEvent.PlayPauseToggle)
                        onClickChange()
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (!isShowing && !isShowingEpisodeOverlay) {
                        onClickChange()
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (!isShowing && !isLocked && !isShowingEpisodeOverlay) {
                        player.seekTime(-androidTVInterfaceOffSeekTime)
                        return true
                    } else if (playerBinding?.playerPausePlay?.isFocused == true) {
                        player.seekTime(-androidTVInterfaceOnSeekTime)
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!isShowing && !isLocked && !isShowingEpisodeOverlay) {
                        player.seekTime(androidTVInterfaceOffSeekTime)
                        return true
                    } else if (playerBinding?.playerPausePlay?.isFocused == true) {
                        player.seekTime(androidTVInterfaceOnSeekTime)
                        return true
                    }
                }

                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (isLayout(PHONE or EMULATOR) && isFullScreenPlayer) {
                        /**
                         * Some TVs do not support volume boosting, and overriding
                         * the volume buttons can be inconvenient for TV users.
                         * Since boosting volume is mainly useful on phones and emulators,
                         * we limit this feature to those devices.
                         */
                        verifyVolume()
                        if (currentRequestedVolume <= 1.0f) {
                            hasShownVolumeToast = false
                        }
                        isVolumeLocked = currentRequestedVolume < 1.0f
                        handleVolumeAdjustment(
                            // +- 5%
                            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                                0.05f
                            } else {
                                -0.05f
                            },
                            true
                        )
                        return true
                    }
                }
            }
        }

        when (keyCode) {
            // don't allow dpad move when hidden

            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
            KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP_LEFT,
            KeyEvent.KEYCODE_DPAD_UP_RIGHT -> {
                if (!isShowing) {
                    return true
                } else {
                    autoHide()
                }
            }

            // netflix capture back and hide ~monke
            // This is removed due to inconsistent behavior on A36 vs A22, see https://github.com/recloudstream/cloudstream/issues/1804
            /*KeyEvent.KEYCODE_BACK -> {
                if (isShowing && isLayout(TV or EMULATOR)) {
                    onClickChange()
                    return true
                }
            }*/
        }

        return false
    }

    private var loudnessEnhancer: LoudnessEnhancer? = null

    @OptIn(UnstableApi::class)
    private fun handleVolumeAdjustment(
        delta: Float,
        fromButton: Boolean,
    ) {
        val audioManager =
            activity?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val currentVolumeStep = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolumeStep = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        val currentVolume = currentRequestedVolume
        val isCurrentVolumeLocked = isVolumeLocked

        val nextVolume =
            (currentVolume + delta).coerceIn(0.0f, if (isCurrentVolumeLocked) 1.0f else 2.0f)

        val nextVolumeStep =
            (nextVolume * maxVolumeStep.toFloat()).roundToInt().coerceIn(0, maxVolumeStep)

        // show toast
        if (fromButton) {
            // for button related request we only show a toast when we exceeded the volume
            if (currentVolume <= 1.0f && nextVolume > 1.0f && !hasShownVolumeToast) {
                showToast(R.string.volume_exceeded_100)
                hasShownVolumeToast = true
            }
        } else {
            val nextRequestedVolume = currentVolume + delta

            // for swipes, we show toast that we need to swipe again
            if (nextRequestedVolume > 1.0 && isCurrentVolumeLocked && !hasShownVolumeToast) {
                showToast(R.string.slide_up_again_to_exceed_100)
                hasShownVolumeToast = true
            }
        }

        // set the current volume step
        if (nextVolumeStep != currentVolumeStep) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVolumeStep, 0)
        }

        var hasBoostError = false

        // Apply loudness enhancer for volumes > 100%, removes it if less
        if (nextVolume > 1.0f) {
            val boostFactor = ((nextVolume - 1.0f) * 1000).toInt()
            val currentEnhancer = loudnessEnhancer

            if (currentEnhancer != null) {
                currentEnhancer.setTargetGain(boostFactor)
            } else {
                val audioSessionId = (playerView?.player as? ExoPlayer)?.audioSessionId
                if (audioSessionId != null && audioSessionId != AudioManager.ERROR) {
                    try {
                        loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                            setTargetGain(boostFactor)
                            enabled = true
                        }
                    } catch (t: Throwable) {
                        logError(t)
                        hasBoostError = true
                    }
                }
            }
        } else {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        }

        currentRequestedVolume = nextVolume

        // Update the progress bar
        playerBinding?.apply {
            val level1ProgressBar = playerProgressbarLeftLevel1
            val level2ProgressBar = playerProgressbarLeftLevel2

            // Change color to show that LoudnessEnhancer broke
            // this is not a real fix, but solves the crash issue
            if (nextVolume > 1.0f) {
                level2ProgressBar.progressTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        level2ProgressBar.context, if (hasBoostError) {
                            R.color.colorPrimaryRed
                        } else {
                            R.color.colorPrimaryOrange
                        }
                    )
                )
            }

            level1ProgressBar.max = 100_000
            level1ProgressBar.progress =
                (nextVolume * 100_000f).toInt().coerceIn(2_000, 100_000)

            level2ProgressBar.max = 100_000
            level2ProgressBar.progress =
                if (nextVolume > 1.0f) ((nextVolume - 1.0) * 100_000f).toInt()
                    .coerceIn(2_000, 100_000) else 0
            level2ProgressBar.isVisible = nextVolume > 1.0f

            // Calculate the clamped index for the volume icon based on the requested volume
            val iconIndex = (nextVolume * (volumeIcons.lastIndex))
                .roundToInt()
                .coerceIn(0, volumeIcons.lastIndex)

            // Update icon
            playerProgressbarLeftIcon.setImageResource(volumeIcons[iconIndex])
        }

        // alpha fade
        playerBinding?.playerProgressbarLeftHolder?.apply {
            if (!isVisible || alpha < 1f) {
                alpha = 1f
                isVisible = true
            }

            progressBarLeftHideRunnable?.let { removeCallbacks(it) }
            progressBarLeftHideRunnable = Runnable {
                // Fade out the progress bar
                animate().cancel()
                animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction { isVisible = false }
                    .start()
            }
            // Show the progress bar for 1.5 seconds
            postDelayed(progressBarLeftHideRunnable, 1500)
        }
    }

    protected fun uiReset() {
        isShowing = false
        toggleEpisodesOverlay(false)
        // if nothing has loaded these buttons should not be visible
        playerBinding?.apply {
            playerSkipEpisode.isVisible = false
            playerGoForwardRoot.isVisible = false
            playerTracksBtt.isVisible = false
            playerSkipOp.isVisible = false
            shadowOverlay.isVisible = false
        }
        updateLockUI()
        updateUIVisibility()
        animateLayoutChanges()
        resetFastForwardText()
        resetRewindText()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // As this is video specific it is better to not do any setKey/getKey
        outState.putLong(SUBTITLE_DELAY_BUNDLE_KEY, subtitleDelay)
        super.onSaveInstanceState(outState)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // init variables
        setPlayBackSpeed(DataStoreHelper.playBackSpeed)
        savedInstanceState?.getLong(SUBTITLE_DELAY_BUNDLE_KEY)?.let {
            subtitleDelay = it
        }

        // handle tv controls
        playerEventListener = { eventType ->
            when (eventType) {
                PlayerEventType.Lock -> {
                    toggleLock()
                }

                PlayerEventType.NextEpisode -> {
                    player.handleEvent(CSPlayerEvent.NextEpisode)
                }

                PlayerEventType.Pause -> {
                    player.handleEvent(CSPlayerEvent.Pause)
                }

                PlayerEventType.PlayPauseToggle -> {
                    player.handleEvent(CSPlayerEvent.PlayPauseToggle)
                }

                PlayerEventType.Play -> {
                    player.handleEvent(CSPlayerEvent.Play)
                }

                PlayerEventType.SkipCurrentChapter -> {
                    player.handleEvent(CSPlayerEvent.SkipCurrentChapter)
                }

                PlayerEventType.Resize -> {
                    nextResize()
                }

                PlayerEventType.PrevEpisode -> {
                    player.handleEvent(CSPlayerEvent.PrevEpisode)
                }

                PlayerEventType.SeekForward -> {
                    player.handleEvent(CSPlayerEvent.SeekForward)
                }

                PlayerEventType.ShowSpeed -> {
                    showSpeedDialog()
                }

                PlayerEventType.SeekBack -> {
                    player.handleEvent(CSPlayerEvent.SeekBack)
                }

                PlayerEventType.Restart -> {
                    player.handleEvent(CSPlayerEvent.Restart)
                }

                PlayerEventType.ToggleMute -> {
                    player.handleEvent(CSPlayerEvent.ToggleMute)
                }

                PlayerEventType.ToggleHide -> {
                    onClickChange()
                }

                PlayerEventType.ShowMirrors -> {
                    showMirrorsDialogue()
                }

                PlayerEventType.SearchSubtitlesOnline -> {
                    if (subsProvidersIsActive) {
                        openOnlineSubPicker(view.context, null) {}
                    }
                }

                PlayerEventType.SkipOp -> {
                    skipOp()
                }
            }
        }

        // handle tv controls directly based on player state
        keyEventListener = { eventNav ->
            // Don't hook player keys if player isn't active
            if (player.isActive()) {
                val (event, hasNavigated) = eventNav
                if (event != null)
                    handleKeyEvent(event, hasNavigated)
                else false
            } else false
        }

        try {
            context?.let { ctx ->
                val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)

                fastForwardTime =
                    settingsManager.getInt(ctx.getString(R.string.double_tap_seek_time_key), 10)
                        .toLong() * 1000L

                androidTVInterfaceOffSeekTime =
                    settingsManager.getInt(
                        ctx.getString(R.string.android_tv_interface_off_seek_key),
                        10
                    )
                        .toLong() * 1000L
                androidTVInterfaceOnSeekTime =
                    settingsManager.getInt(
                        ctx.getString(R.string.android_tv_interface_on_seek_key),
                        10
                    )
                        .toLong() * 1000L

                navigationBarHeight = ctx.getNavigationBarHeight()
                statusBarHeight = ctx.getStatusBarHeight()

                swipeHorizontalEnabled =
                    settingsManager.getBoolean(ctx.getString(R.string.swipe_enabled_key), true)
                swipeVerticalEnabled =
                    settingsManager.getBoolean(
                        ctx.getString(R.string.swipe_vertical_enabled_key),
                        true
                    )
                playBackSpeedEnabled = settingsManager.getBoolean(
                    ctx.getString(R.string.playback_speed_enabled_key),
                    false
                )
                playerRotateEnabled = settingsManager.getBoolean(
                    ctx.getString(R.string.rotate_video_key),
                    false
                )
                autoPlayerRotateEnabled = settingsManager.getBoolean(
                    ctx.getString(R.string.auto_rotate_video_key),
                    false
                )
                playerResizeEnabled =
                    settingsManager.getBoolean(
                        ctx.getString(R.string.player_resize_enabled_key),
                        true
                    )
                doubleTapEnabled =
                    settingsManager.getBoolean(
                        ctx.getString(R.string.double_tap_enabled_key),
                        false
                    )

                doubleTapPauseEnabled =
                    settingsManager.getBoolean(
                        ctx.getString(R.string.double_tap_pause_enabled_key),
                        false
                    )

                hideControlsNames = settingsManager.getBoolean(
                    ctx.getString(R.string.hide_player_control_names_key),
                    false
                )

                speedupEnabled = settingsManager.getBoolean(
                    ctx.getString(R.string.speedup_key),
                    false
                )


                val profiles = QualityDataHelper.getProfiles()
                val type = if (ctx.isUsingMobileData())
                    QualityDataHelper.QualityProfileType.Data
                else QualityDataHelper.QualityProfileType.WiFi

                currentQualityProfile =
                    profiles.firstOrNull { it.type == type }?.id ?: profiles.firstOrNull()?.id
                            ?: currentQualityProfile

//                currentPrefQuality = settingsManager.getInt(
//                    ctx.getString(if (ctx.isUsingMobileData()) R.string.quality_pref_mobile_data_key else R.string.quality_pref_key),
//                    currentPrefQuality
//                )
                // useSystemBrightness =
                //    settingsManager.getBoolean(ctx.getString(R.string.use_system_brightness_key), false)
            }
            playerBinding?.apply {
                playerSpeedBtt.isVisible = playBackSpeedEnabled
                playerResizeBtt.isVisible = playerResizeEnabled
                playerRotateBtt.isVisible = if(isLayout(TV or EMULATOR)) false else playerRotateEnabled
                if (hideControlsNames) {
                    hideControlsNames()
                }
            }
        } catch (e: Exception) {
            logError(e)
        }

        playerBinding?.apply {

            if (isLayout(TV or EMULATOR)) {
                mapOf(
                    playerGoBack to playerGoBackText,
                    playerRestart to playerRestartText,
                    playerGoForward to playerGoForwardText,
                    downloadHeaderToggle to downloadHeaderToggleText,
                    playerEpisodesButton to playerEpisodesButtonText
                ).forEach { (button, text) ->
                    button.setOnFocusChangeListener { _, hasFocus ->
                        if (!hasFocus) {
                            text.isSelected = false
                            text.isVisible = false
                            return@setOnFocusChangeListener
                        }
                        if (button.id == R.id.player_episodes_button) {
                            toggleEpisodesOverlay(show = true)
                        } else {
                            toggleEpisodesOverlay(show = false)
                        }
                        text.isSelected = true
                        text.isVisible = true
                    }
                }
            }

            playerPausePlay.setOnClickListener {
                autoHide()
                player.handleEvent(CSPlayerEvent.PlayPauseToggle)
            }

            exoDuration.setOnClickListener {
                setRemainingTimeCounter(true)
            }

            timeLeft.setOnClickListener {
                setRemainingTimeCounter(false)
            }

            skipChapterButton.setOnClickListener {
                player.handleEvent(CSPlayerEvent.SkipCurrentChapter)
            }

            playerRotateBtt.setOnClickListener {
                autoHide()
                toggleRotate()
            }

            // init clicks
            playerResizeBtt.setOnClickListener {
                autoHide()
                nextResize()
            }

            playerSpeedBtt.setOnClickListener {
                autoHide()
                showSpeedDialog()
            }

            playerSkipOp.setOnClickListener {
                autoHide()
                skipOp()
            }

            playerSkipEpisode.setOnClickListener {
                autoHide()
                player.handleEvent(CSPlayerEvent.NextEpisode)
            }

            playerGoForward.setOnClickListener {
                autoHide()
                player.handleEvent(CSPlayerEvent.NextEpisode)
            }

            playerRestart.setOnClickListener {
                autoHide()
                player.handleEvent(CSPlayerEvent.Restart)
            }

            playerLock.setOnClickListener {
                autoHide()
                toggleLock()
            }

            playerSubtitleOffsetBtt.setOnClickListener {
                showSubtitleOffsetDialog()
            }

            playerRew.setOnClickListener {
                autoHide()
                rewind()
            }

            playerFfwd.setOnClickListener {
                autoHide()
                fastForward()
            }

            playerGoBack.setOnClickListener {
                activity?.popCurrentPage("FullScreenPlayer")
            }

            playerSourcesBtt.setOnClickListener {
                showMirrorsDialogue()
            }

            playerTracksBtt.setOnClickListener {
                showTracksDialogue()
            }

            // it is !not! a bug that you cant touch the right side, it does not register inputs on navbar or status bar
            playerHolder.setOnTouchListener { callView, event ->
                return@setOnTouchListener handleMotionEvent(callView, event)
            }

            exoProgress.setOnTouchListener { _, event ->
                // this makes the bar not disappear when sliding
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        currentTapIndex++
                    }

                    MotionEvent.ACTION_MOVE -> {
                        currentTapIndex++
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_BUTTON_RELEASE -> {
                        autoHide()
                    }
                }
                return@setOnTouchListener false
            }
            playerEpisodesButton.setOnClickListener {
                toggleEpisodesOverlay(show = true)
            }
        }
        // cs3 is peak media center
        setRemainingTimeCounter(durationMode || isLayout(TV))
        playerBinding?.exoPosition?.doOnTextChanged { _, _, _, _ ->
            updateRemainingTime()
        }
        // init UI
        try {
            uiReset()
        } catch (e: Exception) {
            logError(e)
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun toggleRotate() {
        activity?.let {
            toggleOrientationWithSensor(it)
        }
    }

    private fun PlayerCustomLayoutBinding.hideControlsNames() {
        fun iterate(layout: LinearLayout) {
            layout.children.forEach {
                if (it is MaterialButton) {
                    it.textSize = 0f
                    it.iconPadding = 0
                    it.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                    it.setPadding(0, 0, 0, 0)
                } else if (it is LinearLayout) {
                    iterate(it)
                }
            }
        }
        iterate(playerLockHolder.parent as LinearLayout)
    }

    override fun playerDimensionsLoaded(width: Int, height: Int) {
        isVerticalOrientation = height > width
        updateOrientation()
    }

    private fun updateRemainingTime() {
        val duration = player.getDuration()
        val position = player.getPosition()

        if (duration != null && duration > 1 && position != null) {
            val remainingTimeSeconds = (duration - position + 500) / 1000
            val formattedTime = "-${DateUtils.formatElapsedTime(remainingTimeSeconds)}"

            playerBinding?.timeLeft?.text = formattedTime
        }
    }

    private fun setRemainingTimeCounter(showRemaining: Boolean) {
        durationMode = showRemaining
        playerBinding?.exoDuration?.isInvisible = showRemaining
        playerBinding?.timeLeft?.isVisible = showRemaining
    }

    private fun dynamicOrientation(): Int {
        return if (autoPlayerRotateEnabled) {
            if (isVerticalOrientation) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE // default orientation
        }
    }

    private fun toggleEpisodesOverlay(show: Boolean) {
        if (show && !isShowingEpisodeOverlay) {
            player.handleEvent(CSPlayerEvent.Pause)
            showEpisodesOverlay()
            isShowingEpisodeOverlay = true
            animateEpisodesOverlay(true)
        } else if (isShowingEpisodeOverlay) {
            isShowingEpisodeOverlay = false
            animateEpisodesOverlay(false)
        }
    }

    private fun animateEpisodesOverlay(show: Boolean) {
        playerBinding?.playerEpisodeOverlay?.let { overlay ->
            overlay.animate().cancel()
            (overlay.parent as? ViewGroup)?.layoutTransition = null // Disable layout transitions

            val offset = 50 * overlay.resources.displayMetrics.density

            overlay.translationX = if (show) offset else 0f
            playerBinding?.playerEpisodeOverlay?.isVisible = true

            overlay.animate()
                .translationX(if (show) 0f else offset)
                .alpha(if (show) 1f else 0f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
                    if (!show) {
                        playerBinding?.playerEpisodeOverlay?.isGone = true
                    }
                }
                .start()
        }
    }
}