package com.lagradost.cloudstream3.ui.player

import android.os.Looper
import androidx.media3.exoplayer.text.SubtitleDecoderFactory
import androidx.media3.exoplayer.text.TextOutput

class CustomTextRenderer(
    offset: Long,
    output: TextOutput?,
    outputLooper: Looper?,
    decoderFactory: SubtitleDecoderFactory = SubtitleDecoderFactory.DEFAULT
) : NonFinalTextRenderer(output, outputLooper, decoderFactory) {
    private var offsetPositionUs: Long = 0L

    init {
        setRenderOffsetMs(offset)
    }

    fun setRenderOffsetMs(offset : Long) {
        offsetPositionUs = offset * 1000L
    }

    fun getRenderOffsetMs() : Long {
        return offsetPositionUs / 1000L
    }

    override fun render( positionUs: Long,  elapsedRealtimeUs: Long) {
        super.render(positionUs + offsetPositionUs, elapsedRealtimeUs + offsetPositionUs)
    }
}