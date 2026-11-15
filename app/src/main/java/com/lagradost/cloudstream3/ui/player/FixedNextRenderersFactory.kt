package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegVideoRenderer
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

import android.os.Handler
import androidx.media3.common.util.Log
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

@UnstableApi
class FixedNextRenderersFactory(
    context: Context,
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

    /**
     * Only builds a custom [DefaultAudioSink] when a compressor is actually supplied.
     * When [compressor] is null (the setting is off, or unsupported on this device) we
     * fall through to the completely unmodified default sink from the parent factory,
     * so playback behaves exactly as it did before this feature existed.
     */
    @OptIn(UnstableApi::class)
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink? {
        val activeCompressor = compressor
            ?: return super.buildAudioSink(
                context,
                enableFloatOutput,
                enableAudioTrackPlaybackParams
            )

        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf<AudioProcessor>(activeCompressor))
            .build()
    }
}


@UnstableApi
open class FixedNextRenderersFactoryWithoutVideo(
    context: Context,
    private val compressor: DynamicRangeCompressor? = null,
) : DefaultRenderersFactory(context) {
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
    /**
     * Only builds a custom [DefaultAudioSink] when a compressor is actually supplied.
     * When [compressor] is null (the setting is off, or unsupported on this device) we
     * fall through to the completely unmodified default sink from the parent factory,
     * so playback behaves exactly as it did before this feature existed.
     */
    @OptIn(UnstableApi::class)
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink? {
        val activeCompressor = compressor
            ?: return super.buildAudioSink(
                context,
                enableFloatOutput,
                enableAudioTrackPlaybackParams
            )

        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf<AudioProcessor>(activeCompressor))
            .build()
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out
        )

        if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) return

        var extensionRendererIndex = out.size
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
            extensionRendererIndex--
        }

        try {
            val renderer = FfmpegAudioRenderer(eventHandler, eventListener, audioSink)
            out.add(extensionRendererIndex++, renderer)
            Log.i(TAG, "Loaded FfmpegAudioRenderer.")
        } catch (e: Exception) {
            // The extension is present, but instantiation failed.
            throw RuntimeException("Error instantiating Ffmpeg extension", e)
        }
    }

    companion object {
        const val TAG = "NextRenderersFactory"
    }
}
