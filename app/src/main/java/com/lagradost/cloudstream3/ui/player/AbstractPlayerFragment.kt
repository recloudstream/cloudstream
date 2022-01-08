package com.lagradost.cloudstream3.ui.player

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.AnimatedVectorDrawable
import android.media.metrics.PlaybackErrorEvent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
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
import com.lagradost.cloudstream3.utils.UIHelper
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
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
const val NEXT_WATCH_EPISODE_PERCENTAGE = 95

abstract class AbstractPlayerFragment(
    @LayoutRes val layout: Int,
    val player: IPlayer = CS3IPlayer()
) : Fragment() {
    var resizeMode: Int = 0
    var subStyle: SaveCaptionStyle? = null
    var subView: SubtitleView? = null
    var isBuffering = true

    open fun nextEpisode() {
        throw NotImplementedError()
    }

    open fun prevEpisode() {
        throw NotImplementedError()
    }

    open fun playerPositionChanged(posDur: Pair<Long, Long>) {
        throw NotImplementedError()
    }

    open fun playerDimensionsLoaded(widthHeight : Pair<Int, Int>) {
        throw NotImplementedError()
    }

    private fun updateIsPlaying(playing: Pair<CSPlayerLoading, CSPlayerLoading>) {
        val (wasPlaying, isPlaying) = playing
        val isPlayingRightNow = CSPlayerLoading.IsPlaying == isPlaying

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
                if(!startedAnimation) {
                    player_pause_play?.setImageResource(if (isPlayingRightNow) R.drawable.netflix_pause else R.drawable.netflix_play)
                }
            } else {
                player_pause_play?.setImageResource(if (isPlayingRightNow) R.drawable.netflix_pause else R.drawable.netflix_play)
            }
        }

        canEnterPipMode = isPlayingRightNow
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
                player_holder.alpha = 0f
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
                player_holder.alpha = 1f
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

    open fun nextMirror() {
        throw NotImplementedError()
    }

    private fun playerError(exception: Exception) {
        when (exception) {
            is PlaybackException -> {
                val msg = exception.message ?: ""
                val errorName = exception.errorCodeName
                when (val code = exception.errorCode) {
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, PlaybackException.ERROR_CODE_IO_NO_PERMISSION, PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                        showToast(
                            activity,
                            "${getString(R.string.source_error)}\n$errorName ($code)\n$msg",
                            Toast.LENGTH_SHORT
                        )
                        nextMirror()
                    }
                    PlaybackException.ERROR_CODE_REMOTE_ERROR, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, PlaybackException.ERROR_CODE_TIMEOUT, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> {
                        showToast(
                            activity,
                            "${getString(R.string.remote_error)}\n$errorName ($code)\n$msg",
                            Toast.LENGTH_SHORT
                        )
                        nextMirror()
                    }
                    PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackErrorEvent.ERROR_AUDIO_TRACK_INIT_FAILED, PlaybackErrorEvent.ERROR_AUDIO_TRACK_OTHER, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> {
                        showToast(
                            activity,
                            "${getString(R.string.render_error)}\n$errorName ($code)\n$msg",
                            Toast.LENGTH_SHORT
                        )
                        nextMirror()
                    }
                    else -> {
                        showToast(
                            activity,
                            "${getString(R.string.unexpected_error)}\n$errorName ($code)\n$msg",
                            Toast.LENGTH_SHORT
                        )
                    }
                }
            }
            else -> {
                showToast(activity, exception.message, Toast.LENGTH_SHORT)
            }
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.requestLocalAudioFocus(AppUtils.getFocusRequest())
        }
    }

    private fun onSubStyleChanged(style: SaveCaptionStyle) {
        if (player is CS3IPlayer) {
            player.updateSubtitleStyle(style)
        }
    }

    private fun playerUpdated(player: Any?) {
        if (player is ExoPlayer) {
            player_view?.player = player
            player_view?.performClick()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        resizeMode = getKey(RESIZE_MODE_KEY) ?: 0
        resize(resizeMode, false)

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
            )
        )

        if (player is CS3IPlayer) {
            subView = player_view?.findViewById(R.id.exo_subtitles)
            subStyle = SubtitlesFragment.getCurrentSavedStyle()
            player.initSubtitles(subView, subtitle_holder, subStyle)
            SubtitlesFragment.applyStyleEvent += ::onSubStyleChanged
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
        SubtitlesFragment.applyStyleEvent -= ::onSubStyleChanged

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
            PlayerResize.Fill -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            PlayerResize.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            PlayerResize.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
        player_view?.resizeMode = type

        exo_play?.setOnClickListener {
            player.handleEvent(CSPlayerEvent.Play)
        }

        exo_pause?.setOnClickListener {
            player.handleEvent(CSPlayerEvent.Pause)
        }

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