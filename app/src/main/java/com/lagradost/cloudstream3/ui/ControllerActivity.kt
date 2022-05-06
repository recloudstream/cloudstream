package com.lagradost.cloudstream3.ui

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus.REPEAT_MODE_REPEAT_OFF
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.TextTrackStyle
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.cast.framework.media.uicontroller.UIController
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.sortSubs
import com.lagradost.cloudstream3.sortUrls
import com.lagradost.cloudstream3.ui.player.RepoLinkGenerator
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.subtitles.ChromecastSubtitlesFragment
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.CastHelper.awaitLinks
import com.lagradost.cloudstream3.utils.CastHelper.getMediaInfo
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStore.toKotlinObject
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import org.json.JSONObject

/*class SkipOpController(val view: ImageView) : UIController() {
    init {
        view.setImageResource(R.drawable.exo_controls_fastforward)
        view.setOnClickListener {
            remoteMediaClient?.let {
                val options = MediaSeekOptions.Builder()
                    .setPosition(it.approximateStreamPosition + 85000)
                it.seek(options.build())
            }
        }
    }
}*/

private fun RemoteMediaClient.getItemIndex(): Int? {
    return try {
        val index = this.mediaQueue.itemIds.indexOf(this.currentItem?.itemId ?: 0)
        if (index < 0) null else index
    } catch (e: Exception) {
        null
    }
}

class SkipNextEpisodeController(val view: ImageView) : UIController() {
    init {
        view.setImageResource(R.drawable.ic_baseline_skip_next_24)
        view.setOnClickListener {
            remoteMediaClient?.let {
                it.queueNext(JSONObject())
                view.visibility = GONE // TO PREVENT MULTI CLICK
            }
        }
    }

    override fun onMediaStatusUpdated() {
        super.onMediaStatusUpdated()
        view.visibility = GONE
        val currentIdIndex = remoteMediaClient?.getItemIndex() ?: return
        val itemCount = remoteMediaClient?.mediaQueue?.itemCount ?: return
        if (itemCount - currentIdIndex > 1 && remoteMediaClient?.isLoadingNextItem == false) {
            view.visibility = VISIBLE
        }
    }
}

data class MetadataHolder(
    val apiName: String,
    val isMovie: Boolean,
    val title: String?,
    val poster: String?,
    val currentEpisodeIndex: Int,
    val episodes: List<ResultEpisode>,
    val currentLinks: List<ExtractorLink>,
    val currentSubtitles: List<SubtitleData>
)

