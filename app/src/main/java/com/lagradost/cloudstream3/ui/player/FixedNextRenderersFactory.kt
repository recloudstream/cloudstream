package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

@UnstableApi
class FixedNextRenderersFactory(
    context: Context,
    /** Shared compressor instance, injected into the audio sink. Null = bypass. */
    private val compressor: DynamicRangeCompressor? = null,
) : NextRenderersFactory(context) {

    /** Somehow the nextlib authors decided that we need a text renderer that causes
     * "ERROR_CODE_FAILED_RUNTIME_CHECK".
     *
     * Core issue: https://github.com/anilbeesetti/nextlib/pull/158
     * Comment: https://github.com/recloudstream/cloudstream/pull/2342#issuecomment-3917751718
     * */
    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>
    ) {
        out.add(TextRenderer(output, outputLooper))
    }

    @OptIn(UnstableApi::class)
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink? {
        val processors = if (compressor != null) arrayOf(compressor) else emptyArray()
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(processors)
            .build()
    }
}
