package com.lagradost.cloudstream3.actions.temp

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.lagradost.cloudstream3.actions.OpenInAppAction
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.txt

/**
 * MX Player integration. The selected extractor URL is passed directly to MX Player;
 * no alternate quality or source is selected by this action.
 */
open class MxPlayerPackage(
    packageId: String = "com.mxtech.videoplayer.ad",
    label: String = "MX Player"
) : OpenInAppAction(
    appName = txt(label),
    packageName = packageId
) {
    override val oneSource: Boolean = true

    override val sourceTypes: Set<ExtractorLinkType> =
        setOf(ExtractorLinkType.VIDEO, ExtractorLinkType.M3U8, ExtractorLinkType.DASH)

    override suspend fun putExtra(
        context: Context,
        intent: Intent,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        val selected = index?.let { result.links.getOrNull(it) }
            ?: result.links.singleOrNull()
            ?: throw IllegalArgumentException("Choose a server before opening MX Player")

        intent.action = Intent.ACTION_VIEW
        intent.setDataAndType(selected.url.toUri(), "video/*")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    override fun onResult(activity: Activity, intent: Intent?) = Unit
}

class MxPlayerProPackage : MxPlayerPackage(
    packageId = "com.mxtech.videoplayer.pro",
    label = "MX Player Pro"
)
