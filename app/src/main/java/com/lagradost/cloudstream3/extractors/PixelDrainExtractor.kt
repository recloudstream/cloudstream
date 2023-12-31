// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.lagradost.cloudstream3.extractors

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class PixelDrain : ExtractorApi() {
    override val name            = "PixelDrain"
    override val mainUrl         = "https://pixeldrain.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val ext_ref      = referer ?: ""
        val pixel_id     = Regex("""([^\/]+)(?=\?download)""").find(url)?.groupValues?.get(1)
        val downloadLink = "${mainUrl}/api/file/${pixel_id}?download"
        Log.d("Kekik_${this.name}", "downloadLink » ${downloadLink}")

        callback.invoke(
            ExtractorLink(
                source  = "pixeldrain - ${pixel_id}",
                name    = "pixeldrain - ${pixel_id}",
                url     = downloadLink,
                referer = "${mainUrl}/u/${pixel_id}?download",
                quality = Qualities.Unknown.value,
                type    = INFER_TYPE
            )
        )
    }
}