package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.graphics.scale
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
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

data class ImageParams(
    val width: Int,
    val height: Int,
) {
    companion object {
        val DEFAULT = ImageParams(200, 320)
        fun new16by9(width: Int): ImageParams {
            if (width < 100) {
                return DEFAULT
            }
            return ImageParams(
                width / 4,
                (width * 9) / (4 * 16)
            )
        }
    }

    init {
        assert(width > 0 && height > 0)
    }
}

interface IPreviewGenerator {
    fun hasPreview(): Boolean
    fun getPreviewImage(fraction: Float): Bitmap?
    fun release()

    var params: ImageParams

    var durationMs: Long
    var loadedImages: Int

    companion object {
        fun new(): IPreviewGenerator {
            val userDisabled = AcraApplication.context?.let { ctx ->
                PreferenceManager.getDefaultSharedPreferences(ctx)?.getBoolean(
                    ctx.getString(R.string.preview_seekbar_key), true
                ) == false
            } ?: false
            /** because TV has low ram + not show we disable this for now */
            return if (isLayout(TV) || userDisabled) {
                empty()
            } else {
                PreviewGenerator()
            }
        }

        fun empty(): IPreviewGenerator {
            return NoPreviewGenerator()
        }
    }
}

private fun rescale(image: Bitmap, params: ImageParams): Bitmap {
    if (image.width <= params.width && image.height <= params.height) return image
    val new = image.scale(params.width, params.height)
    // throw away the old image
    if (new != image) {
        image.recycle()
    }
    return new
}

/** rescale to not take up as much memory */
private fun MediaMetadataRetriever.image(timeUs: Long, params: ImageParams): Bitmap? {
    /*if (timeUs <= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        try {
            val primary = this.primaryImage
            if (primary != null) {
                return rescale(primary, params)
            }
        } catch (t: Throwable) {
            logError(t)
        }
    }*/

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        this.getScaledFrameAtTime(
            timeUs,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            params.width,
            params.height
        )
    } else {
        return rescale(this.getFrameAtTime(timeUs) ?: return null, params)
    }
}

/** PreviewGenerator that hides the implementation details of the sub generators that is used, used for source switch cache */
class PreviewGenerator : IPreviewGenerator {

    /** the most up to date generator, will always mirror the actual source in the player */
    private var currentGenerator: IPreviewGenerator = NoPreviewGenerator()

    /** the longest generated preview of the same episode */
    private var lastGenerator: IPreviewGenerator = NoPreviewGenerator()

    /** always NoPreviewGenerator, used as a cache for nothing */
    private val dummy: IPreviewGenerator = NoPreviewGenerator()

    /** if the current generator is the same as the last by checking time */
    private fun isSameLength(): Boolean =
        currentGenerator.durationMs.minus(lastGenerator.durationMs).absoluteValue < 10_000L

    /** use the backup if the current generator is init or if they have the same length */
    private val backupGenerator: IPreviewGenerator
        get() {
            if (currentGenerator.durationMs == 0L || isSameLength()) {
                return lastGenerator
            }
            return dummy
        }

    override fun hasPreview(): Boolean {
        return currentGenerator.hasPreview() || backupGenerator.hasPreview()
    }

