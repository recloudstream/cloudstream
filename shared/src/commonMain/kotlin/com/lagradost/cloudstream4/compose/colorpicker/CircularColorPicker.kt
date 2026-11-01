package com.lagradost.cloudstream4.compose.colorpicker

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.toOffset
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Circular color picker that allows the user to select a hue by dragging a thumb around the circle.
 * The color is represented in HSV color space with a fixed value.
 *
 * @param color The current color
 * @param onColorChange Callback that is called when the color changes
 * @param modifier The modifier to be applied to the color picker
 * @param interactionSource The interaction source for the color picker
 * @param onColorChangeFinished Callback that is called when the user finishes changing the color
 * @param thumb Composable that is used to draw the thumb
 *
 * @see RingColorPicker
 * @see SquareColorPicker
 */
@Composable
fun CircularColorPicker(
    color: () -> HsvColor,
    onColorChange: (HsvColor) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onColorChangeFinished: () -> Unit = {},
    thumb: @Composable () -> Unit = {
        ColorPickerDefaults.Thumb(color().toColor(), interactionSource)
    }
) {
    CircularColorPicker(
        hue = { color().hue },
        saturation = { color().saturation },
        value = { color().value },
        onColorChange = { h, s -> onColorChange(color().copy(hue = h, saturation = s)) },
        modifier = modifier,
        interactionSource = interactionSource,
        onColorChangeFinished = onColorChangeFinished,
        thumb = thumb
    )
}

/**
 * Circular color picker that allows the user to select a hue by dragging a thumb around the circle.
 * The color is represented in HSV color space with a fixed value.
 *
 * @param hue The hue of the color
 * @param saturation The saturation of the color
 * @param value The value of the color
 * @param onColorChange Callback that is called when the color changes
 * @param modifier The modifier to be applied to the color picker
 * @param interactionSource The interaction source for the color picker
 * @param onColorChangeFinished Callback that is called when the user finishes changing the color
 * @param thumb Composable that is used to draw the thumb
 *
 * @see RingColorPicker
 * @see SquareColorPicker
 */
@Composable
fun CircularColorPicker(
    hue: () -> Float,
    saturation: () -> Float,
    modifier: Modifier = Modifier,
    value: () -> Float = { 1f },
    onColorChange: (hue: Float, saturation: Float) -> Unit,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onColorChangeFinished: () -> Unit = {},
    thumb: @Composable () -> Unit = {
        ColorPickerDefaults.Thumb(Color.hsv(hue(), saturation(), value()), interactionSource)
    }
) {
    val scope = rememberCoroutineScope()
    var radius by remember { mutableFloatStateOf(0f) }

    val currentOnColorChange by rememberUpdatedState(onColorChange)
    val currentOnColorChangeFinished by rememberUpdatedState(onColorChangeFinished)

    Box(
        modifier = modifier
            .size(ColorPickerDefaults.ComponentSize)
            .onSizeChanged {
                radius = it.width / 2f
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val downPosition = down.position
                    val center = size.center.toOffset()

                    // Check if initial touch is within the circle
                    if ((downPosition - center).getDistanceSquared() > radius * radius) return@awaitEachGesture

                    // Handle initial tap
                    updateColorFromPosition(downPosition, center, radius, currentOnColorChange)

                    // Start drag interaction
                    val interaction = DragInteraction.Start()
                    scope.launch {
                        interactionSource.emit(interaction)
                    }

                    var change = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                        val adjustedPosition = clampPositionToRadius(change.position, center, radius)
                        updateColorFromPosition(adjustedPosition, center, radius, currentOnColorChange)
                    }

                    // Continue dragging
                    while (change != null && change.pressed) {
                        change.consume()
                        val adjustedPosition = clampPositionToRadius(change.position, center, radius)
                        updateColorFromPosition(adjustedPosition, center, radius, currentOnColorChange)
                        change = awaitDragOrCancellation(change.id)
                    }

                    scope.launch {
                        interactionSource.emit(DragInteraction.Stop(interaction))
                    }

                    currentOnColorChangeFinished()
                }
            }
            .drawWithCache {
                val v = value()
                val hueBrush = Brush.sweepGradient(
                    colors = List(7) { i ->
                        Color.hsv(
                            hue = i * 60f,
                            saturation = 1f,
                            value = v
                        )
                    }
                )
                val saturationBrush = Brush.radialGradient(
                    listOf(Color.hsv(0f, 0f, v), Color.Transparent)
                )

                onDrawBehind {
                    drawCircle(hueBrush)
                    drawCircle(saturationBrush)
                }
            }
    ) {
        Box(
            modifier = Modifier.offset {
                val angle = hue() * DEG_TO_RAD
                val distance = saturation() * radius

                IntOffset(
                    x = (radius + distance * cos(angle)).roundToInt(),
                    y = (radius + distance * sin(angle)).roundToInt()
                )
            }
        ) {
            thumb()
        }
    }
}

/**
 * Get the color for a given position in the circular color picker.
 *
 * @return A pair of hue and saturation values, or null if the position is outside the circle.
 */
private inline fun updateColorFromPosition(
    position: Offset,
    center: Offset,
    radius: Float,
    onResult: (hue: Float, saturation: Float) -> Unit
) {
    val offset = position - center

    val degrees = atan2(offset.y, offset.x) * RAD_TO_DEG
    val centerAngle = (degrees + 360) % 360
    val distance = offset.getDistance()
    val saturation = (distance / radius).coerceIn(0f, 1f)

    onResult(centerAngle, saturation)
}

/**
 * Clamp the position to the radius of the color picker circle.
 *
 * @return The clamped position that lies within the circle of the given radius.
 */
private fun clampPositionToRadius(position: Offset, center: Offset, radius: Float): Offset {
    val offset = position - center

    // If the position is already within the radius, return it as is
    if (offset.getDistanceSquared() <= radius * radius) return position

    // Otherwise, clamp the position to the edge of the circle
    val scale = radius / offset.getDistance()
    return center + (offset * scale)
}