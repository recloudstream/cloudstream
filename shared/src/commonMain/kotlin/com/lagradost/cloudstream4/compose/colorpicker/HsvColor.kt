package com.lagradost.cloudstream4.compose.colorpicker

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.toArgb
import kotlin.jvm.JvmInline
import kotlin.math.max

/**
 * Represents a color in the HSV (Hue, Saturation, Value) color space.
 *
 * This class provides properties to access the hue, saturation, and value of the color,
 * as well as methods to convert to and from RGB color space.
 */
@Immutable
@JvmInline
value class HsvColor private constructor(val packedValue: ULong) {
    /**
     * The hue of the color, in degrees (0-360).
     */
    @Stable
    val hue: Float
        get() = (packedValue shr 40 and 0xFFFFu).toFloat() / 100f

    /**
     * The saturation of the color (0-1).
     */
    @Stable
    val saturation: Float
        get() = (packedValue shr 20 and 0xFFFFFu).toFloat() / 1000000f

    /**
     * The value (brightness) of the color (0-1).
     */
    @Stable
    val value: Float
        get() = (packedValue and 0xFFFFFu).toFloat() / 1000000f

    /**
     * The red component of the color (0-1).
     */
    @Stable
    val red: Float
        get() = hsvToRgbComponent(5, hue, saturation, value)

    /**
     * The green component of the color (0-1).
     */
    @Stable
    val green: Float
        get() = hsvToRgbComponent(3, hue, saturation, value)

    /**
     * The blue component of the color (0-1).
     */
    @Stable
    val blue: Float
        get() = hsvToRgbComponent(1, hue, saturation, value)

    /**
     * Creates an HsvColor from the given hue, saturation, and value.
     */
    constructor(hue: Float, saturation: Float, value: Float) : this(
        ((hue * 100).toULong() and 0xFFFFu shl 40) or
                ((saturation * 1000000).toULong() and 0xFFFFFu shl 20) or
                ((value * 1000000).toULong() and 0xFFFFFu)
    )

    /**
     * Converts the HSV color to a Color object.
     *
     * **Note**: HSV to RGB conversion is a lossy process, so the resulting color may not be exactly the same as the original HSV color.
     * When red, green and blue are equal, hue will be 0 and saturation will be 0.
     */
    fun toColor(): Color = Color.hsv(hue, saturation, value)

    @Stable
    operator fun component1(): Float = hue

    @Stable
    operator fun component2(): Float = saturation

    @Stable
    operator fun component3(): Float = value

    /**
     * Copies the existing color, changing only the provided values.
     */
    @Stable
    fun copy(
        hue: Float = this.hue,
        saturation: Float = this.saturation,
        value: Float = this.value
    ): HsvColor {
        return HsvColor(hue, saturation, value)
    }

    /**
     * Returns a string representation of the color in HSV format.
     */
    @Stable
    override fun toString(): String {
        return "HsvColor(hue=$hue, saturation=$saturation, value=$value)"
    }

    companion object {
        val Saver: Saver<HsvColor, Long> = Saver(
            save = { it.packedValue.toLong() },
            restore = { HsvColor(it.toULong()) }
        )

        private fun hsvToRgbComponent(n: Int, h: Float, s: Float, v: Float): Float {
            val k = (n.toFloat() + h / 60f) % 6f
            return v - (v * s * max(0f, minOf(k, 4 - k, 1f)))
        }
    }
}

/**
 * Creates a new [HsvColor] instance from a [Color].
 *
 * @param color The Color to create an HsvColor from.
 * @return A non-null instance of [HsvColor]
 */
@Stable
fun HsvColor(color: Color): HsvColor = HsvColor(color.toArgb())

/**
 * Creates a new [Color] instance from an ARGB color int.
 *
 * @param color The ARGB color int to create a Color from.
 * @return A non-null instance of [HsvColor]
 */
@Stable
fun HsvColor(color: Int): HsvColor = HsvColor(color.toLong())

/**
 * Creates a new [Color] instance from an ARGB color long.
 * The resulting color is in the [sRGB][ColorSpaces.Srgb]
 * color space.
 *
 * @param color The ARGB color long to create a Color from.
 * @return A non-null instance of [HsvColor]
 */
@Stable
fun HsvColor(color: Long): HsvColor {
    val r = ((color shr 16) and 0xFF).toFloat() / 255f
    val g = ((color shr 8) and 0xFF).toFloat() / 255f
    val b = (color and 0xFF).toFloat() / 255f

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    val hue = when {
        delta == 0f -> 0f
        max == r -> ((g - b) / delta) % 6
        max == g -> ((b - r) / delta) + 2
        max == b -> ((r - g) / delta) + 4
        else -> 0f
    } * 60f

    val positiveHue = if (hue < 0f) hue + 360f else hue
    val saturation = if (max == 0f) 0f else 1 - min / max

    return HsvColor(positiveHue, saturation, max)
}