package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.text.SubtitleDecoderFactory
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SimpleSubtitleDecoder
import androidx.media3.extractor.text.Subtitle
import androidx.media3.extractor.text.SubtitleDecoder
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.dvb.DvbParser
import androidx.media3.extractor.text.pgs.PgsParser
import androidx.media3.extractor.text.ssa.SsaParser
import androidx.media3.extractor.text.subrip.SubripParser
import androidx.media3.extractor.text.ttml.TtmlParser
import androidx.media3.extractor.text.tx3g.Tx3gParser
import androidx.media3.extractor.text.webvtt.Mp4WebvttParser
import androidx.media3.extractor.text.webvtt.WebvttParser
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import org.mozilla.universalchardet.UniversalDetector
import java.nio.charset.Charset

/**
 * @param fallbackFormat used to create a decoder based on mimetype if the subtitle string is not
 * enough to identify the subtitle format.
 **/
@UnstableApi
class CustomDecoder(private val fallbackFormat: Format?) : SubtitleParser {
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

        /** Subtitle offset in milliseconds */
        var subtitleOffset: Long = 0
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
        val captionRegex = listOf(Regex("""(-\s?|)[\[({][\w\s]*?[])}]\s*"""))

        //https://emptycharacter.com/
        //https://www.fileformat.info/info/unicode/char/200b/index.htm
        fun trimStr(string: String): String {
            return string.trimStart().trim('\uFEFF', '\u200B').replace(
                Regex("[\u00A0\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u205F]"),
                " "
            )
        }
    }

    private var realDecoder: SubtitleParser? = null

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

    private fun getSubtitleParser(data: String): SubtitleParser? {
        // this way we read the subtitle file and decide what decoder to use instead of relying fully on mimetype
        //https://github.com/LagradOst/CloudStream-2/blob/ddd774ee66810137ff7bd65dae70bcf3ba2d2489/CloudStreamForms/CloudStreamForms/Script/MainChrome.cs#L388
        val subtitleParser = when {
            // "WEBVTT" can be hidden behind invisible characters not filtered by trim
            data.substring(0, 10).contains("WEBVTT", ignoreCase = true) -> WebvttParser()
            data.startsWith("<?xml version=\"", ignoreCase = true) -> TtmlParser()
            (data.startsWith(
                "[Script Info]",
                ignoreCase = true
            ) || data.startsWith(
                "Title:",
                ignoreCase = true
            )) -> SsaParser(fallbackFormat?.initializationData)

            data.startsWith("1", ignoreCase = true) -> SubripParser()
            fallbackFormat != null -> {
                when (val mimeType = fallbackFormat.sampleMimeType) {
                    MimeTypes.TEXT_VTT -> WebvttParser()
                    MimeTypes.TEXT_SSA -> SsaParser(fallbackFormat.initializationData)
                    MimeTypes.APPLICATION_MP4VTT -> Mp4WebvttParser()
                    MimeTypes.APPLICATION_TTML -> TtmlParser()
                    MimeTypes.APPLICATION_SUBRIP -> SubripParser()
                    MimeTypes.APPLICATION_TX3G -> Tx3gParser(fallbackFormat.initializationData)
                    // These decoders are not converted to parsers yet
                    // TODO
//                            MimeTypes.APPLICATION_CEA608, MimeTypes.APPLICATION_MP4CEA608 -> Cea608Decoder(
//                                mimeType,
//                                fallbackFormat.accessibilityChannel,
//                                Cea608Decoder.MIN_DATA_CHANNEL_TIMEOUT_MS
//                            )
//                            MimeTypes.APPLICATION_CEA708 -> Cea708Decoder(
//                                fallbackFormat.accessibilityChannel,
//                                fallbackFormat.initializationData
//                            )
                    MimeTypes.APPLICATION_DVBSUBS -> DvbParser(fallbackFormat.initializationData)
                    MimeTypes.APPLICATION_PGS -> PgsParser()
                    else -> null
                }
            }

            else -> null
        }
        return subtitleParser
    }

    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>
    ) {
        val customOutput = Consumer<CuesWithTiming> { o ->
            val updatedCues = CuesWithTiming(o.cues, o.startTimeUs - subtitleOffset.times(1000), o.durationUs)
            output.accept(updatedCues)
        }
        Log.i(TAG, "Parse subtitle, current parser: $realDecoder")
        try {
            val inputString = getStr(data).first
            Log.i(TAG, "Subtitle preview: ${inputString.substring(0, 30)}")
            if (inputString.isNotBlank()) {
                var str: String = trimStr(inputString)
                realDecoder = realDecoder ?: getSubtitleParser(inputString)
                Log.i(
                    TAG,
                    "Parser selected: $realDecoder"
                )
                realDecoder?.let { decoder ->
                    if (decoder !is SsaParser) {
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
                }
                val array = str.toByteArray()
                realDecoder?.parse(
                    array,
                    minOf(array.size, offset),
                    minOf(array.size, length),
                    outputOptions,
                    customOutput
                )
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    override fun getCueReplacementBehavior(): Int {
        return realDecoder?.cueReplacementBehavior ?: Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE
    }
}

/** See https://github.com/google/ExoPlayer/blob/release-v2/library/core/src/main/java/com/google/android/exoplayer2/text/SubtitleDecoderFactory.java */
@OptIn(UnstableApi::class)
class CustomSubtitleDecoderFactory : SubtitleDecoderFactory {

    override fun supportsFormat(format: Format): Boolean {
        return listOf(
            MimeTypes.TEXT_VTT,
            MimeTypes.TEXT_SSA,
            MimeTypes.APPLICATION_TTML,
            MimeTypes.APPLICATION_MP4VTT,
            MimeTypes.APPLICATION_SUBRIP,
            MimeTypes.APPLICATION_TX3G,
            //MimeTypes.APPLICATION_CEA608,
            //MimeTypes.APPLICATION_MP4CEA608,
            //MimeTypes.APPLICATION_CEA708,
            MimeTypes.APPLICATION_DVBSUBS,
            MimeTypes.APPLICATION_PGS,
            //MimeTypes.TEXT_EXOPLAYER_CUES
        ).contains(format.sampleMimeType)
    }

    /**
     * Decoders created here persists across reset()
     * Do not save state in the decoder which you want to reset (e.g subtitle offset)
     **/
    override fun createDecoder(format: Format): SubtitleDecoder {
        val parser = CustomDecoder(format)

        return DelegatingSubtitleDecoder(
            parser::class.simpleName + "Decoder", parser
        )
    }
}

@OptIn(UnstableApi::class)
/** We need to convert the newer SubtitleParser to an older SubtitleDecoder */
class DelegatingSubtitleDecoder(name: String, private val parser: SubtitleParser) :
    SimpleSubtitleDecoder(name) {

    override fun decode(data: ByteArray, length: Int, reset: Boolean): Subtitle {
        if (reset) {
            parser.reset()
        }
        return parser.parseToLegacySubtitle(data, 0, length);
    }
}
