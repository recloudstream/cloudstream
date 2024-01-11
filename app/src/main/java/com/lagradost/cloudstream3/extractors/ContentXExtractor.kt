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

		val sub_urls = mutableSetOf<String>()
        Regex("""\"file\":\"([^\"]+)\",\"label\":\"([^\"]+)\"""").findAll(i_source).forEach {
            val (sub_url, sub_lang) = it.destructured

			if (sub_url in sub_urls) { return@forEach }
 			sub_urls.add(sub_url)

            subtitleCallback.invoke(
                SubtitleFile(
                    lang = sub_lang.replace("\\u0131", "ı").replace("\\u0130", "İ").replace("\\u00fc", "ü").replace("\\u00e7", "ç"),
                    url  = fixUrl(sub_url.replace("\\", ""))
                )
            )
        }

        val vid_source  = app.get("${mainUrl}/source2.php?v=${i_extract}", referer=ext_ref).text
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
            val dublaj_source  = app.get("${mainUrl}/source2.php?v=${i_dublaj}", referer=ext_ref).text
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