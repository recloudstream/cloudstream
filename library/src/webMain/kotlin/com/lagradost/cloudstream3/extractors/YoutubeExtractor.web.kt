package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * Web/JS target shim.
 *
 * NewPipeExtractor is JVM-only, so the full extraction logic used on
 * Android/desktop can't run here. This stub keeps the multiplatform
 * source set compiling; it performs no extraction and simply reports
 * that no links were found on this target.
 *
 * If/when a JS-compatible YouTube extraction path exists, replace the
 * body of getUrl() with that implementation.
 */
actual open class YoutubeExtractor actual constructor() : ExtractorApi() {

    actual override val mainUrl = "https://www.youtube.com"
    actual override val name = "YouTube"
    actual override val requiresReferer = false

    actual override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        // No-op on web target: NewPipeExtractor is unavailable here.
    }
}
