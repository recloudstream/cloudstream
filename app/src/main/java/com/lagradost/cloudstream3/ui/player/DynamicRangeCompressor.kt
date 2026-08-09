package com.lagradost.cloudstream3.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * A real-time dynamic range compressor implemented as a Media3 [AudioProcessor].
 *
 * Ported from VLC's compressor.c / dynamics.c (LGPL, Steve Harris / Ronald Wright).
 * Works on PCM_FLOAT (32-bit float, interleaved) — ExoPlayer's internal format
 * after the decoder, so no format conversion is needed.
 *
 * Parameters match the VLC compressor exactly:
 *  - [threshold]   dB level above which gain reduction starts   (range: -30..0,  default: -14)
 *  - [ratio]       compression ratio n:1                        (range:  1..20,  default:  4)
 *  - [attackMs]    attack  time in milliseconds                 (range:  1..400, default:  10)
 *  - [releaseMs]   release time in milliseconds                 (range:  2..800, default:  50)
 *  - [makeupGain]  output makeup gain in dB                     (range:  0..24,  default:   6)
 *  - [enabled]     bypass flag — when false the processor is a no-op
 *
 * Thread-safety: parameters are read/written atomically via @Volatile; the
 * process() method is always called from ExoPlayer's audio thread.
 */
@OptIn(UnstableApi::class)
class DynamicRangeCompressor : AudioProcessor {

    // ── Parameters (all @Volatile so UI thread writes are seen immediately) ──

    @Volatile var enabled: Boolean = false

    /** Threshold in dB. Signals above this level get compressed. */
    @Volatile var threshold: Float = -14f

    /** Compression ratio (n:1). Higher = more compression. */
    @Volatile var ratio: Float = 4f

    /** Attack time in ms — how fast the compressor clamps down. */
    @Volatile var attackMs: Float = 10f

    /** Release time in ms — how fast the compressor lets go. */
    @Volatile var releaseMs: Float = 50f

    /** Makeup gain in dB applied after compression. */
    @Volatile var makeupGain: Float = 6f

    // ── Internal state ────────────────────────────────────────────────────────

    private var inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var sampleRate = 44100
    private var channelCount = 2

    /** Per-channel envelope followers (one float per channel). */
    private var envelope = FloatArray(0)

    /** Precomputed attack/release coefficients, recalculated when params change. */
    private var attackCoeff  = 0f
    private var releaseCoeff = 0f
    private var lastAttackMs  = -1f
    private var lastReleaseMs = -1f
    private var lastSampleRate = -1

    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    // ── AudioProcessor ────────────────────────────────────────────────────────

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Only handle PCM_FLOAT (what ExoPlayer uses internally post-decode).
        // Return the same format either way — we never change channel count or rate.
        this.inputAudioFormat = inputAudioFormat
        sampleRate   = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        envelope     = FloatArray(channelCount)
        return inputAudioFormat
    }

    override fun isActive(): Boolean = enabled && inputAudioFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val frameCount = inputBuffer.remaining() / (channelCount * 4) // 4 bytes per float
        val output = replaceOutputBuffer(inputBuffer.remaining())

        // Lazily recompute time constants when parameters change.
        updateCoefficients()

        val makeupLinear = dbToLinear(makeupGain)
        val threshLinear = dbToLinear(threshold)

        for (frame in 0 until frameCount) {
            for (ch in 0 until channelCount) {
                val sample = inputBuffer.getFloat()

                // ── Envelope follower (peak detector) ────────────────────────
                val abs = if (sample < 0f) -sample else sample
                val coeff = if (abs > envelope[ch]) attackCoeff else releaseCoeff
                envelope[ch] = abs + coeff * (envelope[ch] - abs)

                // ── Gain computation ──────────────────────────────────────────
                val gain = if (envelope[ch] <= threshLinear) {
                    // Below threshold — no gain reduction
                    1f
                } else {
                    // Above threshold — reduce by (ratio - 1) / ratio per dB over threshold
                    val overDb = linearToDb(envelope[ch]) - threshold
                    val reductionDb = overDb * (1f - 1f / ratio)
                    dbToLinear(-reductionDb)
                }

                output.putFloat(sample * gain * makeupLinear)
            }
        }

        output.flip()
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        envelope = FloatArray(channelCount) // reset envelope on seek/flush
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        envelope = FloatArray(0)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

        // Standard 1-pole IIR time constants (same formula as VLC / typical compressors)
        attackCoeff  = exp(-1.0 / (sampleRate * attackMs  / 1000.0)).toFloat()
        releaseCoeff = exp(-1.0 / (sampleRate * releaseMs / 1000.0)).toFloat()
    }

    private fun dbToLinear(db: Float): Float =
        10f.pow(db / 20f)

    private fun linearToDb(linear: Float): Float =
        if (linear <= 0f) -120f else (20f * ln(linear.toDouble()) / ln(10.0)).toFloat()
}
