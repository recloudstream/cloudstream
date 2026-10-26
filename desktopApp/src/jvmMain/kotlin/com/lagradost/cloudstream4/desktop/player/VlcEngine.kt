package com.lagradost.cloudstream4.desktop.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.lagradost.cloudstream3.mvvm.logError
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

data class Track(val id: Int, val name: String)

object Vlc {
    /** Null if LibVLC (the VLC app) is not installed */
    val factory: MediaPlayerFactory? by lazy {
        try {
            if (NativeDiscovery().discover()) {
                MediaPlayerFactory("--no-video-title-show", "--quiet", "--network-caching=3000")
            } else null
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    val isAvailable: Boolean get() = factory != null
}

/**
 * Wraps a LibVLC media player that renders frames into a Compose [ImageBitmap], so the video
 * can be drawn with regular Compose UI layered on top, on every desktop platform.
 */
class VlcEngine(factory: MediaPlayerFactory) {
    var frame by mutableStateOf<ImageBitmap?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(true)
        private set
    var timeMs by mutableStateOf(0L)
        private set
    var lengthMs by mutableStateOf(0L)
        private set
    var volume by mutableStateOf(100)
        private set
    var rate by mutableStateOf(1f)
        private set
    var audioTracks by mutableStateOf(emptyList<Track>())
        private set
    var audioTrack by mutableStateOf(-1)
        private set
    var subtitleTracks by mutableStateOf(emptyList<Track>())
        private set
    var subtitleTrack by mutableStateOf(-1)
        private set
    var hasError by mutableStateOf(false)
        private set
    var hasFinished by mutableStateOf(false)
        private set

    /** Only touched on the UI thread */
    private var bitmap = Bitmap()
    private var bitmapInfo: ImageInfo? = null

    /** Written by the VLC threads */
    @Volatile
    private var imageInfo: ImageInfo? = null
    @Volatile
    private var frameBytes = ByteArray(0)
    private val framePending = AtomicBoolean(false)
    private var released = false

    private val player: EmbeddedMediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer()

    init {
        val formatCallback = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                // Never block on the UI thread here, release() on the UI thread waits for VLC threads
                frameBytes = ByteArray(sourceWidth * sourceHeight * 4)
                imageInfo = ImageInfo.makeN32(sourceWidth, sourceHeight, ColorAlphaType.OPAQUE)
                return RV32BufferFormat(sourceWidth, sourceHeight)
            }

            override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) = Unit
            override fun allocatedBuffers(buffers: Array<out ByteBuffer>) = Unit
        }

        val renderCallback = object : RenderCallback {
            override fun lock(mediaPlayer: MediaPlayer) = Unit
            override fun unlock(mediaPlayer: MediaPlayer) = Unit

            override fun display(
                mediaPlayer: MediaPlayer,
                nativeBuffers: Array<out ByteBuffer>,
                bufferFormat: BufferFormat,
                displayWidth: Int,
                displayHeight: Int,
            ) {
                // Drop frames instead of queueing them if the UI thread falls behind
                if (!framePending.compareAndSet(false, true)) return
                val buffer = nativeBuffers[0]
                buffer.rewind()
                if (buffer.remaining() < frameBytes.size) {
                    framePending.set(false)
                    return
                }
                val bytes = frameBytes
                val info = imageInfo
                buffer.get(bytes)
                SwingUtilities.invokeLater {
                    try {
                        if (info != null && !released) {
                            if (bitmapInfo != info) {
                                bitmap.close()
                                bitmap = Bitmap().apply { allocPixels(info) }
                                bitmapInfo = info
                            }
                            bitmap.installPixels(bytes)
                            frame = bitmap.asComposeImageBitmap()
                        }
                    } finally {
                        framePending.set(false)
                    }
                }
            }
        }

        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(formatCallback, renderCallback, true))
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) = ui {
                isPlaying = true
                isBuffering = false
            }

            override fun paused(mediaPlayer: MediaPlayer) = ui { isPlaying = false }
            override fun stopped(mediaPlayer: MediaPlayer) = ui { isPlaying = false }
            override fun finished(mediaPlayer: MediaPlayer) = ui {
                isPlaying = false
                hasFinished = true
            }

            override fun error(mediaPlayer: MediaPlayer) = ui { hasError = true }
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) = ui { isBuffering = newCache < 100f }
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) = ui { timeMs = newTime }
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) = ui { lengthMs = newLength }
            override fun elementaryStreamAdded(
                mediaPlayer: MediaPlayer,
                type: uk.co.caprica.vlcj.media.TrackType,
                id: Int,
            ) = refreshTracks()

            override fun elementaryStreamSelected(
                mediaPlayer: MediaPlayer,
                type: uk.co.caprica.vlcj.media.TrackType,
                id: Int,
            ) = refreshTracks()
        })
    }

    private fun ui(block: () -> Unit) = SwingUtilities.invokeLater(block)

    private fun refreshTracks() {
        // Track queries are not allowed from the native callback thread
        player.submit {
            val audio = player.audio().trackDescriptions().map { Track(it.id(), it.description()) }
            val selectedAudio = player.audio().track()
            val subs = player.subpictures().trackDescriptions().map { Track(it.id(), it.description()) }
            val selectedSub = player.subpictures().track()
            ui {
                audioTracks = audio
                audioTrack = selectedAudio
                subtitleTracks = subs
                subtitleTrack = selectedSub
            }
        }
    }

    fun play(url: String, startMs: Long = 0) {
        hasError = false
        hasFinished = false
        isBuffering = true
        timeMs = startMs
        lengthMs = 0
        val options = buildList {
            if (startMs > 0) add(":start-time=${startMs / 1000.0}")
        }.toTypedArray()
        player.media().play(url, *options)
        player.audio().setVolume(volume)
    }

    fun togglePause() {
        if (hasFinished) {
            hasFinished = false
            player.controls().setTime(0)
            player.controls().play()
        } else {
            player.controls().pause()
        }
    }

    fun setPaused(paused: Boolean) = player.controls().setPause(paused)

    fun seekTo(ms: Long) {
        val clamped = if (lengthMs > 0) ms.coerceIn(0, lengthMs) else ms.coerceAtLeast(0)
        timeMs = clamped
        player.controls().setTime(clamped)
    }

    fun skip(ms: Long) = seekTo(timeMs + ms)

    fun changeVolume(value: Int) {
        volume = value.coerceIn(0, 200)
        player.audio().setVolume(volume)
    }

    fun changeRate(value: Float) {
        rate = value
        player.controls().setRate(value)
    }

    fun selectAudio(id: Int) {
        player.audio().setTrack(id)
        refreshTracks()
    }

    fun selectSubtitle(id: Int) {
        player.subpictures().setTrack(id)
        refreshTracks()
    }

    fun addSubtitleFile(file: File) {
        player.subpictures().setSubTitleFile(file)
        refreshTracks()
    }

    fun stop() = player.controls().stop()

    fun release() {
        if (released) return
        released = true
        frame = null
        // Releasing blocks until LibVLC is done, keep that off the UI thread
        Thread {
            player.controls().stop()
            player.release()
            SwingUtilities.invokeLater { bitmap.close() }
        }.start()
    }
}
