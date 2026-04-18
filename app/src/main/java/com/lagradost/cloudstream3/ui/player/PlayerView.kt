package com.lagradost.cloudstream3.ui.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.AnimatedVectorDrawable
import android.media.metrics.PlaybackErrorEvent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.TimeBar
import androidx.preference.PreferenceManager
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.github.rubensousa.previewseekbar.PreviewBar
import com.github.rubensousa.previewseekbar.media3.PreviewTimeBar
import com.lagradost.cloudstream3.CommonActivity.isInPIPMode
import com.lagradost.cloudstream3.CommonActivity.playerEventListener
import com.lagradost.cloudstream3.CommonActivity.screenWidth
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.ui.player.live.LivePreviewTimeBar
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.utils.AppContextUtils
import com.lagradost.cloudstream3.utils.AppContextUtils.requestLocalAudioFocus
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import android.content.pm.ActivityInfo
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.UserPreferenceDelegate
import com.lagradost.cloudstream3.utils.videoskip.VideoSkipStamp
import java.net.SocketTimeoutException
import android.text.format.DateUtils
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE

/**
 * Shared player view - manages ExoPlayer setup, view binding, lifecycle, and event
 * dispatching.  Gesture/volume/brightness/key-event input is handled by [gestureHelper]
 * ([PlayerGestureHelper]), which is exposed via delegate properties for easier access.
 */