    override fun getPreviewImage(fraction: Float): Bitmap? {
        return try {
            currentGenerator.getPreviewImage(fraction) ?: backupGenerator.getPreviewImage(fraction)
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    override fun release() {
        lastGenerator.release()
        currentGenerator.release()
        lastGenerator = NoPreviewGenerator()
        currentGenerator = NoPreviewGenerator()
    }

    override var params: ImageParams = ImageParams.DEFAULT
        set(value) {
            field = value
            lastGenerator.params = value
            backupGenerator.params = value
            currentGenerator.params = value
        }

    override var durationMs: Long
        get() = currentGenerator.durationMs
        set(_) {}
    override var loadedImages: Int
        get() = currentGenerator.loadedImages
        set(_) {}

    fun clear(keepCache: Boolean) {
        if (keepCache) {
            if (!isSameLength() || currentGenerator.loadedImages >= lastGenerator.loadedImages || lastGenerator.durationMs == 0L) {
                // the current generator is better than the last generator, therefore keep the current
                // or the lengths are not the same, therefore favoring the more recent selection

                // if they are the same we favor the current generator
                lastGenerator.release()
                lastGenerator = currentGenerator
            } else {
                // otherwise just keep the last generator and throw away the current generator
                currentGenerator.release()
            }
        } else {
            // we switched the episode, therefore keep nothing
            lastGenerator.release()
            lastGenerator = NoPreviewGenerator()
            currentGenerator.release()
            // we assume that we set currentGenerator right after this, so currentGenerator != NoPreviewGenerator
        }
    }

    fun load(link: ExtractorLink, keepCache: Boolean) {
        clear(keepCache)

        when (link.type) {
            ExtractorLinkType.M3U8 -> {
                currentGenerator = M3u8PreviewGenerator(params).apply {
                    load(url = link.url, headers = link.getAllHeaders())
                }
            }

            ExtractorLinkType.VIDEO -> {
                currentGenerator = Mp4PreviewGenerator(params).apply {
                    load(url = link.url, headers = link.getAllHeaders())
                }
            }

            else -> {
                Log.i("PreviewImg", "unsupported format for $link")
            }
        }
    }

    fun load(context: Context, link: ExtractorUri, keepCache: Boolean) {
        clear(keepCache)
        currentGenerator = Mp4PreviewGenerator(params).apply {
            load(keepCache = keepCache, context = context, uri = link.uri)
        }
    }
}

@Suppress("UNUSED_PARAMETER")
private class NoPreviewGenerator : IPreviewGenerator {
    override fun hasPreview(): Boolean = false
    override fun getPreviewImage(fraction: Float): Bitmap? = null
    override fun release() = Unit
    override var params: ImageParams
        get() = ImageParams(0, 0)
        set(value) {}
    override var durationMs: Long = 0L
    override var loadedImages: Int = 0
}

private class M3u8PreviewGenerator(override var params: ImageParams) : IPreviewGenerator {
    // generated images 1:1 to idx of hsl
    private var images: Array<Bitmap?> = arrayOf()

    companion object {
        private const val TAG = "PreviewImgM3u8"
    }


    // prefixSum[i] = sum(hsl.ts[0..i].time)
    // where [0] = 0, [1] = hsl.ts[0].time aka time at start of segment, do [b] - [a] for range a,b
    private var prefixSum: Array<Double> = arrayOf()

    // how many images has been generated
    override var loadedImages: Int = 0

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
            for (i in images.indices) {
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

    private fun clear() {
        synchronized(images) {
            currentJob?.cancel()
            // for (i in images.indices) {
            //     images[i]?.recycle()
            // }
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

    override var durationMs: Long = 0L

    private var currentJob: Job? = null
    fun load(url: String, headers: Map<String, String>) {
        clear()
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
                    M3u8Helper.M3u8Stream(
                        streamUrl = url,
                        headers = headers
                    ),
                    selectBest = false,
                    requireAudio = false,
                )

                // no support for encryption atm
                if (hsl.isEncrypted) {
                    Log.i(TAG, "m3u8 is encrypted")
                    totalImages = 0
                    return@withContext
                }

                // total duration of the entire m3u8 in seconds
                val duration = hsl.allTsLinks.sumOf { it.time ?: 0.0 }
                durationMs = (duration * 1000.0).toLong()
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
                            val img = retriever.image(0, params)
                            if (!isActive) {
                                return@withContext
                            }
                            if (img == null || img.width <= 1 || img.height <= 1) continue
                            synchronized(images) {
                                images[index] = img
                                loadedImages += 1
                            }
                        } catch (t: Throwable) {
                            logError(t)
                            continue
                        }
                    }
                }

            }
        }
    }
}

private class Mp4PreviewGenerator(override var params: ImageParams) : IPreviewGenerator {
    // lod = level of detail where the number indicates how many ones there is
    // 2^(lod-1) = images
    private var loadedLod = 0
    override var loadedImages = 0
    private var images = Array<Bitmap?>((1 shl MAX_LOD) - 1) {
        null
    }

    companion object {
        private const val TAG = "PreviewImgMp4"
    }

    override fun hasPreview(): Boolean {
        synchronized(images) {
            return loadedLod >= MIN_LOD
        }
    }

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
                    if (images[idx] == null) {
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

    private fun clear(keepCache: Boolean) {
        if (keepCache) return
        synchronized(images) {
            loadedLod = 0
            loadedImages = 0
            // for (i in images.indices) {
            //    images[i]?.recycle()
            //     images[i] = null
            //}
            images.fill(null)
        }
    }

    private var currentJob: Job? = null
    fun load(url: String, headers: Map<String, String>) {
        currentJob?.cancel()
        currentJob = ioSafe {
            Log.i(TAG, "Loading with url = $url headers = $headers")
            clear(true)
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

    override var durationMs: Long = 0L

    @Throws
    @WorkerThread
    private fun start(scope: CoroutineScope) {
        Log.i(TAG, "Started loading preview")

        val durationMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
                ?: throw IllegalArgumentException("Bad video duration")
        this.durationMs = durationMs
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
                val img = retriever.image(frame.toLong(), params)
                if (!scope.isActive) return
                if (img == null || img.width <= 1 || img.height <= 1) continue
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