package com.lagradost.cloudstream3.actions.temp

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.lagradost.cloudstream3.actions.OpenInAppAction
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.txt

/**
 * https://github.com/xbmc/xbmc/
 * https://github.com/xbmc/xbmc/blob/6a524b06c89168708a9486e01d0125b03737d1f6/tools/android/packaging/xbmc/src/Main.java.in#L64
 * https://github.com/xbmc/xbmc/blob/6a524b06c89168708a9486e01d0125b03737d1f6/xbmc/platform/android/activity/XBMCApp.cpp#L1382
 */
object KodiPackage : OpenInAppAction(
    appName = txt("Kodi"),
    packageName = "org.xbmc.kodi",
    intentClass = "org.xbmc.kodi.Splash"
) {
    override val oneSource: Boolean = true
    override suspend fun putExtra(
        context: Context,
        intent: Intent,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        val link = result.links[index!!]
        val headers = link.getAllHeaders()
        /** See https://kodi.wiki/view/HTTP */
        if (headers.isNotEmpty()) {
            intent.data = "${link.url}|${
                headers.map { "${it.key}=${it.value}" }.joinToString(separator = "&")
            }".toUri()
        } else {
            intent.data = link.url.toUri()
        }
    }

    override fun onResult(activity: Activity, intent: Intent?) = Unit
}