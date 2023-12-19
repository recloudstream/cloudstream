// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.lagradost.cloudstream3.extractors

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

open class HDMomPlayer : ExtractorApi() {
    override val name            = "HDMomPlayer"
    override val mainUrl         = "https://hdmomplayer.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val m3u_link:String?
        val ext_ref  = referer ?: ""
        val i_source = app.get(url, referer=ext_ref).text

        val bePlayer = Regex("""bePlayer\('([^']+)',\s*'(\{[^\}]+\})'\);""").find(i_source)?.groupValues
        if (bePlayer != null) {
            val bePlayerPass = bePlayer.get(1)
            val bePlayerData = bePlayer.get(2)
            val encrypted    = AesHelper.cryptoAESHandler(bePlayerData, bePlayerPass.toByteArray(), false)?.replace("\\", "") ?: throw ErrorLoadingException("failed to decrypt")
            Log.d("Kekik_${this.name}", "encrypted » ${encrypted}")

            m3u_link = Regex("""video_location\":\"([^\"]+)""").find(encrypted)?.groupValues?.get(1)
        } else {
            m3u_link = Regex("""file:\"([^\"]+)""").find(i_source)?.groupValues?.get(1)

            val track_str = Regex("""tracks:\[([^\]]+)""").find(i_source)?.groupValues?.get(1)
            if (track_str != null) {
                val tracks:List<Track> = jacksonObjectMapper().readValue("[${track_str}]")

                for (track in tracks) {
                    if (track.file == null || track.label == null) continue
                    if (track.label.contains("Forced")) continue

                    subtitleCallback.invoke(
                        SubtitleFile(
                            lang = track.label,
                            url  = fixUrl(mainUrl + track.file)
                        )
                    )
                }
            }
        }

        callback.invoke(
            ExtractorLink(
                source  = this.name,
                name    = this.name,
                url     = m3u_link ?: throw ErrorLoadingException("m3u link not found"),
                referer = url,
                quality = Qualities.Unknown.value,
                isM3u8  = true
            )
        )
    }

    data class Track(
        @JsonProperty("file")     val file: String?,
        @JsonProperty("label")    val label: String?,
        @JsonProperty("kind")     val kind: String?,
        @JsonProperty("language") val language: String?,
        @JsonProperty("default")  val default: String?
    )
}