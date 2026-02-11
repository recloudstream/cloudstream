package com.lagradost.cloudstream3.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.media3.common.MediaMetadata
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.asDrawable
import androidx.core.graphics.drawable.toBitmap
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.ui.player.NEXT_WATCH_EPISODE_PERCENTAGE
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.setVideoWatchState

object PlayerCarHelper {
    private const val TAG = "PlayerCarHelper"

    fun saveHeaderCache(
        item: SearchResponse?,
        data: LoadResponse?,
        currentParentId: Int?
    ) {
        try {
            Log.d(TAG, "saveHeaderCache called. Item: ${item?.url}, Data: ${data?.url}, ParentID: $currentParentId")

            val url = item?.url ?: data?.url ?: return
            val apiName = item?.apiName ?: data?.apiName ?: return
            val name = item?.name ?: data?.name ?: return
            val type = item?.type ?: data?.type ?: TvType.Movie
            val poster = item?.posterUrl ?: data?.posterUrl

            val api = getApiFromNameNull(apiName)
            val mainUrl = api?.mainUrl ?: ""
            val id = url.replace(mainUrl, "").replace("/", "").hashCode()

            val header = VideoDownloadHelper.DownloadHeaderCached(
                apiName = apiName,
                url = url,
                type = type,
                name = name,
                poster = poster,
                id = id,
                cacheTime = System.currentTimeMillis()
            )
            Log.e(TAG, "Saving Header Cache: ID=$id, ParentID=$currentParentId, Name=${name}, Type=${header.type}")
            setKey(DOWNLOAD_HEADER_CACHE, id.toString(), header)
            // Ensure parentId is also covered if different
            currentParentId?.let { parentId ->
                if (parentId != id) {
                    setKey(DOWNLOAD_HEADER_CACHE, parentId.toString(), header.copy(id = parentId))
                    Log.e(TAG, "Saved extra copy for ParentID: $parentId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving header cache", e)
        }
    }

    fun saveProgress(
        pos: Long,
        dur: Long,
        item: SearchResponse?,
        activeEpisode: Episode?,
        currentEpisodeId: Int?,
        currentParentId: Int?
    ) {
        Log.d(TAG, "saveProgress - Pos: $pos, Dur: $dur")

        if (item?.type == TvType.Live) {
            Log.d(TAG, "Skipping saveProgress for Live content")
            return
        }

        if (dur <= 0) return

        // 1. Save detailed position (setViewPos)
        // Only if we have a valid ID. For Movies, currentEpisodeId might be enough.
        // For Episodes, we need the specific episode ID.
        if (currentEpisodeId != null) {
            DataStoreHelper.setViewPos(currentEpisodeId, pos, dur)
            Log.d(TAG, "Called setViewPos for EpisodeID: $currentEpisodeId")
        } else {
            Log.d(TAG, "Skipping setViewPos (EpisodeID is null)")
        }

        // 2. Save "Last Watched" for Continue Watching list
        // This requires parentId.
        if (currentParentId != null) {
            // Logic to check if finished (95% rule)
            val percentage = pos * 100L / dur
            if (percentage >= NEXT_WATCH_EPISODE_PERCENTAGE) {
                // Mark as finished / remove from resume
                DataStoreHelper.removeLastWatched(currentParentId)
                // Mark as watched
                if (currentEpisodeId != null) {
                    setVideoWatchState(currentEpisodeId, VideoWatchState.Watched)
                }
                Log.d(TAG, "Marked as Watched & Removed Last Watched (Finished)")
            } else {
                // Update resume state
                val epNum = activeEpisode?.episode
                val seasonNum = activeEpisode?.season

                DataStoreHelper.setLastWatched(
                    parentId = currentParentId,
                    episodeId = currentEpisodeId,
                    episode = epNum,
                    season = seasonNum,
                    isFromDownload = false,
                    updateTime = System.currentTimeMillis()
                )
                Log.d(TAG, "Set Last Watched for ParentID: $currentParentId")
            }
        } else {
            Log.d(TAG, "Skipping setLastWatched (ParentID is null)")
        }
    }

    suspend fun createMediaMetadata(
        context: Context,
        item: SearchResponse?,
        activeEpisode: Episode?
    ): MediaMetadata = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // Create MediaMetadata
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(item?.name ?: activeEpisode?.name ?: "Video")
            .setDisplayTitle(item?.name ?: activeEpisode?.name ?: "Video")
            .setArtist(activeEpisode?.name ?: item?.apiName ?: "Cloudstream")

        // Try to load artwork
        val posterUrl = activeEpisode?.posterUrl ?: item?.posterUrl
        if (!posterUrl.isNullOrEmpty()) {
            try {
                // Use applicationContext to avoid leaks if possible, but CarContext is fine for this scope usually
                val request = ImageRequest.Builder(context)
                    .data(posterUrl)
                    .size(512, 512) // Optimize size for metadata
                    .build()
                val result = SingletonImageLoader.get(context).execute(request)
                val bitmap = result.image?.asDrawable(context.resources)?.toBitmap()
                if (bitmap != null) {
                    metadataBuilder.setArtworkData(
                        bitmap.let {
                            val stream = java.io.ByteArrayOutputStream()
                            it.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            stream.toByteArray()
                        },
                        MediaMetadata.PICTURE_TYPE_FRONT_COVER
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load artwork for metadata", e)
            }
        }
        metadataBuilder.build()
    }
}
