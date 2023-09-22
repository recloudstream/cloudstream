package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.util.Rational
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.utils.EpisodeSkip
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorUri

enum class PlayerEventType(val value: Int) {
    //Stop(-1),
    Pause(0),
    Play(1),
    SeekForward(2),
    SeekBack(3),

    SkipCurrentChapter(4),
    NextEpisode(5),
    PrevEpisode(6),
    PlayPauseToggle(7),
    ToggleMute(8),
    Lock(9),
    ToggleHide(10),
    ShowSpeed(11),
    ShowMirrors(12),
    Resize(13),
    SearchSubtitlesOnline(14),
    SkipOp(15),
}

enum class CSPlayerEvent(val value: Int) {
    Pause(0),
    Play(1),
    SeekForward(2),
    SeekBack(3),

    SkipCurrentChapter(4),
    NextEpisode(5),
    PrevEpisode(6),
    PlayPauseToggle(7),
    ToggleMute(8),
}

enum class CSPlayerLoading {
    IsPaused,
    IsPlaying,
    IsBuffering,
}

enum class PlayerEventSource {
    /** This event was invoked from the user pressing some button or selecting something */
    UI,

    /** This event was invoked automatically */
    Player,

    /** This event was invoked from a external sync tool like WatchTogether */
    Sync,
}

abstract class PlayerEvent {
    abstract val source: PlayerEventSource
}

/** this is used to update UI based of the current time,
 * using requestedListeningPercentages as well as saving time */
data class PositionEvent(
    override val source: PlayerEventSource,
    val fromMs: Long,
    val toMs: Long,
    /** duration of the entire video */
    val durationMs: Long,
) : PlayerEvent() {
    /** how many ms (+-) we have skipped */
    val seekMs : Long get() = toMs - fromMs
}

/** player error when rendering or misc, used to display toast or log */
data class ErrorEvent(
    val error: Throwable,
    override val source: PlayerEventSource = PlayerEventSource.Player,
) : PlayerEvent()

/** Event when timestamps appear, null when it should disappear */
data class TimestampInvokedEvent(
    val timestamp: EpisodeSkip.SkipStamp,
    override val source: PlayerEventSource = PlayerEventSource.Player,
) : PlayerEvent()

/** Event for when a chapter is skipped, aka when event is handled (or for future use when skip automatically ads/sponsor) */
data class TimestampSkippedEvent(
    val timestamp: EpisodeSkip.SkipStamp,
    override val source: PlayerEventSource = PlayerEventSource.Player,
) : PlayerEvent()

/** this is used by the player to load the next or prev episode */
data class EpisodeSeekEvent(
    /** -1 = prev, 1 = next, will never be 0, atm the user cant seek more than +-1 */
    val offset: Int,
    override val source: PlayerEventSource = PlayerEventSource.Player,
) : PlayerEvent() {
    init {
        assert(offset != 0)
    }
}

/** Event when the video is resized aka changed resolution or mirror */
data class ResizedEvent(
    val height: Int,
    val width: Int,
    override val source: PlayerEventSource = PlayerEventSource.Player,
) : PlayerEvent()

/** Event when the player status update, along with the previous status (for animation)*/
data class StatusEvent(
    val wasPlaying: CSPlayerLoading,
    val isPlaying: CSPlayerLoading,
    override val source: PlayerEventSource = PlayerEventSource.Player
) : PlayerEvent()

/** Event when tracks are changed, used for UI changes */
data class TracksChangedEvent(
    override val source: PlayerEventSource = PlayerEventSource.Player
) : PlayerEvent()

/** Event from player to give all embedded subtitles */
data class EmbeddedSubtitlesFetchedEvent(
    val tracks: List<SubtitleData>,
    override val source: PlayerEventSource = PlayerEventSource.Player
) : PlayerEvent()

/** on attach player to view */
data class PlayerAttachedEvent(
    val player: Any?,
    override val source: PlayerEventSource = PlayerEventSource.Player
) : PlayerEvent()

/** Event from player to inform that subtitles have updated in some way */
data class SubtitlesUpdatedEvent(
    override val source: PlayerEventSource = PlayerEventSource.Player
) : PlayerEvent()

/** current player starts, asking for all other programs to shut the fuck up */
data class RequestAudioFocusEvent(
    override val source: PlayerEventSource = PlayerEventSource.Player
) : PlayerEvent()

/** Pause event, separate from StatusEvent */
data class PauseEvent(
    override val source: PlayerEventSource = PlayerEventSource.Player
) : PlayerEvent()

/** Play event, separate from StatusEvent */
data class PlayEvent(
    override val source: PlayerEventSource = PlayerEventSource.Player
) : PlayerEvent()

