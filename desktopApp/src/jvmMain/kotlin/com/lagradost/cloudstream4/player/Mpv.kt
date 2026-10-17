package com.lagradost.cloudstream4.player

import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.awt.Canvas
import java.awt.Color
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.io.File
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.SwingUtilities
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import kotlin.math.log2

/** The libmpv functions the player uses, see mpv/client.h */
@Suppress("FunctionName")
internal interface MpvLibrary : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_set_option_string(ctx: Pointer, name: String, value: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int
    fun mpv_free(data: Pointer)
    fun mpv_error_string(error: Int): String
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer
    fun mpv_request_log_messages(ctx: Pointer, minLevel: String): Int
    fun mpv_terminate_destroy(ctx: Pointer)
}

/** The Windows functions that show only part of the video, see MpvPlayer.place */
@Suppress("FunctionName")
private class Win32(val user: User, val gdi: Gdi) {
    interface User : StdCallLibrary {
        fun SetWindowRgn(hwnd: Pointer, region: Pointer?, redraw: Boolean): Int
    }

    interface Gdi : StdCallLibrary {
        fun CreateRectRgn(left: Int, top: Int, right: Int, bottom: Int): Pointer?
    }

    companion object {
        val instance: Win32? by lazy {
            if (!System.getProperty("os.name").startsWith("Windows")) return@lazy null
            runCatching { Win32(Native.load("user32", User::class.java), Native.load("gdi32", Gdi::class.java)) }
                .onFailure { println("ERROR Mpv: user32 or gdi32 could not be loaded: $it") }
                .getOrNull()
        }
    }
}

/** Finds and loads libmpv-2.dll once */
object Mpv {
    private const val FILE = "libmpv-2.dll"

    /** Why libmpv could not be loaded, or null when it loaded */
    var loadError: String? = null
        private set

    internal val library: MpvLibrary? by lazy {
        // The installed app keeps it in its resources folder. When run from Gradle it is in the build folder
        val candidates = listOfNotNull(
            System.getProperty("compose.application.resources.dir")?.let { File(it, FILE) },
            File("build/libmpv/windows", FILE),
            File("desktopApp/build/libmpv/windows", FILE),
        )
        val file = candidates.firstOrNull { it.isFile }
        if (file == null) {
            loadError = "$FILE was not found. Looked in: ${candidates.joinToString { it.absolutePath }}"
            println("ERROR Mpv: $loadError")
            return@lazy null
        }
        runCatching {
            Native.load(file.absolutePath, MpvLibrary::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
        }.onSuccess {
            println("INFO Mpv: Loaded ${file.absolutePath}")
        }.onFailure {
            loadError = "$FILE could not be loaded: ${it.message}"
            println("ERROR Mpv: $loadError")
        }.getOrNull()
    }
}

data class MpvTrack(
    val id: Int,
    /** "video", "audio" or "sub" */
    val type: String,
    val title: String?,
    val lang: String?,
    val selected: Boolean,
    val external: Boolean,
    /** The file an external track was added from */
    val externalFilename: String? = null,
    /** For example "eac3" or "subrip" */
    val codec: String? = null,
    /** Audio channels, for example 6 for 5.1 */
    val channels: Int? = null,
    val default: Boolean = false,
) {
    /** The language spelled out, for example "Hindi" for "hin" */
    val language: String? get() = lang?.let(::languageName)

    /** For example "Hindi", "Commentary · English" or "Track 2" */
    val label: String get() = listOfNotNull(title, language).filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        .joinToString(" · ").ifEmpty { "Track $id" }

    /** For example "5.1 · EAC3", what tells two tracks in one language apart */
    val details: String get() = listOfNotNull(
        channels?.let { when (it) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> "$it channels" } },
        codec?.let { codecNames[it] ?: it.uppercase() },
        "Default".takeIf { default },
        "Picture, keeps its own look".takeIf { isPicture },
    ).joinToString(" · ")

