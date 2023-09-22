package com.lagradost.cloudstream3.ui.player

import android.annotation.SuppressLint
import android.content.*
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.AnimatedVectorDrawable
import android.media.metrics.PlaybackErrorEvent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.TimeBar
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.canEnterPipMode
import com.lagradost.cloudstream3.CommonActivity.isInPIPMode
import com.lagradost.cloudstream3.CommonActivity.keyEventListener
import com.lagradost.cloudstream3.CommonActivity.playerEventListener
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.unixTimeMs
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.requestLocalAudioFocus
import com.lagradost.cloudstream3.utils.EpisodeSkip
import com.lagradost.cloudstream3.utils.UIHelper
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage

enum class PlayerResize(@StringRes val nameRes: Int) {
    Fit(R.string.resize_fit),
    Fill(R.string.resize_fill),
    Zoom(R.string.resize_zoom),
}

// when the player should switch skip op to next episode
const val SKIP_OP_VIDEO_PERCENTAGE = 50

// when the player should preload the next episode for faster loading
const val PRELOAD_NEXT_EPISODE_PERCENTAGE = 80

// when the player should mark the episode as watched and resume watching the next
const val NEXT_WATCH_EPISODE_PERCENTAGE = 90

// when the player should sync the progress of "watched", TODO MAKE SETTING
const val UPDATE_SYNC_PROGRESS_PERCENTAGE = 80

