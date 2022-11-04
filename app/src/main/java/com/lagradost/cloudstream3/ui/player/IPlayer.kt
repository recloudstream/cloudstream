package com.lagradost.cloudstream3.ui.player

import android.content.Context
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
    //IsDone,
}


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

    fun seekTime(time: Long)
    fun seekTo(time: Long)

    fun getSubtitleOffset(): Long // in ms
    fun setSubtitleOffset(offset: Long) // in ms

    fun initCallbacks(
        playerUpdated: (Any?) -> Unit,                              // attach player to view
        updateIsPlaying: ((Pair<CSPlayerLoading, CSPlayerLoading>) -> Unit)? = null, // (wasPlaying, isPlaying)
        requestAutoFocus: (() -> Unit)? = null,                     // current player starts, asking for all other programs to shut the fuck up
        playerError: ((Exception) -> Unit)? = null,                 // player error when rendering or misc, used to display toast or log
        playerDimensionsLoaded: ((Pair<Int, Int>) -> Unit)? = null, // (with, height), for UI
        requestedListeningPercentages: List<Int>? = null,           // this is used to request when the player should report back view percentage
        playerPositionChanged: ((Pair<Long, Long>) -> Unit)? = null,// (position, duration) this is used to update UI based of the current time
        nextEpisode: (() -> Unit)? = null,                          // this is used by the player to load the next episode
        prevEpisode: (() -> Unit)? = null,                          // this is used by the player to load the previous episode
        subtitlesUpdates: (() -> Unit)? = null,                     // callback from player to inform that subtitles have updated in some way
        embeddedSubtitlesFetched: ((List<SubtitleData>) -> Unit)? = null, // callback from player to give all embedded subtitles
        onTracksInfoChanged: (() -> Unit)? = null,                  // Callback when tracks are changed, used for UI changes
        onTimestampInvoked: ((EpisodeSkip.SkipStamp?) -> Unit)? = null, // Callback when timestamps appear, null when it should disappear
        onTimestampSkipped: ((EpisodeSkip.SkipStamp) -> Unit)? = null, // callback for when a chapter is skipped, aka when event is handled (or for future use when skip automatically ads/sponsor)
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

    fun handleEvent(event: CSPlayerEvent)

    fun onStop()
    fun onPause()
    fun onResume(context: Context)

    fun release()

    /** Get if player is actually used */
    fun isActive(): Boolean

    fun getVideoTracks(): CurrentTracks

    /** If no parameters are set it'll default to no set size, Specifying the id allows for track overrides to force the player to pick the quality. */
    fun setMaxVideoSize(width: Int = Int.MAX_VALUE, height: Int = Int.MAX_VALUE, id: String? = null)

    /** If no trackLanguage is set it'll default to first track. Specifying the id allows for track overrides as the language can be identical. */
    fun setPreferredAudioTrack(trackLanguage: String?, id: String? = null)
}