package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.mozilla.javascript.Context

class StreamTapeNet : StreamTape() {
    override var mainUrl = "https://streamtape.net"
}

class StreamTapeXyz : StreamTape() {
    override var mainUrl = "https://streamtape.xyz"
}

class ShaveTape : StreamTape() {
    override var mainUrl = "https://shavetape.cash"
}

open class StreamTape : ExtractorApi() {
    override var name = "StreamTape"
    override var mainUrl = "https://streamtape.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(app.get(url)) {
            var result =
                this.document.select("script").firstOrNull { it.html().contains("botlink').innerHTML") }
                    ?.html()?.lines()?.firstOrNull{ it.contains("botlink').innerHTML") }?.let {
                        val scriptContent =
                            it.substringAfter(").innerHTML").replaceFirst("=", "var url =")
                        val rhino = Context.enter()
                        rhino.setInterpretedMode(true)
                        val scope = rhino.initStandardObjects()
                        var result = ""
                        try {
                            rhino.evaluateString(scope, scriptContent, "url", 1, null)
                            result = scope.get("url", scope).toString()
                        }finally {
                            rhino.close()
                        }
                        result
                    }
            if(!result.isNullOrEmpty()){
                val extractedUrl = "https:${result}&stream=1"
                return listOf(
                    newExtractorLink(
                        name,
                        name,
                        extractedUrl,
                    ) {
                        this.referer = url
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }
        return null
    }
}
