package com.lagradost.cloudstream3.utils.downloader

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil3.Extras
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.result.ExtractorSubtitleLink
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.getFileName
import com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.getFolder
import com.lagradost.cloudstream3.utils.txt
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

/** Separate object with helper functions for the downloader */
object DownloadUtils {
    private val cachedBitmaps = hashMapOf<String, Bitmap>()
    internal fun Context.getImageBitmapFromUrl(
        url: String,
        headers: Map<String, String>? = null
    ): Bitmap? = safe {
        if (cachedBitmaps.containsKey(url)) {
            return@safe cachedBitmaps[url]
        }

        val imageLoader = SingletonImageLoader.get(this)

        val request = ImageRequest.Builder(this)
            .data(url)
            .apply {
                headers?.forEach { (key, value) ->
                    extras[Extras.Key<String>(key)] = value
                }
            }
            .build()

        val bitmap = runBlocking {
            val result = imageLoader.execute(request)
            (result as? SuccessResult)?.image?.asDrawable(applicationContext.resources)
                ?.toBitmap()
        }

        bitmap?.let {
            cachedBitmaps[url] = it
        }

        return@safe bitmap
    }

    //calculate the time
    internal fun getEstimatedTimeLeft(
        context: Context,
        bytesPerSecond: Long,
        progress: Long,
        total: Long
    ): String {
        if (bytesPerSecond <= 0) return ""
        val timeInSec = (total - progress) / bytesPerSecond
        val hrs = timeInSec / 3600
        val mins = (timeInSec % 3600) / 60
        val secs = timeInSec % 60
        val timeFormated: UiText? = when {
            hrs > 0 -> txt(
                R.string.download_time_left_hour_min_sec_format,
                hrs,
                mins,
                secs
            )

            mins > 0 -> txt(
                R.string.download_time_left_min_sec_format,
                mins,
                secs
            )

            secs > 0 -> txt(
                R.string.download_time_left_sec_format,
                secs
            )

            else -> null
        }
        return timeFormated?.asString(context) ?: ""
    }

    internal fun downloadSubtitle(
        context: Context?,
        link: ExtractorSubtitleLink,
        fileName: String,
        folder: String
    ) {
        ioSafe {
            VideoDownloadManager.downloadThing(
                context ?: return@ioSafe,
                link,
                "$fileName ${link.name}",
                folder,
                if (link.url.contains(".srt")) "srt" else "vtt",
                false,
                null, createNotificationCallback = {}
            )
        }
    }

    fun downloadSubtitle(
        context: Context?,
        link: SubtitleData,
        meta: DownloadObjects.DownloadEpisodeMetadata,
    ) {
        context?.let { ctx ->
            val fileName = getFileName(ctx, meta)
            val folder = getFolder(meta.type ?: return, meta.mainName)
            downloadSubtitle(
                ctx,
                ExtractorSubtitleLink(link.name, link.url, "", link.headers),
                fileName,
                folder
            )
        }
    }


    /** Helper function to make sure duplicate attributes don't get overridden or inserted without lowercase cmp
     * example: map("a" to 1) appendAndDontOverride map("A" to 2, "a" to 3, "c" to 4) = map("a" to 1, "c" to 4)
     * */
    internal fun <V> Map<String, V>.appendAndDontOverride(rhs: Map<String, V>): Map<String, V> {
        val out = this.toMutableMap()
        val current = this.keys.map { it.lowercase() }
        for ((key, value) in rhs) {
            if (current.contains(key.lowercase())) continue
            out[key] = value
        }
        return out
    }

    internal fun List<Job>.cancel() {
        forEach { job ->
            try {
                job.cancel()
            } catch (t: Throwable) {
                logError(t)
            }
        }
    }

    internal suspend fun List<Job>.join() {
        forEach { job ->
            try {
                job.join()
            } catch (t: Throwable) {
                logError(t)
            }
        }
    }
}