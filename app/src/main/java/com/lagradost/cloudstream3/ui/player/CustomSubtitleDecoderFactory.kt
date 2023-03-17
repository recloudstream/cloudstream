package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.text.*
import com.google.android.exoplayer2.text.cea.Cea608Decoder
import com.google.android.exoplayer2.text.cea.Cea708Decoder
import com.google.android.exoplayer2.text.dvb.DvbDecoder
import com.google.android.exoplayer2.text.pgs.PgsDecoder
import com.google.android.exoplayer2.text.ssa.SsaDecoder
import com.google.android.exoplayer2.text.subrip.SubripDecoder
import com.google.android.exoplayer2.text.ttml.TtmlDecoder
import com.google.android.exoplayer2.text.tx3g.Tx3gDecoder
import com.google.android.exoplayer2.text.webvtt.Mp4WebvttDecoder
import com.google.android.exoplayer2.text.webvtt.WebvttDecoder
import com.google.android.exoplayer2.util.MimeTypes
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import org.mozilla.universalchardet.UniversalDetector
import java.nio.ByteBuffer
import java.nio.charset.Charset

/**
 * @param fallbackFormat used to create a decoder based on mimetype if the subtitle string is not
 * enough to identify the subtitle format.
 **/
class CustomDecoder(private val fallbackFormat: Format?) : SubtitleDecoder {
    companion object {
        fun updateForcedEncoding(context: Context) {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
            val value = settingsManager.getString(
                context.getString(R.string.subtitles_encoding_key),
                null
            )
            overrideEncoding = if (value.isNullOrBlank()) {
                null
            } else {
                value
            }
        }

        private const val UTF_8 = "UTF-8"
        private const val TAG = "CustomDecoder"
        private var overrideEncoding: String? = null
        var regexSubtitlesToRemoveCaptions = false
        var regexSubtitlesToRemoveBloat = false
        var uppercaseSubtitles = false
        val bloatRegex =
            listOf(
                Regex(
                    """Support\s+us\s+and\s+become\s+VIP\s+member\s+to\s+remove\s+all\s+ads\s+from\s+(www\.|)OpenSubtitles(\.org|)""",
                    RegexOption.IGNORE_CASE
                ),
                Regex(
                    """Please\s+rate\s+this\s+subtitle\s+at\s+.*\s+Help\s+other\s+users\s+to\s+choose\s+the\s+best\s+subtitles""",
                    RegexOption.IGNORE_CASE
                ),
                Regex(
                    """Contact\s(www\.|)OpenSubtitles(\.org|)\s+today""",
                    RegexOption.IGNORE_CASE
                ),
                Regex(
                    """Advertise\s+your\s+product\s+or\s+brand\s+here""",
                    RegexOption.IGNORE_CASE
                ),
            )
        val captionRegex = listOf(Regex("""(-\s?|)[\[({][\w\d\s]*?[])}]\s*"""))

        //https://emptycharacter.com/
        //https://www.fileformat.info/info/unicode/char/200b/index.htm
        fun trimStr(string: String): String {
            return string.trimStart().trim('\uFEFF', '\u200B').replace(
                Regex("[\u00A0\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u205F]"),
                " "
            )
        }
    }

    private var realDecoder: SubtitleDecoder? = null

    override fun getName(): String {
        return realDecoder?.name ?: this::javaClass.name
    }

    override fun dequeueInputBuffer(): SubtitleInputBuffer {
        Log.i(TAG, "dequeueInputBuffer")
        return realDecoder?.dequeueInputBuffer() ?: SubtitleInputBuffer()
    }

    private fun getStr(byteArray: ByteArray): Pair<String, Charset> {
        val encoding = try {
            val encoding = overrideEncoding ?: run {
                val detector = UniversalDetector()

                detector.handleData(byteArray, 0, byteArray.size)
                detector.dataEnd()

                detector.detectedCharset // "windows-1256"
            }

            Log.i(
                TAG,
                "Detected encoding with charset $encoding and override = $overrideEncoding"
            )
            encoding ?: UTF_8
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect encoding throwing error")
            logError(e)
            UTF_8
        }

        return try {
            val set = charset(encoding)
            Pair(String(byteArray, set), set)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse using encoding $encoding")
            logError(e)
            Pair(byteArray.decodeToString(), charset(UTF_8))
        }
    }

    private fun getStr(input: SubtitleInputBuffer): String? {
        try {
            val data = input.data ?: return null
            data.position(0)
            val fullDataArr = ByteArray(data.remaining())
            data.get(fullDataArr)
            return trimStr(getStr(fullDataArr).first)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse text returning plain data")
            logError(e)
            return null
        }
    }

    private fun SubtitleInputBuffer.setSubtitleText(text: String) {
//        println("Set subtitle text -----\n$text\n-----")
        this.data = ByteBuffer.wrap(text.toByteArray(charset(UTF_8)))
    }

