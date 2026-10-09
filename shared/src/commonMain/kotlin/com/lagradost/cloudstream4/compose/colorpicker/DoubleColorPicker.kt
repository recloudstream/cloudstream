package com.lagradost.cloudstream4.compose.colorpicker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitDragOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun DoubleColorPicker(
    color: () -> HsvColor,
    onColorChange: (color: HsvColor) -> Unit,
    modifier: Modifier = Modifier,
    ringStrokeWidth: Dp = 16.dp,
    innerPadding: Dp = 50.dp,
    ringInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    ringThumb: @Composable () -> Unit = {
        ColorPickerDefaults.Thumb(Color.hsv(color().hue, 1f, 1f), ringInteractionSource)
    },
    innerInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    innerThumb: @Composable () -> Unit = {
        ColorPickerDefaults.Thumb(color().toColor(), innerInteractionSource)
    },
) {
    Box(modifier) {
        RingColorPicker(
            interactionSource = ringInteractionSource,
            thumb = ringThumb,
            ringStrokeWidth = ringStrokeWidth,
            color = color,
            onColorChange = onColorChange,
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            CircularSquareColorPicker(
                interactionSource = innerInteractionSource,
                thumb = innerThumb,
                color = color,
                onColorChange = onColorChange,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * The standard circle color picker that allows the user to select a color by dragging a thumb around the color space.
 *
 * The color is represented in HSV color space with a fixed hue. The saturation and value can be controlled by
 * dragging the thumb.
 *
 * @param color The current color
 * @param onColorChange Callback that is called when the color changes
 * @param modifier The modifier to be applied to the color picker
 * @param interactionSource The interaction source for the color picker
 * @param thumb Composable that is used to draw the thumb
 * @param shape The shape of the color picker, note that the corner radius should be kept small,
 * to prevent the thumb from visually appearing outside the color picker
 * @param onColorChangeFinished Callback that is called when the user finishes changing the color
 *
 * @see CircularColorPicker
 * @see RingColorPicker
 */
@Composable
fun CircularSquareColorPicker(
    color: () -> HsvColor,
    onColorChange: (color: HsvColor) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    thumb: @Composable () -> Unit = {
        ColorPickerDefaults.Thumb(color().toColor(), interactionSource)
    },
    onColorChangeFinished: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }

    val currentColor by rememberUpdatedState(color)
    val currentOnColorChange by rememberUpdatedState(onColorChange)
    val currentOnColorChangeFinished by rememberUpdatedState(onColorChangeFinished)

    Box {
        Canvas(
            modifier = modifier
                .size(ColorPickerDefaults.ComponentSize)
                .onSizeChanged { size = it }
                .clip(CircleShape)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()

                        hsvColorForPosition(down.position, size).let { (s, v) ->
                            currentOnColorChange(currentColor().copy(saturation = s, value = v))
                        }

                        // Start drag interaction
                        val interaction = DragInteraction.Start()
                        scope.launch {
                            interactionSource.emit(interaction)
                        }

                        var change = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                            change.consume()
                            hsvColorForPosition(change.position, size).let { (s, v) ->
                                currentOnColorChange(currentColor().copy(saturation = s, value = v))
                            }
                        }

                        // Continue dragging
                        while (change != null && change.pressed) {
                            change.consume()
                            hsvColorForPosition(change.position, size).let { (s, v) ->
                                currentOnColorChange(currentColor().copy(saturation = s, value = v))
                            }
                            change = awaitDragOrCancellation(change.id)
                        }

                        scope.launch {
                            interactionSource.emit(DragInteraction.Stop(interaction))
                        }

                        currentOnColorChangeFinished()
                    }
                }
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            val saturationBrush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
            val hueBrush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color.hsv(currentColor().hue, 1f, 1f)
                )
            )

            drawRect(Color.White)
            drawRect(hueBrush)
            drawRect(saturationBrush)
        }

        Box(
            modifier = Modifier.offset {
                val x = -1.0 + currentColor().saturation * 2.0
                val y = 1.0 - currentColor().value * 2.0

                // https://squircular.blogspot.com/2015/09/mapping-circle-to-square.html
                val u = x * sqrt(1 - y * y * 0.5)
                val v = y * sqrt(1 - x * x * 0.5)

                IntOffset(
                    x = (u * size.width * 0.5 + size.width * 0.5).roundToInt(),//(currentColor().saturation * size.width).roundToInt(),
                    y = (v * size.height * 0.5 + size.height * 0.5).roundToInt(), //(size.height - currentColor().value * size.height).roundToInt()
                )
            }
        ) {
            thumb()
        }
    }
}

private fun hsvColorForPosition(position: Offset, size: IntSize): Pair<Float, Float> {
    val clampedX = position.x / size.width
    val clampedY = position.y / size.height

    val x = -1.0 + clampedX * 2.0
    val y = 1.0 - clampedY * 2.0

    // coerce here to prevent drag from feeling like a square
    val radius = sqrt(x * x + y * y).coerceIn(0.0, 1.0)
    val angle = atan2(y, x)

    val u = cos(angle) * radius
    val v = sin(angle) * radius

    val u2 = u * u
    val v2 = v * v
    val sqrt22 = 2.0 * sqrt(2.0)

    // https://squircular.blogspot.com/2015/09/mapping-circle-to-square.html
    val xOut = 0.5 * sqrt(2 + sqrt22 * u + u2 - v2) - 0.5 * sqrt(2 - sqrt22 * u + u2 - v2)
    val yOut = 0.5 * sqrt(2 + sqrt22 * v - u2 + v2) - 0.5 * sqrt(2 - sqrt22 * v - u2 + v2)

    return (xOut * 0.5 + 0.5).toFloat() to (yOut * 0.5 + 0.5).toFloat()
}