package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.helper.AesHelper.cryptoAESHandler
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element

class DatabaseGdrive2 : Gdriveplayer() {
    override var mainUrl = "https://databasegdriveplayer.co"
}

class DatabaseGdrive : Gdriveplayer() {
    override var mainUrl = "https://series.databasegdriveplayer.co"
}

class Gdriveplayerapi : Gdriveplayer() {
    override val mainUrl: String = "https://gdriveplayerapi.com"
}

class Gdriveplayerapp : Gdriveplayer() {
    override val mainUrl: String = "https://gdriveplayer.app"
}

class Gdriveplayerfun : Gdriveplayer() {
    override val mainUrl: String = "https://gdriveplayer.fun"
}

class Gdriveplayerio : Gdriveplayer() {
    override val mainUrl: String = "https://gdriveplayer.io"
}

class Gdriveplayerme : Gdriveplayer() {
    override val mainUrl: String = "https://gdriveplayer.me"
}

class Gdriveplayerbiz : Gdriveplayer() {
    override val mainUrl: String = "https://gdriveplayer.biz"
}

class Gdriveplayerorg : Gdriveplayer() {
    override val mainUrl: String = "https://gdriveplayer.org"
}

class Gdriveplayerus : Gdriveplayer() {
    override val mainUrl: String = "https://gdriveplayer.us"
}

class Gdriveplayerco : Gdriveplayer() {
    override val mainUrl: String = "https://gdriveplayer.co"
}

open class Gdriveplayer : ExtractorApi() {
    override val name = "Gdrive"
    override val mainUrl = "https://gdriveplayer.to"
    override val requiresReferer = false

    private fun unpackJs(script: Element): String? {
        return script.select("script").find { it.data().contains("eval(function(p,a,c,k,e,d)") }
            ?.data()?.let { getAndUnpack(it) }
    }

    private fun Regex.first(str: String): String? {
        return find(str)?.groupValues?.getOrNull(1)
    }

    private fun String.addMarks(str: String): String {
        return this.replace(Regex("\"?$str\"?"), "\"$str\"")
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url).document

        val eval = unpackJs(document)?.replace("\\", "") ?: return
        val data = Regex("data='(\\S+?)'").first(eval) ?: return
        val password = Regex("null,['|\"](\\w+)['|\"]").first(eval)
            ?.split(Regex("\\D+"))
            ?.joinToString("") {
                Char(it.toInt()).toString()
            }.let { Regex("var pass = \"(\\S+?)\"").first(it ?: return)?.toByteArray() }
            ?: throw ErrorLoadingException("can't find password")
        val decryptedData = cryptoAESHandler(data, password, false, "AES/CBC/NoPadding")?.let { getAndUnpack(it) }?.replace("\\", "")

        val sourceData = decryptedData?.substringAfter("sources:[")?.substringBefore("],")
        val subData = decryptedData?.substringAfter("tracks:[")?.substringBefore("],")

        Regex("\"file\":\"(\\S+?)\".*?res=(\\d+)").findAll(sourceData ?: return).map {
            it.groupValues[1] to it.groupValues[2]
        }.toList().distinctBy { it.second }.map { (link, quality) ->
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = "${httpsify(link)}&res=$quality",
                ) {
                    this.referer = mainUrl
                    this.quality = quality.toIntOrNull() ?: Qualities.Unknown.value
                    this.headers = mapOf("Range" to "bytes=0-")
                }
            )
        }

        subData?.addMarks("file")?.addMarks("kind")?.addMarks("label").let { dataSub ->
            tryParseJson<List<Tracks>>("[$dataSub]")?.map { sub ->
                subtitleCallback.invoke(
                    newSubtitleFile(
                        sub.label,
                        httpsify(sub.file)
                    )
                )
            }
        }

    }

    data class Tracks(
        @JsonProperty("file") val file: String,
        @JsonProperty("kind") val kind: String,
        @JsonProperty("label") val label: String
    )

}