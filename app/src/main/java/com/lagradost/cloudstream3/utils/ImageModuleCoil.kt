package com.lagradost.cloudstream3.utils

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import android.widget.ImageView
import androidx.annotation.DrawableRes
import coil3.EventListener
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.decode.BitmapFactoryDecoder
import coil3.disk.DiskCache
import coil3.dispose
import coil3.load
import coil3.memory.MemoryCache
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.network.buildDefaultClient
import okio.Path.Companion.toOkioPath
import java.io.File
import java.nio.ByteBuffer

object ImageLoader {
    private const val TAG = "CoilImgLoader"
    internal fun buildImageLoader(context: PlatformContext): ImageLoader {
        val isBrokenHardware = hasPotentialBrokenHardware()
        return ImageLoader.Builder(context)
            .crossfade(200)
            .allowHardware(SDK_INT >= 28 && !isBrokenHardware)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.1)//10 % of heap for mem-cache
                    .strongReferencesEnabled(false)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("cs3_image_cache").toOkioPath())
                    .maxSizeBytes(512L * 1024 * 1024) // 512 MB
                    .maxSizePercent(0.04) // max 4% of storage for disk caching
                    .build()
            }
            /** Pass interceptors with care, unnecessary passing tokens to servers
            or image hosting services causes unauthorized exceptions **/
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { buildDefaultClient(context) }))
                if (isBrokenHardware) {
                    add(BitmapFactoryDecoder.Factory())
                } // sw decoder
            }
            .apply {
                if (isBrokenHardware) { // coil will auto choose optimal config on modern device
                    bitmapConfig(Bitmap.Config.ARGB_8888)
                }
                setupCoilLogger()
            }
            .build()
    }

    /** DebugLogger on debug builds which won't slow down release builds & use EventListener for
    Errors on release builds. **/
    internal fun ImageLoader.Builder.setupCoilLogger() {
        if (BuildConfig.DEBUG) {
            logger(DebugLogger())
        } else {
            eventListener(object : EventListener() {
                override fun onError(request: ImageRequest, result: ErrorResult) {
                    super.onError(request, result)
                    Log.e(TAG, "Image load error: ${result.throwable.message ?: result.throwable}")
                    Log.e(TAG, "  URL: ${request.data}")
                    Log.e(TAG, "  allowHardware: ${request.allowHardware}")
                    Log.e(TAG, "  hardware: ${Build.HARDWARE}, board: ${Build.BOARD}")
                }
            })
        }
    }

    /** coil's built in loader attached w/ global synchronized instance **/
    private fun ImageView.loadImageInternal(
        imageData: Any?,
        headers: Map<String, String>? = null,
        builder: ImageRequest.Builder.() -> Unit = {} // for placeholder, error & transformations
    ) {
        // clear image to avoid loading & flickering issue at fast scrolling (~recycler view/lazy column)
        this.dispose()
        if (imageData == null) return
        // setImageResource is better than coil3 on resources due to attr
        if (imageData is Int) {
            this.setImageResource(imageData); return
        }
        // headers can be overridden by extensions.
        this.load(imageData, SingletonImageLoader.get(context)) {
            this.httpHeaders(NetworkHeaders.Builder().also { headerBuilder ->
                headerBuilder["User-Agent"] = USER_AGENT
                headers?.forEach { (key, value) ->
                    headerBuilder[key] = value
                }
            }.build())
            builder() // if passed
        }
    }

    private fun hasPotentialBrokenHardware(): Boolean {
        val hardware = Build.HARDWARE?.lowercase() ?: ""
        val board = Build.BOARD?.lowercase() ?: ""
        val model = Build.MODEL?.lowercase() ?: ""
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        val allwinnerPatterns = listOf("sun50iw9", "h713", "allwinner", "sunxi")
        val problematicModels =
            listOf("hy320", "hy300", "a10plus", "magcubic", "sinoy", "android tv box")
        return allwinnerPatterns.any { it in hardware || it in board || it in manufacturer } ||
                problematicModels.any { it in model }
    }

    /** TYPE_SAFE_LOADERS **/
    fun ImageView.loadImage(
        imageData: UiImage?,
        builder: ImageRequest.Builder.() -> Unit = {}
    ) = when (imageData) {
        is UiImage.Image -> loadImageInternal(
            imageData = imageData.url,
            headers = imageData.headers,
            builder = builder
        )

        is UiImage.Bitmap -> loadImageInternal(imageData = imageData.bitmap, builder = builder)
        is UiImage.Drawable -> loadImageInternal(imageData = imageData.resId, builder = builder)
        null -> loadImageInternal(null, builder = builder)
    }

    fun ImageView.loadImage(
        imageData: String?,
        headers: Map<String, String>? = null,
        builder: ImageRequest.Builder.() -> Unit = {}
    ) = loadImageInternal(imageData = imageData, headers = headers, builder = builder)

    fun ImageView.loadImage(
        imageData: Uri?,
        headers: Map<String, String>? = null,
        builder: ImageRequest.Builder.() -> Unit = {}
    ) = loadImageInternal(imageData = imageData, headers = headers, builder = builder)

    fun ImageView.loadImage(
        imageData: File?,
        builder: ImageRequest.Builder.() -> Unit = {}
    ) = loadImageInternal(imageData = imageData, builder = builder)

    fun ImageView.loadImage(
        @DrawableRes imageData: Int?,
        builder: ImageRequest.Builder.() -> Unit = {}
    ) = loadImageInternal(imageData = imageData, builder = builder)

    fun ImageView.loadImage(
        imageData: Drawable?,
        builder: ImageRequest.Builder.() -> Unit = {}
    ) = loadImageInternal(imageData = imageData, builder = builder)

    fun ImageView.loadImage(
        imageData: Bitmap?,
        builder: ImageRequest.Builder.() -> Unit = {}
    ) = loadImageInternal(imageData = imageData, builder = builder)

    fun ImageView.loadImage(
        imageData: ByteArray?,
        builder: ImageRequest.Builder.() -> Unit = {}
    ) = loadImageInternal(imageData = imageData, builder = builder)

    fun ImageView.loadImage(
        imageData: ByteBuffer?,
        builder: ImageRequest.Builder.() -> Unit = {}
    ) = loadImageInternal(imageData = imageData, builder = builder)
}
