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
 * A dynamic range compressor for the audio player.
 *
 * ## What it does
 * A dynamic range compressor automatically turns down loud sounds and turns up
 * quiet ones, narrowing the gap between the loudest and quietest moments.
 * This is useful for watching movies or TV shows where action/explosion scenes
 * are very loud but dialogue scenes are very quiet — the compressor brings
 * everything to a more comfortable, even volume.
 *
 * ## Algorithm
 * Implements a standard single-band feed-forward peak compressor using a
 * 1-pole IIR envelope follower. This is the same algorithm used in most
 * audio software (Audacity, VLC, etc.) and is well-established DSP. It is
 * NOT a port or derivative of any specific implementation — the math is
 * standard and covered extensively in audio engineering literature
 * (e.g. Zölzer "Digital Audio Signal Processing").
 *
 * Uses a single shared envelope across all channels so left and right always
 * receive identical gain — independent per-channel envelopes would destroy
 * the stereo image.
 *
 * Handles both PCM_16BIT and PCM_FLOAT (both are used by ExoPlayer depending
 * on the source and device). Always stays "active" in the pipeline so that
 * enable/disable works instantly without reloading the player — when disabled,
 * samples are copied unchanged (passthrough).
 */
@OptIn(UnstableApi::class)
class DynamicRangeCompressor : AudioProcessor {

    @Volatile var enabled: Boolean = false

    /**
     * The level (in dB) above which compression kicks in. Signals quieter than
     * this pass through unchanged; louder signals get compressed.
     * Lower = compresses more content (including quieter sounds like dialogue).
     * -24 dB is a good starting point for movies: it catches action peaks
     * while leaving quiet dialogue mostly untouched before makeup gain.
     */
    @Volatile var threshold: Float = -24f  // dB, range -30..0

    /**
     * How aggressively to compress sounds that exceed the threshold.
     * A ratio of 8:1 means an 8 dB increase above the threshold becomes only
     * 1 dB in the output. Higher = more "squashed" dynamic range.
     * Below ~2 is barely noticeable; above ~10 sounds very processed/radio-like.
     */
    @Volatile var ratio: Float = 8f  // n:1, range 1..20

    /**
     * How quickly (in ms) the compressor clamps down when a loud sound starts.
     * Too low (< 1 ms): no transient punch, sounds dull.
     * Too high (> 50 ms): loud transients slip through before gain reduces.
     * 5 ms preserves punch while still catching most action scene peaks.
     */
    @Volatile var attackMs: Float = 5f  // ms, range 1..400

    /**
     * How quickly (in ms) the compressor lets go after a loud sound ends.
     * Too low (< 50 ms): audible "pumping" — volume visibly breathes up/down.
     * Too high (> 800 ms): gain stays low too long, quieter sounds after
     * an action scene stay suppressed for a noticeable time.
     * 400 ms is slow enough to be transparent on most movie content.
     */
    @Volatile var releaseMs: Float = 400f  // ms, range 2..800

    /**
     * Output gain (in dB) applied after compression.
     * Compression reduces overall loudness so makeup gain brings it back up.
     * +12 dB compensates for the typical reduction at a 8:1 ratio with a
     * -24 dB threshold, and also lifts quiet dialogue to a more audible level.
     * Too high risks clipping on uncompressed peaks below the threshold.
     */
    @Volatile var makeupGain: Float = 12f  // dB, range 0..24

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
        if (!enabled) { out.put(inputBuffer); out.flip(); return }
        updateCoefficients()
        val makeupLinear = dbToLinear(makeupGain)
        val threshLinear = dbToLinear(threshold)
        val bytesPerSample = if (isFloat) 4 else 2
        val frameCount = remaining / (channelCount * bytesPerSample)
        if (isFloat) processFloat(inputBuffer, out, frameCount, threshLinear, makeupLinear)
        else processShort(inputBuffer, out, frameCount, threshLinear, makeupLinear)
        out.flip()
    }

    /** Processes PCM_FLOAT frames. Two-pass: peak detection then gain application. */
    private fun processFloat(
        input: ByteBuffer, out: ByteBuffer,
        frameCount: Int, threshLinear: Float, makeupLinear: Float
    ) {
        repeat(frameCount) {
            val frameStart = input.position()
            var peak = 0f
            repeat(channelCount) { val s = input.getFloat(); val a = if (s < 0f) -s else s; if (a > peak) peak = a }
            advanceEnvelope(peak)
            val gain = computeGain(threshLinear) * makeupLinear
            input.position(frameStart)
            repeat(channelCount) { out.putFloat(input.getFloat() * gain) }
        }
    }

    /** Processes PCM_16BIT frames. Two-pass: peak detection then gain application. */
    private fun processShort(
        input: ByteBuffer, out: ByteBuffer,
        frameCount: Int, threshLinear: Float, makeupLinear: Float
    ) {
        repeat(frameCount) {
            val frameStart = input.position()
            var peak = 0f
            repeat(channelCount) { val s = input.getShort() / 32768f; val a = if (s < 0f) -s else s; if (a > peak) peak = a }
            advanceEnvelope(peak)
            val gain = computeGain(threshLinear) * makeupLinear
            input.position(frameStart)
            repeat(channelCount) {
                val s = input.getShort() / 32768f
                out.putShort((s * gain).coerceIn(-1f, 1f).let { (it * 32768f).toInt().toShort() })
            }
        }
    }

    private fun advanceEnvelope(peak: Float) {
        val coeff = if (peak > envelope) attackCoeff else releaseCoeff
        envelope = peak + coeff * (envelope - peak)
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
