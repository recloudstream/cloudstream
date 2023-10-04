package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.annotation.WorkerThread
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.ExtractorUri
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.M3u8Helper2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.log2

const val MAX_LOD = 6
const val MIN_LOD = 3

interface IPreviewGenerator {
    fun hasPreview(): Boolean
    fun getPreviewImage(fraction: Float): Bitmap?
    fun clear(keepCache: Boolean = false)
    fun release()
}

class PreviewGenerator : IPreviewGenerator {
    private var currentGenerator: IPreviewGenerator = NoPreviewGenerator()
    override fun hasPreview(): Boolean {
        return currentGenerator.hasPreview()
    }

    override fun getPreviewImage(fraction: Float): Bitmap? {
        return try {
            currentGenerator.getPreviewImage(fraction)
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    override fun clear(keepCache: Boolean) {
        currentGenerator.clear(keepCache)
    }

    override fun release() {
        currentGenerator.release()
    }

    fun load(link: ExtractorLink, keepCache: Boolean) {
        val gen = currentGenerator
        when (link.type) {
            ExtractorLinkType.M3U8 -> {
                if (gen is M3u8PreviewGenerator) {
                    gen.load(keepCache = keepCache, url = link.url, headers = link.getAllHeaders())
                } else {
                    currentGenerator.release()
                    currentGenerator = M3u8PreviewGenerator().apply {
                        load(keepCache = keepCache, url = link.url, headers = link.getAllHeaders())
                    }
                }
            }

            ExtractorLinkType.VIDEO -> {
                if (gen is Mp4PreviewGenerator) {
                    gen.load(keepCache = keepCache, url = link.url, headers = link.getAllHeaders())
                } else {
                    currentGenerator.release()
                    currentGenerator = Mp4PreviewGenerator().apply {
                        load(keepCache = keepCache, url = link.url, headers = link.getAllHeaders())
                    }
                }
            }

            else -> {
                Log.i("PreviewImg", "unsupported format for $link")
                currentGenerator.clear(keepCache)
            }
        }
    }

    fun load(context: Context, link: ExtractorUri, keepCache: Boolean) {
        val gen = currentGenerator
        if (gen is Mp4PreviewGenerator) {
            gen.load(keepCache = keepCache, context = context, uri = link.uri)
        } else {
            currentGenerator.release()
            currentGenerator = Mp4PreviewGenerator().apply {
                load(keepCache = keepCache, context = context, uri = link.uri)
            }
        }
    }
}

class NoPreviewGenerator : IPreviewGenerator {
    override fun hasPreview(): Boolean = false
    override fun getPreviewImage(fraction: Float): Bitmap? = null
    override fun clear(keepCache: Boolean) = Unit
    override fun release() = Unit
}

class M3u8PreviewGenerator : IPreviewGenerator {
    // generated images 1:1 to idx of hsl
    private var images: Array<Bitmap?> = arrayOf()

    private val TAG = "PreviewImgM3u8"

    // prefixSum[i] = sum(hsl.ts[0..i].time)
    // where [0] = 0, [1] = hsl.ts[0].time aka time at start of segment, do [b] - [a] for range a,b
    private var prefixSum: Array<Double> = arrayOf()

    // how many images has been generated
    private var loadedImages: Int = 0

    // how many images we can generate in total, == hsl.size ?: 0
    private var totalImages: Int = 0

    override fun hasPreview(): Boolean {
        return totalImages > 0 && loadedImages >= minOf(totalImages, 4)
    }

    override fun getPreviewImage(fraction: Float): Bitmap? {
        var bestIdx = -1
        var bestDiff = Double.MAX_VALUE
        synchronized(images) {
            // just find the best one in a for loop, we don't care about bin searching rn
            for (i in 0..images.size) {
                val diff = prefixSum[i].minus(fraction).absoluteValue
                if (diff > bestDiff) {
                    break
                }
                if (images[i] != null) {
                    bestIdx = i
                    bestDiff = diff
                }
            }
            return images.getOrNull(bestIdx)
        }
        /*
        val targetIndex = prefixSum.binarySearch(target)
        var ret = images[targetIndex]
        if (ret != null) {
            return ret
        }
        for (i in 0..images.size) {
            ret = images.getOrNull(i+targetIndex) ?:
        }*/
    }

    override fun clear(keepCache: Boolean) {
        synchronized(images) {
            currentJob?.cancel()
            images = arrayOf()
            prefixSum = arrayOf()
            loadedImages = 0
            totalImages = 0
        }
    }

    override fun release() {
        clear()
        images = arrayOf()
    }

    private var currentJob: Job? = null
    fun load(keepCache: Boolean, url: String, headers: Map<String, String>) {
        clear(keepCache)
        currentJob?.cancel()
        currentJob = ioSafe {
            withContext(Dispatchers.IO) {
                Log.i(TAG, "Loading with url = $url headers = $headers")
                //tmpFile =
                //    File.createTempFile("video", ".ts", context.cacheDir).apply {
                //        deleteOnExit()
                //    }
                val retriever = MediaMetadataRetriever()
                val hsl = M3u8Helper2.hslLazy(
                    listOf(
                        M3u8Helper.M3u8Stream(
                            streamUrl = url,
                            headers = headers
                        )
                    ),
                    selectBest = false
                )

                // no support for encryption atm
                if (hsl.isEncrypted) {
                    Log.i(TAG, "m3u8 is encrypted")
                    totalImages = 0
                    return@withContext
                }

                // total duration of the entire m3u8 in seconds
                val duration = hsl.allTsLinks.sumOf { it.time ?: 0.0 }
                val durationInv = 1.0 / duration

                // if the total duration is less then 10s then something is very wrong or
                // too short playback to matter
                if (duration <= 10.0) {
                    totalImages = 0
                    return@withContext
                }

                totalImages = hsl.allTsLinks.size

                // we cant init directly as it is no guarantee of in order
                prefixSum = Array(hsl.allTsLinks.size + 1) { 0.0 }
                var runningSum = 0.0
                for (i in hsl.allTsLinks.indices) {
                    runningSum += (hsl.allTsLinks[i].time ?: 0.0)
                    prefixSum[i + 1] = runningSum * durationInv
                }
                synchronized(images) {
                    images = Array(hsl.size) { null }
                    loadedImages = 0
                }

                val maxLod = ceil(log2(duration)).toInt().coerceIn(MIN_LOD, MAX_LOD)
                val count = hsl.allTsLinks.size
                for (l in 1..maxLod) {
                    val items = (1 shl (l - 1))
                    for (i in 0 until items) {
                        val index = (count.div(1 shl l) + (i * count) / items).coerceIn(0, hsl.size)
                        if (synchronized(images) { images[index] } != null) {
                            continue
                        }
                        Log.i(TAG, "Generating preview for $index")

                        val ts = hsl.allTsLinks[index]
                        try {
                            retriever.setDataSource(ts.url, hsl.headers)
                            if (!isActive) {
                                return@withContext
                            }
                            val img = retriever.getFrameAtTime(0)
                            if (!isActive) {
                                return@withContext
                            }
                            if(img == null || img.width <= 1 || img.height <= 1) continue
                            synchronized(images) {
                                images[index] = img
                                loadedImages += 1
                            }
                        } catch (t: Throwable) {
                            logError(t)
                            continue
                        }

                        /*
                        val buffer = hsl.resolveLinkSafe(index) ?: continue
                        tmpFile?.writeBytes(buffer)
                        val buff = FileOutputStream(tmpFile)
                        retriever.setDataSource(buff.fd)
                        val frame = retriever.getFrameAtTime(0L)*/
                    }
                }

            }
        }
    }
}

class Mp4PreviewGenerator : IPreviewGenerator {
    // lod = level of detail where the number indicates how many ones there is
    // 2^(lod-1) = images
    private var loadedLod = 0
    private var loadedImages = 0
    private var images = Array<Bitmap?>((1 shl MAX_LOD) - 1) {
        null
    }

    override fun hasPreview(): Boolean {
        synchronized(images) {
            return loadedLod >= MIN_LOD
        }
    }

    val TAG = "PreviewImgMp4"

    override fun getPreviewImage(fraction: Float): Bitmap? {
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
                    if(images[idx] == null) {
                        continue
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

    override fun clear(keepCache: Boolean) {
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

    override fun release() {
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
                if(img == null || img.width <= 1 || img.height <= 1) continue
                synchronized(images) {
                    images[idx] = img
                    loadedImages = maxOf(loadedImages, idx)
                }
            }

            synchronized(images) {
                loadedLod = maxOf(loadedLod, l)
            }
        }
    }
}