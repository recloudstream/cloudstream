package com.lagradost.cloudstream3.utils

import android.content.Context
import android.util.Log
import android.widget.ImageView
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
import okhttp3.OkHttpClient

object ImageLoader {

    private var instance: ImageLoader? = null

    fun initializeCoilImageLoader(context: Context) {
        if (instance == null) {
            synchronized(this) {
                instance = buildImageLoader(context.applicationContext)
            }
        }
    }

    private fun buildImageLoader(context: Context): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(DdosGuardKiller(alwaysBypass = false)) // Add DdosGuardKiller Interceptor
            .build()

        return ImageLoader.Builder(context)
            .crossfade(true)
            .respectCacheHeaders(false)
            /** !Only use default placeholders and errors, if not using this instance for local
             * image buttons because when animating this will appear or in more cases **/
            //.placeholder(R.drawable.logo)
            //.error(R.drawable.logo)
            .allowHardware(true)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.15) // Use 15% of the app's available memory for image caching
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .maxSizeBytes(128 * 1024 * 1024) // 128 MB
                    .directory(context.cacheDir.resolve("cs3_image_cache"))
                    .maxSizePercent(0.02) // Use 2% of the device's storage space for disk caching
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            /** ! Pass interceptors with care, unnecessary passing a token to servers
             * or other image service causes unauthorized exceptions **/
            .okHttpClient(okHttpClient)
            .eventListener(object : EventListener {
                override fun onStart(request: ImageRequest) {
                    super.onStart(request)
                    Log.d("ImageLoader", "Loading Image ${request.data}")
                }

                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    super.onSuccess(request, result)
                    Log.d("ImageLoader", "Image Loading successful")
                }

                override fun onError(request: ImageRequest, result: ErrorResult) {
                    super.onError(request, result)
                    Log.e("ImageLoadError", "Error loading image: ${result.throwable}")
                }
            })
            .build()
    }

    /** we use coil's built in loader with our global synchronized instance, this way we achieve
    latest and complete functionality as well as stability **/
    fun ImageView.loadImage(
        imageData: Any?,
        headers: Map<String, String>? = null,
        builder: ImageRequest.Builder.() -> Unit = {} // 'in' case extra config is needed somewhere
    ) {
        // clear image to avoid loading issues at fast scrolling
        this.setImageBitmap(null)

        // Use Coil's built-in load method but with the shared ImageLoader instance
        this.load(imageData, instance ?: return) {
            addHeader("User-Agent", USER_AGENT)
            headers?.forEach { (key, value) ->
                addHeader(key, value)
            }
            builder()  // if passed
        }
    }
}