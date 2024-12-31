package com.lagradost.cloudstream3.utils

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import android.widget.ImageView
import androidx.annotation.DrawableRes
import coil3.EventListener
import coil3.Extras
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.load
import coil3.memory.MemoryCache
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.network.buildDefaultClient
import okhttp3.HttpUrl
import okio.Path.Companion.toOkioPath
import java.io.File
import java.nio.ByteBuffer

object ImageLoader {

    private const val TAG = "CoilImgLoader"

    internal fun buildImageLoader(context: PlatformContext): ImageLoader {
        val okHttpClient = buildDefaultClient(context)

        return ImageLoader.Builder(context)
            .crossfade(250)
            /** !Only use default placeholders and errors, if not using this instance for local
             * image buttons because when animating this will appear or in more cases **/
            //.placeholder { getImageFromDrawable(context, R.drawable.x) }
            //.error { getImageFromDrawable(context, R.drawable.x) }
            .allowHardware(false) // SDK_INT >= 28, cant use hardware bitmaps for Palette Builder
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.1) // Use 10 % of the app's available memory for caching
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("cs3_image_cache").toOkioPath())
                    .maxSizeBytes(256 * 1024 * 1024) // 256 MB
                    .maxSizePercent(0.04) // Use 4% of the device's storage space for disk caching
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            /** Pass interceptors with care, unnecessary passing tokens to servers
            or image hosting services causes unauthorized exceptions **/
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient })) }
            .eventListener(object : EventListener() {
                override fun onStart(request: ImageRequest) {
                    super.onStart(request)
                    Log.i(TAG, "Loading Image ${request.data}")
                }

                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    super.onSuccess(request, result)
                    Log.d(TAG, "Image Loading successful")
                }

                override fun onError(request: ImageRequest, result: ErrorResult) {
                    super.onError(request, result)
                    Log.e(TAG, "Error loading image: ${result.throwable}")
                }
            })
            .build()
    }

    /** we use coil's built in loader with our global synchronized instance, this way we achieve
    latest and complete functionality as well as stability **/
    private fun ImageView.loadImageInternal(
        imageData: Any?,
        headers: Map<String, String>? = null,
        builder: ImageRequest.Builder.() -> Unit = {} // for placeholder, error & transformations
    ) {
        // clear image to avoid loading & flickering issue at fast scrolling (e.g, an image recycler)
        this.load(null)

        // Use Coil's built-in load method but with our custom module
        this.load(imageData, SingletonImageLoader.get(context)) {
            this.httpHeaders(NetworkHeaders.Builder().also { headerBuilder ->
                headerBuilder.add("User-Agent", USER_AGENT)
                headers?.forEach { (key, value) ->
                    headerBuilder.add(key,value)
                }
            }.build())

            builder() // if passed
        }
    }

    /** TYPE_SAFE_LOADERS **/
    fun ImageView.loadImage(
        imageData: UiImage?,
        builder: ImageRequest.Builder.() -> Unit = {}
    ) = when(imageData) {
        is UiImage.Image -> loadImageInternal(imageData = imageData.url, headers = imageData.headers, builder = builder)
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
    ) = loadImageInternal(imageData = imageData,headers = headers, builder = builder)

    fun ImageView.loadImage(
        imageData: HttpUrl?,
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