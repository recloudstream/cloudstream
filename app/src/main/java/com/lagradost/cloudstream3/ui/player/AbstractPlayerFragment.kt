package com.lagradost.cloudstream3.ui.player

import android.annotation.SuppressLint
import android.content.*
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.AnimatedVectorDrawable
import android.media.metrics.PlaybackErrorEvent
import android.os.Build
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.media.session.MediaButtonReceiver
import androidx.preference.PreferenceManager
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.SubtitleView
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.canEnterPipMode
import com.lagradost.cloudstream3.CommonActivity.isInPIPMode
import com.lagradost.cloudstream3.CommonActivity.keyEventListener
import com.lagradost.cloudstream3.CommonActivity.playerEventListener
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.requestLocalAudioFocus
import com.lagradost.cloudstream3.utils.EpisodeSkip
import com.lagradost.cloudstream3.utils.UIHelper
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import kotlinx.android.synthetic.main.fragment_player.*
import kotlinx.android.synthetic.main.player_custom_layout.*

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


    @LayoutRes
    protected var layout: Int = R.layout.fragment_player

    open fun nextEpisode() {
        throw NotImplementedError()
    }

    open fun prevEpisode() {
        throw NotImplementedError()
    }

    open fun playerPositionChanged(posDur: Pair<Long, Long>) {
        throw NotImplementedError()
    }

    open fun playerDimensionsLoaded(widthHeight: Pair<Int, Int>) {
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

    private fun updateIsPlaying(playing: Pair<CSPlayerLoading, CSPlayerLoading>) {
        val (wasPlaying, isPlaying) = playing
        val isPlayingRightNow = CSPlayerLoading.IsPlaying == isPlaying
        val isPausedRightNow = CSPlayerLoading.IsPaused == isPlaying

        keepScreenOn(!isPausedRightNow)

        isBuffering = CSPlayerLoading.IsBuffering == isPlaying
        if (isBuffering) {
            player_pause_play_holder_holder?.isVisible = false
            player_buffering?.isVisible = true
        } else {
            player_pause_play_holder_holder?.isVisible = true
            player_buffering?.isVisible = false

            if (wasPlaying != isPlaying) {
                player_pause_play?.setImageResource(if (isPlayingRightNow) R.drawable.play_to_pause else R.drawable.pause_to_play)
                val drawable = player_pause_play?.drawable

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
                    player_pause_play?.setImageResource(if (isPlayingRightNow) R.drawable.netflix_pause else R.drawable.netflix_play)
                }
            } else {
                player_pause_play?.setImageResource(if (isPlayingRightNow) R.drawable.netflix_pause else R.drawable.netflix_play)
            }
        }

        canEnterPipMode = isPlayingRightNow && hasPipModeSupport
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPIPMode) {
            activity?.let { act ->
                PlayerPipHelper.updatePIPModeActions(act, isPlayingRightNow)
            }
        }
    }

    private var pipReceiver: BroadcastReceiver? = null
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
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
                            )]
                        )
                    }
                }
                val filter = IntentFilter()
                filter.addAction(
                    ACTION_MEDIA_CONTROL
                )
                activity?.registerReceiver(pipReceiver, filter)
                val isPlaying = player.getIsPlaying()
                val isPlayingValue =
                    if (isPlaying) CSPlayerLoading.IsPlaying else CSPlayerLoading.IsPaused
                updateIsPlaying(Pair(isPlayingValue, isPlayingValue))
            } else {
                // Restore the full-screen UI.
                piphide?.isVisible = true
                exitedPipMode()
                pipReceiver?.let {
                    activity?.unregisterReceiver(it)
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

    open fun playerError(exception: Exception) {
        fun showToast(message: String, gotoNext: Boolean = false) {
            if (gotoNext && hasNextMirror()) {
                showToast(
                    activity,
                    message,
                    Toast.LENGTH_SHORT
                )
                nextMirror()
            } else {
                showToast(
                    activity,
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

    private fun playerUpdated(player: Any?) {
        if (player is ExoPlayer) {
            context?.let { ctx ->
                val mediaButtonReceiver = ComponentName(ctx, MediaButtonReceiver::class.java)
                MediaSessionCompat(ctx, "Player", mediaButtonReceiver, null).let { media ->
                    //media.setCallback(mMediaSessionCallback)
                    //media.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
                    val mediaSessionConnector = MediaSessionConnector(media)
                    mediaSessionConnector.setPlayer(player)
                    media.isActive = true
                    mMediaSessionCompat = media
                }
            }

            // Necessary for multiple combined videos
            player_view?.setShowMultiWindowTimeBar(true)
            player_view?.player = player
            player_view?.performClick()
        }
    }

    private var mediaSessionConnector: MediaSessionConnector? = null
    private var mMediaSessionCompat: MediaSessionCompat? = null

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


    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        resizeMode = getKey(RESIZE_MODE_KEY) ?: 0
        resize(resizeMode, false)

        player.releaseCallbacks()
        player.initCallbacks(
            playerUpdated = ::playerUpdated,
            updateIsPlaying = ::updateIsPlaying,
            playerError = ::playerError,
            requestAutoFocus = ::requestAudioFocus,
            nextEpisode = ::nextEpisode,
            prevEpisode = ::prevEpisode,
            playerPositionChanged = ::playerPositionChanged,
            playerDimensionsLoaded = ::playerDimensionsLoaded,
            requestedListeningPercentages = listOf(
                SKIP_OP_VIDEO_PERCENTAGE,
                PRELOAD_NEXT_EPISODE_PERCENTAGE,
                NEXT_WATCH_EPISODE_PERCENTAGE,
                UPDATE_SYNC_PROGRESS_PERCENTAGE,
            ),
            subtitlesUpdates = ::subtitlesChanged,
            embeddedSubtitlesFetched = ::embeddedSubtitlesFetched,
            onTracksInfoChanged = ::onTracksInfoChanged,
            onTimestampInvoked = ::onTimestamp,
            onTimestampSkipped = ::onTimestampSkipped
        )

        if (player is CS3IPlayer) {
            subView = player_view?.findViewById(R.id.exo_subtitles)
            subStyle = SubtitlesFragment.getCurrentSavedStyle()
            player.initSubtitles(subView, subtitle_holder, subStyle)

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

    fun resize(resize: PlayerResize, showToast: Boolean) {
        setKey(RESIZE_MODE_KEY, resize.ordinal)
        val type = when (resize) {
            PlayerResize.Fill -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            PlayerResize.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            PlayerResize.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
        player_view?.resizeMode = type

        if (showToast)
            showToast(activity, resize.nameRes, Toast.LENGTH_SHORT)
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
        return inflater.inflate(layout, container, false)
    }
}