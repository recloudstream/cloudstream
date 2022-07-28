package com.lagradost.cloudstream3.ui.player

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
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
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer.Companion.subsProvidersIsActive
import com.lagradost.cloudstream3.utils.Qualities
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
import kotlinx.android.synthetic.main.player_custom_layout.*
import kotlinx.android.synthetic.main.player_custom_layout.bottom_player_bar
import kotlinx.android.synthetic.main.player_custom_layout.exo_ffwd
import kotlinx.android.synthetic.main.player_custom_layout.exo_ffwd_text
import kotlinx.android.synthetic.main.player_custom_layout.exo_progress
import kotlinx.android.synthetic.main.player_custom_layout.exo_rew
import kotlinx.android.synthetic.main.player_custom_layout.exo_rew_text
import kotlinx.android.synthetic.main.player_custom_layout.player_center_menu
import kotlinx.android.synthetic.main.player_custom_layout.player_ffwd_holder
import kotlinx.android.synthetic.main.player_custom_layout.player_holder
import kotlinx.android.synthetic.main.player_custom_layout.player_pause_play
import kotlinx.android.synthetic.main.player_custom_layout.player_pause_play_holder
import kotlinx.android.synthetic.main.player_custom_layout.player_progressbar_left
import kotlinx.android.synthetic.main.player_custom_layout.player_progressbar_left_holder
import kotlinx.android.synthetic.main.player_custom_layout.player_progressbar_left_icon
import kotlinx.android.synthetic.main.player_custom_layout.player_progressbar_right
import kotlinx.android.synthetic.main.player_custom_layout.player_progressbar_right_holder
import kotlinx.android.synthetic.main.player_custom_layout.player_progressbar_right_icon
import kotlinx.android.synthetic.main.player_custom_layout.player_rew_holder
import kotlinx.android.synthetic.main.player_custom_layout.player_time_text
import kotlinx.android.synthetic.main.player_custom_layout.player_video_bar
import kotlinx.android.synthetic.main.player_custom_layout.shadow_overlay
import kotlinx.android.synthetic.main.trailer_custom_layout.*
import kotlin.math.*

const val MINIMUM_SEEK_TIME = 7000L         // when swipe seeking
const val MINIMUM_VERTICAL_SWIPE = 2.0f     // in percentage
const val MINIMUM_HORIZONTAL_SWIPE = 2.0f   // in percentage
const val VERTICAL_MULTIPLIER = 2.0f
const val HORIZONTAL_MULTIPLIER = 2.0f
const val DOUBLE_TAB_MAXIMUM_HOLD_TIME = 200L
const val DOUBLE_TAB_MINIMUM_TIME_BETWEEN = 200L    // this also affects the UI show response time
const val DOUBLE_TAB_PAUSE_PERCENTAGE = 0.15        // in both directions

// All the UI Logic for the player
open class FullScreenPlayer : AbstractPlayerFragment() {
    protected open var lockRotation = true
    protected open var isFullScreenPlayer = true
    protected open var isTv = false

    // state of player UI
    protected var isShowing = false
    protected var isLocked = false

    //private var episodes: List<Any> = listOf()
    protected fun setEpisodes(ep: List<Any>) {
        //hasEpisodes = ep.size > 1 // if has 2 episodes or more because you dont want to switch to your current episode
        //(player_episode_list?.adapter as? PlayerEpisodeAdapter?)?.updateList(ep)
    }

    protected var hasEpisodes = false
        private set
    //protected val hasEpisodes
    //    get() = episodes.isNotEmpty()

    // options for player
    protected var currentPrefQuality =
        Qualities.P2160.value // preferred maximum quality, used for ppl w bad internet or on cell
    protected var fastForwardTime = 10000L
    protected var swipeHorizontalEnabled = false
    protected var swipeVerticalEnabled = false
    protected var playBackSpeedEnabled = false
    protected var playerResizeEnabled = false
    protected var doubleTapEnabled = false
    protected var doubleTapPauseEnabled = true

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

    protected val displayMetrics: DisplayMetrics = Resources.getSystem().displayMetrics

