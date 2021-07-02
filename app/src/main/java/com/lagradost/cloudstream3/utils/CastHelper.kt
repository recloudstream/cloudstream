package com.lagradost.cloudstream3.utils

import android.content.Context
import android.net.Uri
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.android.exoplayer2.ext.cast.CastPlayer
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.gms.cast.*
import com.google.android.gms.cast.MediaStatus.REPEAT_MODE_REPEAT_OFF
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.common.images.WebImage
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.ui.MetadataHolder
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.Coroutines.main
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object CastHelper {
    private val mapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

    fun getMediaInfo(
        epData: ResultEpisode,
        holder: MetadataHolder,
        index: Int,
        data: JSONObject?,
        subtitles: List<SubtitleFile>
    ): MediaInfo {
        val link = holder.currentLinks[index]
        val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
        movieMetadata.putString(
            MediaMetadata.KEY_SUBTITLE,
            if (holder.isMovie)
                link.name
            else
                (epData.name ?: "Episode ${epData.episode}") + " - ${link.name}"
        )

        movieMetadata.putString(MediaMetadata.KEY_TITLE, holder.title)

        val srcPoster = epData.poster ?: holder.poster
        if (srcPoster != null) {
            movieMetadata.addImage(WebImage(Uri.parse(srcPoster)))
        }

        var subIndex = 0
        val tracks = subtitles.map {
            MediaTrack.Builder(subIndex++.toLong(), MediaTrack.TYPE_TEXT)
                .setName(it.lang)
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setContentId(it.url)
                .build()
        }

        return MediaInfo.Builder(link.url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(MimeTypes.VIDEO_UNKNOWN)
            .setCustomData(data)
            .setMetadata(movieMetadata)
            .setMediaTracks(tracks)
            .build()
    }

    fun awaitLinks(pending: PendingResult<RemoteMediaClient.MediaChannelResult>?, callback: (Boolean) -> Unit) {
        if (pending == null) return
        main {
            val res = withContext(Dispatchers.IO) { pending.await() }
            when (res.status?.statusCode) {
                CastStatusCodes.FAILED -> {
                    callback.invoke(true)
                    println("FAILED AND LOAD NEXT")
                }
                else -> {
                    //IDK DO SMTH HERE
                }
            }
        }
    }


    fun Context.startCast(
        apiName: String,
        isMovie: Boolean,
        title: String?,
        poster: String?,
        currentEpisodeIndex: Int,
        episodes: List<ResultEpisode>,
        currentLinks: List<ExtractorLink>,
        subtitles: List<SubtitleFile>,
        startIndex: Int? = null,
        startTime: Long? = null,
    ) : Boolean {
        if (episodes.isEmpty()) return false
        if (currentLinks.size <= currentEpisodeIndex) return false

        val castContext = CastContext.getSharedInstance(this)

        val epData = episodes[currentEpisodeIndex]

        val holder =
            MetadataHolder(apiName, isMovie, title, poster, currentEpisodeIndex, episodes, currentLinks, subtitles)

        val index = startIndex ?: 0
        val mediaItem =
            getMediaInfo(epData, holder, index, JSONObject(mapper.writeValueAsString(holder)), subtitles)

        val castPlayer = CastPlayer(castContext)

        castPlayer.repeatMode = REPEAT_MODE_REPEAT_OFF

        awaitLinks(
            castPlayer.loadItem(
                MediaQueueItem.Builder(mediaItem).build(),
                startTime ?: 0,
            )
        ) {
            if (currentLinks.size > index + 1)
                startCast(
                    apiName,
                    isMovie,
                    title,
                    poster,
                    currentEpisodeIndex,
                    episodes,
                    currentLinks,
                    subtitles,
                    index + 1,
                    startTime
                )
        }
        return true
    }
}