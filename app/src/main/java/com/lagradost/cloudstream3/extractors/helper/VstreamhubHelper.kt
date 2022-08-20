package com.lagradost.cloudstream3.extractors.helper

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor

class VstreamhubHelper {
    companion object {
        private val baseUrl: String = "https://vstreamhub.com"
        private val baseName: String = "Vstreamhub"

        suspend fun getUrls(
            url: String,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ) {
            if (url.startsWith(baseUrl)) {
                // Fetch links
                val doc = app.get(url).document.select("script")
                doc?.forEach {
                    val innerText = it?.toString()
                    if (!innerText.isNullOrEmpty()) {
                        if (innerText.contains("file:")) {
                            val startString = "file: "
                            val aa = innerText.substring(innerText.indexOf(startString))
                            val linkUrl =
                                aa.substring(startString.length + 1, aa.indexOf("\",")).trim()
                            //Log.i(baseName, "Result => (linkUrl) ${linkUrl}")
                            val exlink = ExtractorLink(
                                name = "$baseName m3u8",
                                source = baseName,
                                url = linkUrl,
                                quality = Qualities.Unknown.value,
                                referer = url,
                                isM3u8 = true
                            )
                            callback.invoke(exlink)
                        }
                        if (innerText.contains("playerInstance")) {
                            val aa =
                                innerText.substring(innerText.indexOf("playerInstance.addButton"))
                            val startString = "window.open(["
                            val bb = aa.substring(aa.indexOf(startString))
                            val datavid = bb.substring(startString.length, bb.indexOf("]"))
                                .removeSurrounding("\"")
                            if (datavid.isNotBlank()) {
                                loadExtractor(datavid, url, subtitleCallback, callback)
                                //Log.i(baseName, "Result => (datavid) ${datavid}")
                            }
                        }
                    }
                }
            }
        }
    }
}