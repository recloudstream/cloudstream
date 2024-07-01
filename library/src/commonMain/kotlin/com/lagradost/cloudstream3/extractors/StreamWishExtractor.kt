package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName


class WishembedPro : StreamWishExtractor() {
    override val mainUrl = "https://wishembed.pro"
}
class CdnwishCom : StreamWishExtractor() {
    override val mainUrl = "https://cdnwish.com"
}
class FlaswishCom : StreamWishExtractor() {
    override val mainUrl = "https://flaswish.com"
}
class SfastwishCom : StreamWishExtractor() {
    override val mainUrl = "https://sfastwish.com"
}
open class StreamWishExtractor : ExtractorApi() {
    override var name = "StreamWish"
    override val mainUrl = "https://streamwish.to"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(
            url,
            referer = referer,
            allowRedirects = false
        ).document
        var script = doc.select("script").find {
            it.html().contains("jwplayer(\"vplayer\").setup(")
        }
        var scriptContent = script?.html()
        val extractedurl = Regex("""sources: \[\{file:"(.*?)"""").find(scriptContent ?: "")?.groupValues?.get(1)
        if (!extractedurl.isNullOrBlank()) {
            callback(
                ExtractorLink(
                    this.name,
                    this.name,
                    extractedurl,
                    referer ?: "$mainUrl/",
                    getQualityFromName(""),
                    extractedurl.contains("m3u8")
                )
            )
        }
    }
}