package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.NoSampleRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.extractor.text.SubtitleParser
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream4.AppSettings
import io.github.peerless2012.ass.AssTrack
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.AssHandlerConfig
import io.github.peerless2012.ass.media.parser.AssNoOpSubtitleParser
import io.github.peerless2012.ass.media.type.AssRenderType
import io.github.peerless2012.ass.media.widget.AssSubtitleView

/**
 * Full ASS/SSA rendering through libass (https://github.com/libass/libass).
 *
 * Instead, ASS tracks are fed into libass and rendered on top of the video by an overlay view,
 * exactly like mpv/VLC do.
 *
 * Supported sources:
 * - External .ass/.ssa files (URL, downloaded or OpenSubtitles), see [CustomDecoder].
 * - ASS tracks embedded in MKV (including attached fonts), see [UpdatedMatroskaExtractor].
 *
 * Any other subtitle format keeps using the normal CloudStream pipeline and styling.
 */
@OptIn(UnstableApi::class)
class LibassSubtitleSupport private constructor(
    private val assHandler: AssHandler
) : Player.Listener {
    companion object {
        private const val TAG = "LibassSupport"
        private const val COMMA = ','.code.toByte()
        private val trackPrefixRegex = Regex("""^\d+:""")

        fun isEnabled(context: Context): Boolean {
            return try {
                AppSettings(context).player.libassEnabled.get()
            } catch (t: Throwable) {
                logError(t)
                false
            }
        }

        /** Returns null if disabled in settings or libass could not be set up */
        fun createIfEnabled(context: Context): LibassSubtitleSupport? {
            if (!isEnabled(context)) return null
            return try {
                LibassSubtitleSupport(
                    AssHandler(
                        // OpenGL overlay: does not block the UI thread and supports animations
                        AssRenderType.OVERLAY_OPEN_GL,
                        // Avoid rendering at full 4K on TVs
                        AssHandlerConfig(maxRenderPixels = 1920 * 1080)
                    )
                )
            } catch (t: Throwable) {
                logError(t)
                null
            }
        }

        private fun String.stripMergeId(): String = this.replace(trackPrefixRegex, "")
    }

    private var player: ExoPlayer? = null
    private var mainHandler: Handler? = null
    private var overlayView: AssSubtitleView? = null
    private var overlayObserver: Pair<ViewTreeObserver, ViewTreeObserver.OnPreDrawListener>? = null

    /** Unprefixed format ids -> registered libass tracks */
    private val tracks = HashMap<String, AssTrack>()

    /** Unprefixed format ids of external subtitles where the whole file was loaded into libass */
    private val fullFileIds = HashSet<String>()

    @Volatile
    private var released = false

    /** Renderer only used to know the current playback position, needed for animations */
    val timeRenderer: Renderer = object : NoSampleRenderer() {
        private var rendererOffsetUs = 0L

        override fun getName(): String = "LibassTimeRenderer"

        override fun onRendererOffsetChanged(offsetUs: Long) {
            rendererOffsetUs = offsetUs
        }

        override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
            if (released) return
            // Same semantic as CustomDecoder: cues are shifted by -subtitleOffset
            assHandler.videoTime =
                positionUs - rendererOffsetUs + CustomDecoder.subtitleOffset * 1000L
        }
    }

    /** Must be called before setMediaSource */
    fun attach(player: ExoPlayer) {
        this.player = player
        mainHandler = Handler(player.applicationLooper)
        // We do not call assHandler.init(player) as we forward the events ourselves,
        // this allows us to also handle external ASS files with a wrong mimetype.
        player.addListener(this)
    }

    /** Where the overlay should be placed, it is only created once an ASS track is found */
    @Volatile
    private var overlayPlayerView: androidx.media3.ui.PlayerView? = null

    /**
     * Sets the view where the libass overlay is drawn. It is placed inside exo_content_frame so it
     * always matches the video area (letterboxing, resize modes), and it follows the pinch zoom.
     * The overlay (an OpenGL surface) is only created when the video actually has ASS subtitles.
     */
    fun attachOverlay(playerView: androidx.media3.ui.PlayerView?) {
        if (released || playerView == null) return
        overlayPlayerView = playerView
        val hasTracks = synchronized(this) { tracks.isNotEmpty() }
        if (hasTracks) {
            requestOverlay()
        } else {
            runOnMain { detachOverlay() }
        }
    }

    /**
     * Not View.post: on API < 24 a runnable posted from a background thread to a detached view
     * is stored in a thread local queue and never runs.
     */
    private val uiHandler = Handler(Looper.getMainLooper())

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else uiHandler.post(action)
    }

    private fun requestOverlay() {
        val playerView = overlayPlayerView ?: return
        runOnMain { createOverlay(playerView) }
    }

    private fun createOverlay(playerView: androidx.media3.ui.PlayerView) {
        if (released || overlayPlayerView !== playerView) return
        val container =
            playerView.findViewById<ViewGroup>(androidx.media3.ui.R.id.exo_content_frame) ?: return
        // Already attached to the correct view
        if (overlayView != null && overlayView?.parent === container) return
        detachOverlay()
        try {
            val view = AssSubtitleView(container.context, assHandler)
            container.addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            overlayView = view

            // CloudStream zooms by transforming the video surface, mirror that on the subtitles
            val listener = ViewTreeObserver.OnPreDrawListener {
                playerView.videoSurfaceView?.let { surface -> view.copyTransformFrom(surface) }
                true
            }
            val observer = container.viewTreeObserver
            observer.addOnPreDrawListener(listener)
            overlayObserver = observer to listener
        } catch (t: Throwable) {
            logError(t)
        }
    }

    private fun View.copyTransformFrom(other: View) {
        if (scaleX != other.scaleX) scaleX = other.scaleX
        if (scaleY != other.scaleY) scaleY = other.scaleY
        if (translationX != other.translationX) translationX = other.translationX
        if (translationY != other.translationY) translationY = other.translationY
    }

    private fun detachOverlay() {
        overlayObserver?.let { (observer, listener) ->
            try {
                if (observer.isAlive) observer.removeOnPreDrawListener(listener)
            } catch (t: Throwable) {
                logError(t)
            }
        }
        overlayObserver = null
        overlayView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        overlayView = null
    }

    /** Wraps the parser factory so embedded MKV ASS tracks are handled by libass */
    fun wrapParserFactory(delegate: SubtitleParser.Factory): SubtitleParser.Factory {
        return object : SubtitleParser.Factory {
            override fun supportsFormat(format: Format): Boolean =
                delegate.supportsFormat(format)

            override fun getCueReplacementBehavior(format: Format): Int =
                delegate.getCueReplacementBehavior(format)

            override fun create(format: Format): SubtitleParser {
                val isMkv = format.containerMimeType == MimeTypes.VIDEO_MATROSKA ||
                        format.containerMimeType == MimeTypes.VIDEO_WEBM
                // Only our UpdatedMatroskaExtractor feeds the dialogues to libass
                if (isMkv && format.sampleMimeType == MimeTypes.TEXT_SSA && format.initializationData.size >= 2) {
                    if (registerTrack(format) != null) {
                        // libass draws it, do not output any text cues
                        return AssNoOpSubtitleParser()
                    }
                }
                return delegate.create(format)
            }
        }
    }

    fun hasTrack(id: String?): Boolean {
        if (id == null) return false
        synchronized(this) {
            return tracks.containsKey(id.stripMergeId())
        }
    }

    private fun registerTrack(format: Format): AssTrack? {
        if (released) return null
        val id = format.id ?: return null
        synchronized(this) {
            tracks[id]?.let { return it }
            return try {
                assHandler.createTrack(format).also {
                    tracks[id] = it
                    // First ASS track, show the overlay
                    requestOverlay()
                }
            } catch (t: Throwable) {
                logError(t)
                null
            }
        }
    }

    /**
     * Called by [UpdatedMatroskaExtractor] for every ASS sample.
     * @param data "Dialogue: 0:00:00:00,H:MM:SS:CC,ReadOrder,Layer,Style,Name,...,Text"
     */
    fun readEmbeddedDialogue(
        trackId: String?,
        timeUs: Long,
        durationUs: Long,
        data: ByteArray,
        limit: Int
    ) {
        if (released || timeUs == C.TIME_UNSET || durationUs == C.TIME_UNSET) return
        if (!hasTrack(trackId)) return
        // Skip the "Dialogue: start,end," prefix, libass wants the matroska chunk format
        var commas = 0
        var start = -1
        for (i in 0 until minOf(limit, data.size)) {
            if (data[i] == COMMA && ++commas == 2) {
                start = i + 1
                break
            }
        }
        if (start < 0 || start >= limit) return
        try {
            assHandler.readTrackDialogue(
                trackId = trackId,
                start = timeUs / 1000L,
                duration = durationUs / 1000L,
                data = data,
                offset = start,
                length = limit - start
            )
        } catch (t: Throwable) {
            logError(t)
        }
    }

    /** Fonts attached in MKV files, used by most anime releases */
    fun addFont(name: String, data: ByteArray) {
        if (released) return
        try {
            assHandler.addFont(name, data)
        } catch (t: Throwable) {
            logError(t)
        }
    }

    /**
     * Loads an entire external .ass/.ssa file into libass.
     * @param data the full subtitle file, UTF-8 encoded
     * @return true if libass is now responsible for rendering this subtitle
     */
    fun loadFullSubtitle(format: Format, data: ByteArray): Boolean {
        if (released) return false
        // The decoder gets the merged format ("1:id"), while AssHandler matches the selected
        // track using the id after the colon, so the track must be registered without prefix
        val id = format.id?.stripMergeId() ?: return false
        if (synchronized(this) { fullFileIds.contains(id) }) return true
        val track = registerTrack(
            // External files have their header inside the data
            format.buildUpon().setId(id).setInitializationData(null).build()
        ) ?: return false
        try {
            // Not inside synchronized: big typeset files can take a while to parse and
            // onTracksChanged (main thread) also takes this lock
            track.readBuffer(data, 0, data.size)
        } catch (t: Throwable) {
            logError(t)
            return false
        }
        synchronized(this) { fullFileIds.add(id) }
        Log.i(TAG, "Loaded external ASS subtitle with libass: $id")
        // The track might have a non-SSA mimetype (eg. .ass served without extension),
        // so refresh the selection now that we know it is an ASS file.
        mainHandler?.post {
            val currentPlayer = player ?: return@post
            if (!released) onTracksChanged(currentPlayer.currentTracks)
        }
        return true
    }

    /**
     * AssHandler only recognizes tracks with an SSA mimetype, so external files that we know are
     * ASS (because their content was loaded) get their mimetype corrected here.
     */
    private fun normalizeTracks(tracks: Tracks): Tracks {
        val fullIds = synchronized(this) { fullFileIds.toSet() }
        if (fullIds.isEmpty()) return tracks
        var changed = false
        val groups = tracks.groups.map { group ->
            if (group.type != C.TRACK_TYPE_TEXT || !group.isSelected) return@map group
            val needsFix = (0 until group.length).any { i ->
                val format = group.getTrackFormat(i)
                format.sampleMimeType != MimeTypes.TEXT_SSA &&
                        format.id?.let { id -> fullIds.contains(id.stripMergeId()) } == true
            }
            if (!needsFix) return@map group
            changed = true
            val formats = Array(group.length) { i ->
                group.getTrackFormat(i).buildUpon().setSampleMimeType(MimeTypes.TEXT_SSA).build()
            }
            Tracks.Group(
                TrackGroup(group.mediaTrackGroup.id, *formats),
                false,
                IntArray(group.length) { C.FORMAT_HANDLED },
                BooleanArray(group.length) { i -> group.isTrackSelected(i) }
            )
        }
        return if (changed) Tracks(groups) else tracks
    }

    override fun onTracksChanged(tracks: Tracks) {
        if (released) return
        try {
            assHandler.onTracksChanged(normalizeTracks(tracks))
        } catch (t: Throwable) {
            logError(t)
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (released) return
        synchronized(this) {
            tracks.clear()
            fullFileIds.clear()
        }
        assHandler.onMediaItemTransition(mediaItem, reason)
    }

    override fun onSurfaceSizeChanged(width: Int, height: Int) {
        if (released) return
        assHandler.onSurfaceSizeChanged(width, height)
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        if (released) return
        assHandler.onVideoSizeChanged(videoSize)
    }

    /** Call on the main thread after the player has been released */
    fun release() {
        if (released) return
        released = true
        uiHandler.removeCallbacksAndMessages(null)
        detachOverlay()
        overlayPlayerView = null
        player?.removeListener(this)
        player = null
        mainHandler = null
        synchronized(this) {
            tracks.clear()
            fullFileIds.clear()
        }
        try {
            assHandler.release()
        } catch (t: Throwable) {
            logError(t)
        }
    }
}
