package com.lagradost.cloudstream3.ui.player

import java.text.DecimalFormat

/**
 * Shared constants and helpers for playback speed, used by the speed dialog
 * ([FullScreenPlayer]) and the hold-to-speed-up gesture ([PlayerGestureHelper]).
 */
object PlaybackSpeeds {
    const val MIN_SPEED = 0.1f

    /** Upper bound of the speed dialog (slider, +/- buttons and presets). */
    const val MAX_DIALOG_SPEED = 4.0f

    /** Upper bound for the long-press speed, including the automatic escalation. */
    const val MAX_HOLD_SPEED = 8.0f

    /** Default long-press speed, stored in percent in the settings (200 = 2x). */
    const val DEFAULT_HOLD_SPEED_PERCENT = 200

    private const val EPSILON = 0.001f

    /** Converts the stored percent setting (200 = 2x) to a speed multiplier. */
    fun holdSpeedFromPercent(percent: Int): Float =
        (percent / 100f).coerceIn(MIN_SPEED, MAX_HOLD_SPEED)

    /**
     * Speed to use while the screen is held.
     *
     * Starts at the [configured] long-press speed, but if playback is already running at that
     * speed (or faster) the speed is doubled until it is actually faster than [current],
     * so with a 2x setting: 1x -> 2x, 2x -> 4x, 4x -> 8x. Never exceeds [MAX_HOLD_SPEED].
     */
    fun getHoldSpeed(current: Float, configured: Float): Float {
        var target = configured.coerceIn(MIN_SPEED, MAX_HOLD_SPEED)
        while (target <= current + EPSILON && target < MAX_HOLD_SPEED) {
            target *= 2f
        }
        return target.coerceAtMost(MAX_HOLD_SPEED)
    }

    /** Formats a speed for display without trailing zeroes, e.g. 2.0 -> "2x", 1.25 -> "1.25x". */
    fun format(speed: Float): String = DecimalFormat("0.##").format(speed.toDouble()) + "x"
}
