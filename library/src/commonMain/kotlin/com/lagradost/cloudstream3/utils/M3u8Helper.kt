package com.lagradost.cloudstream3.utils

import com.lagradost.api.Log
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
        return M3u8Helper2.m3u8Generation(m3u8, returnThis ?: true)
    }
}


object M3u8Helper2 {
    private val TAG = "M3u8Helper"

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
            ), true
        )
            .map { stream ->
                newExtractorLink(
                    source,
                    name = name,
                    stream.streamUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = referer
                    this.quality = stream.quality ?: Qualities.Unknown.value
                    this.headers = stream.headers
                }
            }
    }

    private val ENCRYPTION_DETECTION_REGEX = Regex("#EXT-X-KEY:METHOD=([^,]+),")
    private val ENCRYPTION_URL_IV_REGEX =
        Regex("#EXT-X-KEY:METHOD=([^,]+),URI=\"([^\"]+)\"(?:,IV=(.*))?")
    private val QUALITY_REGEX =
        Regex("""#EXT-X-STREAM-INF:(?:(?:.*?(?:RESOLUTION=\d+x(\d+)).*?\s+(.*))|(?:.*?\s+(.*)))""")
    private val TS_EXTENSION_REGEX =
        Regex("""#EXTINF:(([0-9]*[.])?[0-9]+|).*\n(.+?\n)""") // fuck it we ball, who cares about the type anyways
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

    private fun defaultIv(index: Int): ByteArray {
        return toBytes16Big(index + 1)
    }

    fun getDecrypted(
        secretKey: ByteArray,
        data: ByteArray,
        iv: ByteArray = byteArrayOf(),
        index: Int,
    ): ByteArray {
        val ivKey = if (iv.isEmpty()) defaultIv(index) else iv
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val skSpec = SecretKeySpec(secretKey, "AES")
        val ivSpec = IvParameterSpec(ivKey)
        c.init(Cipher.DECRYPT_MODE, skSpec, ivSpec)
        return c.doFinal(data)
    }

    private fun getParentLink(uri: String): String {
        val split = uri.split("/").toMutableList()
        split.removeAt(split.lastIndex)
        return split.joinToString("/")
    }

    private fun isNotCompleteUrl(url: String): Boolean {
        return !url.startsWith("https://") && !url.startsWith("http://")
    }

    @Throws
    suspend fun m3u8Generation(
        m3u8: M3u8Helper.M3u8Stream,
        returnThis: Boolean = true
    ): List<M3u8Helper.M3u8Stream> {
        val list = mutableListOf<M3u8Helper.M3u8Stream>()
        val response = app.get(m3u8.streamUrl, headers = m3u8.headers, verify = false).text
        val parsed = HlsPlaylistParser.parse(
            m3u8.streamUrl,
            response,
        )

        var anyFound = false
        if (parsed != null) {
            for (video in parsed.variants) {
                // The m3u8 should not be split it that causes a loss of audio, however this can not be done reliably
                // Therefore that should be figured out on the extension level, so only "trick play" is checked for
                if (video.isTrickPlay()) {
                    //println("Denied m3u8Generation, isTrickPlay = ${video.isTrickPlay()} containsAudio = ${video.containsAudio()}, codec = ${video.format.codecs}, url = ${video.url}")
                    continue
                }

                anyFound = true
                val quality = video.format.height
                list.add(
                    M3u8Helper.M3u8Stream(
                        streamUrl = video.url.toString(),
                        quality = if (quality > 0) quality else null,
                        headers = m3u8.headers
                    )
                )
            }
        }

        // If it is not a "Master Playlist", or if it does not contain any playable "Media Playlist" or if it should return itself
        if (parsed == null || !anyFound || returnThis) {
            // Only include it if is a "Media Playlist" (any TS files are found), or if it is a "Master Playlist" (parsing is non null)
            if (parsed != null || TS_EXTENSION_REGEX.containsMatchIn(response)) {
                list += m3u8
            } else {
                Log.i(TAG, "M3u8 Playlist is not a \"Master Playlist\" nor a \"Media Playlist\". Removing this link as it is invalid and will not open in player: ${m3u8.streamUrl}")
            }
        }

        return list
    }

    data class TsLink(
        val url: String,
        val time: Double?,
    )

    data class LazyHlsDownloadData(
        private val encryptionData: ByteArray,
        private val encryptionIv: ByteArray,
        val isEncrypted: Boolean,
        val allTsLinks: List<TsLink>,
        val relativeUrl: String,
        val headers: Map<String, String>,
    ) {

        val size get() = allTsLinks.size

        suspend fun resolveLinkWhileSafe(
            index: Int,
            tries: Int = 3,
            failDelay: Long = 3000,
            condition: (() -> Boolean)
        ): ByteArray? {
            for (i in 0 until tries) {
                if (!condition()) return null

                try {
                    val out = resolveLink(index)
                    return if (condition()) out else null
                } catch (e: IllegalArgumentException) {
                    return null
                } catch (e: CancellationException) {
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
                } catch (e: CancellationException) {
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
            val ts = allTsLinks[index]

            val tsResponse = app.get(ts.url, headers = headers, verify = false)
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
        playlistStream: M3u8Helper.M3u8Stream,
        selectBest: Boolean = true,
        requireAudio: Boolean,
        depth: Int = 3,
    ): LazyHlsDownloadData {
        // Allow nesting, but not too much:
        // Master Playlist (different videos)
        // -> Media Playlist (different qualities of the same video)
        // -> Media Segments (ts files of a single video)
        if (depth < 0) {
            throw IllegalArgumentException()
        }

        val playlistResponse =
            app.get(
                playlistStream.streamUrl,
                headers = playlistStream.headers,
                verify = false
            ).text

        val parsed = HlsPlaylistParser.parse(playlistStream.streamUrl, playlistResponse)
        if (parsed != null) {
            // find first with no audio group if audio is required, as otherwise muxing is required
            // as m3u8 files can include separate tracks for dubs/subs
            val variants = if (requireAudio) {
                parsed.variants.filter { it.isPlayableStandalone(parsed) }
            } else {
                parsed.variants.filter { !it.isTrickPlay() }
            }

            if (variants.isEmpty()) {
                throw IllegalStateException(
                    if (requireAudio) {
                        "M3u8 contains no video with audio"
                    } else {
                        "M3u8 contains no video"
                    }
                )
            }

            // M3u8 can also include different camera angles (parsed.videos) for the same quality
            // but here the default is used
            val bestVideo = if (selectBest) {
                // Sort by pixels, while this normally is a bad idea if one is -1,
                // in the m3u8 parsing must contains both or neither, so NO_VALUE => 1
                // Tie break on averageBitrate
                variants.maxBy { (it.format.width * it.format.height).toLong() * 1000L + it.format.averageBitrate.toLong() }
            } else {
                variants.minBy { (it.format.width * it.format.height).toLong() * 1000L + it.format.averageBitrate.toLong() }
            }

            val quality = bestVideo.format.height
            return hslLazy(
                playlistStream = M3u8Helper.M3u8Stream(
                    bestVideo.url.toString(),
                    if (quality > 0) quality else null,
                    playlistStream.headers
                ),
                selectBest = selectBest,
                requireAudio = requireAudio,
                depth = depth - 1
            )
        }
        // This is already a "Media Segments" file

        // Encryption, this is because crunchy uses it
        var encryptionIv = byteArrayOf()
        var encryptionData = byteArrayOf()

        val match = ENCRYPTION_URL_IV_REGEX.find(playlistResponse)?.groupValues
        val encryptionState: Boolean

        if (!match.isNullOrEmpty()) {
            encryptionState = true
            var encryptionUri = match[2]

            if (isNotCompleteUrl(encryptionUri)) {
                encryptionUri = "${getParentLink(playlistStream.streamUrl)}/$encryptionUri"
            }

            encryptionIv = match[3].toByteArray()
            val encryptionKeyResponse =
                app.get(encryptionUri, headers = playlistStream.headers, verify = false)
            encryptionData = encryptionKeyResponse.body.bytes()
        } else {
            encryptionState = false
        }

        val relativeUrl = getParentLink(playlistStream.streamUrl)
        val allTsList = TS_EXTENSION_REGEX.findAll(playlistResponse + "\n").map { ts ->
            val time = ts.groupValues[1]
            val value = ts.groupValues[3]
            val url = if (isNotCompleteUrl(value)) {
                "$relativeUrl/${value}"
            } else {
                value
            }
            TsLink(url = url, time = time.toDoubleOrNull())
        }.toList()

        if (allTsList.isEmpty()) throw IllegalStateException("M3u8 must contains TS files")

        return LazyHlsDownloadData(
            encryptionData = encryptionData,
            encryptionIv = encryptionIv,
            isEncrypted = encryptionState,
            allTsLinks = allTsList,
            relativeUrl = relativeUrl,
            headers = playlistStream.headers
        )
    }
}
