package com.lagradost.cloudstream3.ui.player

import android.util.Log
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.text.SubtitleDecoder
import com.google.android.exoplayer2.text.SubtitleDecoderFactory
import com.google.android.exoplayer2.text.SubtitleInputBuffer
import com.google.android.exoplayer2.text.SubtitleOutputBuffer
import com.google.android.exoplayer2.text.ssa.SsaDecoder
import com.google.android.exoplayer2.text.subrip.SubripDecoder
import com.google.android.exoplayer2.text.ttml.TtmlDecoder
import com.google.android.exoplayer2.text.webvtt.WebvttDecoder
import com.google.android.exoplayer2.util.MimeTypes
import com.lagradost.cloudstream3.mvvm.logError


class CustomDecoder : SubtitleDecoder {
    companion object {
        private const val TAG = "CustomDecoder"
    }

    private var realDecoder: SubtitleDecoder? = null

    override fun getName(): String {
        return realDecoder?.name ?: this::class.java.name
    }

    override fun dequeueInputBuffer(): SubtitleInputBuffer {
        Log.i(TAG, "dequeueInputBuffer")
        return realDecoder?.dequeueInputBuffer() ?: SubtitleInputBuffer()
    }

    override fun queueInputBuffer(inputBuffer: SubtitleInputBuffer) {
        Log.i(TAG, "queueInputBuffer")
        try {
            if (realDecoder == null) {
                inputBuffer.data?.let { data ->
                    // this way we read the subtitle file and decide what decoder to use instead of relying on mimetype

                    val pos = data.position()
                    data.position(0)
                    val arr = ByteArray(minOf(data.remaining(), 100))
                    data.get(arr)
                    data.position(pos)

                    val str = arr.decodeToString().trimStart()
                    Log.i(TAG, "Got data from queueInputBuffer")
                    Log.i(TAG, "first string is $str")

                    //https://github.com/LagradOst/CloudStream-2/blob/ddd774ee66810137ff7bd65dae70bcf3ba2d2489/CloudStreamForms/CloudStreamForms/Script/MainChrome.cs#L388
                    realDecoder = when {
                        str.startsWith("WEBVTT") -> WebvttDecoder()
                        str.startsWith("<?xml version=\"") -> TtmlDecoder()
                        str.startsWith("[Script Info]") || str.startsWith("Title:") -> SsaDecoder()
                        str.startsWith("1") -> SubripDecoder()
                        else -> null
                    }

                    realDecoder?.dequeueInputBuffer()?.let { buff ->
                        buff.data = data
                        realDecoder?.queueInputBuffer(buff)
                    }
                }
            } else {
                realDecoder?.dequeueInputBuffer()
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
        return CustomDecoder()
        //return when (val mimeType = format.sampleMimeType) {
        //    MimeTypes.TEXT_VTT -> WebvttDecoder()
        //    MimeTypes.TEXT_SSA -> SsaDecoder(format.initializationData)
        //    MimeTypes.APPLICATION_MP4VTT -> Mp4WebvttDecoder()
        //    MimeTypes.APPLICATION_TTML -> TtmlDecoder()
        //    MimeTypes.APPLICATION_SUBRIP -> SubripDecoder()
        //    MimeTypes.APPLICATION_TX3G -> Tx3gDecoder(format.initializationData)
        //    MimeTypes.APPLICATION_CEA608, MimeTypes.APPLICATION_MP4CEA608 -> return Cea608Decoder(
        //        mimeType,
        //        format.accessibilityChannel,
        //        Cea608Decoder.MIN_DATA_CHANNEL_TIMEOUT_MS
        //    )
        //    MimeTypes.APPLICATION_CEA708 -> Cea708Decoder(
        //        format.accessibilityChannel,
        //        format.initializationData
        //    )
        //    MimeTypes.APPLICATION_DVBSUBS -> DvbDecoder(format.initializationData)
        //    MimeTypes.APPLICATION_PGS -> PgsDecoder()
        //    MimeTypes.TEXT_EXOPLAYER_CUES -> ExoplayerCuesDecoder()
        //    // Default WebVttDecoder
        //    else -> WebvttDecoder()
        //}
    }
}