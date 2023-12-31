// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.lagradost.cloudstream3.extractors

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class ContentX : ExtractorApi() {
    override val name            = "ContentX"
    override val mainUrl         = "https://contentx.me"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val ext_ref   = referer ?: ""
        Log.d("Kekik_${this.name}", "url » ${url}")

        val i_source  = app.get(url, referer=ext_ref).text
        val i_extract = Regex("""window\.openPlayer\('([^']+)'""").find(i_source)!!.groups[1]?.value ?: throw ErrorLoadingException("i_extract is null")

        val vid_source  = app.get("https://contentx.me/source2.php?v=${i_extract}", referer=ext_ref).text
        val vid_extract = Regex("""file\":\"([^\"]+)""").find(vid_source)!!.groups[1]?.value ?: throw ErrorLoadingException("vid_extract is null")
        val m3u_link    = vid_extract.replace("\\", "")

        callback.invoke(
            ExtractorLink(
                source  = this.name,
                name    = this.name,
                url     = m3u_link,
                referer = url,
                quality = Qualities.Unknown.value,
                isM3u8  = true
            )
        )

        val i_dublaj = Regex(""",\"([^']+)\",\"Türkçe""").find(i_source)!!.groups[1]?.value
        if (i_dublaj != null) {
            val dublaj_source  = app.get("https://contentx.me/source2.php?v=${i_dublaj}", referer=ext_ref).text
            val dublaj_extract = Regex("""file\":\"([^\"]+)""").find(dublaj_source)!!.groups[1]?.value ?: throw ErrorLoadingException("dublaj_extract is null")
            val dublaj_link    = dublaj_extract.replace("\\", "")

            callback.invoke(
                ExtractorLink(
                    source  = "${this.name} Türkçe Dublaj",
                    name    = "${this.name} Türkçe Dublaj",
                    url     = dublaj_link,
                    referer = url,
                    quality = Qualities.Unknown.value,
                    isM3u8  = true
                )
            )
        }
    }
}