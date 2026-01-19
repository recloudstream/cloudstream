package com.lagradost.cloudstream3.ui.car

import android.net.Uri
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lagradost.cloudstream3.R

class AboutMeScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver, SurfaceCallback {

    private var player: ExoPlayer? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        try {
            player = ExoPlayer.Builder(carContext).build().apply {
                val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()
                setAudioAttributes(audioAttributes, true)
                
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 1.0f
                // android.resource://package/id
                val uri = Uri.parse("android.resource://${carContext.packageName}/${R.raw.aboutme}")
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        player?.release()
        player = null
        super.onDestroy(owner)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        player?.play()
    }

    override fun onStop(owner: LifecycleOwner) {
        player?.pause()
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
        super.onStop(owner)
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface
        if (surface != null) {
            player?.setVideoSurface(surface)
        }
    }



    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        player?.clearVideoSurface()
    }

    override fun onGetTemplate(): Template {
        val backAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, androidx.appcompat.R.drawable.abc_ic_ab_back_material)).build())
            .setOnClickListener { screenManager.pop() }
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(backAction)
                    .build()
            )
            .build()
    }
}
