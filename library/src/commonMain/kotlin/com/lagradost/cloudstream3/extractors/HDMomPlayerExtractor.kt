// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.lagradost.cloudstream3.extractors

import com.lagradost.api.Log
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
        val m3uLink:String?
        val extRef  = referer ?: ""
        val iSource = app.get(url, referer=extRef).text

        val bePlayer = Regex("""bePlayer\('([^']+)',\s*'(\{[^\}]+\})'\);""").find(iSource)?.groupValues
        if (bePlayer != null) {
            val bePlayerPass = bePlayer.get(1)
            val bePlayerData = bePlayer.get(2)
            val encrypted    = AesHelper.cryptoAESHandler(bePlayerData, bePlayerPass.toByteArray(), false)?.replace("\\", "") ?: throw ErrorLoadingException("failed to decrypt")

            m3uLink = Regex("""video_location\":\"([^\"]+)""").find(encrypted)?.groupValues?.get(1)
        } else {
            m3uLink = Regex("""file:\"([^\"]+)""").find(iSource)?.groupValues?.get(1)

            val trackStr = Regex("""tracks:\[([^\]]+)""").find(iSource)?.groupValues?.get(1)
            if (trackStr != null) {
                val tracks:List<Track> = jacksonObjectMapper().readValue("[${trackStr}]")

                for (track in tracks) {
                    if (track.file == null || track.label == null) continue
                    if (track.label.contains("Forced")) continue

                    subtitleCallback.invoke(
                        newSubtitleFile(
                            lang = track.label,
                            url  = fixUrl(mainUrl + track.file)
                        )
                    )
                }
            }
        }

        callback.invoke(
            newExtractorLink(
                source  = this.name,
                name    = this.name,
                url     = m3uLink ?: throw ErrorLoadingException("m3u link not found"),
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
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