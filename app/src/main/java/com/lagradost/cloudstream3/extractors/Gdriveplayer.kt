package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element
import java.security.DigestException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

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

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    // https://stackoverflow.com/a/41434590/8166854
    private fun GenerateKeyAndIv(
        password: ByteArray,
        salt: ByteArray,
        hashAlgorithm: String = "MD5",
        keyLength: Int = 32,
        ivLength: Int = 16,
        iterations: Int = 1
    ): List<ByteArray>? {

        val md = MessageDigest.getInstance(hashAlgorithm)
        val digestLength = md.digestLength
        val targetKeySize = keyLength + ivLength
        val requiredLength = (targetKeySize + digestLength - 1) / digestLength * digestLength
        val generatedData = ByteArray(requiredLength)
        var generatedLength = 0

        try {
            md.reset()

            while (generatedLength < targetKeySize) {
                if (generatedLength > 0)
                    md.update(
                        generatedData,
                        generatedLength - digestLength,
                        digestLength
                    )

                md.update(password)
                md.update(salt, 0, 8)
                md.digest(generatedData, generatedLength, digestLength)

                for (i in 1 until iterations) {
                    md.update(generatedData, generatedLength, digestLength)
                    md.digest(generatedData, generatedLength, digestLength)
                }

                generatedLength += digestLength
            }
            return listOf(
                generatedData.copyOfRange(0, keyLength),
                generatedData.copyOfRange(keyLength, targetKeySize)
            )
        } catch (e: DigestException) {
            return null
        }
    }

    private fun cryptoAESHandler(
        data: AesData,
        pass: ByteArray,
        encrypt: Boolean = true
    ): String? {
        val (key, iv) = GenerateKeyAndIv(pass, data.s.decodeHex()) ?: return null
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        return if (!encrypt) {
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            String(cipher.doFinal(base64DecodeArray(data.ct)))
        } else {
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            base64Encode(cipher.doFinal(data.ct.toByteArray()))

        }
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
        val data = tryParseJson<AesData>(Regex("data='(\\S+?)'").first(eval)) ?: return
        val password = Regex("null,['|\"](\\w+)['|\"]").first(eval)
            ?.split(Regex("\\D+"))
            ?.joinToString("") {
                Char(it.toInt()).toString()
            }.let { Regex("var pass = \"(\\S+?)\"").first(it ?: return)?.toByteArray() }
            ?: throw ErrorLoadingException("can't find password")
        val decryptedData = cryptoAESHandler(data, password, false)?.let { getAndUnpack(it) }?.replace("\\", "")

        val sourceData = decryptedData?.substringAfter("sources:[")?.substringBefore("],")
        val subData = decryptedData?.substringAfter("tracks:[")?.substringBefore("],")

        Regex("\"file\":\"(\\S+?)\".*?res=(\\d+)").findAll(sourceData ?: return).map {
            it.groupValues[1] to it.groupValues[2]
        }.toList().distinctBy { it.second }.map { (link, quality) ->
            callback.invoke(
                ExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = "${httpsify(link)}&res=$quality",
                    referer = mainUrl,
                    quality = quality.toIntOrNull() ?: Qualities.Unknown.value,
                    headers = mapOf("Range" to "bytes=0-")
                )
            )
        }

        subData?.addMarks("file")?.addMarks("kind")?.addMarks("label").let { dataSub ->
            tryParseJson<List<Tracks>>("[$dataSub]")?.map { sub ->
                subtitleCallback.invoke(
                    SubtitleFile(
                        sub.label,
                        httpsify(sub.file)
                    )
                )
            }
        }

    }

    data class AesData(
        @JsonProperty("ct") val ct: String,
        @JsonProperty("iv") val iv: String,
        @JsonProperty("s") val s: String
    )

    data class Tracks(
        @JsonProperty("file") val file: String,
        @JsonProperty("kind") val kind: String,
        @JsonProperty("label") val label: String
    )

}