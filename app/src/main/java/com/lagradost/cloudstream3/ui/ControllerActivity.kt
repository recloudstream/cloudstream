package com.lagradost.cloudstream3.ui

import android.os.Bundle
import android.view.Menu
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.cast.MediaStatus.REPEAT_MODE_REPEAT_SINGLE
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.uicontroller.UIController
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity
import com.lagradost.cloudstream3.R

import org.json.JSONObject

class SkipOpController(val view: ImageView) : UIController() {
    init {
        view.setImageResource(R.drawable.exo_controls_fastforward)
        view.setOnClickListener {
            remoteMediaClient.seek(remoteMediaClient.approximateStreamPosition + 85000)
        }
    }
}

class SelectSourceController(val view: ImageView) : UIController() {

    init {
        view.setImageResource(R.drawable.ic_baseline_playlist_play_24)
        view.setOnClickListener {
            //remoteMediaClient.mediaQueue.itemCount
            //println(remoteMediaClient.mediaInfo.customData)
            //remoteMediaClient.queueJumpToItem()
            lateinit var dialog: AlertDialog
            val items = mutableListOf<Pair<Int, String>>()
            for (i in 0 until remoteMediaClient.mediaQueue.itemCount) {
                (remoteMediaClient.mediaQueue.getItemAtIndex(i)?.media?.customData?.get("data") as? String)?.let { name ->
                    items.add(
                        remoteMediaClient.mediaQueue.getItemAtIndex(i)!!.itemId to name
                    )
                }
            }

            // TODO FIX
            if (items.isNotEmpty()) {
                val builder = AlertDialog.Builder(view.context, R.style.AlertDialogCustom)
                builder.setTitle("Pick source")

                builder.setSingleChoiceItems(
                    items.map { it.second }.toTypedArray(),
                    remoteMediaClient.currentItem.itemId - 1
                ) { _, which ->
                    println(
                        remoteMediaClient.queueJumpToItem(
                            items[which].first,
                            remoteMediaClient.approximateStreamPosition,
                            null
                        )
                    )
                    dialog.dismiss()
                }
                dialog = builder.create()
                dialog.show()
            }
        }
    }

    override fun onMediaStatusUpdated() {
        super.onMediaStatusUpdated()
        // If there's 1 item it won't show
        val dataString = remoteMediaClient.mediaQueue.getItemAtIndex(1)?.media?.customData?.get("data") as? String
        view.visibility = if (dataString != null) VISIBLE else INVISIBLE
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
        view.setImageResource(if (forwards) R.drawable.netflix_skip_forward else R.drawable.netflix_skip_back)
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
        uiMediaController.bindViewToUIController(sourcesButton, SelectSourceController(sourcesButton))
        uiMediaController.bindViewToUIController(skipBackButton, SkipTimeController(skipBackButton, false))
        uiMediaController.bindViewToUIController(skipForwardButton, SkipTimeController(skipForwardButton, true))
        uiMediaController.bindViewToUIController(skipOpButton, SkipOpController(skipOpButton))
    }
}