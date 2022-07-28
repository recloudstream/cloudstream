package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.animeproviders.NineAnimeProvider.Companion.cipher
import com.lagradost.cloudstream3.animeproviders.NineAnimeProvider.Companion.encrypt
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class Vidstreamz : WcoStream() {
    override var mainUrl = "https://vidstreamz.online"
}

class Vizcloud : WcoStream() {
    override var mainUrl = "https://vizcloud2.ru"
}

class Vizcloud2 : WcoStream() {
    override var mainUrl = "https://vizcloud2.online"
}

class VizcloudOnline : WcoStream() {
    override var mainUrl = "https://vizcloud.online"
}

class VizcloudXyz : WcoStream() {
    override var mainUrl = "https://vizcloud.xyz"
}

class VizcloudLive : WcoStream() {
    override var mainUrl = "https://vizcloud.live"
}

class VizcloudInfo : WcoStream() {
    override var mainUrl = "https://vizcloud.info"
}

class MwvnVizcloudInfo : WcoStream() {
    override var mainUrl = "https://mwvn.vizcloud.info"
}

class VizcloudDigital : WcoStream() {
    override var mainUrl = "https://vizcloud.digital"
}

class VizcloudCloud : WcoStream() {
    override var mainUrl = "https://vizcloud.cloud"
}

class VizcloudSite : WcoStream() {
    override var mainUrl = "https://vizcloud.site"
}

open class WcoStream : ExtractorApi() {
    override var name = "VidStream" // Cause works for animekisa and wco
    override var mainUrl = "https://vidstream.pro"
    override val requiresReferer = false
    private val regex = Regex("(.+?/)e(?:mbed)?/([a-zA-Z0-9]+)")

    companion object {
        // taken from https://github.com/saikou-app/saikou/blob/b35364c8c2a00364178a472fccf1ab72f09815b4/app/src/main/java/ani/saikou/parsers/anime/extractors/VizCloud.kt
        // GNU General Public License v3.0 https://github.com/saikou-app/saikou/blob/main/LICENSE.md
        private var lastChecked = 0L
        private const val jsonLink =
            "https://raw.githubusercontent.com/chenkaslowankiya/BruhFlow/main/keys.json"
        private var cipherKey: VizCloudKey? = null
        suspend fun getKey(): VizCloudKey {
            cipherKey =
                if (cipherKey != null && (lastChecked - System.currentTimeMillis()) < 1000 * 60 * 30) cipherKey!!
                else {
                    lastChecked = System.currentTimeMillis()
                    app.get(jsonLink).parsed()
                }
            return cipherKey!!
        }

        data class VizCloudKey(
            @JsonProperty("cipherKey") val cipherKey: String,
            @JsonProperty("mainKey") val mainKey: String,
            @JsonProperty("encryptKey") val encryptKey: String,
            @JsonProperty("dashTable") val dashTable: String
        )

        private const val baseTable =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+=/_"

        private fun dashify(id: String, dashTable: String): String {
            val table = dashTable.split(" ")
            return id.mapIndexedNotNull { i, c ->
                table.getOrNull((baseTable.indexOf(c) * 16) + (i % 16))
            }.joinToString("-")
        }
    }

    //private val key = "LCbu3iYC7ln24K7P" // key credits @Modder4869
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val group = regex.find(url)?.groupValues!!

        val host = group[1]
        val viz = getKey()
        val id = encrypt(
            cipher(
                viz.cipherKey,
                encrypt(group[2], viz.encryptKey).also { println(it) }
            ).also { println(it) },
            viz.encryptKey
        ).also { println(it) }

        val link =
            "${host}mediainfo/${dashify(id, viz.dashTable)}?key=${viz.mainKey}" //
        val response = app.get(link, referer = referer)

        data class Sources(@JsonProperty("file") val file: String)
        data class Media(@JsonProperty("sources") val sources: List<Sources>)
        data class Data(@JsonProperty("media") val media: Media)
        data class Response(@JsonProperty("data") val data: Data)


        if (!response.text.startsWith("{")) throw ErrorLoadingException("Seems like 9Anime kiddies changed stuff again, Go touch some grass for bout an hour Or use a different Server")
        return response.parsed<Response>().data.media.sources.map {
            ExtractorLink(name, it.file,it.file,host,Qualities.Unknown.value,it.file.contains(".m3u8"))
        }

    }
}
