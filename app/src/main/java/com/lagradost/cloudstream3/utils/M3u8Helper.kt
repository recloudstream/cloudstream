package com.lagradost.cloudstream3.utils

import java.lang.Exception
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow
import com.lagradost.cloudstream3.mvvm.logError


class M3u8Helper {
    private val ENCRYPTION_DETECTION_REGEX = Regex("#EXT-X-KEY:METHOD=([^,]+),")
    private val ENCRYPTION_URL_IV_REGEX = Regex("#EXT-X-KEY:METHOD=([^,]+),URI=\"([^\"]+)\"(?:,IV=(.*))?")
    private val QUALITY_REGEX = Regex("""#EXT-X-STREAM-INF:.*RESOLUTION=\d+x(\d+).*\n(.*)""")
    private val TS_EXTENSION_REGEX = Regex("""(.*\.ts.*)""")

    private fun absoluteExtensionDetermination(url: String): String? {
        val split = url.split("/")
        val gg: String = split[split.size - 1].split("?")[0]
        return if (gg.contains(".")) {
            gg.split(".")[1].ifEmpty { null }
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

    private fun getDecrypter(secretKey: ByteArray, data: ByteArray, iv: ByteArray = "".toByteArray()): ByteArray {
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

    public data class M3u8Stream(
        val streamUrl: String,
        val quality: Int? = null,
        val headers: Map<String, String> = mapOf()
    )

    private fun selectBest(qualities: List<M3u8Stream>): M3u8Stream? {
        val result = qualities.sortedBy { if (it.quality != null && it.quality <= 1080) it.quality else 0
        }.reversed().filter {
            listOf("m3u", "m3u8").contains(absoluteExtensionDetermination(it.streamUrl))
        }
        return result.getOrNull(0)
    }

    private fun getParentLink(uri: String): String {
        val split = uri.split("/").toMutableList()
        split.removeLast()
        return split.joinToString("/")
    }

    private fun isCompleteUrl(url: String): Boolean {
        return url.contains("https://") && url.contains("http://")
    }

    public fun m3u8Generation(m3u8: M3u8Stream): List<M3u8Stream> {
        val generate = sequence {
            val m3u8Parent = getParentLink(m3u8.streamUrl)
            val response = khttp.get(m3u8.streamUrl, headers=m3u8.headers)

            for (match in QUALITY_REGEX.findAll(response.text)) {
                var (quality, m3u8Link) = match.destructured
                if (absoluteExtensionDetermination(m3u8Link) == "m3u8") {
                    if (!isCompleteUrl(m3u8Link)) {
                        m3u8Link = "$m3u8Parent/$m3u8Link"
                    }
                    yieldAll(
                        m3u8Generation(
                            M3u8Stream(
                                m3u8Link,
                                quality.toIntOrNull(),
                                m3u8.headers
                            )
                        )
                    )
                }
                yield(
                    M3u8Stream(
                        m3u8Link,
                        quality.toInt(),
                        m3u8.headers
                    )
                )
            }
        }
        return generate.toList()
    }

    data class HlsDownloadData(
        val bytes: ByteArray,
        val currentIndex: Int,
        val totalTs: Int,
        val errored: Boolean = false
    )

    public fun hlsYield(qualities: List<M3u8Stream>): Iterator<HlsDownloadData> {
        if (qualities.isEmpty()) return listOf<HlsDownloadData>().iterator()

        var selected = selectBest(qualities)
        if (selected == null) {
            selected = qualities[0]
        }
        val headers = selected.headers

        val streams = qualities.map { m3u8Generation(it) }.flatten()
        val sslVerification = if (headers.containsKey("ssl_verification")) headers["ssl_verification"].toBoolean() else true

        val secondSelection = selectBest(streams.ifEmpty { listOf(selected) })
        if (secondSelection != null) {
            val m3u8Response = khttp.get(secondSelection.streamUrl, headers=headers)
            val m3u8Data = m3u8Response.text

            var encryptionUri: String? = null
            var encryptionIv = byteArrayOf()
            var encryptionData= byteArrayOf()

            val encryptionState = isEncrypted(m3u8Data)

            if (encryptionState) {
                val match = ENCRYPTION_URL_IV_REGEX.find(m3u8Data)!!.destructured  // its safe to assume that its not going to be null
                encryptionUri = match.component2()

                if (!isCompleteUrl(encryptionUri)) {
                    encryptionUri = "${getParentLink(secondSelection.streamUrl)}/$encryptionUri"
                }

                encryptionIv = match.component3().toByteArray()
                val encryptionKeyResponse = khttp.get(encryptionUri, headers=headers)
                encryptionData = encryptionKeyResponse.content
            }

            val allTs = TS_EXTENSION_REGEX.findAll(m3u8Data)
            val totalTs = allTs.toList().size
            if (totalTs == 0) {
                return listOf<HlsDownloadData>().iterator()
            }
            var lastYield = 0

            val relativeUrl = getParentLink(secondSelection.streamUrl)
            var retries = 0
            val tsByteGen = sequence<HlsDownloadData> {
                loop@ for ((index, ts) in allTs.withIndex()) {
                    val url = if (
                        isCompleteUrl(ts.destructured.component1())
                    ) ts.destructured.component1() else "$relativeUrl/${ts.destructured.component1()}"
                    val c = index+1

                    while (lastYield != c) {
                        try {
                            val tsResponse = khttp.get(url, headers=headers)
                            var tsData = tsResponse.content

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
        return listOf<HlsDownloadData>().iterator()
    }
}
