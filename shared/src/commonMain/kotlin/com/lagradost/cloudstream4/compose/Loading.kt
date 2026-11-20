package com.lagradost.cloudstream4.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val grayColor = Color(0x24696969)
val shimmerColor = Color.White.copy(alpha = 0.05f)
const val loadingLineHeight = 15

@Composable
fun Modifier.loading(): Modifier {
    return this
        .clip(RoundedCornerShape(5.dp))
        .background(grayColor)
        .gradientEffect(shimmerColor)
}

@Composable
fun LoadingLine(fraction: Float = 1.0f,
                @Suppress("ModifierParameter") modifier: Modifier = Modifier,) {
    Box(
        modifier = modifier
            .fillMaxWidth(fraction)
            .height(loadingLineHeight.dp)
            .loading()
    )
}

@Composable
fun ColumnScope.LoadingButton() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(35.dp)
            .loading()
    )
}

@Composable
fun RowScope.LoadingButton() {
    Box(
        modifier = Modifier
            .weight(1.0f)
            .height(35.dp)
            .loading()
    )
}

@Composable
fun LoadingWidth(width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(loadingLineHeight.dp)
            .loading()
    )
}

@Composable
fun RowScope.LoadingWeight() {
    Box(
        modifier = Modifier
            .weight(1.0f)
            .height(loadingLineHeight.dp)
            .loading()
    )
}
