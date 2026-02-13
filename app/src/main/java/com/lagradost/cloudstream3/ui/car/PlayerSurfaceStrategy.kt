package com.lagradost.cloudstream3.ui.car

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Surface
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.exoplayer.ExoPlayer
import com.lagradost.cloudstream3.R

/**
 * Strategy interface for managing the surface rendering pipeline.
 * Implementations handle how the video is displayed on the car screen
 * and how the ActionStrip template is built.
 */
interface PlayerSurfaceStrategy {
    fun onSurfaceAvailable(surfaceContainer: SurfaceContainer)
    fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer)
    fun onClick(x: Float, y: Float)
    fun attachPlayer(player: ExoPlayer)
    fun applyVideoScale(fill: Boolean)
    fun updateProgress(current: Long, total: Long)
    fun updatePlayPauseState()
    fun buildTemplate(): Template
    fun release()
}

/**
 * Holds dynamic state and callbacks needed by each strategy to build its template.
 * Injected once at construction time; lambdas keep values current.
 */
class TemplateCallbacks(
    val carContext: android.content.Context,
    val getIsPlaying: () -> Boolean,
    val getIsFillMode: () -> Boolean,
    val isNextEnabled: () -> Boolean,
    val onExit: () -> Unit,
    val onPlayPause: () -> Unit,
    val onSeekBack: () -> Unit,
    val onSeekForward: () -> Unit,
    val onToggleFillMode: () -> Unit,
    val onNextEpisode: () -> Unit,
    val onInvalidate: () -> Unit
)

// ─────────────────────────────────────────────────────
//  Advanced Player (VirtualDisplay + Presentation overlay)
// ─────────────────────────────────────────────────────

/**
 * Advanced player mode: renders through a VirtualDisplay + CarPlayerPresentation overlay.
 * Provides on-screen touch controls (play/pause, seek, progress bar, title).
 * The ActionStrip is minimal (Exit, Resize, Next) since the overlay handles interaction.
 */
