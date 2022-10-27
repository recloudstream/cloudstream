/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lagradost.cloudstream3.ui.player

import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.annotation.IntDef
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.SampleStream.ReadDataResult
import com.google.android.exoplayer2.text.*
import com.google.android.exoplayer2.text.Cue.DIMEN_UNSET
import com.google.android.exoplayer2.text.Cue.LINE_TYPE_NUMBER
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util

/**
 * A renderer for text.
 *
 *
 * [Subtitle]s are decoded from sample data using [SubtitleDecoder] instances
 * obtained from a [SubtitleDecoderFactory]. The actual rendering of the subtitle [Cue]s
 * is delegated to a [TextOutput].
 */
open class NonFinalTextRenderer @JvmOverloads constructor(
    output: TextOutput?,
    outputLooper: Looper?,
    private val decoderFactory: SubtitleDecoderFactory = SubtitleDecoderFactory.DEFAULT
) :
    BaseRenderer(C.TRACK_TYPE_TEXT), Handler.Callback {
    @MustBeDocumented
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    @IntDef(
        REPLACEMENT_STATE_NONE,
        REPLACEMENT_STATE_SIGNAL_END_OF_STREAM,
        REPLACEMENT_STATE_WAIT_END_OF_STREAM
    )
    private annotation class ReplacementState

    private val outputHandler: Handler? = if (outputLooper == null) null else Util.createHandler(
        outputLooper,  /* callback= */
        this
    )
    private val output: TextOutput = Assertions.checkNotNull(output)
    private val formatHold: FormatHolder = FormatHolder()
    private var inputStreamEnded = false
    private var outputStreamEnded = false
    private var waitingForKeyFrame = false

    @ReplacementState
    private var decoderReplacementState = 0
    private var streamFormat: Format? = null
    private var decoder: SubtitleDecoder? = null
    private var nextInputBuffer: SubtitleInputBuffer? = null
    private var subtitle: SubtitleOutputBuffer? = null
    private var nextSubtitle: SubtitleOutputBuffer? = null
    private var nextSubtitleEventIndex = 0
    private var finalStreamEndPositionUs: Long
    override fun getName(): String {
        return TAG
    }

    @RendererCapabilities.Capabilities
    override fun supportsFormat(format: Format): Int {
        return if (decoderFactory.supportsFormat(format)) {
            RendererCapabilities.create(
                if (format.cryptoType == C.CRYPTO_TYPE_NONE) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_DRM
            )
        } else if (MimeTypes.isText(format.sampleMimeType)) {
            RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE)
        } else {
            RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        }
    }

    /**
     * Sets the position at which to stop rendering the current stream.
     *
     *
     * Must be called after [.setCurrentStreamFinal].
     *
     * @param streamEndPositionUs The position to stop rendering at or [C.LENGTH_UNSET] to
     * render until the end of the current stream.
     */

    override fun onStreamChanged(formats: Array<Format>, startPositionUs: Long, offsetUs: Long) {
        streamFormat = formats[0]
        if (decoder != null) {
            decoderReplacementState = REPLACEMENT_STATE_SIGNAL_END_OF_STREAM
        } else {
            initDecoder()
        }
    }

    override fun onPositionReset(positionUs: Long, joining: Boolean) {
        clearOutput()
        inputStreamEnded = false
        outputStreamEnded = false
        finalStreamEndPositionUs = C.TIME_UNSET
        if (decoderReplacementState != REPLACEMENT_STATE_NONE) {
            replaceDecoder()
        } else {
            releaseBuffers()
            Assertions.checkNotNull(decoder).flush()
        }
    }

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        if (isCurrentStreamFinal
            && finalStreamEndPositionUs != C.TIME_UNSET && positionUs >= finalStreamEndPositionUs
        ) {
            releaseBuffers()
            outputStreamEnded = true
        }
        if (outputStreamEnded) {
            return
        }
        if (nextSubtitle == null) {
            Assertions.checkNotNull(decoder).setPositionUs(positionUs)
            nextSubtitle = try {
                Assertions.checkNotNull(decoder).dequeueOutputBuffer()
            } catch (e: SubtitleDecoderException) {
                handleDecoderError(e)
                return
            }
        }
        if (state != STATE_STARTED) {
            return
        }
        var textRendererNeedsUpdate = false
        if (subtitle != null) {
            // We're iterating through the events in a subtitle. Set textRendererNeedsUpdate if we
            // advance to the next event.
            var subtitleNextEventTimeUs = nextEventTime
            while (subtitleNextEventTimeUs <= positionUs) {
                nextSubtitleEventIndex++
                subtitleNextEventTimeUs = nextEventTime
                textRendererNeedsUpdate = true
            }
        }
        if (nextSubtitle != null) {
            val nextSubtitle = nextSubtitle
            if (nextSubtitle!!.isEndOfStream) {
                if (!textRendererNeedsUpdate && nextEventTime == Long.MAX_VALUE) {
                    if (decoderReplacementState == REPLACEMENT_STATE_WAIT_END_OF_STREAM) {
                        replaceDecoder()
                    } else {
                        releaseBuffers()
                        outputStreamEnded = true
                    }
                }
            } else if (nextSubtitle.timeUs <= positionUs) {
                // Advance to the next subtitle. Sync the next event index and trigger an update.
                if (subtitle != null) {
                    subtitle!!.release()
                }
                nextSubtitleEventIndex = nextSubtitle.getNextEventTimeIndex(positionUs)
                subtitle = nextSubtitle
                this.nextSubtitle = null
                textRendererNeedsUpdate = true
            }
        }
        if (textRendererNeedsUpdate) {
            // If textRendererNeedsUpdate then subtitle must be non-null.
            Assertions.checkNotNull(subtitle)
            // textRendererNeedsUpdate is set and we're playing. Update the renderer.
            updateOutput(subtitle!!.getCues(positionUs))
        }
        if (decoderReplacementState == REPLACEMENT_STATE_WAIT_END_OF_STREAM) {
            return
        }
        try {
            while (!inputStreamEnded) {
                var nextInputBuffer = nextInputBuffer
                if (nextInputBuffer == null) {
                    nextInputBuffer = Assertions.checkNotNull(decoder).dequeueInputBuffer()
                    if (nextInputBuffer == null) {
                        return
                    }
                    this.nextInputBuffer = nextInputBuffer
                }
                if (decoderReplacementState == REPLACEMENT_STATE_SIGNAL_END_OF_STREAM) {
                    nextInputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM)
                    Assertions.checkNotNull(decoder).queueInputBuffer(nextInputBuffer)
                    this.nextInputBuffer = null
                    decoderReplacementState = REPLACEMENT_STATE_WAIT_END_OF_STREAM
                    return
                }
                // Try and read the next subtitle from the source.
                @ReadDataResult val result =
                    readSource(formatHold, nextInputBuffer,  /* readFlags= */0)
                if (result == C.RESULT_BUFFER_READ) {
                    if (nextInputBuffer.isEndOfStream) {
                        inputStreamEnded = true
                        waitingForKeyFrame = false
                    } else {
                        val format = formatHold.format
                            ?: // We haven't received a format yet.
                            return
                        nextInputBuffer.subsampleOffsetUs = format.subsampleOffsetUs
                        nextInputBuffer.flip()
                        waitingForKeyFrame = waitingForKeyFrame and !nextInputBuffer.isKeyFrame
                    }
                    if (!waitingForKeyFrame) {
                        Assertions.checkNotNull(decoder).queueInputBuffer(nextInputBuffer)
                        this.nextInputBuffer = null
                    }
                } else if (result == C.RESULT_NOTHING_READ) {
                    return
                }
            }
        } catch (e: SubtitleDecoderException) {
            handleDecoderError(e)
        }
    }

    override fun onDisabled() {
        streamFormat = null
        finalStreamEndPositionUs = C.TIME_UNSET
        clearOutput()
        releaseDecoder()
    }

    override fun isEnded(): Boolean {
        return outputStreamEnded
    }

    override fun isReady(): Boolean {
        // Don't block playback whilst subtitles are loading.
        // Note: To change this behavior, it will be necessary to consider [Internal: b/12949941].
        return true
    }

    private fun releaseBuffers() {
        nextInputBuffer = null
        nextSubtitleEventIndex = C.INDEX_UNSET
        if (subtitle != null) {
            subtitle!!.release()
            subtitle = null
        }
        if (nextSubtitle != null) {
            nextSubtitle!!.release()
            nextSubtitle = null
        }
    }

    private fun releaseDecoder() {
        releaseBuffers()
        Assertions.checkNotNull(decoder).release()
        decoder = null
        decoderReplacementState = REPLACEMENT_STATE_NONE
    }

    private fun initDecoder() {
        waitingForKeyFrame = true
        decoder = decoderFactory.createDecoder(Assertions.checkNotNull(streamFormat))
    }

    private fun replaceDecoder() {
        releaseDecoder()
        initDecoder()
    }

    private val nextEventTime: Long
        get() {
            if (nextSubtitleEventIndex == C.INDEX_UNSET) {
                return Long.MAX_VALUE
            }
            Assertions.checkNotNull(subtitle)
            return if (nextSubtitleEventIndex >= subtitle!!.eventTimeCount) Long.MAX_VALUE else subtitle!!.getEventTime(
                nextSubtitleEventIndex
            )
        }

    private fun updateOutput(cues: List<Cue>) {
        if (outputHandler != null) {
            outputHandler.obtainMessage(MSG_UPDATE_OUTPUT, cues).sendToTarget()
        } else {
            invokeUpdateOutputInternal(cues)
        }
    }

    private fun clearOutput() {
        updateOutput(emptyList())
    }

    override fun handleMessage(msg: Message): Boolean {
        return when (msg.what) {
            MSG_UPDATE_OUTPUT -> {
                invokeUpdateOutputInternal(msg.obj as List<Cue>)
                true
            }
            else -> throw IllegalStateException()
        }
    }

    private fun invokeUpdateOutputInternal(cues: List<Cue>) {
        output.onCues(cues.map { cue ->
            val builder = cue.buildUpon()

            // See https://github.com/google/ExoPlayer/issues/7934
            // SubripDecoder texts tend to be DIMEN_UNSET which pushes up the
            // subs unlike WEBVTT which creates an inconsistency
            if (cue.line == DIMEN_UNSET)
                builder.setLine(-1f, LINE_TYPE_NUMBER)

            // this fixes https://github.com/LagradOst/CloudStream-3/issues/717
            builder.setSize(DIMEN_UNSET).build()
        })
    }

    /**
     * Called when [.decoder] throws an exception, so it can be logged and playback can
     * continue.
     *
     *
     * Logs `e` and resets state to allow decoding the next sample.
     */
    private fun handleDecoderError(e: SubtitleDecoderException) {
        Log.e(
            TAG,
            "Subtitle decoding failed. streamFormat=$streamFormat", e
        )
        clearOutput()
        replaceDecoder()
    }

    companion object {
        private const val TAG = "TextRenderer"

        /** The decoder does not need to be replaced.  */
        private const val REPLACEMENT_STATE_NONE = 0

        /**
         * The decoder needs to be replaced, but we haven't yet signaled an end of stream to the existing
         * decoder. We need to do so in order to ensure that it outputs any remaining buffers before we
         * release it.
         */
        private const val REPLACEMENT_STATE_SIGNAL_END_OF_STREAM = 1

        /**
         * The decoder needs to be replaced, and we've signaled an end of stream to the existing decoder.
         * We're waiting for the decoder to output an end of stream signal to indicate that it has output
         * any remaining buffers before we release it.
         */
        private const val REPLACEMENT_STATE_WAIT_END_OF_STREAM = 2
        private const val MSG_UPDATE_OUTPUT = 0
    }
    /**
     * @param output The output.
     * @param outputLooper The looper associated with the thread on which the output should be called.
     * If the output makes use of standard Android UI components, then this should normally be the
     * looper associated with the application's main thread, which can be obtained using [     ][android.app.Activity.getMainLooper]. Null may be passed if the output should be called
     * directly on the player's internal rendering thread.
     * @param decoderFactory A factory from which to obtain [SubtitleDecoder] instances.
     */
    /**
     * @param output The output.
     * @param outputLooper The looper associated with the thread on which the output should be called.
     * If the output makes use of standard Android UI components, then this should normally be the
     * looper associated with the application's main thread, which can be obtained using [     ][android.app.Activity.getMainLooper]. Null may be passed if the output should be called
     * directly on the player's internal rendering thread.
     */
    init {
        finalStreamEndPositionUs = C.TIME_UNSET
    }
}