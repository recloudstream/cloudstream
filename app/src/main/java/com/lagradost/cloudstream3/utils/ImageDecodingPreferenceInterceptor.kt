package com.lagradost.cloudstream3.utils

import android.graphics.Bitmap
import androidx.preference.PreferenceManager
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig

internal class ImageDecodingPreferenceInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val softwareSelected = PreferenceManager.getDefaultSharedPreferences(request.context)
            .getString("image_decoding_mode", "auto") == "software"
        if (!softwareSelected || !request.allowHardware) return chain.proceed()
        return chain.withRequest(request.newBuilder().allowHardware(false)
            .bitmapConfig(Bitmap.Config.ARGB_8888).build()).proceed()
    }
}
