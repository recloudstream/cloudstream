package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import kotlin.random.Random

class Vidgomunimesb : StreamSB() {
    override var mainUrl = "https://vidgomunimesb.xyz"
}

class Sbasian : StreamSB() {
    override var mainUrl = "https://sbasian.pro"
    override var name = "Sbasian"
}

class Sbnet : StreamSB() {
    override var name = "Sbnet"
    override var mainUrl = "https://sbnet.one"
}

class Keephealth : StreamSB() {
    override var name = "Keephealth"
    override var mainUrl = "https://keephealth.info"
}

class Sbspeed : StreamSB() {
    override var name = "Sbspeed"
    override var mainUrl = "https://sbspeed.com"
}

class Streamsss : StreamSB() {
    override var mainUrl = "https://streamsss.net"
}

class Sbflix : StreamSB() {
    override var mainUrl = "https://sbflix.xyz"
    override var name = "Sbflix"
}

class Vidgomunime : StreamSB() {
    override var mainUrl = "https://vidgomunime.xyz"
}

class Sbthe : StreamSB() {
    override var mainUrl = "https://sbthe.com"
}

class Ssbstream : StreamSB() {
    override var mainUrl = "https://ssbstream.net"
}

class SBfull : StreamSB() {
    override var mainUrl = "https://sbfull.com"
}

class StreamSB1 : StreamSB() {
    override var mainUrl = "https://sbplay1.com"
}

class StreamSB2 : StreamSB() {
    override var mainUrl = "https://sbplay2.com"
}

class StreamSB3 : StreamSB() {
    override var mainUrl = "https://sbplay3.com"
}

class StreamSB4 : StreamSB() {
    override var mainUrl = "https://cloudemb.com"
}

class StreamSB5 : StreamSB() {
    override var mainUrl = "https://sbplay.org"
}

class StreamSB6 : StreamSB() {
    override var mainUrl = "https://embedsb.com"
}

class StreamSB7 : StreamSB() {
    override var mainUrl = "https://pelistop.co"
}

class StreamSB8 : StreamSB() {
    override var mainUrl = "https://streamsb.net"
}

class StreamSB9 : StreamSB() {
    override var mainUrl = "https://sbplay.one"
}

class StreamSB10 : StreamSB() {
    override var mainUrl = "https://sbplay2.xyz"
}

class StreamSB11 : StreamSB() {
    override var mainUrl = "https://sbbrisk.com"
}

class Sblongvu : StreamSB() {
    override var mainUrl = "https://sblongvu.com"
}

open class StreamSB : ExtractorApi() {
    override var name = "StreamSB"
    override var mainUrl = "https://watchsb.com"
    override val requiresReferer = false
    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val regexID =
            Regex("(embed-[a-zA-Z\\d]{0,8}[a-zA-Z\\d_-]+|/e/[a-zA-Z\\d]{0,8}[a-zA-Z\\d_-]+)")
        val id = regexID.findAll(url).map {
            it.value.replace(Regex("(embed-|/e/)"), "")
        }.first()
        val master = "$mainUrl/375664356a494546326c4b797c7c6e756577776778623171737/${encodeId(id)}"
        val headers = mapOf(
            "watchsb" to "sbstream",
        )
        val mapped = app.get(
            master.lowercase(),
            headers = headers,
            referer = url,
        ).parsedSafe<Main>()
        M3u8Helper.generateM3u8(
            name,
            mapped?.streamData?.file ?: return,
            url,
            headers = headers
        ).forEach(callback)

        mapped.streamData.subs?.map {sub ->
            subtitleCallback.invoke(
                SubtitleFile(
                    sub.label.toString(),
                    sub.file ?: return@map null,
                )
            )
        }
    }

    private fun encodeId(id: String): String {
        val code = "${createHashTable()}||$id||${createHashTable()}||streamsb"
        return code.toCharArray().joinToString("") { char ->
            char.code.toString(16)
        }
    }

    private fun createHashTable(): String {
        return buildString {
            repeat(12) {
                append(alphabet[Random.nextInt(alphabet.length)])
            }
        }
    }

    data class Subs (
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

    data class StreamData (
        @JsonProperty("file") val file: String,
        @JsonProperty("cdn_img") val cdnImg: String,
        @JsonProperty("hash") val hash: String,
        @JsonProperty("subs") val subs: ArrayList<Subs>? = arrayListOf(),
        @JsonProperty("length") val length: String,
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("backup") val backup: String,
    )

    data class Main (
        @JsonProperty("stream_data") val streamData: StreamData,
        @JsonProperty("status_code") val statusCode: Int,
    )

}
