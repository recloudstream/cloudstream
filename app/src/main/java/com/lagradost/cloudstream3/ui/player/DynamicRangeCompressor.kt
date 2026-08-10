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
 * Handles both PCM_16BIT (shorts) and PCM_FLOAT (floats) — ExoPlayer
 * delivers PCM_16BIT by default unless float output is explicitly enabled.
 * Getting the encoding wrong causes crackling on skips and corrupted audio.
 *
 * Parameters are @Volatile so the UI thread can update them live.
 *
 * Defaults: threshold -14 dB, ratio 4:1, attack 10 ms, release 50 ms,
 * makeup +6 dB — the recommended dialogue-boost / action-limiter preset.
 */
@OptIn(UnstableApi::class)
class DynamicRangeCompressor : AudioProcessor {

    @Volatile var enabled: Boolean = false
    @Volatile var threshold: Float = -14f    // dB, -30..0
    @Volatile var ratio: Float = 4f          // n:1, 1..20
    @Volatile var attackMs: Float = 10f      // ms,  1..400
    @Volatile var releaseMs: Float = 50f     // ms,  2..800
    @Volatile var makeupGain: Float = 6f     // dB,  0..24

    private var format = AudioFormat.NOT_SET
    private var isFloat = false
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

    // ── AudioProcessor ────────────────────────────────────────────────────────

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        // Only accept PCM_16BIT or PCM_FLOAT — pass everything else through.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return inputAudioFormat
        }
        format       = inputAudioFormat
        isFloat      = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        sampleRate   = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        envelope     = FloatArray(channelCount)
        return inputAudioFormat
    }

    override fun isActive(): Boolean =
        format != AudioFormat.NOT_SET &&
        (format.encoding == C.ENCODING_PCM_16BIT || format.encoding == C.ENCODING_PCM_FLOAT)
    // NOTE: we always stay active when the format is valid and passthrough when disabled.
    // Toggling isActive() mid-stream has no effect in media3 — ExoPlayer only re-checks
    // isActive() when configure() is called (i.e. on a new audio track/source), so
    // disabling would silently have no effect until the next source loads.
    // Instead we keep the processor active and copy samples unchanged when disabled.

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val bytesPerSample = if (isFloat) 4 else 2
        val remaining = inputBuffer.remaining()
        val out = replaceOutputBuffer(remaining)

        // Passthrough when disabled — copy bytes unchanged.
        if (!enabled) {
            out.put(inputBuffer)
            out.flip()
            return
        }

        updateCoefficients()

        val makeupLinear = dbToLinear(makeupGain)
        val threshLinear = dbToLinear(threshold)
        val frameCount = remaining / (channelCount * bytesPerSample)

        if (isFloat) {
            repeat(frameCount) {
                repeat(channelCount) { ch ->
                    val sample = inputBuffer.getFloat()
                    val gain = computeGain(ch, sample, threshLinear)
                    out.putFloat(sample * gain * makeupLinear)
                }
            }
        } else {
            repeat(frameCount) {
                repeat(channelCount) { ch ->
                    val rawShort = inputBuffer.getShort()
                    val sample = rawShort / 32768f
                    val gain = computeGain(ch, sample, threshLinear)
                    val result = (sample * gain * makeupLinear * 32768f)
                        .coerceIn(-32768f, 32767f).toInt().toShort()
                    out.putShort(result)
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
        // Called on every seek — reset envelope to silence so there's no
        // burst of incorrect gain reduction after seeking (causes crackling).
        envelope = FloatArray(channelCount)
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        format = AudioFormat.NOT_SET
        isFloat = false
        envelope = FloatArray(0)
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun computeGain(ch: Int, sample: Float, threshLinear: Float): Float {
        val abs = if (sample < 0f) -sample else sample
        val coeff = if (abs > envelope[ch]) attackCoeff else releaseCoeff
        envelope[ch] = abs + coeff * (envelope[ch] - abs)
        return if (envelope[ch] <= threshLinear) {
            1f
        } else {
            val overDb = linearToDb(envelope[ch]) - threshold
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
        if (attackMs == lastAttackMs && releaseMs == lastReleaseMs && sampleRate == lastSampleRate) return
        lastAttackMs = attackMs; lastReleaseMs = releaseMs; lastSampleRate = sampleRate
        attackCoeff  = exp(-1.0 / (sampleRate * attackMs  / 1000.0)).toFloat()
        releaseCoeff = exp(-1.0 / (sampleRate * releaseMs / 1000.0)).toFloat()
    }

    private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)
    private fun linearToDb(linear: Float): Float =
        if (linear <= 0f) -120f else (20.0 * ln(linear.toDouble()) / ln(10.0)).toFloat()
}
