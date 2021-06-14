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
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus.REPEAT_MODE_REPEAT_OFF
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.uicontroller.UIController
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity
import com.lagradost.cloudstream3.APIHolder.getApiFromName
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.sortUrls
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.CastHelper.getMediaInfo
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore.toKotlinObject
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SkipOpController(val view: ImageView) : UIController() {
    init {
        view.setImageResource(R.drawable.exo_controls_fastforward)
        view.setOnClickListener {
            remoteMediaClient.seek(remoteMediaClient.approximateStreamPosition + 85000)
        }
    }
}

data class MetadataHolder(
    val apiName: String,
    val title: String?,
    val poster: String?,
    val currentEpisodeIndex: Int,
    val episodes: List<ResultEpisode>,
    val currentLinks: List<ExtractorLink>,
)

class SelectSourceController(val view: ImageView, val activity: ControllerActivity) : UIController() {
    private val mapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

    init {
        view.setImageResource(R.drawable.ic_baseline_playlist_play_24)
        view.setOnClickListener {
            lateinit var dialog: AlertDialog
            val holder = getCurrentMetaData()

            if (holder != null) {
                val items = holder.currentLinks
                if (items.isNotEmpty() && remoteMediaClient.currentItem != null) {
                    val builder = AlertDialog.Builder(view.context, R.style.AlertDialogCustom)
                    builder.setTitle("Pick source")

                    //https://developers.google.com/cast/docs/reference/web_receiver/cast.framework.messages.MediaInformation
                    val contentUrl = (remoteMediaClient.currentItem.media.contentUrl
                        ?: remoteMediaClient.currentItem.media.contentId)

                    builder.setSingleChoiceItems(
                        items.map { it.name }.toTypedArray(),
                        items.indexOfFirst { it.url == contentUrl }
                    ) { _, which ->
                        val epData = holder.episodes[holder.currentEpisodeIndex]

                        val mediaItem = getMediaInfo(epData,
                            holder,
                            holder.currentLinks[which],
                            remoteMediaClient.mediaInfo.customData)

                        val startAt = remoteMediaClient.approximateStreamPosition

                        try {

                            val currentIdIndex = getItemIndex() ?: return@setSingleChoiceItems

                            val nextId = remoteMediaClient.mediaQueue.itemIds?.get(currentIdIndex + 1)

                            if (nextId != null) {
                                remoteMediaClient.queueInsertAndPlayItem(MediaQueueItem.Builder(mediaItem).build(),
                                    nextId,
                                    startAt,
                                    JSONObject())
                            } else {
                                remoteMediaClient.load(mediaItem, true, startAt)
                            }
                        } catch (e: Exception) {
                            remoteMediaClient.load(mediaItem, true, startAt)
                        }

                        dialog.dismiss()
                    }
                    dialog = builder.create()
                    dialog.show()
                }
            }
        }
    }

    private fun getItemIndex(): Int? {
        val index = remoteMediaClient?.mediaQueue?.itemIds?.indexOf(remoteMediaClient.currentItem.itemId)
        return if (index == null || index < 0) null else index
    }

    private fun getCurrentMetaData(): MetadataHolder? {
        return try {
            val data = remoteMediaClient?.mediaInfo?.customData?.toString()
            data?.toKotlinObject()
        } catch (e: Exception) {
            null
        }
    }

    var isLoadingMore = false

    override fun onMediaStatusUpdated() {
        super.onMediaStatusUpdated()
        val meta = getCurrentMetaData()

        view.visibility = if ((meta?.currentLinks?.size
                ?: 0) > 1
        ) VISIBLE else INVISIBLE
        try {
            if (meta != null && meta.episodes.size > meta.currentEpisodeIndex + 1) {

                val currentIdIndex = getItemIndex() ?: return
                val itemCount = remoteMediaClient?.mediaQueue?.itemCount

                if (itemCount != null && itemCount - currentIdIndex == 1 && !isLoadingMore) {
                    isLoadingMore = true

                    main {
                        val index = meta.currentEpisodeIndex + 1
                        val epData = meta.episodes[index]
                        val links = ArrayList<ExtractorLink>()

                        val res = safeApiCall {
                            getApiFromName(meta.apiName).loadLinks(epData.data, true) {
                                for (i in links) {
                                    if (i.url == it.url) return@loadLinks
                                }
                                links.add(it)
                            }
                        }
                        if (res is Resource.Success) {
                            val sorted = sortUrls(links)
                            if (sorted.isNotEmpty()) {
                                val jsonCopy = meta.copy(currentLinks = sorted, currentEpisodeIndex = index)

                                val done = withContext(Dispatchers.IO) {
                                    getMediaInfo(epData,
                                        meta,
                                        sorted.first(),
                                        JSONObject(mapper.writeValueAsString(jsonCopy)))
                                }

                                remoteMediaClient?.queueAppendItem(MediaQueueItem.Builder(done).build(), JSONObject())
                                isLoadingMore = false
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println(e)
        }
    }

    override fun onSessionConnected(castSession: CastSession?) {
        super.onSessionConnected(castSession)
        remoteMediaClient?.queueSetRepeatMode(REPEAT_MODE_REPEAT_OFF, JSONObject())
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