    /** A subtitle drawn as pictures, as on Blu-rays and DVDs: the subtitle settings cannot change how it looks */
    val isPicture: Boolean get() = type == "sub" && codec in pictureSubtitles

    private companion object {
        val pictureSubtitles = setOf("hdmv_pgs_subtitle", "dvd_subtitle", "dvb_subtitle", "xsub")
        val codecNames = mapOf(
            "hdmv_pgs_subtitle" to "PGS", "dvd_subtitle" to "VobSub", "dvb_subtitle" to "DVB",
            "subrip" to "SRT", "webvtt" to "WebVTT", "mov_text" to "Text",
        )
    }
}

/** "Hindi" for "hin" or "hi", the code itself when it is not a language */
internal fun languageName(code: String): String {
    val locale = java.util.Locale.forLanguageTag(code)
    val name = locale.getDisplayLanguage(java.util.Locale.ENGLISH)
    return if (name.isBlank() || name.equals(code, ignoreCase = true)) code else name
}

data class MpvStatus(
    val position: Double = 0.0,
    val duration: Double = 0.0,
    val paused: Boolean = false,
    /** Waiting for data: the file is opening, a seek is under way or the cache ran empty */
    val buffering: Boolean = false,
    /** How full the cache is before playback goes on, 0 to 100, when mpv knows */
    val bufferPercent: Int? = null,
    /** How far ahead of the start the cache reaches, in seconds */
    val bufferedTo: Double = 0.0,
    val tracks: List<MpvTrack> = emptyList(),
    /** 0 to 100 */
    val volume: Int = 100,
    val muted: Boolean = false,
    val speed: Double = 1.0,
    /** Seconds the subtitles are shown later, negative for earlier */
    val subDelay: Double = 0.0,
)

/** A subtitle file to show with a link, added again whenever the link changes */
data class MpvSubtitle(val file: File, val title: String, val select: Boolean)

/**
 * How the picture fits the video's area. A 16:9 video on a 16:9 screen looks the same fitted, filled
 * or stretched: the zooms crop into it, also past black bars that are part of the video itself
 */
enum class AspectMode(val label: String, val zoom: Double = 1.0) {
    Fit("Fit"),
    Fill("Crop to Fill"),
    Zoom110("Zoom 110%", 1.10),
    Zoom125("Zoom 125%", 1.25),
    Zoom150("Zoom 150%", 1.50),
    SixteenNine("16:9"),
    FourThree("4:3"),
    Stretch("Stretch"),
}

sealed interface MpvEvent {
    /** F or a double click inside the video */
    data object ToggleFullscreen : MpvEvent
    /** A single click inside the video */
    data object Click : MpvEvent
    /** The mouse's back button inside the video */
    data object Back : MpvEvent
    data object Ended : MpvEvent
    /**
     * The mouse wheel or a touchpad turned over the video, at [x] across it from 0 to 1, by [notches]
     * of a wheel, up positive
     */
    data class Wheel(val x: Float, val notches: Double) : MpvEvent
    /** The file could not be played, for example a refused or broken link */
    data class Failed(val url: String, val message: String) : MpvEvent
}

/**
 * One mpv player drawing into [canvas]. It starts when the canvas is shown and shuts down when the
 * canvas is removed, before its window is destroyed. Calls made before it started are run then.
 * The app draws the controls; the mouse over the video is handled here and reported as [events].
 */
