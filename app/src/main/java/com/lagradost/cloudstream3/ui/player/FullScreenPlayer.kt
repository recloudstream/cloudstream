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
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.CommonActivity.keyEventListener
import com.lagradost.cloudstream3.CommonActivity.playerEventListener
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentPlayerBinding
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
import com.lagradost.cloudstream3.utils.AppContextUtils.shouldShowPlayerMetadata
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.setText
import com.lagradost.cloudstream3.utils.txt
import kotlin.math.roundToInt

private const val SUBTITLE_DELAY_BUNDLE_KEY = "subtitle_delay"

// All the UI Logic for the player
@OptIn(UnstableApi::class)
open class FullScreenPlayer : AbstractPlayerFragment<FragmentPlayerBinding>(
    BindingCreator.Bind(FragmentPlayerBinding::bind)
) {
    override fun pickLayout(): Int = R.layout.fragment_player
    protected open var lockRotation = true
    protected var playerBinding: PlayerCustomLayoutBinding? = null

    // state of player UI
    protected var isShowing = false
    protected var isLocked = false
    protected var timestampShowState = false
    private var metadataVisibilityToken = 0
    protected var hasEpisodes = false
        private set

    /**
     * Default profile 1
     * Decides how links should be sorted based on a priority system.
     * This will be set in runtime based on settings.
     **/
    protected var currentQualityProfile = 1

    protected var androidTVInterfaceOffSeekTime = 10000L
    protected var androidTVInterfaceOnSeekTime = 30000L
    protected var playBackSpeedEnabled = false
    protected var playerResizeEnabled = false
    protected var playerRotateEnabled = false
    protected var rotatedManually = false
    private var hideControlsNames = false
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

    private var isShowingEpisodeOverlay: Boolean = false
    private var previousPlayStatus: Boolean = false

    override fun fixLayout(view: View) = Unit

    /**
     * Wet code but this can not be made into a function as it is a setter.
     *
     * The reason for this setter is to fix a bug with the titlecard popup, as we want it to autohide
     * when pressing back.
     *
     * Note that we move the call to autoHide after field assignment with prevField to avoid inf recursion. */
    protected var selectSourceDialog: Dialog? = null
        set(value) {
            val prevField = field
            field = value
            if (value == null && prevField != null) {
                autoHide()
            }
        }
    protected var selectTrackDialog: Dialog? = null
        set(value) {
            val prevField = field
            field = value
            if (value == null && prevField != null) {
                autoHide()
            }
        }
    protected var selectSpeedDialog: Dialog? = null
        set(value) {
            val prevField = field
            field = value
            if (value == null && prevField != null) {
                autoHide()
            }
        }
    protected var selectSubtitlesDialog: Dialog? = null
        set(value) {
            val prevField = field
            field = value
            if (value == null && prevField != null) {
                autoHide()
            }
        }

    /** Checks if any top level dialog is open and showing */
    fun isDialogOpen() =
        selectSourceDialog?.isShowing == true
                || selectTrackDialog?.isShowing == true
                || selectSpeedDialog?.isShowing == true
                || selectSubtitlesDialog?.isShowing == true

    private fun scheduleMetadataVisibility() {
        val metadataScrim = playerBinding?.playerMetadataScrim ?: return
        val ctx = metadataScrim.context ?: return

        if (!ctx.shouldShowPlayerMetadata()) {
            metadataScrim.isVisible = false
            metadataVisibilityToken++
            return
        }

        if (isLayout(PHONE)) {
            metadataScrim.isVisible = false
            metadataVisibilityToken++
            return
        }

        val isPaused = currentPlayerStatus == CSPlayerLoading.IsPaused
        val token = ++metadataVisibilityToken

        if (isPaused) {
            metadataScrim.postDelayed({
                /** Make sure the user has not interacted with anything */
                if (token != metadataVisibilityToken) return@postDelayed
                /** If already visible, then do not rerun the animation */
                if (metadataScrim.isVisible) return@postDelayed
                /** Failsafe, as this should only be shown when paused */
                if (currentPlayerStatus != CSPlayerLoading.IsPaused) return@postDelayed
                /** We do not want to show the logo in the background when the user is within another screen */
                if (isDialogOpen()) return@postDelayed

                metadataScrim.alpha = 0f
                metadataScrim.isVisible = true
                metadataScrim.animate()
                    .alpha(1f)
                    .setDuration(500L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                hidePlayerUI()
            }, 8000L)
        } else {
            if (metadataScrim.isVisible) {
                metadataScrim.animate()
                    .alpha(0f)
                    .setDuration(300L)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        metadataScrim.alpha = 0f      // force final state
                        metadataScrim.isVisible = false
                    }
                    .start()
            }
        }
    }

    override fun onDestroyView() {
        playerHostView?.releaseOverlayLayoutListener()
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

    override fun exitedPipMode() {
        animateLayoutChanges()
    }

    private fun animateLayoutChangesForSubtitles() =
        // Post here as bottomPlayerBar is gone the first frame => bottomPlayerBar.height = 0
        playerBinding?.bottomPlayerBar?.post {
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
        playerBinding?.playerVideoTitleHolder?.let {
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
        playerBinding?.playerVideoInfo?.let {
            ObjectAnimator.ofFloat(it, "translationY", titleMove).apply {
                duration = 200
                start()
            }
        }
        playerBinding?.playerMetadataScrim?.let {
            ObjectAnimator.ofFloat(it, "translationY", 1f).apply {
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
                playerHostView?.gestureHelper?.animateCenterControls(fadeTo)
                shadowOverlay.isVisible = true
                shadowOverlay.startAnimation(fadeAnimation)
                downloadBothHeader.startAnimation(fadeAnimation)
            }

            bottomPlayerBar.startAnimation(fadeAnimation)
            playerOpenSource.startAnimation(fadeAnimation)
            playerTopHolder.startAnimation(fadeAnimation)
        }
    }

    override fun subtitlesChanged() {
        val tracks = player.getVideoTracks()
        val isBuiltinSubtitles = tracks.currentTextTracks.all { track ->
            track.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES
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

            else -> playerHostView?.dynamicOrientation() ?: return
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

            else -> playerHostView?.dynamicOrientation() ?: return
        }
        activity.requestedOrientation = orientation
    }

    private fun lockOrientation(activity: Activity) {
        val display = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            @Suppress("DEPRECATION")
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

            else -> orientation = playerHostView?.dynamicOrientation() ?: return
        }
        activity.requestedOrientation = orientation
    }

    private fun updateOrientation(ignoreDynamicOrientation: Boolean = false) {
        activity?.apply {
            if (lockRotation) {
                if (isLocked) {
                    lockOrientation(this)
                } else {
                    if (ignoreDynamicOrientation || rotatedManually) {
                        // Restore when lock is disabled.
                        restoreOrientationWithSensor(this)
                    } else {
                        this.requestedOrientation = playerHostView?.dynamicOrientation() ?: return@apply
                    }
                }
            }
        }
    }

    private fun setupKeyEventListener() {
        keyEventListener = { eventNav ->
            val (event, hasNavigated) = eventNav
            when {
                event == null -> false
                event.action == KeyEvent.ACTION_DOWN &&
                    (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                     event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) ->
                    playerHostView?.handleVolumeKey(event.keyCode) ?: false
                player.isActive() -> handleKeyEvent(event, hasNavigated)
                else -> false
            }
        }
    }

    override fun onResume() {
        playerHostView?.enterFullscreen { updateOrientation() }
        setupKeyEventListener()
        playerHostView?.verifyVolume()
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
        playerHostView?.requestUpdateBrightnessOverlayOnNextLayout()
        super.onResume()
    }

    override fun onStop() {
        activity?.detachBackPressedCallback("FullScreenPlayer")
        super.onStop()
    }

    override fun onDestroy() {
        playerHostView?.exitFullscreen()
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
        val dialog = Dialog(ctx, R.style.DialogFullscreenPlayer).apply {
            setContentView(binding.root)
        }
        this.selectSubtitlesDialog = dialog
        dialog.show()

        val isPortrait =
            ctx.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        fixSystemBarsPadding(binding.root, fixIme = isPortrait)

        var currentOffset = subtitleDelay
        binding.apply {
            subtitleOffsetInput.doOnTextChanged { text, _, _, _ ->
                text?.toString()?.toLongOrNull()?.let { time ->
                    currentOffset = time
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
                Editable.Factory.getInstance()?.newEditable(currentOffset.toString())

            val subtitles = player.getSubtitleCues().toMutableList()

            subtitleOffsetRecyclerview.isVisible = subtitles.isNotEmpty()
            noSubtitlesLoadedNotice.isVisible = subtitles.isEmpty()

            val initialSubtitlePosition = (player.getPosition() ?: 0) - currentOffset
            val subtitleAdapter =
                SubtitleOffsetItemAdapter(initialSubtitlePosition) { subtitleCue ->
                    val playerPosition = player.getPosition() ?: 0
                    subtitleOffsetInput.text = Editable.Factory.getInstance()
                        ?.newEditable((playerPosition - subtitleCue.startTimeMs).toString())
                }.apply {
                    submitList(subtitles)
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
                selectSubtitlesDialog = null
                activity?.hideSystemUI()
            }
            applyBtt.setOnClickListener {
                selectSubtitlesDialog = null
                subtitleDelay = currentOffset
                dialog.dismissSafe(activity)
                player.seekTime(1L)
            }
            resetBtt.setOnClickListener {
                selectSubtitlesDialog = null
                subtitleDelay = 0
                dialog.dismissSafe(activity)
                player.seekTime(1L)
            }
            cancelBtt.setOnClickListener {
                selectSubtitlesDialog = null
                dialog.dismissSafe(activity)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    fun updateSpeedDialogBinding(binding: SpeedDialogBinding) {
        val speed = player.getPlaybackSpeed()
        binding.speedText.text = "%.2fx".format(speed).replace(".0x", "x")
        // Android crashes if you don't round to an exact step size
        binding.speedBar.value =
            (speed.coerceIn(0.1f, 2.0f) / binding.speedBar.stepSize).roundToInt()
                .toFloat() * binding.speedBar.stepSize
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

        binding.speedBar.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                setPlayBackSpeed(value)
                updateSpeedDialogBinding(binding)
            }
        }

        val dismiss = DialogInterface.OnDismissListener {
            activity?.hideSystemUI()
            if (isPlaying) {
                player.handleEvent(CSPlayerEvent.Play, PlayerEventSource.UI)
            }
            selectSpeedDialog = null
        }

        // if (isLayout(PHONE)) {
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
        this.selectSpeedDialog = dialog
        dialog.show()
        //}
    }

    private fun onClickChange() {
        isShowing = !isShowing
        if (isShowing) autoHide()
        activity?.hideSystemUI()
        animateLayoutChanges()
        if (playerBinding?.playerEpisodeOverlay?.isGone == true) playerBinding?.playerPausePlay?.requestFocus()
    }

    private fun toggleLock() {
        if (!isShowing) {
            onClickChange()
        }

        isLocked = !isLocked
        playerHostView?.isLocked = isLocked
        updateOrientation(true) // set true to ignore auto rotate to stay in current orientation

        if (isLocked && isShowing) {
            playerBinding?.playerHolder?.postDelayed({
                if (isLocked && isShowing) {
                    onClickChange()
                }
            }, 200)
        }

        val fadeTo = if (isLocked) 0f else 1f
        playerHostView?.gestureHelper?.animateCenterControls(fadeTo)
        playerBinding?.apply {
            val fadeAnimation = AlphaAnimation(playerVideoTitleHolder.alpha, fadeTo).apply {
                duration = 100
                fillAfter = true
            }

            updateUIVisibility()
            downloadBothHeader.startAnimation(fadeAnimation)

            if (hasEpisodes)
                playerEpisodesButton.startAnimation(fadeAnimation)
            // player_media_route_button?.startAnimation(fadeAnimation)
            // video_bar.startAnimation(fadeAnimation)

            // TITLE
            playerVideoTitleRez.startAnimation(fadeAnimation)
            playerVideoInfo.startAnimation(fadeAnimation)
            playerEpisodeFiller.startAnimation(fadeAnimation)
            playerVideoTitleHolder.startAnimation(fadeAnimation)
            playerTopHolder.startAnimation(fadeAnimation)
            // BOTTOM
            playerLockHolder.startAnimation(fadeAnimation)
            // player_go_back_holder?.startAnimation(fadeAnimation)
            shadowOverlay.isVisible = true
            shadowOverlay.startAnimation(fadeAnimation)
        }
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
        playerBinding?.apply {

            playerLockHolder.isGone = isGone
            playerVideoBar.isGone = isGone

            playerPausePlay.isGone = isGone
            // player_buffering?.isGone = isGone
            playerTopHolder.isGone = isGone
            val showPlayerEpisodes = !isGone && isThereEpisodes()
            playerEpisodesButtonRoot.isVisible = showPlayerEpisodes
            playerEpisodesButton.isVisible = showPlayerEpisodes
            playerVideoTitleHolder.isGone = togglePlayerTitleGone
            playerVideoTitleRez.isGone = isGone
            playerEpisodeFiller.isGone = isGone
            playerCenterMenu.isGone = isGone
            playerLock.isGone = !isShowing
            // player_media_route_button?.isClickable = !isGone
            playerGoBackHolder.isGone = isGone
            playerSourcesBtt.isGone = isGone
            playerSkipEpisode.isClickable = !isGone
        }
    }

    private fun updateLockUI() {
        playerBinding?.apply {
            playerLock.setIconResource(if (isLocked) R.drawable.video_locked else R.drawable.video_unlocked)
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

    protected fun autoHide() {
        metadataVisibilityToken++
        playerHostView?.scheduleAutoHide()
        scheduleMetadataVisibility()
    }

    override fun onAutoHideUI() {
        if (player.getIsPlaying()) onClickChange()
    }

    protected fun hidePlayerUI() {
        if (isShowing) {
            isShowing = false
            animateLayoutChanges()
        }
    }

    /** PlayerView.Callbacks touch overrides */

    override fun isUIShowing(): Boolean = isShowing

    override fun onSingleTap() {
        onClickChange()
    }

    override fun onTouchDown() {
        if (isShowingEpisodeOverlay) toggleEpisodesOverlay(show = false)
    }

    @SuppressLint("SetTextI18n")
    override fun onSeekPreviewText(text: String?) {
        playerBinding?.playerTimeText?.apply {
            isVisible = text != null
            if (text != null) this.text = text
        }
    }

    override fun onHidePlayerUI() {
        hidePlayerUI()
    }

    override fun onGestureEnd(hadSwipe: Boolean, wasUiShowing: Boolean) {
        if (!player.getIsPlaying() && hadSwipe && wasUiShowing && !isShowing) {
            isShowing = true
            animateLayoutChanges()
        }
        autoHide()
    }

    override fun playerStatusChanged() {
        super.playerStatusChanged()
        scheduleMetadataVisibility()
    }

    // When the hold-speedup gesture fires, hide controls so the video is unobstructed.
    // The speedup button show/hide and speed change are handled by PlayerView.
    override fun onHoldSpeedUp(show: Boolean) {
        if (show && isShowing) onClickChange()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // If we rotate the device we need to recalculate the zoom
        val gh = playerHostView?.gestureHelper ?: return
        val matrix = gh.zoomMatrix
        val animation = gh.matrixAnimation
        if ((animation == null || !animation.isRunning) && matrix != null) {
            // Ignore if we have no zoom or mid-animation
            playerView?.post {
                gh.applyZoomMatrix(matrix, true)
                playerHostView?.requestUpdateBrightnessOverlayOnNextLayout()
            }
        }
    }

    override fun resize(resize: PlayerResize, showToast: Boolean) {
        super.resize(resize, showToast)
        playerHostView?.requestUpdateBrightnessOverlayOnNextLayout()
    }

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
                        // If UI is not shown make click instantly skip to next chapter even if locked
                        if (timestampShowState) {
                            player.handleEvent(CSPlayerEvent.SkipCurrentChapter)
                        } else if (!isLocked) {
                            player.handleEvent(CSPlayerEvent.PlayPauseToggle)
                        }
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
                    // Handled entirely by PlayerView.handleVolumeKey (checks PHONE/EMULATOR).
                    if (playerHostView?.handleVolumeKey(keyCode) == true) return true
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

    protected fun uiReset() {
        metadataVisibilityToken++
        playerBinding?.playerMetadataScrim?.let {
            it.animate().cancel()
            it.alpha = 0f
            it.isVisible = false
        }
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
        playerHostView?.gestureHelper?.resetFastForwardText()
        playerHostView?.gestureHelper?.resetRewindText()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // As this is video specific it is better to not do any setKey/getKey
        outState.putLong(SUBTITLE_DELAY_BUNDLE_KEY, subtitleDelay)
        super.onSaveInstanceState(outState)
    }

    override fun onBindingCreated(binding: FragmentPlayerBinding, savedInstanceState: Bundle?) {
        // Set up playerBinding before super initializes the player
        // (brightness overlay is now injected by PlayerView.initialize())
        playerBinding = PlayerCustomLayoutBinding.bind(binding.root.findViewById(R.id.player_holder))

        super.onBindingCreated(binding, savedInstanceState)

        // This player is always full-screen; tell PlayerView so volume-key handling is active.
        playerHostView?.isFullScreen = true

        // Wire up the snap-hint outline view and schedule brightness overlay bounds update
        playerHostView?.videoOutline = playerBinding?.videoOutline
        playerHostView?.requestUpdateBrightnessOverlayOnNextLayout()

        val view = binding.root
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
        setupKeyEventListener()

        try {
            context?.let { ctx ->
                val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)

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

                playBackSpeedEnabled = settingsManager.getBoolean(
                    ctx.getString(R.string.playback_speed_enabled_key),
                    false
                )
                playerRotateEnabled = settingsManager.getBoolean(
                    ctx.getString(R.string.rotate_video_key),
                    false
                )
                playerResizeEnabled =
                    settingsManager.getBoolean(
                        ctx.getString(R.string.player_resize_enabled_key),
                        true
                    )
                hideControlsNames = settingsManager.getBoolean(
                    ctx.getString(R.string.hide_player_control_names_key),
                    false
                )

                val profiles = QualityDataHelper.getProfiles()
                val type = if (ctx.isUsingMobileData())
                    QualityDataHelper.QualityProfileType.Data
                else QualityDataHelper.QualityProfileType.WiFi

                currentQualityProfile =
                    profiles.firstOrNull { it.types.contains(type) }?.id ?: profiles.firstOrNull()?.id
                            ?: currentQualityProfile
            }
            playerBinding?.apply {
                playerSpeedBtt.isVisible = playBackSpeedEnabled
                playerResizeBtt.isVisible = playerResizeEnabled
                playerRotateBtt.isVisible =
                    if (isLayout(TV or EMULATOR)) false else playerRotateEnabled
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
                if (currentPlayerStatus == CSPlayerLoading.IsEnded && isLayout(PHONE)) {
                    player.handleEvent(CSPlayerEvent.Restart)
                } else {
                    player.handleEvent(CSPlayerEvent.PlayPauseToggle)
                }
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

            playerGoBack.setOnClickListener {
                activity?.popCurrentPage("FullScreenPlayer")
            }

            playerSourcesBtt.setOnClickListener {
                showMirrorsDialogue()
            }

            playerTracksBtt.setOnClickListener {
                showTracksDialogue()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                playerControlsScroll.setOnScrollChangeListener { _, _, _, _, _ ->
                    autoHide()
                }
            }

            exoProgress.registerPlayerView(playerView)

            @SuppressLint("ClickableViewAccessibility")
            exoProgress.setOnTouchListener { _, event ->
                // this makes the bar not disappear when sliding
                when (event.action) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> {
                        playerHostView?.cancelAutoHide()
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
        // init UI
        try {
            uiReset()
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun toggleRotate() {
        activity?.let {
            toggleOrientationWithSensor(it)
            rotatedManually = true
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
        // PlayerView already set isVerticalOrientation; skip rotation on TV (pillarbox instead).
        if (isLayout(TV or EMULATOR)) return
        // Skip zero-size events emitted when the player transitions to STATE_IDLE,
        // acting on them would reset auto-detected orientation to landscape.
        if (width <= 0 || height <= 0) return
        updateOrientation()
    }

    private fun toggleEpisodesOverlay(show: Boolean) {
        if (show && !isShowingEpisodeOverlay) {
            previousPlayStatus = player.getIsPlaying()
            player.handleEvent(CSPlayerEvent.Pause)
            showEpisodesOverlay()
            isShowingEpisodeOverlay = true
            animateEpisodesOverlay(true)
        } else if (isShowingEpisodeOverlay) {
            if (previousPlayStatus) player.handleEvent(CSPlayerEvent.Play)
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
