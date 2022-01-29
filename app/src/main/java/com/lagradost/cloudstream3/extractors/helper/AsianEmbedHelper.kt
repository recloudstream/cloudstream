package com.lagradost.cloudstream3.extractors.helper

import android.util.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class AsianEmbedHelper {
    companion object {
        suspend fun getUrls(url: String, callback: (ExtractorLink) -> Unit) {
            if (url.startsWith("https://asianembed.io")) {
                // Fetch links
                val doc = app.get(url).document
                val links = doc.select("div#list-server-more > ul > li.linkserver")
                if (!links.isNullOrEmpty()) {
                    links.forEach {
                        val datavid = it.attr("data-video") ?: ""
                        //Log.i("AsianEmbed", "Result => (datavid) ${datavid}")
                        if (datavid.isNotEmpty()) {
                            val res = loadExtractor(datavid, url, callback)
                            Log.i("AsianEmbed", "Result => ($res) (datavid) ${datavid}")
                        }
                    }
                }
            }
        }
    }
}