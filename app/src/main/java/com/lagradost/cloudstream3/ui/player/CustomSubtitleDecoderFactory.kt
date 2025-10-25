package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.text.Layout
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
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
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import org.mozilla.universalchardet.UniversalDetector
import java.lang.ref.WeakReference
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

        private const val DEFAULT_MARGIN: Float = 0.05f
        const val SSA_ALIGNMENT_BOTTOM_LEFT = 1
        const val SSA_ALIGNMENT_BOTTOM_CENTER = 2
        const val SSA_ALIGNMENT_BOTTOM_RIGHT = 3
        const val SSA_ALIGNMENT_MIDDLE_LEFT = 4
        const val SSA_ALIGNMENT_MIDDLE_CENTER = 5
        const val SSA_ALIGNMENT_MIDDLE_RIGHT = 6
        const val SSA_ALIGNMENT_TOP_LEFT = 7
        const val SSA_ALIGNMENT_TOP_CENTER = 8
        const val SSA_ALIGNMENT_TOP_RIGHT = 9

        /** Subtitle offset in milliseconds */
        var subtitleOffset: Long = 0
        private const val UTF_8 = "UTF-8"
        private const val TAG = "CustomDecoder"
        private var overrideEncoding: String? = null
        val style: SaveCaptionStyle get() = SubtitlesFragment.getCurrentSavedStyle()
        private val locationRegex = Regex("""\{\\an(\d+)\}""", RegexOption.IGNORE_CASE)
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

        //https://emptycharacter.com/
        //https://www.fileformat.info/info/unicode/char/200b/index.htm
        fun trimStr(string: String): String {
            return string.trimStart().trim('\uFEFF', '\u200B').replace(
                Regex("[\u00A0\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u205F]"),
                " "
            )
        }

        private fun computeDefaultLineOrPosition(@Cue.AnchorType anchor: Int) = when (anchor) {
            Cue.ANCHOR_TYPE_START -> DEFAULT_MARGIN
            Cue.ANCHOR_TYPE_MIDDLE -> 0.5f
            Cue.ANCHOR_TYPE_END -> 1.0f - DEFAULT_MARGIN
            Cue.TYPE_UNSET -> Cue.DIMEN_UNSET
            else -> Cue.DIMEN_UNSET
        }

        /**
         * Fixes alignment for cues with {\anX},
         * this is common for .vtt that should be parsed as .srt
         *
         * ```
         * WEBVTT
         *
         * 00:00.000 --> 00:01.000
         * {\an1}Label 1
         *
         * 00:01.000 --> 00:02.000
         * {\an2}Label 2
         *
         * 00:02.000 --> 00:03.000
         * {\an3}Label 3
         *
         * 00:03.000 --> 00:04.000
         * {\an4}Label 4
         *
         * 00:04.000 --> 00:05.000
         * {\an5}Label 5
         *
         * 00:05.000 --> 00:06.000
         * {\an6}Label 6
         *
         * 00:06.000 --> 00:07.000
         * {\an7}Label 7
         *
         * 00:07.000 --> 00:08.000
         * {\an8}Label 8
         *
         * 00:08.000 --> 00:09.000
         * {\an9}Label 9
         * ```
         */
        fun Cue.Builder.fixSubtitleAlignment(): Cue.Builder {
            var trimmed = text?.trim() ?: return this
            // https://github.com/androidx/media/blob/main/libraries/extractor/src/main/java/androidx/media3/extractor/text/ssa/SsaStyle.java
            // exoplayer can already parse this, however for eg webvtt it fails
            locationRegex.find(trimmed)?.groupValues?.get(1)?.toIntOrNull()?.let { alignment ->
                // toLineAnchor
                this.setSubtitleAlignment(alignment)
            }

            // remove all matches, so we do not display \anx
            trimmed = trimmed.replace(locationRegex, "")
            setText(trimmed)
            return this
        }

        fun Cue.Builder.setSubtitleAlignment(alignment: Int?): Cue.Builder {
            if (alignment == null) return this
            when (alignment) {
                SSA_ALIGNMENT_BOTTOM_LEFT, SSA_ALIGNMENT_BOTTOM_CENTER, SSA_ALIGNMENT_BOTTOM_RIGHT -> Cue.ANCHOR_TYPE_END
                SSA_ALIGNMENT_MIDDLE_LEFT, SSA_ALIGNMENT_MIDDLE_CENTER, SSA_ALIGNMENT_MIDDLE_RIGHT -> Cue.ANCHOR_TYPE_MIDDLE
                SSA_ALIGNMENT_TOP_LEFT, SSA_ALIGNMENT_TOP_CENTER, SSA_ALIGNMENT_TOP_RIGHT -> Cue.ANCHOR_TYPE_START
                else -> null
            }?.let { anchor ->
                setLineAnchor(anchor)
                setLine(
                    computeDefaultLineOrPosition(anchor), Cue.LINE_TYPE_FRACTION
                )
            }
            // toPositionAnchor
            when (alignment) {
                SSA_ALIGNMENT_BOTTOM_LEFT, SSA_ALIGNMENT_MIDDLE_LEFT, SSA_ALIGNMENT_TOP_LEFT -> Cue.ANCHOR_TYPE_START
                SSA_ALIGNMENT_BOTTOM_CENTER, SSA_ALIGNMENT_MIDDLE_CENTER, SSA_ALIGNMENT_TOP_CENTER -> Cue.ANCHOR_TYPE_MIDDLE
                SSA_ALIGNMENT_BOTTOM_RIGHT, SSA_ALIGNMENT_MIDDLE_RIGHT, SSA_ALIGNMENT_TOP_RIGHT -> Cue.ANCHOR_TYPE_END
                else -> null
            }?.let { anchor ->
                setPositionAnchor(anchor)
                setPosition(computeDefaultLineOrPosition(anchor))
            }

            // toTextAlignment
            when (alignment) {
                SSA_ALIGNMENT_BOTTOM_LEFT, SSA_ALIGNMENT_MIDDLE_LEFT, SSA_ALIGNMENT_TOP_LEFT -> Layout.Alignment.ALIGN_NORMAL
                SSA_ALIGNMENT_BOTTOM_CENTER, SSA_ALIGNMENT_MIDDLE_CENTER, SSA_ALIGNMENT_TOP_CENTER -> Layout.Alignment.ALIGN_CENTER
                SSA_ALIGNMENT_BOTTOM_RIGHT, SSA_ALIGNMENT_MIDDLE_RIGHT, SSA_ALIGNMENT_TOP_RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                else -> null
            }?.let { anchor ->
                setTextAlignment(anchor)
            }
            return this
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
        // This way we read the subtitle file and decide what decoder to use instead of relying fully on mimetype

        // First we remove all invisible characters at the start, this is an issue in some subtitle files
        // Cntrl is control characters: https://en.wikipedia.org/wiki/Unicode_control_characters
        // Cf is formatting characters: https://www.compart.com/en/unicode/category/Cf
        val controlCharsRegex = Regex("""[\p{Cntrl}\p{Cf}]""")
        val trimmedText =
            data.trimStart { it.isWhitespace() || controlCharsRegex.matches(it.toString()) }

        //https://github.com/LagradOst/CloudStream-2/blob/ddd774ee66810137ff7bd65dae70bcf3ba2d2489/CloudStreamForms/CloudStreamForms/Script/MainChrome.cs#L388
        val subtitleParser = when {
            // "WEBVTT" can be hidden behind invisible characters not filtered by trim
            trimmedText.substring(0, 10).contains("WEBVTT", ignoreCase = true) -> WebvttParser()
            trimmedText.startsWith("<?xml version=\"", ignoreCase = true) -> TtmlParser()
            (trimmedText.startsWith(
                "[Script Info]",
                ignoreCase = true
            ) || trimmedText.startsWith(
                "Title:",
                ignoreCase = true
            )) -> SsaParser(fallbackFormat?.initializationData)

            trimmedText.startsWith("1", ignoreCase = true) -> SubripParser()
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

    val currentSubtitleCues = mutableListOf<SubtitleCue>()


    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>
    ) {
        val currentStyle = style
        val customOutput = Consumer<CuesWithTiming> { cue ->
            val newCue =
                CuesWithTiming(cue.cues, cue.startTimeUs, cue.durationUs)

            // Do not apply the offset to the currentSubtitleCues as those are then used for sync subs
            currentSubtitleCues.add(
                SubtitleCue(
                    newCue.startTimeUs / 1000,
                    newCue.durationUs / 1000,
                    newCue.cues.map { it.text.toString() })
            )

            // offset timing for the final
            val updatedCues =
                CuesWithTiming(
                    newCue.cues,
                    newCue.startTimeUs - subtitleOffset.times(1000),
                    newCue.durationUs
                )

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
                        if (currentStyle.removeBloat)
                            bloatRegex.forEach { rgx ->
                                str = str.replace(rgx, "\n")
                            }
                        if (currentStyle.upperCase) {
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
        // CUE_REPLACEMENT_BEHAVIOR_REPLACE seems most compatible, change if required
        return realDecoder?.cueReplacementBehavior ?: Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE
    }

    override fun reset() {
        currentSubtitleCues.clear()
        super.reset()
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

    private var latestDecoder: WeakReference<CustomDecoder>? = null

    fun getSubtitleCues(): List<SubtitleCue>? {
        return latestDecoder?.get()?.currentSubtitleCues
    }

    /**
     * Decoders created here persists across reset()
     * Do not save state in the decoder which you want to reset (e.g subtitle offset)
     **/
    override fun createDecoder(format: Format): SubtitleDecoder {
        val parser = CustomDecoder(format)
        // Allow garbage collection if player releases the decoder
        latestDecoder = WeakReference(parser)

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