class SelectSourceController(val view: ImageView, val activity: ControllerActivity) :
    UIController() {
    private val mapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

    init {
        view.setImageResource(R.drawable.ic_baseline_playlist_play_24)
        view.setOnClickListener {
            //  lateinit var dialog: AlertDialog
            val holder = getCurrentMetaData()

            if (holder != null) {
                val items = holder.currentLinks
                if (items.isNotEmpty() && remoteMediaClient?.currentItem != null) {
                    val subTracks =
                        remoteMediaClient?.mediaInfo?.mediaTracks?.filter { it.type == MediaTrack.TYPE_TEXT }
                            ?: ArrayList()

                    val bottomSheetDialogBuilder =
                        AlertDialog.Builder(view.context, R.style.AlertDialogCustomBlack)
                    bottomSheetDialogBuilder.setView(R.layout.sort_bottom_sheet)
                    val bottomSheetDialog = bottomSheetDialogBuilder.create()
                    bottomSheetDialog.show()
                    //  bottomSheetDialog.setContentView(R.layout.sort_bottom_sheet)
                    val providerList =
                        bottomSheetDialog.findViewById<ListView>(R.id.sort_providers)!!
                    val subtitleList =
                        bottomSheetDialog.findViewById<ListView>(R.id.sort_subtitles)!!
                    if (subTracks.isEmpty()) {
                        bottomSheetDialog.findViewById<LinearLayout>(R.id.sort_subtitles_holder)?.visibility =
                            GONE
                    } else {
                        val arrayAdapter =
                            ArrayAdapter<String>(view.context, R.layout.sort_bottom_single_choice)
                        arrayAdapter.add(view.context.getString(R.string.no_subtitles))
                        arrayAdapter.addAll(subTracks.mapNotNull { it.name })

                        subtitleList.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                        subtitleList.adapter = arrayAdapter

                        val currentTracks = remoteMediaClient?.mediaStatus?.activeTrackIds

                        val subtitleIndex =
                            if (currentTracks == null) 0 else subTracks.map { it.id }
                                .indexOfFirst { currentTracks.contains(it) } + 1

                        subtitleList.setSelection(subtitleIndex)
                        subtitleList.setItemChecked(subtitleIndex, true)

                        subtitleList.setOnItemClickListener { _, _, which, _ ->
                            if (which == 0) {
                                remoteMediaClient?.setActiveMediaTracks(longArrayOf()) // NO SUBS
                            } else {
                                ChromecastSubtitlesFragment.getCurrentSavedStyle().apply {
                                    val font = TextTrackStyle()
                                    font.fontFamily = fontFamily ?: "Google Sans"
                                    fontGenericFamily?.let {
                                        font.fontGenericFamily = it
                                    }
                                    font.windowColor = windowColor
                                    font.backgroundColor = backgroundColor

                                    font.edgeColor = edgeColor
                                    font.edgeType = edgeType
                                    font.foregroundColor = foregroundColor
                                    font.fontScale = fontScale

                                    remoteMediaClient?.setTextTrackStyle(font)
                                }

                                remoteMediaClient?.setActiveMediaTracks(longArrayOf(subTracks[which - 1].id))
                                    ?.setResultCallback {
                                        if (!it.status.isSuccess) {
                                            Log.e(
                                                "CHROMECAST", "Failed with status code:" +
                                                        it.status.statusCode + " > " + it.status.statusMessage
                                            )
                                        }
                                    }
                            }
                            bottomSheetDialog.dismissSafe(activity)
                        }
                    }

                    //https://developers.google.com/cast/docs/reference/web_receiver/cast.framework.messages.MediaInformation
                    val contentUrl = (remoteMediaClient?.currentItem?.media?.contentUrl
                        ?: remoteMediaClient?.currentItem?.media?.contentId)

                    val sortingMethods = items.map { "${it.name} ${Qualities.getStringByInt(it.quality)}" }.toTypedArray()
                    val sotringIndex = items.indexOfFirst { it.url == contentUrl }

                    val arrayAdapter =
                        ArrayAdapter<String>(view.context, R.layout.sort_bottom_single_choice)
                    arrayAdapter.addAll(sortingMethods.toMutableList())

                    providerList.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                    providerList.adapter = arrayAdapter
                    providerList.setSelection(sotringIndex)
                    providerList.setItemChecked(sotringIndex, true)

                    providerList.setOnItemClickListener { _, _, which, _ ->
                        val epData = holder.episodes[holder.currentEpisodeIndex]

                        fun loadMirror(index: Int) {
                            if (holder.currentLinks.size <= index) return

                            val mediaItem = getMediaInfo(
                                epData,
                                holder,
                                index,
                                remoteMediaClient?.mediaInfo?.customData,
                                holder.currentSubtitles,
                            )

                            val startAt = remoteMediaClient?.approximateStreamPosition ?: 0

                            //remoteMediaClient.load(mediaItem, true, startAt)
                            try { // THIS IS VERY IMPORTANT BECAUSE WE NEVER WANT TO AUTOLOAD THE NEXT EPISODE
                                val currentIdIndex = remoteMediaClient?.getItemIndex()

                                val nextId = remoteMediaClient?.mediaQueue?.itemIds?.get(
                                    currentIdIndex?.plus(1) ?: 0
                                )
                                if (currentIdIndex == null && nextId != null) {
                                    awaitLinks(
                                        remoteMediaClient?.queueInsertAndPlayItem(
                                            MediaQueueItem.Builder(mediaItem).build(),
                                            nextId,
                                            startAt,
                                            JSONObject()
                                        )
                                    ) {
                                        loadMirror(index + 1)
                                    }
                                } else {
                                    awaitLinks(remoteMediaClient?.load(mediaItem, true, startAt)) {
                                        loadMirror(index + 1)
                                    }
                                }
                            } catch (e: Exception) {
                                awaitLinks(remoteMediaClient?.load(mediaItem, true, startAt)) {
                                    loadMirror(index + 1)
                                }
                            }
                        }
                        loadMirror(which)

                        bottomSheetDialog.dismissSafe(activity)
                    }
                }
            }
        }
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
                val currentIdIndex = remoteMediaClient?.getItemIndex() ?: return
                val itemCount = remoteMediaClient?.mediaQueue?.itemCount
                val index = meta.currentEpisodeIndex + 1
                val epData = meta.episodes[index]

                try {
                    val currentDuration = remoteMediaClient?.streamDuration
                    val currentPosition = remoteMediaClient?.approximateStreamPosition
                    if (currentDuration != null && currentPosition != null)
                        DataStoreHelper.setViewPos(epData.id, currentPosition, currentDuration)
                } catch (e : Exception) {
                    logError(e)
                }

                if (itemCount != null && itemCount - currentIdIndex == 1 && !isLoadingMore) {
                    isLoadingMore = true
                    ioSafe {
                        val currentLinks = mutableSetOf<ExtractorLink>()
                        val currentSubs = mutableSetOf<SubtitleData>()

                        val generator = RepoLinkGenerator(listOf(epData))

                        val isSuccessful = safeApiCall {
                            generator.generateLinks(clearCache = false, isCasting = true,
                                callback = {
                                    it.first?.let { link ->
                                        currentLinks.add(link)
                                    }
                                }, subtitleCallback = {
                                    currentSubs.add(it)
                                })
                        }

                        val sortedLinks = sortUrls(currentLinks)
                        val sortedSubs = sortSubs(currentSubs)
                        if (isSuccessful == Resource.Success(true)) {
                            if (currentLinks.isNotEmpty()) {
                                val jsonCopy = meta.copy(
                                    currentLinks = sortedLinks,
                                    currentSubtitles = sortedSubs,
                                    currentEpisodeIndex = index
                                )

                                val done =
                                    JSONObject(jsonCopy.toJson())

                                val mediaInfo = getMediaInfo(
                                    epData,
                                    jsonCopy,
                                    0,
                                    done,
                                    sortedSubs
                                )

                                /*fun loadIndex(index: Int) {
                                    println("LOAD INDEX::::: $index")
                                    if (meta.currentLinks.size <= index) return
                                    val info = getMediaInfo(
                                        epData,
                                        meta,
                                        index,
                                        done)
                                    awaitLinks(remoteMediaClient?.load(info, true, 0)) {
                                        loadIndex(index + 1)
                                    }
                                }*/
                                activity.runOnUiThread {
                                    awaitLinks(
                                        remoteMediaClient?.queueAppendItem(
                                            MediaQueueItem.Builder(mediaInfo).build(),
                                            JSONObject()
                                        )
                                    ) {
                                        println("FAILED TO LOAD NEXT ITEM")
                                        //  loadIndex(1)
                                    }
                                    isLoadingMore = false
                                }
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
        castSession?.let {
            super.onSessionConnected(it)
        }
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
            remoteMediaClient?.let {
                val options = MediaSeekOptions.Builder()
                    .setPosition(it.approximateStreamPosition + time * 1000 * if (forwards) 1 else -1)
                it.seek(options.build())
            }
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
        uiMediaController.bindViewToUIController(
            sourcesButton,
            SelectSourceController(sourcesButton, this)
        )
        uiMediaController.bindViewToUIController(
            skipBackButton,
            SkipTimeController(skipBackButton, false)
        )
        uiMediaController.bindViewToUIController(
            skipForwardButton,
            SkipTimeController(skipForwardButton, true)
        )
        uiMediaController.bindViewToUIController(
            skipOpButton,
            SkipNextEpisodeController(skipOpButton)
        )
    }
}