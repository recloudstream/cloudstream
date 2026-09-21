package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.Prerelease
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities.Unknown
import com.lagradost.cloudstream3.utils.newExtractorLink

@Prerelease
class StreamRubyCom : StreamRuby() {
    override var mainUrl = "https://streamruby.com"
}

@Prerelease
open class StreamRuby : ExtractorApi() {
    override var name = "StreamRuby"
    override open var mainUrl = "https://rubyvidhub.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = url.trim().trimEnd('/')
        val fileCode = embedUrl.substringAfterLast("/")
            .removeSuffix(".html")
            .substringAfterLast("-")
        if (fileCode.isBlank()) return

        try {
            app.get(embedUrl, referer = referer ?: mainUrl)
        } catch (_: Exception) {
            return
        }

        val dlText = try {
            app.post(
                "$mainUrl/dl",
                data = mapOf(
                    "op" to "embed",
                    "file_code" to fileCode,
                    "auto" to "1",
                    "referer" to embedUrl,
                ),
                headers = mapOf("User-Agent" to USER_AGENT, "Referer" to embedUrl),
                referer = embedUrl
            ).text
        } catch (_: Exception) {
            return
        }

        val unpacked = JsUnpacker(dlText).unpack() ?: dlText

        val streamUrl = Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']""")
            .find(unpacked)?.groupValues?.get(1)
            ?: Regex("""file:\s*["']([^"']+)["']""")
                .find(unpacked)?.groupValues?.get(1)
                ?.takeIf { it.contains("/hls") || it.endsWith(".mp4") }

        if (streamUrl.isNullOrBlank()) return

        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = streamUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "$mainUrl/"
                this.quality = Unknown.value
                this.headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Origin" to mainUrl,
                    "Referer" to "$mainUrl/"
                )
            }
        )
    }
}