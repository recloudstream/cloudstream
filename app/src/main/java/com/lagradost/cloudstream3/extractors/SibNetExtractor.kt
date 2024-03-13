// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.lagradost.cloudstream3.extractors

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class SibNet : ExtractorApi() {
    override val name            = "SibNet"
    override val mainUrl         = "https://video.sibnet.ru"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val ext_ref   = referer ?: ""
        val i_source  = app.get(url, referer=ext_ref).text
        var m3u_link  = Regex("""player.src\(\[\{src: \"([^\"]+)""").find(i_source)?.groupValues?.get(1) ?: throw ErrorLoadingException("m3u link not found")

        m3u_link = "${mainUrl}${m3u_link}"
        Log.d("Kekik_${this.name}", "m3u_link » ${m3u_link}")

        callback.invoke(
            ExtractorLink(
                source  = this.name,
                name    = this.name,
                url     = m3u_link,
                referer = url,
                quality = Qualities.Unknown.value,
                type    = INFER_TYPE
            )
        )
    }
}