class MpvPlayer internal constructor(private val lib: MpvLibrary) {
    private val ctx: Pointer = lib.mpv_create() ?: error("mpv_create failed")
    private var started = false
    private var closed = false
    private val pending = mutableListOf<() -> Unit>()
    private var eventThread: Thread? = null
    private val stopped = CountDownLatch(1)
    /** The url being played, so a failure can be reported for the right link */
    @Volatile private var currentUrl: String? = null
    /** Whether the current file finished loading. Subtitles can only be added to a loaded file */
    private var fileLoaded = false
    /** From loadfile until the first frame plays */
    @Volatile private var opening = false
    private val afterLoad = mutableListOf<() -> Unit>()
    /** Where the file opening should start, in seconds, until it has started there */
    @Volatile private var resumeAt: Double? = null
    /** The link's own audio tracks and the subtitle files added to the file, to add again when it is opened again */
    private var audioUrls = emptyList<String>()
    private val subtitlesAdded = mutableListOf<Pair<File, String>>()
    /** Since when, in ms, mpv has been waiting to play from a seek, 0 when it is not */
    private var stalledSince = 0L
    /** How often the file was opened again for the seek waited on */
    private var reopens = 0

    private val _events = Channel<MpvEvent>(Channel.UNLIMITED)
    val events: Flow<MpvEvent> = _events.receiveAsFlow()

    /** Called on every mouse move over the video, from the AWT thread */
    @Volatile var onMouseMove: (() -> Unit)? = null

