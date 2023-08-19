package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/** backwards api surface */
class M3u8Helper {
    companion object {
        suspend fun generateM3u8(
            source: String,
            streamUrl: String,
            referer: String,
            quality: Int? = null,
            headers: Map<String, String> = mapOf(),
            name: String = source
        ): List<ExtractorLink> {
            return M3u8Helper2.generateM3u8(source, streamUrl, referer, quality, headers, name)
        }
    }


    data class M3u8Stream(
        val streamUrl: String,
        val quality: Int? = null,
        val headers: Map<String, String> = mapOf()
    )

    suspend fun m3u8Generation(m3u8: M3u8Stream, returnThis: Boolean? = true): List<M3u8Stream> {
        return M3u8Helper2.m3u8Generation(m3u8, returnThis)
    }
}

object M3u8Helper2 {
    suspend fun generateM3u8(
        source: String,
        streamUrl: String,
        referer: String,
        quality: Int? = null,
        headers: Map<String, String> = mapOf(),
        name: String = source
    ): List<ExtractorLink> {
        return m3u8Generation(
            M3u8Helper.M3u8Stream(
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

    private val ENCRYPTION_DETECTION_REGEX = Regex("#EXT-X-KEY:METHOD=([^,]+),")
    private val ENCRYPTION_URL_IV_REGEX =
        Regex("#EXT-X-KEY:METHOD=([^,]+),URI=\"([^\"]+)\"(?:,IV=(.*))?")
    private val QUALITY_REGEX =
        Regex("""#EXT-X-STREAM-INF:(?:(?:.*?(?:RESOLUTION=\d+x(\d+)).*?\s+(.*))|(?:.*?\s+(.*)))""")
    private val TS_EXTENSION_REGEX =
        Regex("""#EXTINF:.*\n(.+?\n)""") // fuck it we ball, who cares about the type anyways
        //Regex("""(.*\.(ts|jpg|html).*)""") //.jpg here 'case vizcloud uses .jpg instead of .ts

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

    private fun defaultIv(index: Int) : ByteArray {
        return toBytes16Big(index+1)
    }

    fun getDecrypted(
        secretKey: ByteArray,
        data: ByteArray,
        iv: ByteArray = byteArrayOf(),
        index : Int,
    ): ByteArray {
        val ivKey = if (iv.isEmpty()) defaultIv(index) else iv
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


    private fun selectBest(qualities: List<M3u8Helper.M3u8Stream>): M3u8Helper.M3u8Stream? {
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
        return !url.startsWith("https://") && !url.startsWith("http://")
    }

    suspend fun m3u8Generation(m3u8: M3u8Helper.M3u8Stream, returnThis: Boolean? = true): List<M3u8Helper.M3u8Stream> {
        val list = mutableListOf<M3u8Helper.M3u8Stream>()

        val m3u8Parent = getParentLink(m3u8.streamUrl)
        val response = app.get(m3u8.streamUrl, headers = m3u8.headers, verify = false).text

        for (match in QUALITY_REGEX.findAll(response)) {
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
                    M3u8Helper.M3u8Stream(
                        m3u8Link,
                        quality.toIntOrNull(),
                        m3u8.headers
                    ), false
                )
            }
            list += M3u8Helper.M3u8Stream(
                m3u8Link,
                quality.toIntOrNull(),
                m3u8.headers
            )
        }
        if (returnThis != false) {
            list += M3u8Helper.M3u8Stream(
                m3u8.streamUrl,
                Qualities.Unknown.value,
                m3u8.headers
            )
        }

        return list
    }

    data class LazyHlsDownloadData(
        private val encryptionData: ByteArray,
        private val encryptionIv: ByteArray,
        private val isEncrypted: Boolean,
        private val allTsLinks: List<String>,
        private val relativeUrl: String,
        private val headers: Map<String, String>,
    ) {
        val size get() = allTsLinks.size

        suspend fun resolveLinkWhileSafe(
            index: Int,
            tries: Int = 3,
            failDelay: Long = 3000,
            condition : (() -> Boolean)
        ): ByteArray? {
            for (i in 0 until tries) {
                if(!condition()) return null

                try {
                    val out = resolveLink(index)
                    return if(condition()) out else null
                } catch (e: IllegalArgumentException) {
                    return null
                } catch (e : CancellationException) {
                    return null
                } catch (t: Throwable) {
                    delay(failDelay)
                }
            }
            return null
        }

        suspend fun resolveLinkSafe(
            index: Int,
            tries: Int = 3,
            failDelay: Long = 3000
        ): ByteArray? {
            for (i in 0 until tries) {
                try {
                    return resolveLink(index)
                } catch (e: IllegalArgumentException) {
                    return null
                } catch (e : CancellationException) {
                    return null
                } catch (t: Throwable) {
                    delay(failDelay)
                }
            }
            return null
        }

        @Throws
        suspend fun resolveLink(index: Int): ByteArray {
            if (index < 0 || index >= size) throw IllegalArgumentException("index must be in the bounds of the ts")
            val url = allTsLinks[index]

            val tsResponse = app.get(url, headers = headers, verify = false)
            val tsData = tsResponse.body.bytes()
            if (tsData.isEmpty()) throw ErrorLoadingException("no data")

            return if (isEncrypted) {
                getDecrypted(encryptionData, tsData, encryptionIv, index)
            } else {
                tsData
            }
        }
    }

    @Throws
    suspend fun hslLazy(
        qualities: List<M3u8Helper.M3u8Stream>
    ): LazyHlsDownloadData {
        if (qualities.isEmpty()) throw IllegalArgumentException("qualities must be non empty")
        val selected = selectBest(qualities) ?: qualities.first()
        val headers = selected.headers
        val streams = qualities.map { m3u8Generation(it, false) }.flatten()
        // this selects the best quality of the qualities offered,
        // due to the recursive nature of m3u8, we only go 2 depth
        val secondSelection = selectBest(streams.ifEmpty { listOf(selected) })
            ?: throw IllegalArgumentException("qualities has no streams")

        val m3u8Response =
            app.get(
                secondSelection.streamUrl,
                headers = headers,
                verify = false
            ).text

        // encryption, this is because crunchy uses it
        var encryptionIv = byteArrayOf()
        var encryptionData = byteArrayOf()

        val encryptionState = isEncrypted(m3u8Response)

        if (encryptionState) {
            // its safe to assume that its not going to be null
            val match =
                ENCRYPTION_URL_IV_REGEX.find(m3u8Response)!!.groupValues

            var encryptionUri = match[2]

            if (isNotCompleteUrl(encryptionUri)) {
                encryptionUri = "${getParentLink(secondSelection.streamUrl)}/$encryptionUri"
            }

            encryptionIv = match[3].toByteArray()
            val encryptionKeyResponse = app.get(encryptionUri, headers = headers, verify = false)
            encryptionData = encryptionKeyResponse.body.bytes()
        }
        val relativeUrl = getParentLink(secondSelection.streamUrl)
        val allTsList = TS_EXTENSION_REGEX.findAll(m3u8Response + "\n").map { ts ->
            val value = ts.groupValues[1]
            if (isNotCompleteUrl(value)) {
                "$relativeUrl/${value}"
            } else {
                value
            }
        }.toList()
        if (allTsList.isEmpty()) throw IllegalArgumentException("ts must be non empty")

        return LazyHlsDownloadData(
            encryptionData = encryptionData,
            encryptionIv = encryptionIv,
            isEncrypted = encryptionState,
            allTsLinks = allTsList,
            relativeUrl = relativeUrl,
            headers = headers
        )
    }
}
