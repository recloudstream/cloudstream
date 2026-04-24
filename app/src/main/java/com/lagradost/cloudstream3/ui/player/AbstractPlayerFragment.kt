package com.lagradost.cloudstream3.ui.player

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.SubtitleView
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.BaseFragment

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

@OptIn(UnstableApi::class)
abstract class AbstractPlayerFragment<T : ViewBinding>(
    bindingCreator: BindingCreator<T>
) : BaseFragment<T>(bindingCreator), PlayerView.Callbacks {

    // Stored pre-initialization so subclasses can set them before onBindingCreated.
    private var _player: IPlayer = CS3IPlayer()

    /** The shared [PlayerView] host that owns all player state and view references. */
    protected var playerHostView: PlayerView? = null

    var player: IPlayer
        get() = playerHostView?.player ?: _player
        set(value) {
            _player = value
            playerHostView?.player = value
        }

    var subView: SubtitleView?
        get() = playerHostView?.subView
        set(value) { playerHostView?.subView = value }

    protected open var hasPipModeSupport: Boolean
        get() = playerHostView?.hasPipModeSupport ?: true
        set(value) { playerHostView?.hasPipModeSupport = value }

    var playerPausePlay: ImageView?
        get() = playerHostView?.playerPausePlay
        set(value) { playerHostView?.playerPausePlay = value }

    /** The underlying [androidx.media3.ui.PlayerView] widget (named to avoid conflict with our [PlayerView]). */
    var playerView: androidx.media3.ui.PlayerView?
        get() = playerHostView?.exoPlayerView
        set(value) { playerHostView?.exoPlayerView = value }

    var currentPlayerStatus: CSPlayerLoading
        get() = playerHostView?.currentPlayerStatus ?: CSPlayerLoading.IsBuffering
        set(value) { playerHostView?.currentPlayerStatus = value }

    protected var mMediaSession: MediaSession?
        get() = playerHostView?.mMediaSession
        set(value) { playerHostView?.mMediaSession = value }

    // No-op callbacks (nextEpisode, prevEpisode, etc.) are intentionally left as
    // open so subclasses can override only what they need.  The ones below throw
    // to make it obvious when an implementation is missing.

    override fun nextEpisode() = throw NotImplementedError()
    override fun prevEpisode() = throw NotImplementedError()
    override fun playerPositionChanged(position: Long, duration: Long) = throw NotImplementedError()
    override fun playerDimensionsLoaded(width: Int, height: Int) = throw NotImplementedError()
    override fun subtitlesChanged() = throw NotImplementedError()
    override fun embeddedSubtitlesFetched(subtitles: List<SubtitleData>) = throw NotImplementedError()
    override fun onTracksInfoChanged() = throw NotImplementedError()
    override fun exitedPipMode() = throw NotImplementedError()
    override fun hasNextMirror(): Boolean = throw NotImplementedError()
    override fun nextMirror() = throw NotImplementedError()

    /** Delegates to [PlayerView.playerError] by default; override to customize. */
    override fun playerError(exception: Throwable) {
        playerHostView?.playerError(exception)
    }

    /** Player fragments don't need system-bar padding adjustment by default. */
    override fun fixLayout(view: View) = Unit

    override fun onBindingCreated(binding: T, savedInstanceState: Bundle?) {
        val ctx = context ?: return
        playerHostView = PlayerView(ctx)
        playerHostView?.player = _player
        playerHostView?.hasPipModeSupport = hasPipModeSupport
        playerHostView?.callbacks = this
        playerHostView?.bindViews(binding.root)
        playerHostView?.initialize()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        playerHostView?.onPictureInPictureModeChanged(isInPictureInPictureMode, activity)
    }

    override fun onDestroy() {
        playerHostView?.release()
        super.onDestroy()
    }

    override fun onPause() {
        playerHostView?.releaseKeyEventListener()
        super.onPause()
    }

    override fun onStop() {
        playerHostView?.onStop()
        super.onStop()
    }

    override fun onResume() {
        context?.let { ctx ->
            playerHostView?.onResume(ctx)
        }
        super.onResume()
    }

    fun nextResize() {
        playerHostView?.nextResize()
    }

    open fun resize(resize: PlayerResize, showToast: Boolean) {
        playerHostView?.resize(resize, showToast)
    }
}
