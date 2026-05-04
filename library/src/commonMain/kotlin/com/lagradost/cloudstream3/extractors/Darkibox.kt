package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

open class Darkibox : ExtractorApi() {
    override val name = "Darkibox"
    override val mainUrl = "https://darkibox.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val sources = mutableListOf<ExtractorLink>()

        // Extract file code from embed URL
        // URL format: https://darkibox.com/embed-FILECODE.html
        val fileCode = Regex("""embed-(.+?)\.html""").find(url)?.groupValues?.get(1)
            ?: url.substringAfterLast("/").substringBefore(".")

        // POST to /dl endpoint with form data
        val response = app.post(
            "$mainUrl/dl",
            data = mapOf(
                "op" to "embed",
                "file_code" to fileCode,
                "auto" to "1"
            ),
            referer = url
        ).document

        response.select("script").forEach { script ->
            if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                val unpacked = getAndUnpack(script.data())

                // Look for file parameter in PlayerJS format
                // Can be: file:"URL" or file:"[label]url,[label]url"
                val fileContent = Regex("""file\s*:\s*"([^"]+)"""").find(unpacked)
                    ?.groupValues?.get(1) ?: return@forEach

                if (fileContent.contains("[") && fileContent.contains("]")) {
                    // Multi-quality format: [label]url,[label]url
                    Regex("""\[([^\]]*)]([^,\s]+)""").findAll(fileContent).forEach { match ->
                        val label = match.groupValues[1]
                        val videoUrl = match.groupValues[2]
                        sources.add(
                            newExtractorLink(
                                name,
                                name,
                                videoUrl,
                            ) {
                                this.referer = mainUrl
                                this.quality = getQualityFromName(label)
                            }
                        )
                    }
                } else if (fileContent.contains(".m3u8")) {
                    // HLS m3u8 URL
                    val m3u8Links = M3u8Helper.generateM3u8(
                        name,
                        fileContent,
                        "$mainUrl/",
                    )
                    sources.addAll(m3u8Links)
                } else {
                    // Direct video URL
                    sources.add(
                        newExtractorLink(
                            name,
                            name,
                            fileContent,
                        ) {
                            this.referer = mainUrl
                        }
                    )
                }
            }
        }

        return sources
    }
}
