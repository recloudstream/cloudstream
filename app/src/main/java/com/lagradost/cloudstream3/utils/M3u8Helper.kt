package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.runBlocking
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow


class M3u8Helper {
    companion object {
        private val generator = M3u8Helper()
        suspend fun generateM3u8(
            source: String,
            streamUrl: String,
            referer: String,
            quality: Int? = null,
            headers: Map<String, String> = mapOf(),
            name: String = source
        ): List<ExtractorLink> {
            return generator.m3u8Generation(
                M3u8Stream(
                    streamUrl = streamUrl,
                    quality = quality,
                    headers = headers,
                ), null
            )
                .map { stream ->
                    ExtractorLink(
                        source,
                        name = name,
                        stream.streamUrl,
                        referer,
                        stream.quality ?: Qualities.Unknown.value,
                        true,
                        stream.headers,
                    )
                }
        }
    }

    private val ENCRYPTION_DETECTION_REGEX = Regex("#EXT-X-KEY:METHOD=([^,]+),")
    private val ENCRYPTION_URL_IV_REGEX =
        Regex("#EXT-X-KEY:METHOD=([^,]+),URI=\"([^\"]+)\"(?:,IV=(.*))?")
    private val QUALITY_REGEX =
        Regex("""#EXT-X-STREAM-INF:(?:(?:.*?(?:RESOLUTION=\d+x(\d+)).*?\s+(.*))|(?:.*?\s+(.*)))""")
    private val TS_EXTENSION_REGEX =
        Regex("""(.*\.ts.*|.*\.jpg.*)""") //.jpg here 'case vizcloud uses .jpg instead of .ts

    private fun absoluteExtensionDetermination(url: String): String? {
        val split = url.split("/")
        val gg: String = split[split.size - 1].split("?")[0]
        return if (gg.contains(".")) {
            gg.split(".").ifEmpty { null }?.last()
        } else null
    }

    private fun toBytes16Big(n: Int): ByteArray {
        return ByteArray(16) {
            val fixed = n / 256.0.pow((15 - it))
            (maxOf(0, fixed.toInt()) % 256).toByte()
        }
    }

    private val defaultIvGen = sequence {
        var initial = 1

        while (true) {
            yield(toBytes16Big(initial))
            ++initial
        }
    }.iterator()

    private fun getDecrypter(
        secretKey: ByteArray,
        data: ByteArray,
        iv: ByteArray = "".toByteArray()
    ): ByteArray {
        val ivKey = if (iv.isEmpty()) defaultIvGen.next() else iv
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val skSpec = SecretKeySpec(secretKey, "AES")
        val ivSpec = IvParameterSpec(ivKey)
        c.init(Cipher.DECRYPT_MODE, skSpec, ivSpec)
        return c.doFinal(data)
    }

    private fun isEncrypted(m3u8Data: String): Boolean {
        val st = ENCRYPTION_DETECTION_REGEX.find(m3u8Data)
        return st != null && (st.value.isNotEmpty() || st.destructured.component1() != "NONE")
    }

    data class M3u8Stream(
        val streamUrl: String,
        val quality: Int? = null,
        val headers: Map<String, String> = mapOf()
    )

    private fun selectBest(qualities: List<M3u8Stream>): M3u8Stream? {
        val result = qualities.sortedBy {
            if (it.quality != null && it.quality <= 1080) it.quality else 0
        }.filter {
            listOf("m3u", "m3u8").contains(absoluteExtensionDetermination(it.streamUrl))
        }
        return result.lastOrNull()
    }

    private fun getParentLink(uri: String): String {
        val split = uri.split("/").toMutableList()
        split.removeLast()
        return split.joinToString("/")
    }

    private fun isNotCompleteUrl(url: String): Boolean {
        return !url.contains("https://") && !url.contains("http://")
    }