@OptIn(UnstableApi::class)
class PlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    /** All gesture, volume, brightness and key-event logic lives here. */
    val gestureHelper = PlayerGestureHelper(this)

    /** Delegate properties (forwarded to gestureHelper for external callers to have easier access) */
    var isFullScreen: Boolean
        get() = gestureHelper.isFullScreen
        set(value) { gestureHelper.isFullScreen = value }

    var isLocked: Boolean
        get() = gestureHelper.isLocked
        set(value) { gestureHelper.isLocked = value }

    var videoOutline: View?
        get() = gestureHelper.videoOutline
        set(value) { gestureHelper.videoOutline = value }

    /** Delegate methods */
    fun handleVolumeKey(keyCode: Int) = gestureHelper.handleVolumeKey(keyCode)
    fun verifyVolume() = gestureHelper.verifyVolume()
    fun setupKeyEventListener() = gestureHelper.setupKeyEventListener()
    fun releaseKeyEventListener() = gestureHelper.releaseKeyEventListener()
    fun clearZoomState() = gestureHelper.clearZoomState()
    fun requestUpdateBrightnessOverlayOnNextLayout() = gestureHelper.requestUpdateBrightnessOverlayOnNextLayout()
    fun releaseOverlayLayoutListener() = gestureHelper.releaseOverlayLayoutListener()

    /** Callbacks */

    /** Host-fragment-level callbacks invoked by [mainCallback]. */
    interface Callbacks {
        fun nextEpisode() {}
        fun prevEpisode() {}
        fun playerPositionChanged(position: Long, duration: Long) {}
        fun playerStatusChanged() {}
        fun playerDimensionsLoaded(width: Int, height: Int) {}
        fun subtitlesChanged() {}
        fun embeddedSubtitlesFetched(subtitles: List<SubtitleData>) {}
        fun onTracksInfoChanged() {}
        fun onTimestamp(timestamp: VideoSkipStamp?) {}
        fun onTimestampSkipped(timestamp: VideoSkipStamp) {}
        fun exitedPipMode() {}
        fun hasNextMirror(): Boolean = false
        fun nextMirror() {}
        fun onDownload(event: DownloadEvent) {}
        fun playerError(exception: Throwable) {}
        /** Called after [PlayerView] finishes its own player-attached setup (MediaSession, ExoPlayer view). */
        fun playerUpdated(player: Any?) {}
        /** Called on a short single-tap on empty player area (no swipe, no double-tap). */
        fun onSingleTap() {}
        /** Called when the hold-for-speedup gesture starts (show=true) or ends (show=false). */
        fun onHoldSpeedUp(show: Boolean) {}
        /** Called during brightness swipe with the current extra-brightness alpha (0–1). */
        fun onBrightnessExtra(alpha: Float) {}

        /** Touch event callbacks */

        /** Called to validate a touch position; return false to discard nav-bar / status-bar touches. */
        fun isValidTouch(rawX: Float, rawY: Float): Boolean = true
        /** Returns whether the player UI (controls overlay) is currently visible. */
        fun isUiShowing(): Boolean = false
        /** Called on a valid ACTION_DOWN; use for e.g. dismissing an episode overlay. */
        fun onTouchDown() {}
        /** Called with seek-preview text during a horizontal-swipe, or null to clear it. */
        fun onSeekPreviewText(text: String?) {}
        /** Called when a swipe gesture begins; hide the player UI if desired. */
        fun onHidePlayerUI() {}
        /**
         * Called at the end of each touch sequence.
         *  @param hadSwipe   true if a swipe (brightness/volume/time) was in progress.
         *  @param wasUiShowing true if the UI was visible when the swipe began.
         */
        fun onGestureEnd(hadSwipe: Boolean, wasUiShowing: Boolean) {}
        /**
         * Called when the auto-hide timer fires: UI is showing, no touch is active.
         * Implement to hide the player controls.
         */
        fun onAutoHideUI() {}
    }

    var callbacks: Callbacks? = null

    /** Player state */

    var player: IPlayer = CS3IPlayer()
    var resizeMode: Int = 0
    var hasPipModeSupport: Boolean = true
    var currentPlayerStatus: CSPlayerLoading = CSPlayerLoading.IsBuffering
    var mMediaSession: MediaSession? = null
    private var pipReceiver: BroadcastReceiver? = null

    /** Auto-hide */
    private var autoHideToken = 0
    private val autoHideHandler = Handler(Looper.getMainLooper())

    /** View references (populated by bindViews) */

    var subView: SubtitleView? = null
    var playerPausePlayHolderHolder: FrameLayout? = null
    var playerPausePlay: ImageView? = null
    var playerBuffering: ProgressBar? = null
    /** The Media3/ExoPlayer [androidx.media3.ui.PlayerView] widget. */
    var exoPlayerView: androidx.media3.ui.PlayerView? = null
    var piphide: FrameLayout? = null
    var subtitleHolder: FrameLayout? = null
    internal var playerRew: View? = null
    internal var playerFfwd: View? = null
    internal var exoRewText: TextView? = null
    internal var exoFfwdText: TextView? = null
    internal var playerCenterMenu: View? = null
    internal var playerRewHolder: View? = null
    internal var playerFfwdHolder: View? = null
    internal var playerVideoHolder: View? = null
    var playerProgressbarLeftHolder: RelativeLayout? = null
    var playerProgressbarLeftIcon: ImageView? = null
    var playerProgressbarLeftLevel1: ProgressBar? = null
    var playerProgressbarLeftLevel2: ProgressBar? = null
    var playerProgressbarRightHolder: RelativeLayout? = null
    var playerProgressbarRightIcon: ImageView? = null
    var playerProgressbarRightLevel1: ProgressBar? = null
    var playerProgressbarRightLevel2: ProgressBar? = null
    /** Accessed by [PlayerGestureHelper.showOrHideSpeedUp]. */
    internal var playerSpeedupButton: View? = null
    var playerHolder: FrameLayout? = null
    private var exoDuration: TextView? = null
    private var timeLeft: TextView? = null
    private var exoPosition: TextView? = null
    private var timeLive: View? = null
    private var exoProgress: LivePreviewTimeBar? = null

    /** Seek delta used by the basic rew/ffwd click listeners. Read from settings in [initialize]. */
    var seekTime: Long = 10_000L

    /** True when the current video is taller than it is wide. Set by [mainCallback] on [ResizedEvent]. */
    var isVerticalOrientation: Boolean = false

    /** When true, [dynamicOrientation] returns portrait for portrait videos. Read from settings in [initialize]. */
    var autoPlayerRotateEnabled: Boolean = false

    var durationMode: Boolean by UserPreferenceDelegate("duration_mode", false)

    // Kept so SubtitlesFragment can unsubscribe the exact same reference.
    private val subStyleListener: (SaveCaptionStyle) -> Unit = ::onSubStyleChanged

    /** View discovery */

    /**
     * Discovers player-related views from [root].  IDs absent in compact layouts (e.g. trailer) simply
     * remain null, all usage is null-safe.
     */
    fun bindViews(root: View) {
        playerPausePlayHolderHolder  = root.findViewById(R.id.player_pause_play_holder_holder)
        playerPausePlay              = root.findViewById(R.id.player_pause_play)
        playerBuffering              = root.findViewById(R.id.player_buffering)
        exoPlayerView                = root.findViewById(R.id.player_view)
        piphide                      = root.findViewById(R.id.piphide)
        subtitleHolder               = root.findViewById(R.id.subtitle_holder)
        playerRew                    = root.findViewById(R.id.player_rew)
        playerFfwd                   = root.findViewById(R.id.player_ffwd)
        exoRewText                   = root.findViewById(R.id.exo_rew_text)
        exoFfwdText                  = root.findViewById(R.id.exo_ffwd_text)
        playerCenterMenu             = root.findViewById(R.id.player_center_menu)
        playerRewHolder              = root.findViewById(R.id.player_rew_holder)
        playerFfwdHolder             = root.findViewById(R.id.player_ffwd_holder)
        playerVideoHolder            = root.findViewById(R.id.player_video_holder)
        playerProgressbarLeftHolder  = root.findViewById(R.id.player_progressbar_left_holder)
        playerProgressbarLeftIcon    = root.findViewById(R.id.player_progressbar_left_icon)
        playerProgressbarLeftLevel1  = root.findViewById(R.id.player_progressbar_left_level1)
        playerProgressbarLeftLevel2  = root.findViewById(R.id.player_progressbar_left_level2)
        playerProgressbarRightHolder = root.findViewById(R.id.player_progressbar_right_holder)
        playerProgressbarRightIcon   = root.findViewById(R.id.player_progressbar_right_icon)
        playerProgressbarRightLevel1 = root.findViewById(R.id.player_progressbar_right_level1)
        playerProgressbarRightLevel2 = root.findViewById(R.id.player_progressbar_right_level2)
        playerSpeedupButton          = root.findViewById(R.id.player_speedup_button)
        playerHolder                 = root.findViewById(R.id.player_holder)
        timeLeft                     = root.findViewById(R.id.time_left)
        timeLive                     = root.findViewById(R.id.time_live)
        exoDuration = playerHolder?.findViewById(androidx.media3.ui.R.id.exo_duration)
            ?: root.findViewById(androidx.media3.ui.R.id.exo_duration)
        exoPosition = playerHolder?.findViewById(androidx.media3.ui.R.id.exo_position)
            ?: root.findViewById(androidx.media3.ui.R.id.exo_position)
    }

    /**
     * Called once after [bindViews].  Sets up the preview seek-bar, subtitle style listener,
     * player callbacks and basic controls; then delegates gesture/input setup to [gestureHelper].
     */
    fun initialize() {
        resizeMode = DataStoreHelper.resizeMode
        resize(resizeMode, false)

        player.releaseCallbacks()
        player.initCallbacks(
            eventHandler = ::mainCallback,
            requestedListeningPercentages = listOf(
                SKIP_OP_VIDEO_PERCENTAGE,
                PRELOAD_NEXT_EPISODE_PERCENTAGE,
                NEXT_WATCH_EPISODE_PERCENTAGE,
                UPDATE_SYNC_PROGRESS_PERCENTAGE,
            ),
        )

        if (player is CS3IPlayer) {
            val progressBar: PreviewTimeBar? = exoPlayerView?.findViewById(R.id.exo_progress)
            exoProgress = progressBar as? LivePreviewTimeBar
            val previewImageView: ImageView? = exoPlayerView?.findViewById(R.id.previewImageView)
            val previewFrameLayout: FrameLayout? =
                exoPlayerView?.findViewById(R.id.previewFrameLayout)

            if (progressBar != null && previewImageView != null && previewFrameLayout != null) {
                var resume = false
                progressBar.addOnScrubListener(object : PreviewBar.OnScrubListener {
                    override fun onScrubStart(previewBar: PreviewBar?) {
                        val cs3 = player as? CS3IPlayer ?: return
                        val hasPreview = cs3.hasPreview()
                        progressBar.isPreviewEnabled = hasPreview
                        resume = cs3.getIsPlaying()
                        if (resume) cs3.handleEvent(CSPlayerEvent.Pause, PlayerEventSource.Player)
                        if (hasPreview) subView?.isVisible = false
                    }

                    override fun onScrubMove(previewBar: PreviewBar?, progress: Int, fromUser: Boolean) {}

                    override fun onScrubStop(previewBar: PreviewBar?) {
                        val cs3 = player as? CS3IPlayer ?: return
                        if (resume) cs3.handleEvent(CSPlayerEvent.Play, PlayerEventSource.Player)
                        subView?.postDelayed({
                            if (previewBar == null || !previewBar.isPreviewEnabled || !previewBar.isShowingPreview) {
                                subView?.isVisible = true
                            }
                        }, 200)
                    }
                })
                progressBar.attachPreviewView(previewFrameLayout)
                progressBar.setPreviewLoader { currentPosition, max ->
                    val cs3 = player as? CS3IPlayer ?: return@setPreviewLoader
                    val bitmap = cs3.getPreview(currentPosition.toFloat().div(max.toFloat()))
                    previewImageView.isGone = bitmap == null
                    previewImageView.setImageBitmap(bitmap)
                }
            }

            subView = exoPlayerView?.findViewById(androidx.media3.ui.R.id.exo_subtitles)
            (player as? CS3IPlayer)?.initSubtitles(subView, subtitleHolder, CustomDecoder.style)
            (player as? CS3IPlayer)?.let {
                (it.imageGenerator as? PreviewGenerator)?.params =
                    ImageParams.new16by9(screenWidth)
            }

            exoPlayerView?.findViewById<androidx.media3.ui.DefaultTimeBar>(R.id.exo_progress)
                ?.addListener(object : TimeBar.OnScrubListener {
                    override fun onScrubStart(timeBar: TimeBar, position: Long) = Unit
                    override fun onScrubMove(timeBar: TimeBar, position: Long) = Unit
                    override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                        if (canceled) return
                        val playerDuration = player.getDuration() ?: return
                        val playerPosition = player.getPosition() ?: return
                        mainCallback(
                            PositionEvent(
                                source = PlayerEventSource.UI,
                                durationMs = playerDuration,
                                fromMs = playerPosition,
                                toMs = position
                            )
                        )
                    }
                })

            // Read seek time and rotation settings.
            try {
                val sm = PreferenceManager.getDefaultSharedPreferences(context)
                seekTime = sm.getInt(context.getString(R.string.double_tap_seek_time_key), 10)
                    .toLong() * 1000L
                autoPlayerRotateEnabled = sm.getBoolean(
                    context.getString(R.string.auto_rotate_video_key), true
                )
            } catch (_: Exception) { }

            val seekSecs = (seekTime / 1000).toInt()
            exoRewText?.text  = context.getString(R.string.rew_text_regular_format).format(seekSecs)
            exoFfwdText?.text = context.getString(R.string.ffw_text_regular_format).format(seekSecs)

            playerPausePlay?.setOnClickListener {
                if (currentPlayerStatus == CSPlayerLoading.IsEnded) {
                    player.handleEvent(CSPlayerEvent.Restart, PlayerEventSource.UI)
                } else {
                    player.handleEvent(CSPlayerEvent.PlayPauseToggle, PlayerEventSource.UI)
                }
            }
            playerRew?.setOnClickListener  {
                scheduleAutoHide()
                gestureHelper.rewind()
            }
            playerFfwd?.setOnClickListener {
                scheduleAutoHide()
                gestureHelper.fastForward()
            }

            SubtitlesFragment.applyStyleEvent += subStyleListener

            try {
                val ctx = context
                val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)
                val cs3 = player as? CS3IPlayer ?: return
                cs3.cacheSize =
                    settingsManager.getInt(context.getString(R.string.video_buffer_size_key), 0) * 1024L * 1024L
                cs3.simpleCacheSize =
                    settingsManager.getInt(context.getString(R.string.video_buffer_disk_key), 0) * 1024L * 1024L
                cs3.videoBufferMs =
                    settingsManager.getInt(context.getString(R.string.video_buffer_length_key), 0) * 1000L
            } catch (e: Exception) {
                logError(e)
            }

            // Duration toggle click listeners
            exoDuration?.setOnClickListener { setRemainingTimeCounter(true) }
            timeLeft?.setOnClickListener { setRemainingTimeCounter(false) }
            // Keep remaining-time text in sync with playback position
            exoPosition?.doOnTextChanged { _, _, _, _ -> updateRemainingTime() }

            // Delegate gesture/input setup (settings, brightness overlay, touch gestures, key listener)
            gestureHelper.initialize()
            setupKeyEventListener()

            // Apply duration-mode display (remaining time vs elapsed); TV always shows remaining
            setRemainingTimeCounter(durationMode || isLayout(TV))
        }
    }

    /** Lifecycle delegation */

    fun onStop() {
        player.onStop()
    }

    fun onResume(ctx: Context) {
        player.onResume(ctx)
    }

    /** Releases all player resources. */
    fun release() {
        player.release()
        player.releaseCallbacks()
        player = CS3IPlayer()

        playerEventListener = null
        // keyEventListener is deregistered in onPause so that the incoming player's
        // onResume can register its own listener without racing against release().

        PlayerPipHelper.updatePIPModeActions(
            context as? Activity,
            CSPlayerLoading.IsPaused,
            false,
            null
        )

        mMediaSession?.release()
        mMediaSession = null
        exoPlayerView?.player = null

        SubtitlesFragment.applyStyleEvent -= subStyleListener

        gestureHelper.release()
        autoHideHandler.removeCallbacksAndMessages(null)

        keepScreenOn(false)
    }

    fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        activity: Activity?
    ) {
        try {
            isInPIPMode = isInPictureInPictureMode
            if (isInPictureInPictureMode) {
                piphide?.isVisible = false
                pipReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (ACTION_MEDIA_CONTROL != intent.action) return
                        player.handleEvent(
                            CSPlayerEvent.entries[intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)],
                            source = PlayerEventSource.UI
                        )
                    }
                }
                val filter = IntentFilter().apply { addAction(ACTION_MEDIA_CONTROL) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activity?.registerReceiver(pipReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    @SuppressLint("UnspecifiedRegisterReceiverFlag")
                    activity?.registerReceiver(pipReceiver, filter)
                }
                val isPlaying = player.getIsPlaying()
                val status = if (isPlaying) CSPlayerLoading.IsPlaying else CSPlayerLoading.IsPaused
                updateIsPlaying(status, status)
            } else {
                piphide?.isVisible = true
                callbacks?.exitedPipMode()
                pipReceiver?.let { safe { activity?.unregisterReceiver(it) } }
                activity?.hideSystemUI()
                hideKeyboard(this)
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    /** Player UI helpers */

    private fun keepScreenOn(on: Boolean) {
        val window = (context as? Activity)?.window ?: return
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun updateIsPlaying(wasPlaying: CSPlayerLoading, isPlaying: CSPlayerLoading) {
        val isPlayingRightNow = CSPlayerLoading.IsPlaying == isPlaying
        val isBuffering = CSPlayerLoading.IsBuffering == isPlaying
        currentPlayerStatus = isPlaying

        keepScreenOn(isPlayingRightNow || isBuffering)

        if (isBuffering) {
            playerPausePlayHolderHolder?.isVisible = false
            playerBuffering?.isVisible = true
        } else {
            playerPausePlayHolderHolder?.isVisible = true
            playerBuffering?.isVisible = false

            if (isPlaying == CSPlayerLoading.IsEnded && isLayout(PHONE)) {
                playerPausePlay?.setImageResource(R.drawable.ic_baseline_replay_24)
            } else if (wasPlaying != isPlaying) {
                playerPausePlay?.setImageResource(
                    if (isPlayingRightNow) R.drawable.play_to_pause else R.drawable.pause_to_play
                )
                val drawable = playerPausePlay?.drawable
                var startedAnimation = false
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    if (drawable is AnimatedImageDrawable) { drawable.start(); startedAnimation = true }
                }
                if (drawable is AnimatedVectorDrawable) { drawable.start(); startedAnimation = true }
                if (drawable is AnimatedVectorDrawableCompat) { drawable.start(); startedAnimation = true }
                if (!startedAnimation) {
                    playerPausePlay?.setImageResource(
                        if (isPlayingRightNow) R.drawable.netflix_pause else R.drawable.netflix_play
                    )
                }
            } else {
                playerPausePlay?.setImageResource(
                    if (isPlayingRightNow) R.drawable.netflix_pause else R.drawable.netflix_play
                )
            }
        }

        PlayerPipHelper.updatePIPModeActions(
            context as? Activity,
            isPlaying,
            hasPipModeSupport,
            player.getAspectRatio()
        )
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (context as? Activity)?.requestLocalAudioFocus(AppContextUtils.getFocusRequest())
        }
    }

    private fun playerUpdated(player: Any?) {
        if (player is ExoPlayer) {
            mMediaSession?.release()
            mMediaSession = MediaSession.Builder(context, player)
                .setId(System.currentTimeMillis().toString())
                .build()

            @Suppress("DEPRECATION")
            exoPlayerView?.setShowMultiWindowTimeBar(true)
            exoPlayerView?.player = player
            exoPlayerView?.performClick()
        }
        callbacks?.playerUpdated(player)
    }

    private fun onSubStyleChanged(style: SaveCaptionStyle) {
        player.updateSubtitleStyle(style)
        player.seekTime(-1)
    }

    /** Error handling */

    fun playerError(exception: Throwable) {
        fun showErrorToast(message: String, gotoNext: Boolean = false) {
            if (gotoNext && callbacks?.hasNextMirror() == true) {
                showToast(message, Toast.LENGTH_SHORT)
                callbacks?.nextMirror()
            } else {
                showToast(
                    context.getString(R.string.no_links_found_toast) + "\n" + message,
                    Toast.LENGTH_LONG
                )
                (context as? FragmentActivity)?.popCurrentPage()
            }
        }

        when (exception) {
            is PlaybackException -> {
                val msg = exception.message ?: ""
                val errorName = exception.errorCodeName
                when (val code = exception.errorCode) {
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                    PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                        showErrorToast("${context.getString(R.string.source_error)}\n$errorName ($code)\n$msg", gotoNext = true)

                    PlaybackException.ERROR_CODE_REMOTE_ERROR,
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    PlaybackException.ERROR_CODE_TIMEOUT,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
                        showErrorToast("${context.getString(R.string.remote_error)}\n$errorName ($code)\n$msg", gotoNext = true)

                    PlaybackErrorEvent.ERROR_AUDIO_TRACK_INIT_FAILED,
                    PlaybackErrorEvent.ERROR_AUDIO_TRACK_OTHER,
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
                        showErrorToast("${context.getString(R.string.render_error)}\n$errorName ($code)\n$msg", gotoNext = true)

                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
                        showErrorToast("${context.getString(R.string.unsupported_error)}\n$errorName ($code)\n$msg", gotoNext = true)

                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                        showErrorToast("${context.getString(R.string.encoding_error)}\n$errorName ($code)\n$msg", gotoNext = true)

                    else ->
                        showErrorToast("${context.getString(R.string.unexpected_error)}\n$errorName ($code)\n$msg", gotoNext = false)
                }
            }

            is InvalidFileException ->
                showErrorToast("${context.getString(R.string.source_error)}\n${exception.message}", gotoNext = true)

            is SocketTimeoutException ->
                (context as? Activity)?.runOnUiThread {
                    showErrorToast("${context.getString(R.string.remote_error)}\n${exception.message}", gotoNext = true)
                }

            is ErrorLoadingException ->
                exception.message?.let { showErrorToast(it, gotoNext = true) }
                    ?: showErrorToast(exception.toString(), gotoNext = true)

            else ->
                exception.message?.let { showErrorToast(it, gotoNext = false) }
                    ?: showErrorToast(exception.toString(), gotoNext = false)
        }
    }

    /** Resize */

    fun nextResize() {
        resizeMode = (resizeMode + 1) % PlayerResize.entries.size
        resize(resizeMode, true)
    }

    fun resize(resize: Int, showToast: Boolean) {
        resize(PlayerResize.entries[resize], showToast)
    }

    fun resize(resize: PlayerResize, showToast: Boolean) {
        DataStoreHelper.resizeMode = resize.ordinal
        val type = when (resize) {
            PlayerResize.Fill -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            PlayerResize.Fit  -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            PlayerResize.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
        exoPlayerView?.resizeMode = type
        if (showToast) showToast(resize.nameRes, Toast.LENGTH_SHORT)
    }

    /** Orientation */

    /**
     * Returns the desired [ActivityInfo] orientation constant based on [isVerticalOrientation]
     * and [autoPlayerRotateEnabled].  TV/emulator always returns sensor-landscape.
     * Host fragments call this from [Callbacks.playerDimensionsLoaded] to apply rotation.
     */
    fun dynamicOrientation(): Int {
        if (isLayout(TV or EMULATOR)) return ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        return if (autoPlayerRotateEnabled && isVerticalOrientation)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    /** Event dispatch */

    fun mainCallback(event: PlayerEvent) {
        if (event !is DownloadEvent) Log.i(TAG, "Handle event: $event")
        when (event) {
            is DownloadEvent              -> callbacks?.onDownload(event)
            is ResizedEvent               -> {
                // TV never rotates; otherwise track whether the video is portrait.
                isVerticalOrientation = !isLayout(TV or EMULATOR) && event.height > event.width
                callbacks?.playerDimensionsLoaded(event.width, event.height)
            }
            is PlayerAttachedEvent        -> playerUpdated(event.player)
            is SubtitlesUpdatedEvent      -> callbacks?.subtitlesChanged()
            is TimestampSkippedEvent      -> callbacks?.onTimestampSkipped(event.timestamp)
            is TimestampInvokedEvent      -> callbacks?.onTimestamp(event.timestamp)
            is TracksChangedEvent         -> callbacks?.onTracksInfoChanged()
            is EmbeddedSubtitlesFetchedEvent -> callbacks?.embeddedSubtitlesFetched(event.tracks)
            is ErrorEvent                 -> {
                val cb = callbacks
                if (cb != null) cb.playerError(event.error)
                else playerError(event.error)
            }
            is RequestAudioFocusEvent -> requestAudioFocus()
            is EpisodeSeekEvent -> when (event.offset) {
                -1 -> callbacks?.prevEpisode()
                1  -> callbacks?.nextEpisode()
            }
            is StatusEvent -> {
                updateIsPlaying(wasPlaying = event.wasPlaying, isPlaying = event.isPlaying)
                scheduleAutoHide()
                callbacks?.playerStatusChanged()
            }
            is PositionEvent -> callbacks?.playerPositionChanged(
                position = event.toMs,
                duration = event.durationMs
            )
            is VideoEndedEvent -> {
                val ctx = context
                if (PreferenceManager.getDefaultSharedPreferences(ctx)
                        ?.getBoolean(ctx.getString(R.string.autoplay_next_key), true) == true
                ) {
                    player.handleEvent(CSPlayerEvent.NextEpisode, source = PlayerEventSource.Player)
                }
            }
            is PauseEvent -> Unit
            is PlayEvent  -> Unit
        }
    }

    /** Duration display */

    fun setRemainingTimeCounter(showRemaining: Boolean) {
        durationMode = showRemaining
        exoDuration?.isInvisible = showRemaining
        timeLeft?.isVisible = showRemaining
        if (showRemaining) updateRemainingTime()
    }

    fun updateRemainingTime() {
        val duration = player.getDuration()
        val position = player.getPosition()

        if (exoProgress?.isAtLiveEdge() == true) {
            timeLeft?.alpha = 0f
            exoDuration?.alpha = 0f
            timeLive?.isVisible = true
        } else {
            timeLeft?.alpha = 1f
            exoDuration?.alpha = 1f
            timeLive?.isVisible = false
        }

        if (duration != null && duration > 1 && position != null) {
            val remainingTimeSeconds = (duration - position + 500) / 1000
            @SuppressLint("SetTextI18n")
            timeLeft?.text = "-${DateUtils.formatElapsedTime(remainingTimeSeconds)}"
        }
    }

    /** Auto-hide */

    /**
     * Schedules a delayed auto-hide of the player UI after [delayMs] ms.
     * Any previously pending hide is canceled first.
     * The hide fires only when no touch is active and [Callbacks.isUiShowing] is true;
     * the actual hide action is delegated to [Callbacks.onAutoHideUI].
     */
    fun scheduleAutoHide(delayMs: Long = 3000L) {
        val token = ++autoHideToken
        autoHideHandler.removeCallbacksAndMessages(null)
        autoHideHandler.postDelayed({
            if (token != autoHideToken) return@postDelayed
            if (gestureHelper.isCurrentTouchValid) return@postDelayed
            if (callbacks?.isUiShowing() != true) return@postDelayed
            callbacks?.onAutoHideUI()
        }, delayMs)
    }

    /** Cancels any pending auto-hide scheduled by [scheduleAutoHide]. */
    fun cancelAutoHide() {
        autoHideToken++
        autoHideHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val TAG = "PlayerView"
    }
}
