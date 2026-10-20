package com.lagradost.cloudstream4.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** If the tvOS-like TV style is used, mirrors the TV layouts of the app (layout-television) */
val LocalTvStyle: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** Same as tvGlass in colors.xml */
val TvGlass = Color.White.copy(alpha = 0.15f)

/** Same as tvGlassCard in colors.xml */
val TvGlassCard = Color.White.copy(alpha = 0.08f)

/** Same as the TvPageTitle style */
val TvPageTitle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold)

/** Same as tv_top_glow.xml */
private val TvGlowColor = Color(0xFF2B3040)
private val TvGlowHeight = 320.dp

/** Lists are a narrow centered column on TV */
@Composable
fun Modifier.tvContentWidth(): Modifier =
    if (LocalTvStyle.current) {
        fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 760.dp)
    } else this

/**
 * The page color with a glow at the top on TV, for scaffolds with [tvScaffoldContainerColor].
 * A full screen gradient is too slow for TVs.
 */
@Composable
fun Modifier.tvPageBackground(): Modifier {
    if (!LocalTvStyle.current) return this
    val pageColor = MaterialTheme.colorScheme.background
    return drawBehind {
        drawRect(pageColor)
        val glowHeight = TvGlowHeight.toPx()
        drawRect(
            brush = Brush.verticalGradient(listOf(TvGlowColor, Color.Transparent), endY = glowHeight),
            size = Size(size.width, glowHeight),
        )
    }
}

@Composable
fun tvScaffoldContainerColor(): Color =
    if (LocalTvStyle.current) Color.Transparent else MaterialTheme.colorScheme.background

/** Soft highlight with a slight lift, the focus style on TV instead of an outline */
@Composable
internal fun Modifier.tvHighlight(hasFocus: Boolean, shape: Shape): Modifier {
    val progress by animateFloatAsState(targetValue = if (hasFocus) 1.0f else 0.0f)
    return graphicsLayer {
        scaleX = 1.0f + 0.02f * progress
        scaleY = scaleX
        // Also clips the focus indication of clickable, which is a rectangle
        this.shape = shape
        clip = true
    }.background(Color.White.copy(alpha = 0.20f * progress), shape)
}

private val SelectKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * On TV a text field is only editable after pressing it, so the keyboard does not open
 * when the remote just moves the focus over it. Use [isEditing] as the inverse of readOnly.
 */
@Composable
fun Modifier.editOnSelect(isEditing: MutableState<Boolean>): Modifier {
    if (!LocalTvStyle.current) return this
    val keyboard = LocalSoftwareKeyboardController.current
    return onFocusChanged { if (!it.hasFocus) isEditing.value = false }
        .onPreviewKeyEvent { event ->
            if (isEditing.value || event.key !in SelectKeys) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyUp) {
                isEditing.value = true
                keyboard?.show()
            }
            true
        }
}
