package com.lagradost.cloudstream3.ui.player

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.keyEventListener
import com.lagradost.cloudstream3.CommonActivity.playerEventListener
import com.lagradost.cloudstream3.CommonActivity.screenHeight
import com.lagradost.cloudstream3.CommonActivity.screenWidth
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.PlayerCustomLayoutBinding
import com.lagradost.cloudstream3.databinding.SubtitleOffsetBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer.Companion.subsProvidersIsActive
import com.lagradost.cloudstream3.ui.player.source_priority.QualityDataHelper
import com.lagradost.cloudstream3.ui.result.setText
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.utils.AppUtils.isUsingMobileData
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.getNavigationBarHeight
import com.lagradost.cloudstream3.utils.UIHelper.getStatusBarHeight
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.utils.UIHelper.showSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.Vector2
import kotlin.math.*


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
    protected open var isTv = false

    protected var playerBinding: PlayerCustomLayoutBinding? = null


    // state of player UI
    protected var isShowing = false
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
        imdbId: Long?,
        dismissCallback: (() -> Unit)
    ) {
        throw NotImplementedError()
    }

    /** Returns false if the touch is on the status bar or navigation bar*/
    private fun isValidTouch(rawX: Float, rawY: Float): Boolean {
        val statusHeight = statusBarHeight ?: 0
        // val navHeight = navigationBarHeight ?: 0
        // nav height is removed because screenWidth already takes into account that
        return rawY > statusHeight && rawX < screenWidth //- navHeight
    }

    override fun exitedPipMode() {
        animateLayoutChanges()
    }

    protected fun animateLayoutChanges() {
        if (isShowing) {
            updateUIVisibility()
        } else {
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

        val fadeTo = if (isShowing) 1f else 0f
        val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo)

        fadeAnimation.duration = 100
        fadeAnimation.fillAfter = true

        val sView = subView
        val sStyle = subStyle
        if (sView != null && sStyle != null) {
            val move = if (isShowing) -((playerBinding?.bottomPlayerBar?.height?.toFloat()
                ?: 0f) + 40.toPx) else -sStyle.elevation.toPx.toFloat()
            ObjectAnimator.ofFloat(sView, "translationY", move).apply {
                duration = 200
                start()
            }
        }

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

    override fun subtitlesChanged() {
        playerBinding?.playerSubtitleOffsetBtt?.isGone =
            player.getCurrentPreferredSubtitle() == null
    }

    private fun restoreOrientationWithSensor(activity: Activity){
        val currentOrientation = activity.resources.configuration.orientation
        var orientation = 0
        when (currentOrientation) {
            Configuration.ORIENTATION_LANDSCAPE ->
                orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

            Configuration.ORIENTATION_SQUARE, Configuration.ORIENTATION_UNDEFINED ->
                orientation = dynamicOrientation()

            Configuration.ORIENTATION_PORTRAIT ->
                orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        activity.requestedOrientation = orientation
    }

    private fun toggleOrientationWithSensor(activity: Activity){
        val currentOrientation = activity.resources.configuration.orientation
        var orientation = 0
        when (currentOrientation) {
            Configuration.ORIENTATION_LANDSCAPE ->
                orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

            Configuration.ORIENTATION_SQUARE, Configuration.ORIENTATION_UNDEFINED ->
                orientation = dynamicOrientation()

            Configuration.ORIENTATION_PORTRAIT ->
                orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        activity.requestedOrientation = orientation
    }

    open fun lockOrientation(activity: Activity) {
        val display =
            (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val rotation = display.rotation
        val currentOrientation = activity.resources.configuration.orientation
        var orientation = 0
        when (currentOrientation) {
            Configuration.ORIENTATION_LANDSCAPE ->
                orientation =
                    if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90)
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE

            Configuration.ORIENTATION_SQUARE, Configuration.ORIENTATION_UNDEFINED ->
                orientation = dynamicOrientation()

            Configuration.ORIENTATION_PORTRAIT ->
                orientation =
                    if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_270)
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        }
        activity.requestedOrientation = orientation
    }

    private fun updateOrientation(ignoreDynamicOrientation: Boolean = false) {
        activity?.apply {
            if(lockRotation) {
                if(isLocked) {
                    lockOrientation(this)
                }
                else {
                    if(ignoreDynamicOrientation){
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
        activity?.showSystemUI()
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
    }

    override fun onResume() {
        enterFullscreen()
        super.onResume()
    }

    override fun onDestroy() {
        exitFullscreen()
        player.release()
        player.releaseCallbacks()
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

        val binding = SubtitleOffsetBinding.inflate(LayoutInflater.from(ctx), null, false)

        val builder =
            AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                .setView(binding.root)
        val dialog = builder.create()
        dialog.show()

        val beforeOffset = subtitleDelay

        /*val applyButton = dialog.findViewById<TextView>(R.id.apply_btt)!!
        val cancelButton = dialog.findViewById<TextView>(R.id.cancel_btt)!!
        val input = dialog.findViewById<EditText>(R.id.subtitle_offset_input)!!
        val sub = dialog.findViewById<ImageView>(R.id.subtitle_offset_subtract)!!
        val subMore = dialog.findViewById<ImageView>(R.id.subtitle_offset_subtract_more)!!
        val add = dialog.findViewById<ImageView>(R.id.subtitle_offset_add)!!
        val addMore = dialog.findViewById<ImageView>(R.id.subtitle_offset_add_more)!!
        val subTitle = dialog.findViewById<TextView>(R.id.subtitle_offset_sub_title)!!*/
        binding.apply {
            subtitleOffsetInput.doOnTextChanged { text, _, _, _ ->
                text?.toString()?.toLongOrNull()?.let { time ->
                    subtitleDelay = time
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
            cancelBtt.setOnClickListener {
                subtitleDelay = beforeOffset
                dialog.dismissSafe(activity)
            }
        }
    }


    private fun showSpeedDialog() {
        val speedsText =
            listOf(
                "0.5x",
                "0.75x",
                "0.85x",
                "1x",
                "1.15x",
                "1.25x",
                "1.4x",
                "1.5x",
                "1.75x",
                "2x"
            )
        val speedsNumbers =
            listOf(0.5f, 0.75f, 0.85f, 1f, 1.15f, 1.25f, 1.4f, 1.5f, 1.75f, 2f)
        val speedIndex = speedsNumbers.indexOf(player.getPlaybackSpeed())

        activity?.let { act ->
            act.showDialog(
                speedsText,
                speedIndex,
                act.getString(R.string.player_speed),
                false,
                {
                    if (isFullScreenPlayer)
                        activity?.hideSystemUI()
                }) { index ->
                if (isFullScreenPlayer)
                    activity?.hideSystemUI()
                setPlayBackSpeed(speedsNumbers[index])
            }
        }
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
                exoRew.startAnimation(rotateLeft)

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
                exoFfwd.startAnimation(rotateRight)

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
        playerBinding?.playerPausePlay?.requestFocus()
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

            //if (hasEpisodes)
            //    player_episodes_button?.startAnimation(fadeAnimation)
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

    fun updateUIVisibility() {
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
            //player_episodes_button?.isVisible = !isGone && hasEpisodes
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
    // this value is within the range [0,1]
    private var currentRequestedVolume: Float = 0.0f
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
        val diffX = (touchEnd.x - touchStart.x) * HORIZONTAL_MULTIPLIER / screenWidth.toFloat()
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
                    isCurrentTouchValid = isValidTouch(currentTouch.x, currentTouch.y)
                    /*if (isCurrentTouchValid && player_episode_list?.isVisible == true) {
                        player_episode_list?.isVisible = false
                    } else*/ if (isCurrentTouchValid) {
                        currentTouchStartTime = System.currentTimeMillis()
                        currentTouchStart = currentTouch
                        currentTouchLast = currentTouch
                        currentTouchStartPlayerTime = player.getPosition()

                        getBrightness()?.let {
                            currentRequestedBrightness = it
                        }
                        (activity?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.let { audioManager ->
                            val currentVolume =
                                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val maxVolume =
                                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                            currentRequestedVolume = currentVolume.toFloat() / maxVolume.toFloat()
                        }
                    }
                }

                MotionEvent.ACTION_UP -> {
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
                                        currentTouch.x < screenWidth / 2 - (DOUBLE_TAB_PAUSE_PERCENTAGE * screenWidth) -> {
                                            if (doubleTapEnabled)
                                                rewind()
                                        }

                                        currentTouch.x > screenWidth / 2 + (DOUBLE_TAB_PAUSE_PERCENTAGE * screenWidth) -> {
                                            if (doubleTapEnabled)
                                                fastForward()
                                        }

                                        else -> {
                                            player.handleEvent(CSPlayerEvent.PlayPauseToggle, PlayerEventSource.UI)
                                        }
                                    }
                                } else if (doubleTapEnabled && isFullScreenPlayer) {
                                    if (currentTouch.x < screenWidth / 2) {
                                        rewind()
                                    } else {
                                        fastForward()
                                    }
                                }
                            }
                        } else {
                            // is a valid click but not fast enough for seek
                            currentClickCount = 0
                            toggleShowDelayed()
                            //onClickChange()
                        }
                    } else {
                        currentClickCount = 0
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

                    // resets UI
                    playerTimeText.isVisible = false
                    playerProgressbarLeftHolder.isVisible = false
                    playerProgressbarRightHolder.isVisible = false

                    currentLastTouchEndTime = System.currentTimeMillis()
                }

                MotionEvent.ACTION_MOVE -> {
                    // if current touch is valid
                    if (startTouch != null && isCurrentTouchValid && !isLocked && isFullScreenPlayer) {
                        // action is unassigned and can therefore be assigned
                        if (currentTouchAction == null) {
                            val diffFromStart = startTouch - currentTouch

                            if (swipeVerticalEnabled) {
                                if (abs(diffFromStart.y * 100 / screenHeight) > MINIMUM_VERTICAL_SWIPE) {
                                    // left = Brightness, right = Volume, but the UI is reversed to show the UI better
                                    currentTouchAction = if (startTouch.x < screenWidth / 2) {
                                        // hide the UI if you hold brightness to show screen better, better UX
                                        if (isShowing) {
                                            isShowing = false
                                            animateLayoutChanges()
                                        }

                                        TouchAction.Brightness
                                    } else {
                                        TouchAction.Volume
                                    }
                                }
                            }
                            if (swipeHorizontalEnabled) {
                                if (abs(diffFromStart.x * 100 / screenHeight) > MINIMUM_HORIZONTAL_SWIPE) {
                                    currentTouchAction = TouchAction.Time
                                }
                            }
                        }

                        // display action
                        val lastTouch = currentTouchLast
                        if (lastTouch != null) {
                            val diffFromLast = lastTouch - currentTouch
                            val verticalAddition =
                                diffFromLast.y * VERTICAL_MULTIPLIER / screenHeight.toFloat()

                            // update UI
                            playerTimeText.isVisible = false
                            playerProgressbarLeftHolder.isVisible = false
                            playerProgressbarRightHolder.isVisible = false

                            when (currentTouchAction) {
                                TouchAction.Time -> {
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
                                    playerProgressbarRightHolder.isVisible = true
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
                                    (activity?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.let { audioManager ->
                                        playerProgressbarLeftHolder.isVisible = true
                                        val maxVolume =
                                            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        val currentVolume =
                                            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                                        // clamps volume and adds swipe
                                        currentRequestedVolume =
                                            min(
                                                1.0f,
                                                max(currentRequestedVolume + verticalAddition, 0.0f)
                                            )

                                        // max is set high to make it smooth
                                        playerProgressbarLeft.max = 100_000
                                        playerProgressbarLeft.progress =
                                            max(2_000, (currentRequestedVolume * 100_000f).toInt())

                                        playerProgressbarLeftIcon.setImageResource(
                                            volumeIcons[min( // clamp the value just in case
                                                volumeIcons.size - 1,
                                                max(
                                                    0,
                                                    round(currentRequestedVolume * (volumeIcons.size - 1)).toInt()
                                                )
                                            )]
                                        )

                                        // this is used instead of set volume because old devices does not support it
                                        val desiredVolume =
                                            round(currentRequestedVolume * maxVolume).toInt()
                                        if (desiredVolume != currentVolume) {
                                            val newVolumeAdjusted =
                                                if (desiredVolume < currentVolume) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE

                                            audioManager.adjustStreamVolume(
                                                AudioManager.STREAM_MUSIC,
                                                newVolumeAdjusted,
                                                0
                                            )
                                        }
                                    }
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

    private fun handleKeyEvent(event: KeyEvent, hasNavigated: Boolean): Boolean {
        if (hasNavigated) {
            autoHide()
        } else {
            event.keyCode.let { keyCode ->
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER -> {
                                if (!isShowing) {
                                    if (!isLocked) player.handleEvent(CSPlayerEvent.PlayPauseToggle)
                                    onClickChange()
                                    return true
                                }
                            }

                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (!isShowing) {
                                    onClickChange()
                                    return true
                                }
                            }

                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (!isShowing && !isLocked) {
                                    player.seekTime(-androidTVInterfaceOffSeekTime)
                                    return true
                                } else if (playerBinding?.playerPausePlay?.isFocused == true) {
                                    player.seekTime(-androidTVInterfaceOnSeekTime)
                                    return true
                                }
                            }

                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (!isShowing && !isLocked) {
                                    player.seekTime(androidTVInterfaceOffSeekTime)
                                    return true
                                } else if (playerBinding?.playerPausePlay?.isFocused == true) {
                                    player.seekTime(androidTVInterfaceOnSeekTime)
                                    return true
                                }
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
                    KeyEvent.KEYCODE_BACK -> {
                        if (isShowing && isTv) {
                            onClickChange()
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    protected fun uiReset() {
        isShowing = false

        // if nothing has loaded these buttons should not be visible
        playerBinding?.apply {
            playerSkipEpisode.isVisible = false
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

        //player_episodes_button?.setOnClickListener {
        //    player_episodes_button?.isGone = true
        //    player_episode_list?.isVisible = true
        //}
//
        //player_episode_list?.adapter = PlayerEpisodeAdapter { click ->
//
        //}

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
                playerRotateBtt.isVisible = playerRotateEnabled
            }
        } catch (e: Exception) {
            logError(e)
        }
        playerBinding?.apply {
            playerPausePlay.setOnClickListener {
                autoHide()
                player.handleEvent(CSPlayerEvent.PlayPauseToggle)
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

            playerLock.setOnClickListener {
                autoHide()
                toggleLock()
            }

            playerSubtitleOffsetBtt.setOnClickListener {
                showSubtitleOffsetDialog()
            }

            exoRew.setOnClickListener {
                autoHide()
                rewind()
            }

            exoFfwd.setOnClickListener {
                autoHide()
                fastForward()
            }

            playerGoBack.setOnClickListener {
                activity?.popCurrentPage()
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
        }
        // init UI
        try {
            uiReset()

            // init chromecast UI
            // removed due to having no use and bugging
            //activity?.let {
            //    if (it.isCastApiAvailable()) {
            //        try {
            //            CastButtonFactory.setUpMediaRouteButton(it, player_media_route_button)
            //            val castContext = CastContext.getSharedInstance(it.applicationContext)
            //
            //            player_media_route_button?.isGone =
            //                castContext.castState == CastState.NO_DEVICES_AVAILABLE
            //            castContext.addCastStateListener { state ->
            //                player_media_route_button?.isGone =
            //                    state == CastState.NO_DEVICES_AVAILABLE
            //            }
            //        } catch (e: Exception) {
            //            logError(e)
            //        }
            //    } else {
            //        // if cast is not possible hide UI
            //        player_media_route_button?.isGone = true
            //    }
            //}
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

    override fun playerDimensionsLoaded(width: Int, height: Int) {
        isVerticalOrientation = height > width
        updateOrientation()
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
}