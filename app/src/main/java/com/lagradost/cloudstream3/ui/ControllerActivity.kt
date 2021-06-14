package com.lagradost.cloudstream3.ui

import android.os.Bundle
import android.view.Menu
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.android.gms.cast.MediaStatus.REPEAT_MODE_REPEAT_SINGLE
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.uicontroller.UIController
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.Coroutines
import kotlinx.coroutines.delay

import org.json.JSONObject
import java.lang.Exception

class SkipOpController(val view: ImageView) : UIController() {
    init {
        view.setImageResource(R.drawable.exo_controls_fastforward)
        view.setOnClickListener {
            remoteMediaClient.seek(remoteMediaClient.approximateStreamPosition + 85000)
        }
    }
}

data class MetadataSource(val name: String)
data class MetadataHolder(val data: List<MetadataSource>)

class SelectSourceController(val view: ImageView, val activity: ControllerActivity) : UIController() {
    private val mapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

    init {
        view.setImageResource(R.drawable.ic_baseline_playlist_play_24)
        view.setOnClickListener {
            //remoteMediaClient.mediaQueue.itemCount
            //println(remoteMediaClient.mediaInfo.customData)
            //remoteMediaClient.queueJumpToItem()
            lateinit var dialog: AlertDialog
            val holder = getCurrentMetaData()

            if (holder != null) {
                val items = holder.data
                if (items.isNotEmpty() && remoteMediaClient.currentItem != null) {
                    val builder = AlertDialog.Builder(view.context, R.style.AlertDialogCustom)
                    builder.setTitle("Pick source")

                    builder.setSingleChoiceItems(
                        items.map { it.name }.toTypedArray(),
                        remoteMediaClient.mediaQueue.indexOfItemWithId(remoteMediaClient.currentItem.itemId)
                    ) { _, which ->
                        val itemId = remoteMediaClient.mediaQueue.itemIds?.get(which)

                        itemId?.let { id ->
                            remoteMediaClient.queueJumpToItem(
                                id,
                                remoteMediaClient.approximateStreamPosition,
                                remoteMediaClient.mediaInfo.customData
                            )
                        }

                        dialog.dismiss()
                    }
                    dialog = builder.create()
                    dialog.show()
                }
            }
        }
    }

    private fun getCurrentMetaData(): MetadataHolder? {
        return try {
            val data = remoteMediaClient.mediaInfo.customData
            mapper.readValue<MetadataHolder>(data.toString())
        } catch (e: Exception) {
            null
        }
    }

    override fun onMediaStatusUpdated() {
        super.onMediaStatusUpdated()
        view.visibility =
            if ((getCurrentMetaData()?.data?.size
                    ?: 0) > 1
            ) VISIBLE else INVISIBLE
    }

    override fun onSessionConnected(castSession: CastSession?) {
        super.onSessionConnected(castSession)
        remoteMediaClient.queueSetRepeatMode(REPEAT_MODE_REPEAT_SINGLE, JSONObject())
    }
}

class SkipTimeController(val view: ImageView, forwards: Boolean) : UIController() {
    init {
        //val settingsManager = PreferenceManager.getDefaultSharedPreferences()
        //val time = settingsManager?.getInt("chromecast_tap_time", 30) ?: 30
        val time = 30
        //view.setImageResource(if (forwards) R.drawable.netflix_skip_forward else R.drawable.netflix_skip_back)
        view.setImageResource(if (forwards) R.drawable.go_forward_30 else R.drawable.go_back_30)
        view.setOnClickListener {
            remoteMediaClient.seek(remoteMediaClient.approximateStreamPosition + time * 1000 * if (forwards) 1 else -1)
        }
    }
}

class ControllerActivity : ExpandedControllerActivity() {
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.cast_expanded_controller_menu, menu)
        CastButtonFactory.setUpMediaRouteButton(this, menu, R.id.media_route_menu_item)
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sourcesButton: ImageView = getButtonImageViewAt(0)
        val skipBackButton: ImageView = getButtonImageViewAt(1)
        val skipForwardButton: ImageView = getButtonImageViewAt(2)
        val skipOpButton: ImageView = getButtonImageViewAt(3)
        uiMediaController.bindViewToUIController(sourcesButton, SelectSourceController(sourcesButton, this))
        uiMediaController.bindViewToUIController(skipBackButton, SkipTimeController(skipBackButton, false))
        uiMediaController.bindViewToUIController(skipForwardButton, SkipTimeController(skipForwardButton, true))
        uiMediaController.bindViewToUIController(skipOpButton, SkipOpController(skipOpButton))
    }
}