/** Event when the player video has ended, up to the settings on what to do when that happens */
data class VideoEndedEvent(
    override val source: PlayerEventSource = PlayerEventSource.Player
) : PlayerEvent()

interface Track {
    /**
     * Unique among the class, used to check which track is used.
     * VideoTrack and AudioTrack can have the same id
     **/
    val id: String?
    val label: String?

    //    val isCurrentlyPlaying: Boolean
    val language: String?
}

data class VideoTrack(
    override val id: String?,
    override val label: String?,
//    override val isCurrentlyPlaying: Boolean,
    override val language: String?,
    val width: Int?,
    val height: Int?,
) : Track

data class AudioTrack(
    override val id: String?,
    override val label: String?,
//    override val isCurrentlyPlaying: Boolean,
    override val language: String?,
) : Track

data class CurrentTracks(
    val currentVideoTrack: VideoTrack?,
    val currentAudioTrack: AudioTrack?,
    val allVideoTracks: List<VideoTrack>,
    val allAudioTracks: List<AudioTrack>,
)

class InvalidFileException(msg: String) : Exception(msg)

//http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4
const val STATE_RESUME_WINDOW = "resumeWindow"
const val STATE_RESUME_POSITION = "resumePosition"
const val STATE_PLAYER_FULLSCREEN = "playerFullscreen"
const val STATE_PLAYER_PLAYING = "playerOnPlay"
const val ACTION_MEDIA_CONTROL = "media_control"
const val EXTRA_CONTROL_TYPE = "control_type"
const val PLAYBACK_SPEED = "playback_speed"
const val RESIZE_MODE_KEY = "resize_mode" // Last used resize mode
const val PLAYBACK_SPEED_KEY = "playback_speed" // Last used playback speed
const val PREFERRED_SUBS_KEY = "preferred_subtitles" // Last used resize mode
//const val PLAYBACK_FASTFORWARD = "playback_fastforward" // Last used resize mode

/** Abstract Exoplayer logic, can be expanded to other players */
interface IPlayer {
    fun getPlaybackSpeed(): Float
    fun setPlaybackSpeed(speed: Float)

    fun getIsPlaying(): Boolean
    fun getDuration(): Long?
    fun getPosition(): Long?

    fun seekTime(time: Long, source: PlayerEventSource = PlayerEventSource.UI)
    fun seekTo(time: Long, source: PlayerEventSource = PlayerEventSource.UI)

    fun getSubtitleOffset(): Long // in ms
    fun setSubtitleOffset(offset: Long) // in ms

    fun initCallbacks(
        eventHandler: ((PlayerEvent) -> Unit),
        /** this is used to request when the player should report back view percentage */
        requestedListeningPercentages: List<Int>? = null,
    )

    fun releaseCallbacks()

    fun updateSubtitleStyle(style: SaveCaptionStyle)
    fun saveData()

    fun addTimeStamps(timeStamps: List<EpisodeSkip.SkipStamp>)

    fun loadPlayer(
        context: Context,
        sameEpisode: Boolean,
        link: ExtractorLink? = null,
        data: ExtractorUri? = null,
        startPosition: Long? = null,
        subtitles: Set<SubtitleData>,
        subtitle: SubtitleData?,
        autoPlay: Boolean? = true
    )

    fun reloadPlayer(context: Context)

    fun setActiveSubtitles(subtitles: Set<SubtitleData>)
    fun setPreferredSubtitles(subtitle: SubtitleData?): Boolean // returns true if the player requires a reload, null for nothing
    fun getCurrentPreferredSubtitle(): SubtitleData?

    fun handleEvent(event: CSPlayerEvent, source: PlayerEventSource = PlayerEventSource.UI)

    fun onStop()
    fun onPause()
    fun onResume(context: Context)

    fun release()

    /** Get if player is actually used */
    fun isActive(): Boolean

    fun getVideoTracks(): CurrentTracks

    /**
     * Original video aspect ratio used for PiP mode
     *
     * Set using: Width, Height.
     * Example: Rational(16, 9)
     *
     * If null will default to set no aspect ratio.
     *
     * PiP functions calling this needs to coerce this value between 0.418410 and 2.390000
     * to prevent crashes.
     */
    fun getAspectRatio(): Rational?

    /** If no parameters are set it'll default to no set size, Specifying the id allows for track overrides to force the player to pick the quality. */
    fun setMaxVideoSize(width: Int = Int.MAX_VALUE, height: Int = Int.MAX_VALUE, id: String? = null)

    /** If no trackLanguage is set it'll default to first track. Specifying the id allows for track overrides as the language can be identical. */
    fun setPreferredAudioTrack(trackLanguage: String?, id: String? = null)
}