    // A double click toggles full screen, so a single click waits a moment to be sure it is not one
    private val singleClick = javax.swing.Timer(
        ((Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval") as? Int) ?: 300).coerceAtMost(300),
    ) { _events.trySend(MpvEvent.Click) }.apply { isRepeats = false }

    val canvas: Canvas = object : Canvas() {
        override fun addNotify() {
            super.addNotify()
            start(Native.getComponentID(this))
        }

        override fun removeNotify() {
            singleClick.stop()
            shutdown()
            super.removeNotify()
        }
    }.apply {
        background = Color.BLACK
        // Keys stay with the app, which handles them the same inside and outside the video
        isFocusable = false
        // Embedded mpv takes no input itself: its window is disabled, so the mouse reaches this canvas
        val mouse = object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                onMouseMove?.invoke()
            }

            override fun mouseDragged(e: MouseEvent) = mouseMoved(e)

            override fun mouseClicked(e: MouseEvent) {
                // The mouse's back button, as everywhere else in the app
                if (e.button == 4) _events.trySend(MpvEvent.Back)
                if (e.button != MouseEvent.BUTTON1) return
                when {
                    e.clickCount == 1 -> singleClick.restart()
                    e.clickCount == 2 -> {
                        singleClick.stop()
                        _events.trySend(MpvEvent.ToggleFullscreen)
                    }
                }
            }

            override fun mouseWheelMoved(e: MouseWheelEvent) {
                // Handled here: otherwise the window passes the same turn on to the video again, a few
                // times over, and one notch would move the level several steps
                e.consume()
                // A touchpad sends many small turns, where the whole notches in wheelRotation are 0:
                // only the precise rotation says how far, and which way
                val notches = -e.preciseWheelRotation
                if (notches != 0.0) _events.trySend(MpvEvent.Wheel((e.x.toFloat() / width.coerceAtLeast(1)).coerceIn(0f, 1f), notches))
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
        addMouseWheelListener(mouse)
    }

    private fun start(windowId: Long): Unit = synchronized(this) {
        if (started || closed) return@synchronized
        started = true
        option("wid", windowId.toString())
        // The app draws its own controls. mpv's would not work anyway: embedded, it gets no mouse input
        option("osc", "no")
        option("hwdec", "auto-safe")
        // Stay on the last frame at the end instead of closing the file
        option("keep-open", "yes")
        option("idle", "yes")
        option("force-window", "yes")
        option("sub-auto", "no")
        option("drag-and-drop", "no")
        // VLC-parity buffer & network resilience
        option("cache", "yes")
        option("demuxer-readahead-secs", "30")
        option("demuxer-max-bytes", "150MiB")
        option("demuxer-max-back-bytes", "50MiB")
        option("demuxer-lavf-probesize", "32768")
        option("hr-seek", "default")
        option("hr-seek-framedrop", "yes")
        option("volume-max", "130")
        // Network: a stream that stops answering fails after 30 s instead of 60, so the next source can
        // play, and a dropped connection is opened again where it stopped
        option("network-timeout", "30")
        option("stream-lavf-o", "reconnect=1,reconnect_streamed=1,reconnect_on_network_error=1,reconnect_delay_max=5")
        // Links come from the extensions already: no youtube-dl lookup before every file
        option("ytdl", "no")
        check("initialize", lib.mpv_initialize(ctx))
        // mpv's errors, such as a server refusing a request, go to the app's log
        check("request_log_messages", lib.mpv_request_log_messages(ctx, "error"))
        eventThread = Thread(::eventLoop, "mpv-events").apply { isDaemon = true; start() }
        pending.forEach { it() }
        pending.clear()
    }

    /** Runs now if mpv started, otherwise once it has */
    private fun whenStarted(action: () -> Unit) = synchronized(this) {
        when {
            closed -> Unit
            started -> action()
            else -> pending += action
        }
    }

    private fun shutdown() {
        synchronized(this) {
            if (closed) return
            closed = true
            if (!started) {
                lib.mpv_terminate_destroy(ctx)
                return
            }
            command("quit")
        }
        // The event thread destroys the handle; wait so mpv lets go of the window before it is destroyed
        if (!stopped.await(5, TimeUnit.SECONDS)) println("ERROR MpvPlayer: mpv did not shut down in time")
    }

    private fun eventLoop() {
        try {
            while (true) {
                val event = lib.mpv_wait_event(ctx, -1.0)
                when (event.getInt(0)) {
                    MPV_EVENT_SHUTDOWN -> return
                    MPV_EVENT_LOG_MESSAGE -> {
                        // mpv_event_log_message: prefix, level, text
                        val data = event.getPointer(16)
                        val prefix = data.getPointer(0).getString(0, "UTF-8")
                        val text = data.getPointer(2L * Native.POINTER_SIZE).getString(0, "UTF-8")
                        println("ERROR mpv [$prefix]: ${text.trim()}")
                    }
                    MPV_EVENT_PLAYBACK_RESTART -> {
                        opening = false
                        resumeAt?.let(::checkResumed)
                    }
                    MPV_EVENT_FILE_LOADED -> synchronized(this) {
                        fileLoaded = true
                        afterLoad.forEach { it() }
                        afterLoad.clear()
                    }
                    MPV_EVENT_END_FILE -> {
                        val data = event.getPointer(16)
                        val reason = data.getInt(0)
                        val url = currentUrl ?: continue
                        if (reason == END_FILE_REASON_ERROR) opening = false
                        when (reason) {
                            END_FILE_REASON_EOF -> _events.trySend(MpvEvent.Ended)
                            END_FILE_REASON_ERROR -> _events.trySend(MpvEvent.Failed(url, lib.mpv_error_string(data.getInt(4))))
                        }
                    }
                }
            }
        } finally {
            lib.mpv_terminate_destroy(ctx)
            stopped.countDown()
        }
    }

    private fun option(name: String, value: String) = check(name, lib.mpv_set_option_string(ctx, name, value))

    private fun command(vararg args: String): Int = lib.mpv_command(ctx, arrayOf(*args, null)).also { check(args.first(), it) }

    private fun check(what: String, code: Int) {
        if (code < 0) println("ERROR MpvPlayer: $what failed: ${lib.mpv_error_string(code)}")
    }

    private fun property(name: String): String? {
        if (!started || closed) return null
        val value = lib.mpv_get_property_string(ctx, name) ?: return null
        return value.getString(0, "UTF-8").also { lib.mpv_free(value) }
    }

    /**
     * Plays a link with the headers the provider gave for it. The referer and user agent have their
     * own mpv options, every other header is sent as is on every request, playlists and segments included.
     */
    fun play(link: ExtractorLink, startSeconds: Double? = null, subtitles: List<MpvSubtitle> = emptyList()) = whenStarted {
        val headers = link.headers.toMutableMap()
        fun take(name: String) = headers.keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let { headers.remove(it) }
        val userAgent = take("User-Agent") ?: USER_AGENT
        val headerReferer = take("Referer")
        val referer = link.referer.ifBlank { headerReferer.orEmpty() }

        currentUrl = link.url
        command("set", "user-agent", userAgent)
        command("set", "referrer", referer)
        // Appended one by one, so a comma inside a header value is never taken as a separator
        command("change-list", "http-header-fields", "clr", "")
        headers.forEach { (key, value) -> command("change-list", "http-header-fields", "append", "$key: $value") }
        // keep-open pauses at the end of a file, which would carry over to the next one
        command("set", "pause", "no")
        audioUrls = link.audioTracks.map { it.url }
        subtitlesAdded.clear()
        reopens = 0
        load(link.url, startSeconds)
        // A new file drops the subtitles added to the last one
        subtitles.forEach { addSubtitle(it.file, it.title, it.select) }
    }

    /** Opens [url], with the link's own audio tracks, at [startSeconds] or from the start */
    private fun load(url: String, startSeconds: Double?) {
        command("set", "start", startSeconds?.let { "+$it" } ?: "none")
        resumeAt = startSeconds?.takeIf { it > 0 }
        fileLoaded = false
        opening = true
        stalledSince = 0
        afterLoad.clear()
        command("loadfile", url, "replace")
        audioUrls.forEach { command("audio-add", it, "auto") }
    }

    /**
     * A seek past what is loaded asks the server for that part of the file. Some servers never answer,
     * and mpv would then wait there for good, the spinner turning. Once it waited [SEEK_PATIENCE_MS],
     * the file is opened again at that point, which asks again on a new connection, with the audio
     * track and subtitle that were chosen. Called with every status
     */
    fun recoverStalledSeek() = whenStarted {
        val waiting = fileLoaded && (opening || property("seeking") == "yes")
        if (!waiting) {
            stalledSince = 0
            if (fileLoaded) reopens = 0
            return@whenStarted
        }
        val now = System.currentTimeMillis()
        if (stalledSince == 0L) stalledSince = now
        if (now - stalledSince < SEEK_PATIENCE_MS || reopens >= MAX_REOPENS) return@whenStarted
        val url = currentUrl ?: return@whenStarted
        val at = property("time-pos")?.toDoubleOrNull() ?: return@whenStarted
        reopens++
        println("WARNING MpvPlayer: no answer at ${at.toInt()} s for ${(now - stalledSince) / 1000} s, opening the file again there")
        val audio = property("aid")
        val subtitle = property("sid")
        val subtitles = subtitlesAdded.toList()
        load(url, at)
        whenLoaded {
            subtitles.forEach { (file, title) -> command("sub-add", file.absolutePath, "auto", title) }
            audio?.let { command("set", "aid", it) }
            subtitle?.let { command("set", "sid", it) }
        }
    }

    /**
     * Once the file plays, checks it started where it was asked to. Some servers do not let mpv jump
     * into the file as it opens, and it then starts from the beginning: it is asked to seek once more
     */
    private fun checkResumed(wanted: Double) {
        resumeAt = null
        val at = property("time-pos")?.toDoubleOrNull() ?: return
        val seekable = property("seekable")
        if (at >= wanted - 5) {
            println("INFO MpvPlayer: resumed at ${at.toInt()} s of ${wanted.toInt()} s")
            return
        }
        println("WARNING MpvPlayer: started at ${at.toInt()} s, not ${wanted.toInt()} s (seekable: $seekable), seeking there")
        command("seek", wanted.toString(), "absolute")
    }

    /** Runs once the current file is loaded */
    private fun whenLoaded(action: () -> Unit) = whenStarted {
        if (fileLoaded) action() else afterLoad += action
    }

    /** Adds a subtitle file, shown when [select] is true */
    fun addSubtitle(file: File, title: String, select: Boolean = true) = whenLoaded {
        subtitlesAdded += file to title
        command("sub-add", file.absolutePath, if (select) "select" else "auto", title)
    }

    /** Hides subtitles once the file is loaded, for a new link after the user turned them off */
    fun hideSubtitlesWhenLoaded() = whenLoaded { command("set", "sid", "no") }

    /** Shows a subtitle track, or hides subtitles when [id] is null */
    fun selectSubtitle(id: Int?) = whenStarted { command("set", "sid", id?.toString() ?: "no") }

    fun selectAudio(id: Int) = whenStarted { command("set", "aid", id.toString()) }

    fun togglePause() = whenStarted { command("cycle", "pause") }

    fun pause() = whenStarted { command("set", "pause", "yes") }

    fun seek(seconds: Double) = whenStarted { command("seek", seconds.toString(), "relative") }

    fun seekTo(seconds: Double) = whenStarted { command("seek", seconds.toString(), "absolute") }

    fun changeVolume(delta: Int) = whenStarted { command("add", "volume", delta.toString()) }

    fun setVolume(volume: Int) = whenStarted { command("set", "volume", volume.coerceIn(0, 130).toString()) }

    fun toggleMute() = whenStarted { command("cycle", "mute") }

    fun setSpeed(speed: Double) = whenStarted { command("set", "speed", speed.toString()) }

    /** How the picture fits the video's area: [AspectMode.Fill] crops it, [AspectMode.Stretch] distorts it */
    fun setAspectMode(mode: AspectMode) = whenStarted {
        command("set", "keepaspect", if (mode == AspectMode.Stretch) "no" else "yes")
        command("set", "panscan", if (mode == AspectMode.Fill) "1.0" else "0.0")
        // mpv's zoom is in powers of 2
        command("set", "video-zoom", log2(mode.zoom).toString())
        command("set", "video-aspect-override", when (mode) {
            AspectMode.SixteenNine -> "16:9"
            AspectMode.FourThree -> "4:3"
            else -> "-1"
        })
    }

    /**
     * Placed in the app's layout, where it takes no room: while it is there, [canvas] is in the
     * window, over the app, where [place] puts it. The video is a native window that nothing can be
     * drawn over. Inside the app's layout it would be the size of the part it shows, and change size
     * whenever the bars came or went, and mpv drew the picture again at each new size, which flashed.
     * Over the app it keeps its size, and only the part of it to show is cut out of it, at once.
     */
    val anchor: JComponent = object : JComponent() {
        private var layers: JLayeredPane? = null

        override fun addNotify() {
            super.addNotify()
            val pane = SwingUtilities.getRootPane(this)?.layeredPane ?: return
            layers = pane
            pane.add(canvas, JLayeredPane.PALETTE_LAYER as Any)
            placeCanvas()
        }

        override fun removeNotify() {
            layers?.remove(canvas)
            layers = null
            super.removeNotify()
        }
    }

    /** Where the whole video goes, in the window's content coordinates */
    private var whole: Rectangle? = null
    /** The part of it to show, in pixels from its corner, null for all of it */
    private var shown: Rectangle? = null
    /** Whether [shown] was set on the canvas's window */
    private var shownSet = false

    /**
     * Puts the video at [area], in the window's content coordinates, and shows only [visible] of it,
     * in pixels from the corner of [area], null for all of it: what the app draws there shows instead.
     */
    fun place(area: Rectangle, visible: Rectangle?) {
        if (area != whole) {
            whole = area
            shownSet = false
            placeCanvas()
        }
        if (visible != shown || !shownSet) {
            shown = visible
            showPart()
        }
    }

    private fun placeCanvas() {
        val area = whole ?: return
        if (canvas.parent == null) return
        canvas.setBounds(area)
        showPart()
    }

    private fun showPart() {
        val windows = Win32.instance ?: return
        if (!canvas.isDisplayable) return
        val hwnd = Native.getComponentPointer(canvas) ?: return
        val region = shown?.let { windows.gdi.CreateRectRgn(it.x, it.y, it.x + it.width, it.y + it.height) }
        // Windows owns the region from then on, and frees it
        windows.user.SetWindowRgn(hwnd, region, true)
        shownSet = true
    }

    /** How subtitles look, from SubtitleStyle.mpvOptions. The one shown changes at once */
    fun setSubtitleStyle(options: List<Pair<String, String>>) = whenStarted {
        options.forEach { (name, value) -> command("set", name, value) }
    }

    /** The languages, as codes, whose subtitle track in the video mpv shows on its own, for the next file */
    fun setSubtitleLanguages(codes: List<String>) = whenStarted { command("set", "slang", codes.joinToString(",")) }

    fun changeSubDelay(seconds: Double) = whenStarted { command("add", "sub-delay", seconds.toString()) }

    fun resetSubDelay() = whenStarted { command("set", "sub-delay", "0") }

    /**
     * Draws [ass] over the video, in ASS on a 1280 by 720 canvas, or removes it when null. The
     * video is a native window, so this is the only way to show something on top of it. Each
     * [layer] is drawn and removed on its own
     */
    fun overlay(ass: String?, layer: Int = 1) = whenStarted {
        if (ass == null) command("osd-overlay", layer.toString(), "none", "")
        else command("osd-overlay", layer.toString(), "ass-events", ass, "1280", "720")
    }

    /** The picture's brightness, from 0 for dark to 100 as the video is */
    fun setBrightness(percent: Int) = whenStarted {
        // mpv's -100 is black: the darkest step still shows the picture, like a screen at its lowest
        command("set", "brightness", ((percent.coerceIn(0, 100) - 100) * 0.8).toInt().toString())
    }

    fun status(): MpvStatus {
        if (!started || closed) return MpvStatus()
        val count = property("track-list/count")?.toIntOrNull() ?: 0
        val tracks = (0 until count).mapNotNull { i ->
            val prefix = "track-list/$i/"
            MpvTrack(
                id = property("${prefix}id")?.toIntOrNull() ?: return@mapNotNull null,
                type = property("${prefix}type") ?: return@mapNotNull null,
                title = property("${prefix}title"),
                lang = property("${prefix}lang"),
                selected = property("${prefix}selected") == "yes",
                external = property("${prefix}external") == "yes",
                externalFilename = property("${prefix}external-filename"),
                codec = property("${prefix}codec"),
                channels = property("${prefix}demux-channel-count")?.toIntOrNull(),
                default = property("${prefix}default") == "yes",
            )
        }
        return MpvStatus(
            position = property("time-pos")?.toDoubleOrNull() ?: 0.0,
            duration = property("duration")?.toDoubleOrNull() ?: 0.0,
            paused = property("pause") == "yes",
            buffering = opening || property("paused-for-cache") == "yes" || property("seeking") == "yes",
            bufferPercent = property("cache-buffering-state")?.toIntOrNull(),
            bufferedTo = property("demuxer-cache-time")?.toDoubleOrNull() ?: 0.0,
            tracks = tracks,
            volume = property("volume")?.toDoubleOrNull()?.toInt() ?: 100,
            muted = property("mute") == "yes",
            speed = property("speed")?.toDoubleOrNull() ?: 1.0,
            subDelay = property("sub-delay")?.toDoubleOrNull() ?: 0.0,
        )
    }

    private companion object {
        const val MPV_EVENT_SHUTDOWN = 1
        const val MPV_EVENT_LOG_MESSAGE = 2
        const val MPV_EVENT_END_FILE = 7
        const val MPV_EVENT_FILE_LOADED = 8
        const val MPV_EVENT_PLAYBACK_RESTART = 21
        const val END_FILE_REASON_EOF = 0
        const val END_FILE_REASON_ERROR = 4
        /** A slow but working server answered a far seek within 8 s in tests: 12 s leaves it room */
        const val SEEK_PATIENCE_MS = 12_000L
        /** Opened again this often for one seek, after which the server is left to answer */
        const val MAX_REOPENS = 2
    }
}
