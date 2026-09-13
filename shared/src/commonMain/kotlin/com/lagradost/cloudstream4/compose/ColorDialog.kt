package com.lagradost.cloudstream4.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.cancel
import com.lagradost.cloudstream4.generated.resources.color
import com.lagradost.cloudstream4.generated.resources.luminance
import com.lagradost.cloudstream4.generated.resources.ok
import com.lagradost.cloudstream4.generated.resources.transparency
import com.mihon.material.Slider
import com.mihon.material.padding
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


internal val colors = arrayOf(
    Color(0xFFF44336), // RED 500
    Color(0xFFE91E63), // PINK 500
    Color(0xFFFF2C93), // LIGHT PINK 500
    Color(0xFF9C27B0), // PURPLE 500
    Color(0xFF673AB7), // DEEP PURPLE 500
    Color(0xFF3F51B5), // INDIGO 500
    Color(0xFF2196F3), // BLUE 500
    Color(0xFF03A9F4), // LIGHT BLUE 500
    Color(0xFF00BCD4), // CYAN 500
    Color(0xFF009688), // TEAL 500
    Color(0xFF4CAF50), // GREEN 500
    Color(0xFF8BC34A), // LIGHT GREEN 500
    Color(0xFFCDDC39), // LIME 500
    Color(0xFFFFEB3B), // YELLOW 500
    Color(0xFFFFC107), // AMBER 500
    Color(0xFFFF9800), // ORANGE 500
    Color(0xFF795548), // BROWN 500
    Color(0xFF607D8B), // BLUE GREY 500
    Color(0xFF9E9E9E), // GREY 500
)
internal val colors2 = arrayOf(
    Color(0xFF795548), // BROWN 500
    Color(0xFF607D8B), // BLUE GREY 500
    Color(0xFF9E9E9E), // GREY 500
)

@Composable
fun ColorDialog(
    title: String,
    color: Color,
    dismiss: () -> Unit,
    confirm: (Color) -> Unit,
) {
    var alpha by remember { mutableFloatStateOf(color.alpha) }
    var selectedColor by remember { mutableStateOf(color) }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = MaterialTheme.colorScheme.background,
        onDismissRequest = dismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorCircle(
                    color = selectedColor.copy(alpha = alpha),
                )
                Spacer(modifier = Modifier.size(MaterialTheme.padding.medium))
                Text(text = title)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(Res.string.color),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(MaterialTheme.padding.extraSmall),
                    style = MaterialTheme.typography.titleMedium
                )
                FlowRow {
                    ColorCircle(
                        color = Color.White.copy(alpha = alpha),
                    ) {
                        selectedColor = Color.White
                    }

                    val hsl = floatArrayOf(0.0f, 1.0f, 0.5f)
                    (0..18).forEach { hue ->
                        hsl[0] = hue.toFloat() * 18f
                        val color = hslToColor(hsl)
                        ColorCircle(
                            color = color.copy(alpha = alpha),
                        ) {
                            selectedColor = color
                        }
                    }
                    colors2.forEach { color ->
                        ColorCircle(
                            color = color.copy(alpha = alpha),
                        ) {
                            selectedColor = color
                        }
                    }
                }

                Text(
                    text = stringResource(Res.string.luminance),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(MaterialTheme.padding.extraSmall),
                    style = MaterialTheme.typography.titleMedium
                )

                // https://github.com/mhssn95/compose-color-picker/blob/main/colorPicker/src/main/java/io/mhssn/colorpicker/ext/drawExt.kt
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    val hsl = floatArrayOf(0.0f, 0.0f, 0.0f)
                    (0..10).forEach { luminance ->
                        rbgToHSL(selectedColor.red, selectedColor.green, selectedColor.blue, hsl)
                        hsl[2] = luminance.toFloat() * 0.1f
                        val color = hslToColor(hsl)
                        ColorCircle(
                            color = color.copy(alpha = alpha),
                        ) {
                            selectedColor = color
                        }
                    }
                }

                Text(
                    text = stringResource(Res.string.transparency),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(MaterialTheme.padding.extraSmall),
                    style = MaterialTheme.typography.titleMedium
                )
                val haptic = LocalHapticFeedback.current
                Slider(
                    modifier = Modifier.focusOutline().padding(MaterialTheme.padding.extraSmall),
                    value = (alpha * 10.0f).toInt(),
                    onValueChange = f@{ newValue ->
                        alpha = newValue.toFloat() * 0.1f
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    valueRange = 0..10,
                )
            }
        },
        confirmButton = {
            WhiteButton(
                text = stringResource(Res.string.ok),
                onClick = { confirm(selectedColor.copy(alpha = alpha)) })
        },
        dismissButton = {
            BlackButton(text = stringResource(Res.string.cancel), onClick = dismiss)
        })
}