class PresentationSurfaceStrategy(
    private val context: android.content.Context,
    private val callbacks: TemplateCallbacks,
    private val getPlayer: () -> ExoPlayer?,
    private val getIsPlaying: () -> Boolean,
    private val onSeekBack: () -> Unit,
    private val onSeekForward: () -> Unit,
    private val onSaveProgress: () -> Unit,
    private val getTitle: () -> String,
    private val getSubtitle: () -> String?,
    private val onError: (String) -> Unit
) : PlayerSurfaceStrategy {

    companion object {
        private const val TAG = "PresentationStrategy"
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: CarPlayerPresentation? = null

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        surfaceContainer.surface?.let { surface ->
            setupVirtualDisplay(surface, surfaceContainer.width, surfaceContainer.height, surfaceContainer.dpi)
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        releaseVirtualDisplay()
    }

    override fun onClick(x: Float, y: Float) {
        presentation?.dispatchTouch(x, y)
    }

    override fun attachPlayer(player: ExoPlayer) {
        presentation?.getTextureView()?.let { player.setVideoTextureView(it) }
    }

    override fun applyVideoScale(fill: Boolean) {
        presentation?.applyVideoScale(fill)
    }

    override fun updateProgress(current: Long, total: Long) {
        presentation?.updateProgress(current, total)
    }

    override fun updatePlayPauseState() {
        presentation?.updatePlayPauseState()
    }

    /** Minimal ActionStrip: [Exit] [Resize] [Next Episode] */
    override fun buildTemplate(): Template {
        val ctx = callbacks.carContext
        val actionStripBuilder = ActionStrip.Builder()

        val exitAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(ctx, R.drawable.ic_baseline_arrow_back_24)).build())
            .setOnClickListener { callbacks.onExit() }
            .build()

        val resizeAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(ctx, R.drawable.ic_baseline_aspect_ratio_24)).build())
            .setOnClickListener { callbacks.onToggleFillMode() }
            .build()

        val nextAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(ctx, R.drawable.ic_baseline_skip_next_24)).build())
            .setOnClickListener { callbacks.onNextEpisode() }
            .setEnabled(callbacks.isNextEnabled())
            .build()

        actionStripBuilder.addAction(exitAction)
        actionStripBuilder.addAction(resizeAction)
        actionStripBuilder.addAction(nextAction)

        return NavigationTemplate.Builder()
            .setActionStrip(actionStripBuilder.build())
            .setBackgroundColor(CarColor.createCustom(android.graphics.Color.BLACK, android.graphics.Color.BLACK))
            .build()
    }

    override fun release() {
        releaseVirtualDisplay()
    }

    private fun setupVirtualDisplay(surface: Surface, width: Int, height: Int, dpi: Int) {
        try {
            val displayManager = context.getSystemService(android.content.Context.DISPLAY_SERVICE) as DisplayManager
            Log.d(TAG, "Creating VirtualDisplay: ${width}x${height} @ $dpi")

            virtualDisplay = displayManager.createVirtualDisplay(
                "CloudStreamCarPlayer", width, height, dpi, surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            )

            val display = virtualDisplay?.display ?: return
            val themeContext = ContextThemeWrapper(context, R.style.AppTheme)
            presentation = CarPlayerPresentation(
                context = themeContext,
                display = display,
                getPlayer = getPlayer,
                getIsPlaying = getIsPlaying,
                onSeekBack = onSeekBack,
                onSeekForward = onSeekForward,
                onSaveProgress = onSaveProgress,
                getTitle = getTitle,
                getSubtitle = getSubtitle
            )
            try {
                presentation?.show()
            } catch (e: android.view.WindowManager.InvalidDisplayException) {
                Log.e(TAG, "Invalid display for presentation", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up VirtualDisplay", e)
            onError("Error starting display: ${e.message}")
            // Fallback: direct surface
            getPlayer()?.setVideoSurface(surface)
        }
    }

    private fun releaseVirtualDisplay() {
        presentation?.getTextureView()?.let { getPlayer()?.clearVideoTextureView(it) }
        presentation?.dismiss()
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
    }
}

// ─────────────────────────────────────────────────────
//  Simple Player (direct surface, ActionStrip-only controls)
// ─────────────────────────────────────────────────────

/**
 * Simple player mode: renders video directly to the car surface.
 * No on-screen overlay — playback is controlled entirely via ActionStrip.
 *
 * ActionStrip has two states toggled by [showSeekControls]:
 *  - Default:  [Exit] [Play/Pause] [Next Episode] [Seek Controls]
 *  - Seek:     [Back] [-30s] [+30s] [Resize]
 */
class DirectSurfaceStrategy(
    private val callbacks: TemplateCallbacks,
    private val getPlayer: () -> ExoPlayer?
) : PlayerSurfaceStrategy {

    private var surface: Surface? = null
    private var showSeekControls = false

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        surfaceContainer.surface?.let {
            surface = it
            getPlayer()?.setVideoSurface(it)
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        surface = null
        getPlayer()?.setVideoSurface(null)
    }

    override fun onClick(x: Float, y: Float) {
        // No overlay in simple mode
    }

    override fun attachPlayer(player: ExoPlayer) {
        surface?.let { player.setVideoSurface(it) }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun applyVideoScale(fill: Boolean) {
        getPlayer()?.videoScalingMode = if (fill) {
            androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        } else {
            androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }

    override fun updateProgress(current: Long, total: Long) {
        // No overlay to update
    }

    override fun updatePlayPauseState() {
        // No overlay to update
    }

    /** Full legacy ActionStrip with seek toggle */
    override fun buildTemplate(): Template {
        val ctx = callbacks.carContext
        val actionStripBuilder = ActionStrip.Builder()

        if (showSeekControls) {
            buildSeekModeStrip(ctx, actionStripBuilder)
        } else {
            buildDefaultModeStrip(ctx, actionStripBuilder)
        }

        return NavigationTemplate.Builder()
            .setActionStrip(actionStripBuilder.build())
            .setBackgroundColor(CarColor.createCustom(android.graphics.Color.BLACK, android.graphics.Color.BLACK))
            .build()
    }

    /** SEEK MODE: [Back (to menu)] [-30s] [+30s] [Resize] */
    private fun buildSeekModeStrip(ctx: android.content.Context, builder: ActionStrip.Builder) {
        val backToMenuAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(ctx, R.drawable.ic_baseline_arrow_back_24)).build())
            .setOnClickListener {
                showSeekControls = false
                callbacks.onInvalidate()
            }
            .build()

        val seekBackAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(ctx, R.drawable.go_back_30)).build())
            .setOnClickListener { callbacks.onSeekBack() }
            .build()

        val seekForwardAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(ctx, R.drawable.go_forward_30)).build())
            .setOnClickListener { callbacks.onSeekForward() }
            .build()

        val resizeAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(ctx, R.drawable.ic_baseline_aspect_ratio_24)).build())
            .setOnClickListener { callbacks.onToggleFillMode() }
            .build()

        builder.addAction(backToMenuAction)
            .addAction(seekBackAction)
            .addAction(seekForwardAction)
            .addAction(resizeAction)
    }

    /** DEFAULT MODE: [Exit] [Play/Pause] [Next Episode] [Seek Controls] */
    private fun buildDefaultModeStrip(ctx: android.content.Context, builder: ActionStrip.Builder) {
        val exitAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(ctx, R.drawable.ic_baseline_arrow_back_24)).build())
            .setOnClickListener { callbacks.onExit() }
            .build()

        val playPauseAction = Action.Builder()
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(
                        ctx,
                        if (callbacks.getIsPlaying()) R.drawable.ic_baseline_pause_24 else R.drawable.ic_baseline_play_arrow_24
                    )
                ).build()
            )
            .setOnClickListener { callbacks.onPlayPause() }
            .build()

        val nextAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(ctx, R.drawable.ic_baseline_skip_next_24)).build())
            .setOnClickListener { callbacks.onNextEpisode() }
            .setEnabled(callbacks.isNextEnabled())
            .build()

        val openSeekAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(ctx, R.drawable.ic_baseline_tune_24)).build())
            .setOnClickListener {
                showSeekControls = true
                callbacks.onInvalidate()
            }
            .build()

        builder.addAction(exitAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(openSeekAction)
    }

    override fun release() {
        surface = null
    }
}
