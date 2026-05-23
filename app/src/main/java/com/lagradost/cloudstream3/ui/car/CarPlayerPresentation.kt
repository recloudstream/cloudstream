package com.lagradost.cloudstream3.ui.car

import android.app.Presentation
import android.content.Context
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.exoplayer.ExoPlayer
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.PlayerCarHelper

/**
 * Presentation projected onto the car's VirtualDisplay.
 *
 * Uses TextureView (not SurfaceView) because VirtualDisplay cannot composite
 * SurfaceView's separate layer back to its output Surface.
 * FLAG_HARDWARE_ACCELERATED is required for TextureView.
 */
class CarPlayerPresentation(
    context: Context,
    display: Display,
    private val getPlayer: () -> ExoPlayer?,
    private val getIsPlaying: () -> Boolean,
    private val onSeekBack: () -> Unit,
    private val onSeekForward: () -> Unit,
    private val onSaveProgress: () -> Unit,
    private val getTitle: () -> String,
    private val getSubtitle: () -> String?
) : Presentation(context, display) {

    companion object {
        private const val TAG = "CarPlayerPresentation"
        private const val HIDE_DELAY_MS = 5000L
    }

    private var btnPlay: ImageButton? = null
    private var btnPause: ImageButton? = null
    private var btnRewind: ImageButton? = null
    private var btnForward: ImageButton? = null
    private var txtTitle: TextView? = null
    private var txtSubtitle: TextView? = null
    private var txtCurrentTime: TextView? = null
    private var txtDuration: TextView? = null
    private var progressBar: ProgressBar? = null
    private var controlsContainer: View? = null
    private var textureView: TextureView? = null

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { toggleControls(false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Required for TextureView inside VirtualDisplay
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        setContentView(R.layout.remote_car_player_overlay)
        bindViews()
        setupListeners()
        updateMetadata()
        updatePlayPauseState()
        scheduleHideControls()
    }

    private fun bindViews() {
        textureView = findViewById(R.id.texture_view)
        controlsContainer = findViewById(R.id.controls_container)
        btnPlay = findViewById(R.id.btn_play)
        btnPause = findViewById(R.id.btn_pause)
        btnRewind = findViewById(R.id.btn_rewind)
        btnForward = findViewById(R.id.btn_forward)
        txtTitle = findViewById(R.id.player_title)
        txtSubtitle = findViewById(R.id.player_subtitle)
        txtCurrentTime = findViewById(R.id.text_current_time)
        txtDuration = findViewById(R.id.text_duration)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun setupListeners() {
        textureView?.let { tv ->
            Log.d(TAG, "Binding TextureView to player")
            getPlayer()?.setVideoTextureView(tv)
        }

        btnPlay?.setOnClickListener {
            getPlayer()?.play()
            scheduleHideControls()
        }
        btnPause?.setOnClickListener {
            getPlayer()?.pause()
            onSaveProgress()
            scheduleHideControls()
        }
        btnRewind?.setOnClickListener {
            onSeekBack()
            scheduleHideControls()
        }
        btnForward?.setOnClickListener {
            onSeekForward()
            scheduleHideControls()
        }
        controlsContainer?.setOnClickListener {
            if (controlsContainer?.visibility == View.VISIBLE) {
                scheduleHideControls()
            }
        }
    }

    // --- Public API ---

    fun getTextureView(): TextureView? = textureView

    fun dispatchTouch(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0)

        try {
            val pb = progressBar
            if (pb != null && controlsContainer?.visibility == View.VISIBLE) {
                val loc = IntArray(2)
                pb.getLocationOnScreen(loc)
                val pbX = loc[0]
                val pbY = loc[1]

                if (x >= pbX && x <= (pbX + pb.width) &&
                    y >= (pbY - 20) && y <= (pbY + pb.height + 40)
                ) {
                    val ratio = (x - pbX) / pb.width.toFloat()
                    val dur = getPlayer()?.duration ?: 0L
                    if (dur > 0) {
                        getPlayer()?.seekTo((dur * ratio).toLong())
                        scheduleHideControls()
                        return
                    }
                }
            }
            window?.superDispatchTouchEvent(down)
            window?.superDispatchTouchEvent(up)
        } catch (e: Exception) {
            Log.e(TAG, "Touch dispatch failed", e)
        } finally {
            down.recycle()
            up.recycle()
        }

        if (controlsContainer?.visibility != View.VISIBLE) {
            toggleControls(true)
        } else {
            scheduleHideControls()
        }
    }

    fun toggleControls(show: Boolean) {
        hideHandler.removeCallbacks(hideRunnable)
        if (show) {
            controlsContainer?.visibility = View.VISIBLE
            controlsContainer?.animate()?.alpha(1.0f)?.setDuration(200)?.start()
            scheduleHideControls()
        } else {
            controlsContainer?.animate()?.alpha(0.0f)?.setDuration(500)?.withEndAction {
                controlsContainer?.visibility = View.GONE
            }?.start()
        }
    }

    fun scheduleHideControls() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, HIDE_DELAY_MS)
    }

    fun updatePlayPauseState() {
        if (getIsPlaying()) {
            btnPlay?.visibility = View.GONE
            btnPause?.visibility = View.VISIBLE
        } else {
            btnPlay?.visibility = View.VISIBLE
            btnPause?.visibility = View.GONE
            toggleControls(true)
        }
    }

    fun updateProgress(current: Long, total: Long) {
        txtCurrentTime?.text = PlayerCarHelper.formatDuration(current)
        txtDuration?.text = PlayerCarHelper.formatDuration(total)
        if (total > 0) {
            progressBar?.progress = ((current * 1000) / total).toInt()
        }
    }

    fun updateMetadata() {
        txtTitle?.text = getTitle()
        val subtitle = getSubtitle()
        if (subtitle != null) {
            txtSubtitle?.text = subtitle
            txtSubtitle?.visibility = View.VISIBLE
        } else {
            txtSubtitle?.visibility = View.GONE
        }
    }

    /**
     * Apply fit/fill scaling via Matrix transform.
     * TextureView doesn't support MediaCodec's videoScalingMode.
     */
    fun applyVideoScale(fill: Boolean) {
        val tv = textureView ?: return
        val videoWidth = getPlayer()?.videoSize?.width ?: return
        val videoHeight = getPlayer()?.videoSize?.height ?: return
        if (videoWidth == 0 || videoHeight == 0) return

        val viewWidth = tv.width.toFloat()
        val viewHeight = tv.height.toFloat()
        if (viewWidth == 0f || viewHeight == 0f) return

        val matrix = Matrix()
        if (fill) {
            val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
            val viewAspect = viewWidth / viewHeight
            val scaleX: Float
            val scaleY: Float
            if (videoAspect > viewAspect) {
                scaleY = 1f
                scaleX = videoAspect / viewAspect
            } else {
                scaleX = 1f
                scaleY = viewAspect / videoAspect
            }
            matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
        }
        tv.setTransform(matrix)
        Log.d(TAG, "Video scale: fill=$fill, video=${videoWidth}x$videoHeight, view=${viewWidth.toInt()}x${viewHeight.toInt()}")
    }
}