abstract class AbstractPlayerFragment(
    val player: IPlayer = CS3IPlayer()
) : Fragment() {
    var resizeMode: Int = 0
    var subStyle: SaveCaptionStyle? = null
    var subView: SubtitleView? = null
    var isBuffering = true
    protected open var hasPipModeSupport = true

    var playerPausePlayHolderHolder : FrameLayout? = null
    var playerPausePlay : ImageView? = null
    var playerBuffering : ProgressBar? = null
    var playerView : PlayerView? = null
    var piphide : FrameLayout? = null
    var subtitleHolder : FrameLayout? = null

    @LayoutRes
    protected open var layout: Int = R.layout.fragment_player

    open fun nextEpisode() {
        throw NotImplementedError()
    }

    open fun prevEpisode() {
        throw NotImplementedError()
    }

    open fun playerPositionChanged(position: Long, duration : Long) {
        throw NotImplementedError()
    }

    open fun playerDimensionsLoaded(width: Int, height : Int) {
        throw NotImplementedError()
    }

    open fun subtitlesChanged() {
        throw NotImplementedError()
    }

    open fun embeddedSubtitlesFetched(subtitles: List<SubtitleData>) {
        throw NotImplementedError()
    }

    open fun onTracksInfoChanged() {
        throw NotImplementedError()
    }

    open fun onTimestamp(timestamp: EpisodeSkip.SkipStamp?) {

    }

    open fun onTimestampSkipped(timestamp: EpisodeSkip.SkipStamp) {

    }

    open fun exitedPipMode() {
        throw NotImplementedError()
    }

    private fun keepScreenOn(on: Boolean) {
        if (on) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun updateIsPlaying(wasPlaying : CSPlayerLoading,
                                isPlaying : CSPlayerLoading) {
        val isPlayingRightNow = CSPlayerLoading.IsPlaying == isPlaying
        val isPausedRightNow = CSPlayerLoading.IsPaused == isPlaying

        keepScreenOn(!isPausedRightNow)

        isBuffering = CSPlayerLoading.IsBuffering == isPlaying
        if (isBuffering) {
            playerPausePlayHolderHolder?.isVisible = false
            playerBuffering?.isVisible = true
        } else {
            playerPausePlayHolderHolder?.isVisible = true
            playerBuffering?.isVisible = false

            if (wasPlaying != isPlaying) {
                playerPausePlay?.setImageResource(if (isPlayingRightNow) R.drawable.play_to_pause else R.drawable.pause_to_play)
                val drawable = playerPausePlay?.drawable

                var startedAnimation = false
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    if (drawable is AnimatedImageDrawable) {
                        drawable.start()
                        startedAnimation = true
                    }
                }

                if (drawable is AnimatedVectorDrawable) {
                    drawable.start()
                    startedAnimation = true
                }

                if (drawable is AnimatedVectorDrawableCompat) {
                    drawable.start()
                    startedAnimation = true
                }

                // somehow the phone is wacked
                if (!startedAnimation) {
                    playerPausePlay?.setImageResource(if (isPlayingRightNow) R.drawable.netflix_pause else R.drawable.netflix_play)
                }
            } else {
                playerPausePlay?.setImageResource(if (isPlayingRightNow) R.drawable.netflix_pause else R.drawable.netflix_play)
            }
        }

        canEnterPipMode = isPlayingRightNow && hasPipModeSupport
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.let { act ->
                PlayerPipHelper.updatePIPModeActions(act, isPlayingRightNow, player.getAspectRatio())
            }
        }
    }

    private var pipReceiver: BroadcastReceiver? = null
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        try {
            isInPIPMode = isInPictureInPictureMode
            if (isInPictureInPictureMode) {
                // Hide the full-screen UI (controls, etc.) while in picture-in-picture mode.
                piphide?.isVisible = false
                pipReceiver = object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        if (ACTION_MEDIA_CONTROL != intent.action) {
                            return
                        }
                        player.handleEvent(
                            CSPlayerEvent.values()[intent.getIntExtra(
                                EXTRA_CONTROL_TYPE,
                                0
                            )], source = PlayerEventSource.UI
                        )
                    }
                }
                val filter = IntentFilter()
                filter.addAction(ACTION_MEDIA_CONTROL)
                activity?.registerReceiver(pipReceiver, filter)
                val isPlaying = player.getIsPlaying()
                val isPlayingValue =
                    if (isPlaying) CSPlayerLoading.IsPlaying else CSPlayerLoading.IsPaused
                updateIsPlaying(isPlayingValue, isPlayingValue)
            } else {
                // Restore the full-screen UI.
                piphide?.isVisible = true
                exitedPipMode()
                pipReceiver?.let {
                    // Prevents java.lang.IllegalArgumentException: Receiver not registered
                    normalSafeApiCall {
                        activity?.unregisterReceiver(it)
                    }
                }
                activity?.hideSystemUI()
                this.view?.let { UIHelper.hideKeyboard(it) }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    open fun hasNextMirror(): Boolean {
        throw NotImplementedError()
    }

    open fun nextMirror() {
        throw NotImplementedError()
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.requestLocalAudioFocus(AppUtils.getFocusRequest())
        }
    }

    open fun playerError(exception: Throwable) {
        fun showToast(message: String, gotoNext: Boolean = false) {
            if (gotoNext && hasNextMirror()) {
                showToast(
                    message,
                    Toast.LENGTH_SHORT
                )
                nextMirror()
            } else {
                showToast(
                    context?.getString(R.string.no_links_found_toast) + "\n" + message,
                    Toast.LENGTH_LONG
                )
                activity?.popCurrentPage()
            }
        }

        val ctx = context ?: return
        when (exception) {
            is PlaybackException -> {
                val msg = exception.message ?: ""
                val errorName = exception.errorCodeName
                when (val code = exception.errorCode) {
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_IO_NO_PERMISSION, PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                        showToast(
                            "${ctx.getString(R.string.source_error)}\n$errorName ($code)\n$msg",
                            gotoNext = true
                        )
                    }

                    PlaybackException.ERROR_CODE_REMOTE_ERROR, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, PlaybackException.ERROR_CODE_TIMEOUT, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> {
                        showToast(
                            "${ctx.getString(R.string.remote_error)}\n$errorName ($code)\n$msg",
                            gotoNext = true
                        )
                    }

                    PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackErrorEvent.ERROR_AUDIO_TRACK_INIT_FAILED, PlaybackErrorEvent.ERROR_AUDIO_TRACK_OTHER, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> {
                        showToast(
                            "${ctx.getString(R.string.render_error)}\n$errorName ($code)\n$msg",
                            gotoNext = true
                        )
                    }

                    else -> {
                        showToast(
                            "${ctx.getString(R.string.unexpected_error)}\n$errorName ($code)\n$msg",
                            gotoNext = false
                        )
                    }
                }
            }

            is InvalidFileException -> {
                showToast(
                    "${ctx.getString(R.string.source_error)}\n${exception.message}",
                    gotoNext = true
                )
            }

            else -> {
                exception.message?.let {
                    showToast(
                        it,
                        gotoNext = false
                    )
                }
            }
        }
    }

    private fun onSubStyleChanged(style: SaveCaptionStyle) {
        if (player is CS3IPlayer) {
            player.updateSubtitleStyle(style)
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun playerUpdated(player: Any?) {
        if (player is ExoPlayer) {
            context?.let { ctx ->
                mMediaSession?.release()
                mMediaSession = MediaSession.Builder(ctx, player)
                    // Ensure unique ID for concurrent players
                    .setId(unixTimeMs.toString())
                    .build()
            }

            // Necessary for multiple combined videos
            playerView?.setShowMultiWindowTimeBar(true)
            playerView?.player = player
            playerView?.performClick()
        }
    }

    private var mMediaSession: MediaSession? = null

    // this can be used in the future for players other than exoplayer
    //private val mMediaSessionCallback: MediaSessionCompat.Callback = object : MediaSessionCompat.Callback() {
    //    override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
    //        val keyEvent = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as KeyEvent?
    //        if (keyEvent != null) {
    //            if (keyEvent.action == KeyEvent.ACTION_DOWN) { // NO DOUBLE SKIP
    //                val consumed = when (keyEvent.keyCode) {
    //                    KeyEvent.KEYCODE_MEDIA_PAUSE -> callOnPause()
    //                    KeyEvent.KEYCODE_MEDIA_PLAY -> callOnPlay()
    //                    KeyEvent.KEYCODE_MEDIA_STOP -> callOnStop()
    //                    KeyEvent.KEYCODE_MEDIA_NEXT -> callOnNext()
    //                    else -> false
    //                }
    //                if (consumed) return true
    //            }
    //        }
    //
    //        return super.onMediaButtonEvent(mediaButtonEvent)
    //    }
    //}

    /** This receives the events from the player, if you want to append functionality you do it here,
     * do note that this only receives events for UI changes,
     * and returning early WONT stop it from changing in eg the player time or pause status */
    open fun mainCallback(event : PlayerEvent) {
        Log.i(TAG, "Handle event: $event")
        when(event) {
            is ResizedEvent -> {
                playerDimensionsLoaded(event.width, event.height)
            }
            is PlayerAttachedEvent -> {
                playerUpdated(event.player)
            }
            is SubtitlesUpdatedEvent -> {
                subtitlesChanged()
            }
            is TimestampSkippedEvent -> {
                onTimestampSkipped(event.timestamp)
            }
            is TimestampInvokedEvent -> {
                onTimestamp(event.timestamp)
            }
            is TracksChangedEvent -> {
                onTracksInfoChanged()
            }
            is EmbeddedSubtitlesFetchedEvent -> {
                embeddedSubtitlesFetched(event.tracks)
            }
            is ErrorEvent -> {
                playerError(event.error)
            }
            is RequestAudioFocusEvent -> {
                requestAudioFocus()
            }
            is EpisodeSeekEvent -> {
                when(event.offset) {
                    -1 -> prevEpisode()
                    1 -> nextEpisode()
                    else -> {}
                }
            }
            is StatusEvent -> {
                updateIsPlaying(wasPlaying = event.wasPlaying, isPlaying = event.isPlaying)
            }
            is PositionEvent -> {
                playerPositionChanged(position = event.toMs, duration = event.durationMs)
            }
            is VideoEndedEvent -> {
                context?.let { ctx ->
                    // Only play next episode if autoplay is on (default)
                    if (PreferenceManager.getDefaultSharedPreferences(ctx)
                            ?.getBoolean(
                                ctx.getString(R.string.autoplay_next_key),
                                true
                            ) == true
                    ) {
                        player.handleEvent(
                            CSPlayerEvent.NextEpisode,
                            source = PlayerEventSource.Player
                        )
                    }
                }
            }
            is PauseEvent -> Unit
            is PlayEvent -> Unit
        }
    }

    @SuppressLint("SetTextI18n", "UnsafeOptInUsageError")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        resizeMode = getKey(RESIZE_MODE_KEY) ?: 0
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
            subView = playerView?.findViewById(R.id.exo_subtitles)
            subStyle = SubtitlesFragment.getCurrentSavedStyle()
            player.initSubtitles(subView, subtitleHolder, subStyle)

            /** this might seam a bit fucky and that is because it is, the seek event is captured twice, once by the player
             * and once by the UI even if it should only be registered once by the UI */
            playerView?.findViewById<DefaultTimeBar>(R.id.exo_progress)?.addListener(object : TimeBar.OnScrubListener {
                override fun onScrubStart(timeBar: TimeBar, position: Long) = Unit
                override fun onScrubMove(timeBar: TimeBar, position: Long) = Unit
                override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                    if (canceled) return
                    val playerDuration = player.getDuration() ?: return
                    val playerPosition = player.getPosition() ?: return
                    mainCallback(PositionEvent(source = PlayerEventSource.UI, durationMs = playerDuration, fromMs = playerPosition, toMs = position))
                }
            })

            SubtitlesFragment.applyStyleEvent += ::onSubStyleChanged

            try {
                context?.let { ctx ->
                    val settingsManager = PreferenceManager.getDefaultSharedPreferences(
                        ctx
                    )

                    val currentPrefCacheSize =
                        settingsManager.getInt(getString(R.string.video_buffer_size_key), 0)
                    val currentPrefDiskSize =
                        settingsManager.getInt(getString(R.string.video_buffer_disk_key), 0)
                    val currentPrefBufferSec =
                        settingsManager.getInt(getString(R.string.video_buffer_length_key), 0)

                    player.cacheSize = currentPrefCacheSize * 1024L * 1024L
                    player.simpleCacheSize = currentPrefDiskSize * 1024L * 1024L
                    player.videoBufferMs = currentPrefBufferSec * 1000L
                }
            } catch (e: Exception) {
                logError(e)
            }
        }

        /*context?.let { ctx ->
            player.loadPlayer(
                ctx,
                false,
                ExtractorLink(
                    "idk",
                    "bunny",
                    "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    "",
                    Qualities.P720.value,
                    false
                ),
            )
        }*/
    }

    override fun onDestroy() {
        playerEventListener = null
        keyEventListener = null
        canEnterPipMode = false
        mMediaSession?.release()
        mMediaSession = null
        playerView?.player = null
        SubtitlesFragment.applyStyleEvent -= ::onSubStyleChanged

        keepScreenOn(false)
        super.onDestroy()
    }

    fun nextResize() {
        resizeMode = (resizeMode + 1) % PlayerResize.values().size
        resize(resizeMode, true)
    }

    fun resize(resize: Int, showToast: Boolean) {
        resize(PlayerResize.values()[resize], showToast)
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun resize(resize: PlayerResize, showToast: Boolean) {
        setKey(RESIZE_MODE_KEY, resize.ordinal)
        val type = when (resize) {
            PlayerResize.Fill -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            PlayerResize.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            PlayerResize.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
        playerView?.resizeMode = type

        if (showToast)
            showToast(resize.nameRes, Toast.LENGTH_SHORT)
    }

    override fun onStop() {
        player.onStop()
        super.onStop()
    }

    override fun onResume() {
        context?.let { ctx ->
            player.onResume(ctx)
        }

        super.onResume()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(layout, container, false)
        playerPausePlayHolderHolder = root.findViewById(R.id.player_pause_play_holder_holder)
        playerPausePlay = root.findViewById(R.id.player_pause_play)
        playerBuffering = root.findViewById(R.id.player_buffering)
        playerView = root.findViewById(R.id.player_view)
        piphide = root.findViewById(R.id.piphide)
        subtitleHolder = root.findViewById(R.id.subtitle_holder)
        return root
    }
}