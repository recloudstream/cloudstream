package com.lagradost.cloudstream3.utils

import android.graphics.Bitmap
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import java.util.concurrent.atomic.AtomicBoolean

internal class ImageCompatibilityInterceptor : Interceptor {
    private val softwareRequired = AtomicBoolean(false)

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        if (request.allowHardware && softwareRequired.get()) {
            return chain.withRequest(request.newBuilder().allowHardware(false)
                .bitmapConfig(Bitmap.Config.ARGB_8888).build()).proceed()
        }
        val result = chain.proceed()
        if (request.allowHardware && result is ErrorResult &&
            HardwareImageFailure.isRecoverable(result.throwable)) {
            softwareRequired.set(true)
            return chain.withRequest(request.newBuilder().allowHardware(false)
                .bitmapConfig(Bitmap.Config.ARGB_8888).build()).proceed()
        }
        return result
    }
}
