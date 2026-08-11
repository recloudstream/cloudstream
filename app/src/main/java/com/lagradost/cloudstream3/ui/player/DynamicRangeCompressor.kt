package com.lagradost.cloudstream3.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * Real-time dynamic range compressor ported from VLC's compressor.c
 * (LGPL, Steve Harris / Ronald Wright).
 *
 * Designed for the "MCU problem": action scenes too loud, dialogue too quiet.
 * Uses a SINGLE peak envelope across all channels so L/R gain is always
 * identical — independent per-channel envelopes destroy stereo image and
 * cause crackling when L and R volumes differ (e.g. panned action audio).
 *
 * Handles both PCM_16BIT and PCM_FLOAT. Always stays active when configured
 * so enable/disable works instantly mid-stream via passthrough copy.
 *
 * Defaults tuned for movie dialogue boost:
 *   threshold -24 dB, ratio 8:1, attack 5 ms, release 400 ms, makeup +12 dB
 */
@OptIn(UnstableApi::class)
class DynamicRangeCompressor : AudioProcessor {

    @Volatile var enabled: Boolean  = false
    @Volatile var threshold: Float  = -24f   // dB, -30..0
    @Volatile var ratio: Float      = 8f     // n:1, 1..20
    @Volatile var attackMs: Float   = 5f     // ms,  1..400
    @Volatile var releaseMs: Float  = 400f   // ms,  2..800
    @Volatile var makeupGain: Float = 12f    // dB,  0..24

    private var format       = AudioFormat.NOT_SET
    private var isFloat      = false
    private var sampleRate   = 44100
    private var channelCount = 2

    // Single shared envelope across all channels — keeps L/R gain identical
    // so stereo image is preserved and no crackling from gain mismatch.
    private var envelope = 0f

    private var attackCoeff    = 0f
    private var releaseCoeff   = 0f
    private var lastAttackMs   = -1f
    private var lastReleaseMs  = -1f
    private var lastSampleRate = -1

    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    // ── AudioProcessor ────────────────────────────────────────────────────────

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return inputAudioFormat  // pass unsupported formats through unchanged
        }
        format       = inputAudioFormat
        isFloat      = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        sampleRate   = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        envelope     = 0f
        return inputAudioFormat
    }

    // Always active when format is set — toggling isActive() mid-stream has no
    // effect in media3 (only checked at configure() time). We do passthrough
    // in queueInput() instead so enable/disable works immediately.
    override fun isActive(): Boolean =
        format != AudioFormat.NOT_SET &&
        (format.encoding == C.ENCODING_PCM_16BIT || format.encoding == C.ENCODING_PCM_FLOAT)

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val remaining = inputBuffer.remaining()
        val out = replaceOutputBuffer(remaining)

        // Passthrough when disabled — raw byte copy, zero processing overhead.
        if (!enabled) {
            out.put(inputBuffer)
            out.flip()
            return
        }

        updateCoefficients()

        val makeupLinear = dbToLinear(makeupGain)
        val threshLinear = dbToLinear(threshold)
        val bytesPerSample = if (isFloat) 4 else 2
        val frameCount = remaining / (channelCount * bytesPerSample)

        if (isFloat) {
            repeat(frameCount) {
                // Pass 1: find peak across all channels this frame
                val frameStart = inputBuffer.position()
                var peak = 0f
                repeat(channelCount) {
                    val s = inputBuffer.getFloat()
                    val abs = if (s < 0f) -s else s
                    if (abs > peak) peak = abs
                }

                // Update single shared envelope and compute gain once
                val coeff = if (peak > envelope) attackCoeff else releaseCoeff
                envelope = peak + coeff * (envelope - peak)
                val gain = computeGain(threshLinear) * makeupLinear

                // Pass 2: apply same gain to all channels
                inputBuffer.position(frameStart)
                repeat(channelCount) { out.putFloat(inputBuffer.getFloat() * gain) }
            }
        } else {
            repeat(frameCount) {
                val frameStart = inputBuffer.position()
                var peak = 0f
                repeat(channelCount) {
                    val s = inputBuffer.getShort() / 32768f
                    val abs = if (s < 0f) -s else s
                    if (abs > peak) peak = abs
                }

                val coeff = if (peak > envelope) attackCoeff else releaseCoeff
                envelope = peak + coeff * (envelope - peak)
                val gain = computeGain(threshLinear) * makeupLinear

                inputBuffer.position(frameStart)
                repeat(channelCount) {
                    val s = inputBuffer.getShort() / 32768f
                    out.putShort((s * gain).coerceIn(-1f, 1f).let { (it * 32768f).toInt().toShort() })
                }
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
        // Called on every seek. Reset envelope so there's no gain burst
        // from stale state — which is what caused crackling after skipping.
        envelope = 0f
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        format = AudioFormat.NOT_SET
        isFloat = false
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun computeGain(threshLinear: Float): Float {
        return if (envelope <= threshLinear) {
            1f
        } else {
            val overDb = linearToDb(envelope) - threshold
            dbToLinear(-(overDb * (1f - 1f / ratio)))
        }
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
        if (attackMs  == lastAttackMs  &&
            releaseMs == lastReleaseMs &&
            sampleRate == lastSampleRate) return
        lastAttackMs   = attackMs
        lastReleaseMs  = releaseMs
        lastSampleRate = sampleRate
        attackCoeff  = exp(-1.0 / (sampleRate * attackMs  / 1000.0)).toFloat()
        releaseCoeff = exp(-1.0 / (sampleRate * releaseMs / 1000.0)).toFloat()
    }

    private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)
    private fun linearToDb(linear: Float): Float =
        if (linear <= 0f) -120f else (20.0 * ln(linear.toDouble()) / ln(10.0)).toFloat()
}
