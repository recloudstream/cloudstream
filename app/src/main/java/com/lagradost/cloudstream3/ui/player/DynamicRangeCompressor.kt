package com.lagradost.cloudstream3.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * Real-time dynamic range compressor, ported from VLC's compressor.c
 * (LGPL, Steve Harris / Ronald Wright).
 *
 * Implemented as a Media3 [AudioProcessor] so it sits directly in the
 * audio pipeline after decoding. Parameters are @Volatile so the UI
 * thread can update them live without locking.
 *
 * Defaults match the recommended dialogue-boost preset:
 *   threshold -14 dB, ratio 4:1, attack 10 ms, release 50 ms, makeup +6 dB
 */
@OptIn(UnstableApi::class)
class DynamicRangeCompressor : AudioProcessor {

    @Volatile var enabled: Boolean = false
    @Volatile var threshold: Float = -14f   // dB, range -30..0
    @Volatile var ratio: Float = 4f         // n:1, range 1..20
    @Volatile var attackMs: Float = 10f     // ms, range 1..400
    @Volatile var releaseMs: Float = 50f    // ms, range 2..800
    @Volatile var makeupGain: Float = 6f    // dB, range 0..24

    private var format = AudioFormat.NOT_SET
    private var sampleRate = 44100
    private var channelCount = 2

    private var envelope = FloatArray(0)
    private var attackCoeff = 0f
    private var releaseCoeff = 0f
    private var lastAttackMs = -1f
    private var lastReleaseMs = -1f
    private var lastSampleRate = -1

    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        format = inputAudioFormat
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        envelope = FloatArray(channelCount)
        return inputAudioFormat
    }

    override fun isActive(): Boolean =
        enabled && format != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val remaining = inputBuffer.remaining()
        val out = replaceOutputBuffer(remaining)

        updateCoefficients()

        val makeupLinear = dbToLinear(makeupGain)
        val threshLinear = dbToLinear(threshold)
        val frameCount = remaining / (channelCount * 4)

        repeat(frameCount) {
            repeat(channelCount) { ch ->
                val sample = inputBuffer.getFloat()
                val abs = if (sample < 0f) -sample else sample
                val coeff = if (abs > envelope[ch]) attackCoeff else releaseCoeff
                envelope[ch] = abs + coeff * (envelope[ch] - abs)

                val gain = if (envelope[ch] <= threshLinear) {
                    1f
                } else {
                    val overDb = linearToDb(envelope[ch]) - threshold
                    dbToLinear(-(overDb * (1f - 1f / ratio)))
                }
                out.putFloat(sample * gain * makeupLinear)
            }
        }
        out.flip()
    }

    override fun queueEndOfStream() { inputEnded = true }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean =
        inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        envelope = FloatArray(channelCount)
    }

    override fun reset() {
        flush()
        format = AudioFormat.NOT_SET
        envelope = FloatArray(0)
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (outputBuffer.capacity() < size) {
            outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }
        return outputBuffer
    }

    private fun updateCoefficients() {
        if (attackMs == lastAttackMs && releaseMs == lastReleaseMs && sampleRate == lastSampleRate) return
        lastAttackMs = attackMs; lastReleaseMs = releaseMs; lastSampleRate = sampleRate
        attackCoeff  = exp(-1.0 / (sampleRate * attackMs  / 1000.0)).toFloat()
        releaseCoeff = exp(-1.0 / (sampleRate * releaseMs / 1000.0)).toFloat()
    }

    private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)
    private fun linearToDb(linear: Float): Float =
        if (linear <= 0f) -120f else (20.0 * ln(linear.toDouble()) / ln(10.0)).toFloat()
}