    // screenWidth and screenHeight does always
    // refer to the screen while in landscape mode
    protected val screenWidth: Int
        get() {
            return max(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    protected val screenHeight: Int
        get() {
            return min(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }

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

    open fun showMirrorsDialogue() {
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
            player_holder?.postDelayed({ updateUIVisibility() }, 200)
        }

        val titleMove = if (isShowing) 0f else -50.toPx.toFloat()
        player_video_title?.let {
            ObjectAnimator.ofFloat(it, "translationY", titleMove).apply {
                duration = 200
                start()
            }
        }
        player_video_title_rez?.let {
            ObjectAnimator.ofFloat(it, "translationY", titleMove).apply {
                duration = 200
                start()
            }
        }
        val playerBarMove = if (isShowing) 0f else 50.toPx.toFloat()
        bottom_player_bar?.let {
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
            val move = if (isShowing) -((bottom_player_bar?.height?.toFloat()
                ?: 0f) + 40.toPx) else -sStyle.elevation.toPx.toFloat()
            ObjectAnimator.ofFloat(sView, "translationY", move).apply {
                duration = 200
                start()
            }
        }

        val playerSourceMove = if (isShowing) 0f else -50.toPx.toFloat()
        player_open_source?.let {
            ObjectAnimator.ofFloat(it, "translationY", playerSourceMove).apply {
                duration = 200
                start()
            }
        }


        if (!isLocked) {
            player_ffwd_holder?.alpha = 1f
            player_rew_holder?.alpha = 1f
            // player_pause_play_holder?.alpha = 1f
            shadow_overlay?.isVisible = true
            shadow_overlay?.startAnimation(fadeAnimation)
            player_ffwd_holder?.startAnimation(fadeAnimation)
            player_rew_holder?.startAnimation(fadeAnimation)
            player_pause_play?.startAnimation(fadeAnimation)

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

        bottom_player_bar?.startAnimation(fadeAnimation)
        player_open_source?.startAnimation(fadeAnimation)
        player_top_holder?.startAnimation(fadeAnimation)
    }

    override fun subtitlesChanged() {
        player_subtitle_offset_btt?.isGone = player.getCurrentPreferredSubtitle() == null
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
        if (lockRotation)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
            setKey(PLAYBACK_SPEED_KEY, speed)
            player_speed_btt?.text =
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
        context?.let { ctx ->
            val builder =
                AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                    .setView(R.layout.subtitle_offset)
            val dialog = builder.create()
            dialog.show()

            val beforeOffset = subtitleDelay

            val applyButton = dialog.findViewById<TextView>(R.id.apply_btt)!!
            val cancelButton = dialog.findViewById<TextView>(R.id.cancel_btt)!!
            val input = dialog.findViewById<EditText>(R.id.subtitle_offset_input)!!
            val sub = dialog.findViewById<ImageView>(R.id.subtitle_offset_subtract)!!
            val subMore = dialog.findViewById<ImageView>(R.id.subtitle_offset_subtract_more)!!
            val add = dialog.findViewById<ImageView>(R.id.subtitle_offset_add)!!
            val addMore = dialog.findViewById<ImageView>(R.id.subtitle_offset_add_more)!!
            val subTitle = dialog.findViewById<TextView>(R.id.subtitle_offset_sub_title)!!

            input.doOnTextChanged { text, _, _, _ ->
                text?.toString()?.toLongOrNull()?.let {
                    subtitleDelay = it
                    when {
                        it > 0L -> {
                            context?.getString(R.string.subtitle_offset_extra_hint_later_format)
                                ?.format(it)
                        }
                        it < 0L -> {
                            context?.getString(R.string.subtitle_offset_extra_hint_before_format)
                                ?.format(-it)
                        }
                        it == 0L -> {
                            context?.getString(R.string.subtitle_offset_extra_hint_none_format)
                        }
                        else -> {
                            null
                        }
                    }?.let { str ->
                        subTitle.text = str
                    }
                }
            }
            input.text = Editable.Factory.getInstance()?.newEditable(beforeOffset.toString())

            val buttonChange = 100L
            val buttonChangeMore = 1000L

            fun changeBy(by: Long) {
                val current = (input.text?.toString()?.toLongOrNull() ?: 0) + by
                input.text = Editable.Factory.getInstance()?.newEditable(current.toString())
            }

            add.setOnClickListener {
                changeBy(buttonChange)
            }
            addMore.setOnClickListener {
                changeBy(buttonChangeMore)
            }
            sub.setOnClickListener {
                changeBy(-buttonChange)
            }
            subMore.setOnClickListener {
                changeBy(-buttonChangeMore)
            }

            dialog.setOnDismissListener {
                if (isFullScreenPlayer)
                    activity?.hideSystemUI()
            }
            applyButton.setOnClickListener {
                dialog.dismissSafe(activity)
                player.seekTime(1L)
            }
            cancelButton.setOnClickListener {
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
        exo_rew_text?.text =
            getString(R.string.rew_text_regular_format).format(fastForwardTime / 1000)
    }

    fun resetFastForwardText() {
        exo_ffwd_text?.text =
            getString(R.string.ffw_text_regular_format).format(fastForwardTime / 1000)
    }

    private fun rewind() {
        try {
            player_center_menu?.isGone = false
            player_rew_holder?.alpha = 1f

            val rotateLeft = AnimationUtils.loadAnimation(context, R.anim.rotate_left)
            exo_rew?.startAnimation(rotateLeft)

            val goLeft = AnimationUtils.loadAnimation(context, R.anim.go_left)
            goLeft.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationRepeat(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    exo_rew_text?.post {
                        resetRewindText()
                        player_center_menu?.isGone = !isShowing
                        player_rew_holder?.alpha = if (isShowing) 1f else 0f
                    }
                }
            })
            exo_rew_text?.startAnimation(goLeft)
            exo_rew_text?.text = getString(R.string.rew_text_format).format(fastForwardTime / 1000)
            player.seekTime(-fastForwardTime)
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun fastForward() {
        try {
            player_center_menu?.isGone = false
            player_ffwd_holder?.alpha = 1f

            val rotateRight = AnimationUtils.loadAnimation(context, R.anim.rotate_right)
            exo_ffwd?.startAnimation(rotateRight)

            val goRight = AnimationUtils.loadAnimation(context, R.anim.go_right)
            goRight.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationRepeat(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    exo_ffwd_text?.post {
                        resetFastForwardText()
                        player_center_menu?.isGone = !isShowing
                        player_ffwd_holder?.alpha = if (isShowing) 1f else 0f
                    }
                }
            })
            exo_ffwd_text?.startAnimation(goRight)
            exo_ffwd_text?.text = getString(R.string.ffw_text_format).format(fastForwardTime / 1000)
            player.seekTime(fastForwardTime)
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun onClickChange() {
        isShowing = !isShowing
        if (isShowing) {
            player_intro_play?.isGone = true
            autoHide()
        }
        if (isFullScreenPlayer)
            activity?.hideSystemUI()
        animateLayoutChanges()
        player_pause_play?.requestFocus()
    }

    private fun toggleLock() {
        if (!isShowing) {
            onClickChange()
        }

        isLocked = !isLocked
        if (isLocked && isShowing) {
            player_holder?.postDelayed({
                if (isLocked && isShowing) {
                    onClickChange()
                }
            }, 200)
        }

        val fadeTo = if (isLocked) 0f else 1f

        val fadeAnimation = AlphaAnimation(player_video_title.alpha, fadeTo).apply {
            duration = 100
            fillAfter = true
        }

        updateUIVisibility()
        // MENUS
        //centerMenu.startAnimation(fadeAnimation)
        player_pause_play?.startAnimation(fadeAnimation)
        player_ffwd_holder?.startAnimation(fadeAnimation)
        player_rew_holder?.startAnimation(fadeAnimation)

        //if (hasEpisodes)
        //    player_episodes_button?.startAnimation(fadeAnimation)
        //player_media_route_button?.startAnimation(fadeAnimation)
        //video_bar.startAnimation(fadeAnimation)

        //TITLE
        player_video_title_rez?.startAnimation(fadeAnimation)
        player_episode_filler?.startAnimation(fadeAnimation)
        player_video_title?.startAnimation(fadeAnimation)
        player_top_holder?.startAnimation(fadeAnimation)
        // BOTTOM
        player_lock_holder?.startAnimation(fadeAnimation)
        //player_go_back_holder?.startAnimation(fadeAnimation)

        shadow_overlay?.isVisible = true
        shadow_overlay?.startAnimation(fadeAnimation)

        updateLockUI()
    }

    private fun updateUIVisibility() {
        val isGone = isLocked || !isShowing
        var togglePlayerTitleGone = isGone
        context?.let {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(it)
            val limitTitle = settingsManager.getInt(getString(R.string.prefer_limit_title_key), 0)
            if (limitTitle < 0) {
                togglePlayerTitleGone = true
            }
        }
        player_lock_holder?.isGone = isGone
        player_video_bar?.isGone = isGone
        player_pause_play_holder?.isGone = isGone
        player_pause_play?.isGone = isGone
        //player_buffering?.isGone = isGone
        player_top_holder?.isGone = isGone
        //player_episodes_button?.isVisible = !isGone && hasEpisodes
        player_video_title?.isGone = togglePlayerTitleGone
        player_video_title_rez?.isGone = isGone
        player_episode_filler?.isGone = isGone
        player_center_menu?.isGone = isGone
        player_lock?.isGone = !isShowing
        //player_media_route_button?.isClickable = !isGone
        player_go_back_holder?.isGone = isGone
    }

    private fun updateLockUI() {
        player_lock?.setIconResource(if (isLocked) R.drawable.video_locked else R.drawable.video_unlocked)
        if (layout == R.layout.fragment_player) {
            val color = if (isLocked) context?.colorFromAttribute(R.attr.colorPrimary)
            else Color.WHITE
            if (color != null) {
                player_lock?.setTextColor(color)
                player_lock?.iconTint = ColorStateList.valueOf(color)
                player_lock?.rippleColor =
                    ColorStateList.valueOf(Color.argb(50, color.red, color.green, color.blue))
            }
        }
    }

    private var currentTapIndex = 0
    protected fun autoHide() {
        currentTapIndex++
        val index = currentTapIndex
        player_holder?.postDelayed({
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
            player_holder?.postDelayed({
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
        player_intro_play?.isGone = true
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
                            calculateNewTime(startTime, startTouch, currentTouch)?.let { seekTo ->
                                if (abs(seekTo - startTime) > MINIMUM_SEEK_TIME) {
                                    player.seekTo(seekTo)
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
                                        player.handleEvent(CSPlayerEvent.PlayPauseToggle)
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
                player_time_text?.isVisible = false
                player_progressbar_left_holder?.isVisible = false
                player_progressbar_right_holder?.isVisible = false
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
                        player_time_text?.isVisible = false
                        player_progressbar_left_holder?.isVisible = false
                        player_progressbar_right_holder?.isVisible = false

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
                                        player_time_text?.text =
                                            "${convertTimeToString(newMs / 1000)} [${
                                                (if (abs(skipMs) < 1000) "" else (if (skipMs > 0) "+" else "-"))
                                            }${convertTimeToString(abs(skipMs / 1000))}]"
                                        player_time_text?.isVisible = true
                                    }
                                }
                            }
                            TouchAction.Brightness -> {
                                player_progressbar_right_holder?.isVisible = true
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
                                player_progressbar_right?.max = 100_000
                                player_progressbar_right?.progress =
                                    max(2_000, (currentRequestedBrightness * 100_000f).toInt())

                                player_progressbar_right_icon?.setImageResource(
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
                                    player_progressbar_left_holder?.isVisible = true
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
                                    player_progressbar_left?.max = 100_000
                                    player_progressbar_left?.progress =
                                        max(2_000, (currentRequestedVolume * 100_000f).toInt())

                                    player_progressbar_left_icon?.setImageResource(
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
                                    player.seekTime(-10000L)
                                    return true
                                } else if (player_pause_play?.isFocused == true) {
                                    player.seekTime(-30000L)
                                    return true
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (!isShowing && !isLocked) {
                                    player.seekTime(10000L)
                                    return true
                                } else if (player_pause_play?.isFocused == true) {
                                    player.seekTime(30000L)
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
        isLocked = false
        isShowing = false

        // if nothing has loaded these buttons should not be visible
        player_skip_episode?.isVisible = false
        player_skip_op?.isVisible = false
        shadow_overlay?.isVisible = false

        updateLockUI()
        updateUIVisibility()
        animateLayoutChanges()
        resetFastForwardText()
        resetRewindText()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // init variables
        setPlayBackSpeed(getKey(PLAYBACK_SPEED_KEY) ?: 1.0f)

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

                currentPrefQuality = settingsManager.getInt(
                    ctx.getString(R.string.quality_pref_key),
                    currentPrefQuality
                )
                // useSystemBrightness =
                //    settingsManager.getBoolean(ctx.getString(R.string.use_system_brightness_key), false)
            }

            player_speed_btt?.isVisible = playBackSpeedEnabled
            player_resize_btt?.isVisible = playerResizeEnabled
        } catch (e: Exception) {
            logError(e)
        }

        player_pause_play?.setOnClickListener {
            autoHide()
            player.handleEvent(CSPlayerEvent.PlayPauseToggle)
        }

        // init clicks
        player_resize_btt?.setOnClickListener {
            autoHide()
            nextResize()
        }

        player_speed_btt?.setOnClickListener {
            autoHide()
            showSpeedDialog()
        }

        player_skip_op?.setOnClickListener {
            autoHide()
            skipOp()
        }

        player_skip_episode?.setOnClickListener {
            autoHide()
            player.handleEvent(CSPlayerEvent.NextEpisode)
        }

        player_lock?.setOnClickListener {
            autoHide()
            toggleLock()
        }

        player_subtitle_offset_btt?.setOnClickListener {
            showSubtitleOffsetDialog()
        }

        exo_rew?.setOnClickListener {
            autoHide()
            rewind()
        }

        exo_ffwd?.setOnClickListener {
            autoHide()
            fastForward()
        }

        player_go_back?.setOnClickListener {
            activity?.popCurrentPage()
        }

        player_sources_btt?.setOnClickListener {
            showMirrorsDialogue()
        }

        player_intro_play?.setOnClickListener {
            player_intro_play?.isGone = true
            player.handleEvent(CSPlayerEvent.Play)
            updateUIVisibility()
        }

        // it is !not! a bug that you cant touch the right side, it does not register inputs on navbar or status bar
        player_holder?.setOnTouchListener { callView, event ->
            return@setOnTouchListener handleMotionEvent(callView, event)
        }

        exo_progress?.setOnTouchListener { _, event ->
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
}