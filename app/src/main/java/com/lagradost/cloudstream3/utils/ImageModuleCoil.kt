package com.lagradost.cloudstream3.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.widget.ImageView
import androidx.annotation.DrawableRes
import coil.EventListener
import coil.ImageLoader
import coil.load
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.network.DdosGuardKiller
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.nio.ByteBuffer

object ImageLoader {

    private var instance: ImageLoader? = null
    private const val TAG = "CoilImgLoader"

    fun initializeCoilImageLoader(context: Context) {
        if (instance == null) {
            synchronized(lock = this) {
                instance = buildImageLoader(context.applicationContext)
            }
        }
    }

    private fun buildImageLoader(context: Context): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(DdosGuardKiller(alwaysBypass = false))
            .build()

        return ImageLoader.Builder(context)
            .crossfade(250)
            .respectCacheHeaders(false)
            /** !Only use default placeholders and errors, if not using this instance for local
             * image buttons because when animating this will appear or in more cases **/
            //.placeholder(R.drawable.logo)
            //.error(R.drawable.logo)
            .allowHardware(false) // takes a toll on battery (only allow if app is like instagram or photos)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.10) // Use 10 % of the app's available memory for caching
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .maxSizeBytes(256 * 1024 * 1024) // 256 MB
                    .directory(context.cacheDir.resolve("cs3_image_cache"))
                    .maxSizePercent(0.04) // Use 4% of the device's storage space for disk caching
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            /** Pass interceptors with care, unnecessary passing tokens to servers
            or image hosting services causes unauthorized exceptions **/
            .okHttpClient(okHttpClient)
            .eventListener(object : EventListener {
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

    /** we use coil's built in loader with our global synchronized instance, this way we achieve
    latest and complete functionality as well as stability **/
    private fun ImageView.loadImageInternal(
        imageData: Any?,
        headers: Map<String, String>? = null,
        builder: ImageRequest.Builder.() -> Unit = {} // for placeholder, error & transformations
    ) {
        // clear image to avoid loading issues at fast scrolling (e.g, an image recycler)
        this.load(null)

        // Use Coil's built-in load method but with our custom module
        this.load(imageData, instance ?: return) {
            addHeader("User-Agent", USER_AGENT)
            headers?.forEach { (key, value) ->
                addHeader(key, value)
            }
            builder()  // if passed
        }
    }
}