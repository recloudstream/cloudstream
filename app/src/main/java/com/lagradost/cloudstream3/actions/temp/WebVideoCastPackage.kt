package com.lagradost.cloudstream3.actions.temp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.actions.OpenInAppAction
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.utils.ExtractorLinkType

// https://www.webvideocaster.com/integrations

class WebVideoCastPackage: OpenInAppAction(
    txt("Web Video Cast"),
    "com.instantbits.cast.webvideo"
) {

    override val oneSource = true

    override val sourceTypes = setOf(
        ExtractorLinkType.VIDEO,
        ExtractorLinkType.DASH,
        ExtractorLinkType.M3U8
    )

    override suspend fun putExtra(
        context: Context,
        intent: Intent,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        val link = result.links[index ?: 0]

        intent.apply {
            setDataAndType(Uri.parse(link.url), "video/*")

            val title = video.name ?: video.headerName

            putExtra("subs", result.subs.map { it.url.toUri() }.toTypedArray())
            putExtra("title", title)
            video.poster?.let { putExtra("poster", it) }
            val headers = Bundle().apply {
                if (link.referer.isNotBlank())
                    putString("Referer", link.referer)
                putString("User-Agent", USER_AGENT)
                for ((key, value) in link.headers) {
                    putString(key, value)
                }
            }
            putExtra("android.media.intent.extra.HTTP_HEADERS", headers)
            putExtra("secure_uri", true)
        }
    }

    override fun onResult(activity: Activity, intent: Intent?) = Unit
}