    override fun queueInputBuffer(inputBuffer: SubtitleInputBuffer) {
        Log.i(TAG, "queueInputBuffer")
        try {
            val inputString = getStr(inputBuffer)
            if (realDecoder == null && !inputString.isNullOrBlank()) {
                var str: String = inputString
                // this way we read the subtitle file and decide what decoder to use instead of relying fully on mimetype
                Log.i(TAG, "Got data from queueInputBuffer")
                //https://github.com/LagradOst/CloudStream-2/blob/ddd774ee66810137ff7bd65dae70bcf3ba2d2489/CloudStreamForms/CloudStreamForms/Script/MainChrome.cs#L388
                realDecoder = when {
                    str.startsWith("WEBVTT", ignoreCase = true) -> WebvttDecoder()
                    str.startsWith("<?xml version=\"", ignoreCase = true) -> TtmlDecoder()
                    (str.startsWith(
                        "[Script Info]",
                        ignoreCase = true
                    ) || str.startsWith("Title:", ignoreCase = true)) -> SsaDecoder(fallbackFormat?.initializationData)
                    str.startsWith("1", ignoreCase = true) -> SubripDecoder()
                    fallbackFormat != null -> {
                        when (val mimeType = fallbackFormat.sampleMimeType) {
                            MimeTypes.TEXT_VTT -> WebvttDecoder()
                            MimeTypes.TEXT_SSA -> SsaDecoder(fallbackFormat.initializationData)
                            MimeTypes.APPLICATION_MP4VTT -> Mp4WebvttDecoder()
                            MimeTypes.APPLICATION_TTML -> TtmlDecoder()
                            MimeTypes.APPLICATION_SUBRIP -> SubripDecoder()
                            MimeTypes.APPLICATION_TX3G -> Tx3gDecoder(fallbackFormat.initializationData)
                            MimeTypes.APPLICATION_CEA608, MimeTypes.APPLICATION_MP4CEA608 -> Cea608Decoder(
                                mimeType,
                                fallbackFormat.accessibilityChannel,
                                Cea608Decoder.MIN_DATA_CHANNEL_TIMEOUT_MS
                            )
                            MimeTypes.APPLICATION_CEA708 -> Cea708Decoder(
                                fallbackFormat.accessibilityChannel,
                                fallbackFormat.initializationData
                            )
                            MimeTypes.APPLICATION_DVBSUBS -> DvbDecoder(fallbackFormat.initializationData)
                            MimeTypes.APPLICATION_PGS -> PgsDecoder()
                            MimeTypes.TEXT_EXOPLAYER_CUES -> ExoplayerCuesDecoder()
                            else -> null
                        }
                    }
                    else -> null
                }
                Log.i(
                    TAG,
                    "Decoder selected: $realDecoder"
                )
                realDecoder?.let { decoder ->
                    decoder.dequeueInputBuffer()?.let { buff ->
                        if (decoder !is SsaDecoder) {
                            if (regexSubtitlesToRemoveCaptions)
                                captionRegex.forEach { rgx ->
                                    str = str.replace(rgx, "\n")
                                }
                            if (regexSubtitlesToRemoveBloat)
                                bloatRegex.forEach { rgx ->
                                    str = str.replace(rgx, "\n")
                                }
                        }
                        buff.setSubtitleText(str)
                        decoder.queueInputBuffer(buff)
                        Log.i(
                            TAG,
                            "Decoder queueInputBuffer successfully"
                        )
                    }
                    CS3IPlayer.requestSubtitleUpdate?.invoke()
                }
            } else {
                Log.i(
                    TAG,
                    "Decoder else queueInputBuffer successfully"
                )

                if (!inputString.isNullOrBlank()) {
                    var str: String = inputString
                    if (realDecoder !is SsaDecoder) {
                        if (regexSubtitlesToRemoveCaptions)
                            captionRegex.forEach { rgx ->
                                str = str.replace(rgx, "\n")
                            }
                        if (regexSubtitlesToRemoveBloat)
                            bloatRegex.forEach { rgx ->
                                str = str.replace(rgx, "\n")
                            }
                        if (uppercaseSubtitles) {
                            str = str.uppercase()
                        }
                    }
                    inputBuffer.setSubtitleText(str)
                }

                realDecoder?.queueInputBuffer(inputBuffer)
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    override fun dequeueOutputBuffer(): SubtitleOutputBuffer? {
        return realDecoder?.dequeueOutputBuffer()
    }

    override fun flush() {
        realDecoder?.flush()
    }

    override fun release() {
        realDecoder?.release()
    }

    override fun setPositionUs(positionUs: Long) {
        realDecoder?.setPositionUs(positionUs)
    }
}

/** See https://github.com/google/ExoPlayer/blob/release-v2/library/core/src/main/java/com/google/android/exoplayer2/text/SubtitleDecoderFactory.java */
class CustomSubtitleDecoderFactory : SubtitleDecoderFactory {
    override fun supportsFormat(format: Format): Boolean {
//        return SubtitleDecoderFactory.DEFAULT.supportsFormat(format)
        return listOf(
            MimeTypes.TEXT_VTT,
            MimeTypes.TEXT_SSA,
            MimeTypes.APPLICATION_TTML,
            MimeTypes.APPLICATION_MP4VTT,
            MimeTypes.APPLICATION_SUBRIP,
            //MimeTypes.APPLICATION_TX3G,
            //MimeTypes.APPLICATION_CEA608,
            //MimeTypes.APPLICATION_MP4CEA608,
            //MimeTypes.APPLICATION_CEA708,
            //MimeTypes.APPLICATION_DVBSUBS,
            //MimeTypes.APPLICATION_PGS,
            //MimeTypes.TEXT_EXOPLAYER_CUES
        ).contains(format.sampleMimeType)
    }

    override fun createDecoder(format: Format): SubtitleDecoder {
        return CustomDecoder(format)
    }
}