package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.annotation.WorkerThread
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.log2

const val MAX_LOD = 6
const val MIN_LOD = 3

class PreviewGenerator {
    // lod = level of detail where the number indicates how many ones there is
    // 2^(lod-1) = images
    private var loadedLod = 0
    private var loadedImages = 0
    private var images = Array<Bitmap?>((1 shl MAX_LOD) - 1) {
        null
    }

    fun hasPreview(): Boolean {
        synchronized(images) {
            return loadedLod >= MIN_LOD
        }
    }

    val TAG = "PreviewImg"

    fun getPreviewImage(fraction: Float): Bitmap? {
        synchronized(images) {
            if (loadedLod < MIN_LOD) {
                Log.i(TAG, "Requesting preview for $fraction but $loadedLod < $MIN_LOD")
                return null
            }
            Log.i(TAG, "Requesting preview for $fraction")

            var bestIdx = 0
            var bestDiff = 0.5f.minus(fraction).absoluteValue

            // this should be done mathematically, but for now we just loop all images
            for (l in 1..loadedLod + 1) {
                val items = (1 shl (l - 1))
                for (i in 0 until items) {
                    val idx = items - 1 + i
                    if (idx > loadedImages) {
                        break
                    }
                    val currentFraction =
                        (1.0f.div((1 shl l).toFloat()) + i * 1.0f.div(items.toFloat()))
                    val diff = currentFraction.minus(fraction).absoluteValue
                    if (diff < bestDiff) {
                        bestDiff = diff
                        bestIdx = idx
                    }
                }
            }
            Log.i(TAG, "Best diff found at ${bestDiff * 100}% diff (${bestIdx})")
            return images[bestIdx]
        }
    }

    // also check out https://github.com/wseemann/FFmpegMediaMetadataRetriever
    private val retriever: MediaMetadataRetriever = MediaMetadataRetriever()

    fun clear(keepCache: Boolean = false) {
        if (keepCache) return
        synchronized(images) {
            loadedLod = 0
            loadedImages = 0
            images.fill(null)
        }
    }

    private var currentJob: Job? = null
    fun load(keepCache: Boolean, url: String, headers: Map<String, String>) {
        currentJob?.cancel()
        currentJob = ioSafe {
            Log.i(TAG, "Loading with url = $url headers = $headers")
            clear(keepCache)
            retriever.setDataSource(url, headers)
            start(this)
        }
    }

    fun load(keepCache: Boolean, context: Context, uri: Uri) {
        currentJob?.cancel()
        currentJob = ioSafe {
            Log.i(TAG, "Loading with uri = $uri")
            clear(keepCache)
            retriever.setDataSource(context, uri)
            start(this)
        }
    }

    fun release() {
        currentJob?.cancel()
        clear(false)
    }

    @Throws
    @WorkerThread
    private fun start(scope: CoroutineScope) {
        Log.i(TAG, "Started loading preview")

        val durationMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
                ?: throw IllegalArgumentException("Bad video duration")
        val durationUs = (durationMs * 1000L).toFloat()
        //val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: throw IllegalArgumentException("Bad video width")
        //val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: throw IllegalArgumentException("Bad video height")

        // log2 # 10s durations in the video ~= how many segments we have
        val maxLod = ceil(log2((durationMs / 10_000).toFloat())).toInt().coerceIn(MIN_LOD, MAX_LOD)

        for (l in 1..maxLod) {
            val items = (1 shl (l - 1))
            for (i in 0 until items) {
                val idx = items - 1 + i // as sum(prev) = cur-1
                // frame = 100 / 2^lod + i * 100 / 2^(lod-1) = duration % where lod is one indexed
                val fraction = (1.0f.div((1 shl l).toFloat()) + i * 1.0f.div(items.toFloat()))
                Log.i(TAG, "Generating preview for ${fraction * 100}%")
                val frame = durationUs * fraction
                val img = retriever.getFrameAtTime(
                    frame.toLong(),
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (!scope.isActive) return
                synchronized(images) {
                    images[idx] = img
                    loadedImages = maxOf(loadedImages,idx)
                }
            }

            synchronized(images) {
                loadedLod = maxOf(loadedLod, l)
            }
        }
    }
}