    suspend fun m3u8Generation(m3u8: M3u8Stream, returnThis: Boolean? = true): List<M3u8Stream> {
//        return listOf(m3u8)
        val list = mutableListOf<M3u8Stream>()

        val m3u8Parent = getParentLink(m3u8.streamUrl)
        val response = app.get(m3u8.streamUrl, headers = m3u8.headers, verify = false).text

//        var hasAnyContent = false
        for (match in QUALITY_REGEX.findAll(response)) {
//            hasAnyContent = true
            var (quality, m3u8Link, m3u8Link2) = match.destructured
            if (m3u8Link.isEmpty()) m3u8Link = m3u8Link2
            if (absoluteExtensionDetermination(m3u8Link) == "m3u8") {
                if (isNotCompleteUrl(m3u8Link)) {
                    m3u8Link = "$m3u8Parent/$m3u8Link"
                }
                if (quality.isEmpty()) {
                    println(m3u8.streamUrl)
                }
                list += m3u8Generation(
                    M3u8Stream(
                        m3u8Link,
                        quality.toIntOrNull(),
                        m3u8.headers
                    ), false
                )
            }
            list += M3u8Stream(
                m3u8Link,
                quality.toIntOrNull(),
                m3u8.headers
            )
        }
        if (returnThis != false) {
            list += M3u8Stream(
                m3u8.streamUrl,
                Qualities.Unknown.value,
                m3u8.headers
            )
        }

        return list
    }


    data class HlsDownloadData(
        val bytes: ByteArray,
        val currentIndex: Int,
        val totalTs: Int,
        val errored: Boolean = false
    )

    suspend fun hlsYield(
        qualities: List<M3u8Stream>,
        startIndex: Int = 0
    ): Iterator<HlsDownloadData> {
        if (qualities.isEmpty()) return listOf(
            HlsDownloadData(
                byteArrayOf(),
                1,
                1,
                true
            )
        ).iterator()

        var selected = selectBest(qualities)
        if (selected == null) {
            selected = qualities[0]
        }
        val headers = selected.headers

        val streams = qualities.map { m3u8Generation(it, false) }.flatten()
        //val sslVerification = if (headers.containsKey("ssl_verification")) headers["ssl_verification"].toBoolean() else true

        val secondSelection = selectBest(streams.ifEmpty { listOf(selected) })
        if (secondSelection != null) {
            val m3u8Response =
                runBlocking {
                    app.get(
                        secondSelection.streamUrl,
                        headers = headers,
                        verify = false
                    ).text
                }

            var encryptionUri: String?
            var encryptionIv = byteArrayOf()
            var encryptionData = byteArrayOf()

            val encryptionState = isEncrypted(m3u8Response)

            if (encryptionState) {
                val match =
                    ENCRYPTION_URL_IV_REGEX.find(m3u8Response)!!.destructured  // its safe to assume that its not going to be null
                encryptionUri = match.component2()

                if (isNotCompleteUrl(encryptionUri)) {
                    encryptionUri = "${getParentLink(secondSelection.streamUrl)}/$encryptionUri"
                }

                encryptionIv = match.component3().toByteArray()
                val encryptionKeyResponse =
                    runBlocking { app.get(encryptionUri, headers = headers, verify = false) }
                encryptionData = encryptionKeyResponse.body?.bytes() ?: byteArrayOf()
            }

            val allTs = TS_EXTENSION_REGEX.findAll(m3u8Response)
            val allTsList = allTs.toList()
            val totalTs = allTsList.size
            if (totalTs == 0) {
                return listOf(HlsDownloadData(byteArrayOf(), 1, 1, true)).iterator()
            }
            var lastYield = 0

            val relativeUrl = getParentLink(secondSelection.streamUrl)
            var retries = 0
            val tsByteGen = sequence {
                loop@ for ((index, ts) in allTs.withIndex()) {
                    val url = if (
                        isNotCompleteUrl(ts.destructured.component1())
                    ) "$relativeUrl/${ts.destructured.component1()}" else ts.destructured.component1()
                    val c = index + 1 + startIndex

                    while (lastYield != c) {
                        try {
                            val tsResponse =
                                runBlocking { app.get(url, headers = headers, verify = false) }
                            var tsData = tsResponse.body?.bytes() ?: byteArrayOf()

                            if (encryptionState) {
                                tsData = getDecrypter(encryptionData, tsData, encryptionIv)
                                yield(HlsDownloadData(tsData, c, totalTs))
                                lastYield = c
                                break
                            }
                            yield(HlsDownloadData(tsData, c, totalTs))
                            lastYield = c
                        } catch (e: Exception) {
                            logError(e)
                            if (retries == 3) {
                                yield(HlsDownloadData(byteArrayOf(), c, totalTs, true))
                                break@loop
                            }
                            ++retries
                            Thread.sleep(2_000)
                        }
                    }
                }
            }
            return tsByteGen.iterator()
        }
        return listOf(HlsDownloadData(byteArrayOf(), 1, 1, true)).iterator()
    }
}
