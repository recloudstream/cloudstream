package com.lagradost.cloudstream3.ui.player

import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.text.*
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.text.webvtt.WebvttDecoder
import com.google.android.exoplayer2.text.ssa.SsaDecoder
import com.google.android.exoplayer2.text.webvtt.Mp4WebvttDecoder
import com.google.android.exoplayer2.text.ttml.TtmlDecoder
import com.google.android.exoplayer2.text.subrip.SubripDecoder
import com.google.android.exoplayer2.text.tx3g.Tx3gDecoder
import com.google.android.exoplayer2.text.cea.Cea608Decoder
import com.google.android.exoplayer2.text.cea.Cea708Decoder
import com.google.android.exoplayer2.text.dvb.DvbDecoder
import com.google.android.exoplayer2.text.pgs.PgsDecoder
import java.lang.IllegalArgumentException

//class CustomDecoder : SubtitleDecoder {
//    override fun getName(): String {
//
//    }
//
//    override fun dequeueInputBuffer(): SubtitleInputBuffer? {
//
//    }
//
//    override fun queueInputBuffer(inputBuffer: SubtitleInputBuffer) {
//
//    }
//
//    override fun dequeueOutputBuffer(): SubtitleOutputBuffer? {
//
//    }
//
//    override fun flush() {
//
//    }
//
//    override fun release() {
//
//    }
//
//    override fun setPositionUs(positionUs: Long) {
//
//    }
//}

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
            MimeTypes.APPLICATION_TX3G,
            MimeTypes.APPLICATION_CEA608,
            MimeTypes.APPLICATION_MP4CEA608,
            MimeTypes.APPLICATION_CEA708,
            MimeTypes.APPLICATION_DVBSUBS,
            MimeTypes.APPLICATION_PGS,
            MimeTypes.TEXT_EXOPLAYER_CUES
        ).contains(format.sampleMimeType)
    }

    override fun createDecoder(format: Format): SubtitleDecoder {
        return when (val mimeType = format.sampleMimeType) {
            MimeTypes.TEXT_VTT -> WebvttDecoder()
            MimeTypes.TEXT_SSA -> SsaDecoder(format.initializationData)
            MimeTypes.APPLICATION_MP4VTT -> Mp4WebvttDecoder()
            MimeTypes.APPLICATION_TTML -> TtmlDecoder()
            MimeTypes.APPLICATION_SUBRIP -> SubripDecoder()
            MimeTypes.APPLICATION_TX3G -> Tx3gDecoder(format.initializationData)
            MimeTypes.APPLICATION_CEA608, MimeTypes.APPLICATION_MP4CEA608 -> return Cea608Decoder(
                mimeType,
                format.accessibilityChannel,
                Cea608Decoder.MIN_DATA_CHANNEL_TIMEOUT_MS
            )
            MimeTypes.APPLICATION_CEA708 -> Cea708Decoder(
                format.accessibilityChannel,
                format.initializationData
            )
            MimeTypes.APPLICATION_DVBSUBS -> DvbDecoder(format.initializationData)
            MimeTypes.APPLICATION_PGS -> PgsDecoder()
            MimeTypes.TEXT_EXOPLAYER_CUES -> ExoplayerCuesDecoder()
            // Default WebVttDecoder
            else -> WebvttDecoder()
        }
    }
}