@Composable
fun ColorCircle(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    clickable: (() -> Unit)? = null
) {
    Canvas(
        modifier = modifier
            .focusOutline(shape = CircleShape)
            .padding(MaterialTheme.padding.extraSmall)
            .size(size)
            .border(
                1.dp,
                if (color.alpha > 0.5f) MaterialTheme.colorScheme.onBackground else color.copy(
                    alpha = 1.0f
                ),
                CircleShape
            )
            .circle()
            .clickable(enabled = clickable != null) {
                clickable?.invoke()
            }
    ) {
        drawTransparentBackground(10)
        drawRect(color)
    }
}

internal fun DrawScope.drawTransparentBackground(verticalBoxesSize: Int = 10) {
    val boxSize = size.height / verticalBoxesSize
    repeat((size.width / boxSize).roundToInt() + 1) { x ->
        repeat(verticalBoxesSize) { y ->
            drawRect(
                if ((y + x) % 2 == 0) {
                    Color.LightGray
                } else {
                    Color.White
                }, topLeft = Offset(x * boxSize, y * boxSize), size = Size(boxSize, boxSize)
            )
        }
    }
}

/**
 * Convert RGB components to HSL (hue-saturation-lightness).
 *
 *  * outHsl[0] is Hue [0, 360)
 *  * outHsl[1] is Saturation [0, 1]
 *  * outHsl[2] is Lightness [0, 1]
 *
 *
 * @param rf red component value [0, 1]
 * @param gf green component value [0, 1]
 * @param bf blue component value [0, 1]
 * @param outHsl 3-element array which holds the resulting HSL components
 */
fun rbgToHSL(
    rf: Float,
    gf: Float,
    bf: Float,
    outHsl: FloatArray
) {
    val max = max(rf, max(gf, bf))
    val min = min(rf, min(gf, bf))
    val deltaMaxMin = max - min

    var h: Float
    val s: Float
    val l = (max + min) / 2f

    if (max == min) {
        // Monochromatic
        s = 0f
        h = s
    } else {
        if (max == rf) {
            h = ((gf - bf) / deltaMaxMin) % 6f
        } else if (max == gf) {
            h = ((bf - rf) / deltaMaxMin) + 2f
        } else {
            h = ((rf - gf) / deltaMaxMin) + 4f
        }

        s = deltaMaxMin / (1f - abs(2f * l - 1f))
    }

    h = (h * 60f) % 360f
    if (h < 0) {
        h += 360f
    }

    outHsl[0] = h.coerceIn(0f, 360f)
    outHsl[1] = s.coerceIn(0f, 1f)
    outHsl[2] = l.coerceIn(0f, 1f)
}

fun hslToColor(hsl: FloatArray): Color {
    val h = hsl[0]
    val s = hsl[1]
    val l = hsl[2]

    val c = (1f - abs(2 * l - 1f)) * s
    val m = l - 0.5f * c
    val x = c * (1f - abs((h / 60f % 2f) - 1f))

    val hueSegment = h.toInt() / 60

    var r = 0
    var g = 0
    var b = 0

    when (hueSegment) {
        0 -> {
            r = (255 * (c + m)).roundToInt()
            g = (255 * (x + m)).roundToInt()
            b = (255 * m).roundToInt()
        }

        1 -> {
            r = (255 * (x + m)).roundToInt()
            g = (255 * (c + m)).roundToInt()
            b = (255 * m).roundToInt()
        }

        2 -> {
            r = (255 * m).roundToInt()
            g = (255 * (c + m)).roundToInt()
            b = (255 * (x + m)).roundToInt()
        }

        3 -> {
            r = (255 * m).roundToInt()
            g = (255 * (x + m)).roundToInt()
            b = (255 * (c + m)).roundToInt()
        }

        4 -> {
            r = (255 * (x + m)).roundToInt()
            g = (255 * m).roundToInt()
            b = (255 * (c + m)).roundToInt()
        }

        5, 6 -> {
            r = (255 * (c + m)).roundToInt()
            g = (255 * m).roundToInt()
            b = (255 * (x + m)).roundToInt()
        }
    }

    r = r.coerceIn(0, 255)
    g = g.coerceIn(0, 255)
    b = b.coerceIn(0, 255)

    return Color(r, g, b)
}