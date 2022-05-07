package com.lagradost.cloudstream3.utils

import android.annotation.SuppressLint
import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.network.DdosGuardKiller
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.nicehttp.Requests
import java.io.InputStream

@GlideModule
class GlideModule : AppGlideModule() {
    @SuppressLint("CheckResult")
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        super.applyOptions(context, builder)
        builder.apply {
            RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .signature(ObjectKey(System.currentTimeMillis().toShort()))
        }
    }

    // Needed for DOH
    // https://stackoverflow.com/a/61634041
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val client =
            Requests().apply {
                defaultHeaders = mapOf("user-agent" to USER_AGENT)
            }.initClient(context)
                .newBuilder()
                .addInterceptor(DdosGuardKiller(false))
                .build()

        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpUrlLoader.Factory(client)
        )
        super.registerComponents(context, glide, registry)
    }
}