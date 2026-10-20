package com.lagradost.cloudstream3.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.OptIn
import androidx.core.graphics.createBitmap
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.NoSampleRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream4.AppSettings
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Renders ASS/SSA subtitles with libass (built from source, see src/main/cpp) in a view on top of
 * the video, like mpv and VLC do. Other subtitle formats keep using the normal pipeline.
 *
 * Handles external .ass/.ssa files (see [CustomDecoder]) and ASS tracks embedded in MKV together
 * with their attached fonts (see [UpdatedMatroskaExtractor]).
 *
 * libass is not thread safe, every native call goes through [nativeLock].
 * Rendering is done on its own thread and the main thread never takes the lock.
 */
@OptIn(UnstableApi::class)
class LibassSubtitleSupport private constructor(
    private val library: Long,
    private val renderer: Long
) : Player.Listener {
    companion object {
        private const val TAG = "LibassSupport"
        private const val COMMA = ','.code.toByte()
        private val trackPrefixRegex = Regex("""^\d+:""")

        // Avoid rendering at full 4K on TVs, the bitmap gets scaled instead
        private const val MAX_RENDER_PIXELS = 1920 * 1080

        // Used when the subtitle asks for a font that is not embedded
        private val DEFAULT_FONTS = listOf(
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/NotoSans-Regular.ttf",
            "/system/fonts/DroidSans.ttf",
        )

        fun isEnabled(context: Context): Boolean {
            return try {
                AppSettings(context).player.libassEnabled.get()
            } catch (t: Throwable) {
                logError(t)
                false
            }
        }

        /** Returns null if disabled in settings or libass could not be loaded */
        fun createIfEnabled(context: Context): LibassSubtitleSupport? {
            if (!isEnabled(context)) return null
            return try {
                val library = LibassNative.libraryInit()
                if (library == 0L) return null
                val defaultFont = DEFAULT_FONTS.firstOrNull { File(it).canRead() }
                val renderer = LibassNative.rendererInit(library, defaultFont)
                if (renderer == 0L) {
                    LibassNative.libraryDone(library)
                    return null
                }
                LibassSubtitleSupport(library, renderer)
            } catch (t: Throwable) {
                // UnsatisfiedLinkError if the native library is missing
                logError(t)
                null
            }
        }

        private fun String.stripMergeId(): String = this.replace(trackPrefixRegex, "")
    }

    private val nativeLock = Any()

    @Volatile
    private var released = false

    /** Runs [block] while holding the libass lock, returns [default] once released */
    private inline fun <T> withNative(default: T, block: () -> T): T {
        synchronized(nativeLock) {
            if (released) return default
            return try {
                block()
            } catch (t: Throwable) {
                logError(t)
                default
            }
        }
    }

    private var player: ExoPlayer? = null

    /** Format id (without the merge prefix) to native ASS_Track */
    private val tracks = ConcurrentHashMap<String, Long>()

    /** Tracks to free on the render thread */
    private val retiredTracks = ConcurrentLinkedQueue<Long>()

    @Volatile
    private var selectedTrackId: String? = null

    @Volatile
    private var videoTimeMs = 0L

    @Volatile
    private var videoWidth = 0

    @Volatile
    private var videoHeight = 0

    @Volatile
    private var viewWidth = 0

    @Volatile
    private var viewHeight = 0

    /** Only used to get the playback position, needed for animated subtitles */
    val timeRenderer: Renderer = object : NoSampleRenderer() {
        private var rendererOffsetUs = 0L

        override fun getName(): String = "LibassTimeRenderer"

        override fun onRendererOffsetChanged(offsetUs: Long) {
            rendererOffsetUs = offsetUs
        }

        override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
            // Same as CustomDecoder, cues are shifted by -subtitleOffset
            videoTimeMs = (positionUs - rendererOffsetUs) / 1000L + CustomDecoder.subtitleOffset
            if (tracks.isNotEmpty()) requestRender()
        }
    }

    /** Must be called before setMediaSource */
    fun attach(player: ExoPlayer) {
        this.player = player
        player.addListener(this)
    }

    // region Rendering

    private val renderThread = HandlerThread("LibassRender").apply { start() }
    private val renderHandler = Handler(renderThread.looper)
    private val renderQueued = AtomicBoolean(false)
    private val renderRunnable = Runnable { renderNow() }

    @Volatile
    private var forceRender = false

    private fun requestRender(force: Boolean = false) {
        if (released) return
        if (force) forceRender = true
        if (renderQueued.compareAndSet(false, true)) {
            renderHandler.post(renderRunnable)
        }
    }

    // Only accessed on the render thread
    private var frameWidth = 0
    private var frameHeight = 0
    private var storageWidth = 0
    private var storageHeight = 0
    private var lastTrack = 0L

    // One bitmap is shown, one might still be uploaded by the RenderThread and one is free
    private val buffers = arrayOfNulls<Bitmap>(3)
    private val dirtyRects = Array(buffers.size) { IntArray(4) }
    private var nextBuffer = 0

    /** What the overlay draws, null if there is nothing to show */
    @Volatile
    private var shownBitmap: Bitmap? = null

    private fun renderNow() {
        renderQueued.set(false)
        val force = forceRender
        forceRender = false
        if (released) return

        val (width, height) = renderSize(viewWidth, viewHeight)
        val id = selectedTrackId
        if (overlayView == null || width <= 0 || height <= 0 || id == null || !tracks.containsKey(id)) {
            show(null)
            lastTrack = 0L
            return
        }

        var redraw = force
        if (width != frameWidth || height != frameHeight) {
            frameWidth = width
            frameHeight = height
            for (i in buffers.indices) {
                buffers[i] = null
                dirtyRects[i].fill(0)
            }
            // Makes sure the new frame size is set below
            storageWidth = -1
        }
        if (storageWidth != videoWidth || storageHeight != videoHeight) {
            storageWidth = videoWidth
            storageHeight = videoHeight
            withNative(Unit) {
                LibassNative.setSize(renderer, width, height, storageWidth, storageHeight)
            }
            redraw = true
        }

        val index = nextBuffer
        val bitmap = buffers[index] ?: try {
            createBitmap(width, height).also { buffers[index] = it }
        } catch (t: Throwable) {
            logError(t)
            return
        }
        val dirty = dirtyRects[index]

        val result = withNative(-1) {
            // Looked up while holding the lock so it can not be freed in the meantime
            val track = tracks[id] ?: return@withNative -1
            if (track != lastTrack) {
                lastTrack = track
                redraw = true
            }
            LibassNative.renderFrame(renderer, track, videoTimeMs, bitmap, dirty, redraw)
        }

        when (result) {
            1 -> {
                nextBuffer = (index + 1) % buffers.size
                val isEmpty = dirty[2] <= dirty[0] || dirty[3] <= dirty[1]
                show(if (isEmpty) null else bitmap)
            }

            0 -> {} // nothing changed
            else -> {
                show(null)
                // Force a full redraw next time
                lastTrack = 0L
            }
        }
    }

    private fun renderSize(width: Int, height: Int): Pair<Int, Int> {
        val pixels = width.toLong() * height
        if (pixels <= MAX_RENDER_PIXELS) return width to height
        val scale = sqrt(MAX_RENDER_PIXELS.toDouble() / pixels)
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    private fun show(bitmap: Bitmap?) {
        if (bitmap == null && shownBitmap == null) return
        shownBitmap = bitmap
        overlayView?.postInvalidateOnAnimation()
    }

    private fun retireTrack(track: Long) {
        retiredTracks.add(track)
        renderHandler.post {
            withNative(Unit) {
                while (true) {
                    LibassNative.freeTrack(retiredTracks.poll() ?: break)
                }
            }
        }
    }

    // endregion

    // region Overlay

    /** Where the overlay goes, it is only added once an ASS track is found */
    @Volatile
    private var overlayPlayerView: androidx.media3.ui.PlayerView? = null

    @Volatile
    private var overlayView: LibassOverlayView? = null
    private var overlayObserver: Pair<ViewTreeObserver, ViewTreeObserver.OnPreDrawListener>? = null

    /**
     * The overlay is placed in exo_content_frame so it always matches the video area
     * (letterboxing, resize mode) and follows the pinch zoom.
     */
    fun attachOverlay(playerView: androidx.media3.ui.PlayerView?) {
        if (released || playerView == null) return
        overlayPlayerView = playerView
        if (tracks.isNotEmpty()) {
            requestOverlay()
        } else {
            runOnMain { detachOverlay() }
        }
    }

    // Not View.post, on API < 24 runnables posted to a detached view from another thread never run
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
        // Already in the right place
        if (overlayView != null && overlayView?.parent === container) return
        detachOverlay()
        try {
            val view = LibassOverlayView(container.context)
            container.addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            overlayView = view

            // Zoom is done by transforming the video surface, so copy that to the subtitles
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
        viewWidth = 0
        viewHeight = 0
    }

    @SuppressLint("ViewConstructor")
    private inner class LibassOverlayView(context: Context) : View(context) {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val destination = Rect()

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            viewWidth = w
            viewHeight = h
            requestRender(force = true)
        }

        override fun onDraw(canvas: Canvas) {
            val bitmap = shownBitmap ?: return
            destination.set(0, 0, width, height)
            canvas.drawBitmap(bitmap, null, destination, paint)
        }
    }

    // endregion

    // region Tracks

    /** libass draws the track, so no cues are needed */
    private object NoOpSubtitleParser : SubtitleParser {
        override fun parse(
            data: ByteArray,
            offset: Int,
            length: Int,
            outputOptions: SubtitleParser.OutputOptions,
            output: Consumer<CuesWithTiming>
        ) = Unit

        override fun getCueReplacementBehavior(): Int = Format.CUE_REPLACEMENT_BEHAVIOR_MERGE
    }

    /** Wraps the parser factory so embedded MKV ASS tracks go to libass */
    fun wrapParserFactory(delegate: SubtitleParser.Factory): SubtitleParser.Factory {
        return object : SubtitleParser.Factory {
            override fun supportsFormat(format: Format): Boolean =
                delegate.supportsFormat(format)

            override fun getCueReplacementBehavior(format: Format): Int =
                delegate.getCueReplacementBehavior(format)

            override fun create(format: Format): SubtitleParser {
                val isMkv = format.containerMimeType == MimeTypes.VIDEO_MATROSKA ||
                        format.containerMimeType == MimeTypes.VIDEO_WEBM
                // Only UpdatedMatroskaExtractor sends the dialogues to libass
                if (isMkv && format.sampleMimeType == MimeTypes.TEXT_SSA && registerEmbeddedTrack(format)) {
                    return NoOpSubtitleParser
                }
                return delegate.create(format)
            }
        }
    }

    fun hasTrack(id: String?): Boolean {
        if (id == null) return false
        return tracks.containsKey(id.stripMergeId())
    }

    private fun addTrack(id: String, track: Long) {
        if (tracks.putIfAbsent(id, track) != null) {
            // Another thread was faster
            retireTrack(track)
            return
        }
        requestOverlay()
        requestRender(force = true)
    }

    private fun registerEmbeddedTrack(format: Format): Boolean {
        if (released) return false
        val id = format.id?.stripMergeId() ?: return false
        if (tracks.containsKey(id)) return true
        // MatroskaExtractor puts the dialogue format first and the CodecPrivate (the ASS header) second
        val header = format.initializationData.getOrNull(1) ?: return false
        val track = withNative(0L) {
            LibassNative.newTrack(library).also { track ->
                if (track != 0L) LibassNative.processCodecPrivate(track, header)
            }
        }
        if (track == 0L) return false
        addTrack(id, track)
        return true
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
        val id = trackId?.stripMergeId() ?: return
        if (!tracks.containsKey(id)) return
        // libass wants the matroska format, so skip "Dialogue: start,end,"
        var commas = 0
        var start = -1
        val end = minOf(limit, data.size)
        for (i in 0 until end) {
            if (data[i] == COMMA && ++commas == 2) {
                start = i + 1
                break
            }
        }
        if (start < 0 || start >= end) return
        withNative(Unit) {
            val track = tracks[id] ?: return@withNative
            LibassNative.processChunk(
                track,
                data,
                start,
                end - start,
                timeUs / 1000L,
                durationUs / 1000L
            )
        }
    }

    /** Fonts attached to MKV files, most anime releases have them */
    fun addFont(name: String, data: ByteArray) {
        if (released) return
        withNative(Unit) { LibassNative.addFont(library, name, data) }
    }

    /**
     * Loads a whole external .ass/.ssa file.
     * @param data the subtitle file in UTF-8
     * @return true if libass renders this subtitle
     */
    fun loadFullSubtitle(format: Format, data: ByteArray): Boolean {
        if (released) return false
        // The decoder gets the merged format id ("1:id")
        val id = format.id?.stripMergeId() ?: return false
        if (tracks.containsKey(id)) return true
        // Big typeset files can take a while, but this only blocks the render thread
        val track = withNative(0L) { LibassNative.readMemory(library, data) }
        if (track == 0L) return false
        addTrack(id, track)
        Log.i(TAG, "Loaded external ASS subtitle with libass: $id")
        return true
    }

    // endregion

    // region Player.Listener

    override fun onTracksChanged(tracks: Tracks) {
        if (released) return
        selectedTrackId = tracks.groups.firstNotNullOfOrNull { group ->
            if (group.type != C.TRACK_TYPE_TEXT || !group.isSelected) return@firstNotNullOfOrNull null
            (0 until group.length).firstOrNull { group.isTrackSelected(it) }
                ?.let { group.getTrackFormat(it).id?.stripMergeId() }
        }
        requestRender(force = true)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (released) return
        for ((id, track) in tracks) {
            if (tracks.remove(id, track)) retireTrack(track)
        }
        requestRender(force = true)
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        if (released) return
        videoWidth = videoSize.width
        videoHeight = videoSize.height
        requestRender(force = true)
    }

    // endregion

    /** Call on the main thread after the player has been released */
    fun release() {
        if (released) return
        released = true
        uiHandler.removeCallbacksAndMessages(null)
        detachOverlay()
        overlayPlayerView = null
        shownBitmap = null
        player?.removeListener(this)
        player = null
        renderHandler.removeCallbacksAndMessages(null)
        renderHandler.post {
            // withNative does nothing once released
            synchronized(nativeLock) {
                try {
                    tracks.values.forEach { LibassNative.freeTrack(it) }
                    tracks.clear()
                    while (true) {
                        LibassNative.freeTrack(retiredTracks.poll() ?: break)
                    }
                    LibassNative.rendererDone(renderer)
                    LibassNative.libraryDone(library)
                } catch (t: Throwable) {
                    logError(t)
                }
            }
        }
        renderThread.quitSafely()
    }
}
