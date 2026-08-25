package com.lagradost.cloudstream3.ui.revamp.compose.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey450
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey50
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryRed
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentWhite30

enum class CloneflixProgressSize {
    REGULAR,
    LARGE
}

@Composable
fun CloneflixVideoProgressIndicator(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    bufferedProgress: Float = 0f,
    durationMs: Long = 0L,
    currentPositionMs: Long = 0L,
    size: CloneflixProgressSize = CloneflixProgressSize.LARGE,
    showTimestamps: Boolean = true,
    showRemainingTime: Boolean = false,
    enabled: Boolean = true,
    isHoveredOrActive: Boolean? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHoveredInternal by interactionSource.collectIsHoveredAsState()
    val isHovered = isHoveredOrActive ?: (isFocused || isHoveredInternal)

    val trackHeight by animateDpAsState(
        targetValue = when {
            isHovered && size == CloneflixProgressSize.LARGE -> 8.dp
            isHovered -> 6.dp
            size == CloneflixProgressSize.LARGE -> 5.dp
            else -> 3.5.dp
        },
        label = "trackHeight"
    )

    val thumbRadius by animateDpAsState(
        targetValue = when {
            isHovered && size == CloneflixProgressSize.LARGE -> 9.dp
            isHovered -> 7.dp
            size == CloneflixProgressSize.LARGE -> 7.dp
            else -> 5.dp
        },
        label = "thumbRadius"
    )

    val haloRadius by animateDpAsState(
        targetValue = if (isHovered) (thumbRadius + 8.dp) else 0.dp,
        label = "haloRadius"
    )

    var componentWidth by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(progress) }

    val activeProgress = if (isDragging) dragProgress else progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = activeProgress,
        label = "animatedProgress"
    )

    val effectiveCurrentMs = if (isDragging && durationMs > 0L) {
        (dragProgress * durationMs).toLong()
    } else {
        currentPositionMs
    }

    val elapsedText = remember(effectiveCurrentMs) {
        formatTimeMs(effectiveCurrentMs)
    }

    val endText = remember(effectiveCurrentMs, durationMs, showRemainingTime) {
        if (showRemainingTime && durationMs > effectiveCurrentMs) {
            "-${formatTimeMs(durationMs - effectiveCurrentMs)}"
        } else {
            formatTimeMs(durationMs)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = activeProgress,
                    range = 0f..1f
                )
            }
            .onKeyEvent { keyEvent ->
                if (!enabled) return@onKeyEvent false
                when (keyEvent.key) {
                    Key.DirectionLeft -> {
                        val step = if (durationMs > 0L) (10_000f / durationMs).coerceAtLeast(0.01f) else 0.05f
                        val newProgress = (activeProgress - step).coerceIn(0f, 1f)
                        onProgressChange(newProgress)
                        true
                    }
                    Key.DirectionRight -> {
                        val step = if (durationMs > 0L) (10_000f / durationMs).coerceAtLeast(0.01f) else 0.05f
                        val newProgress = (activeProgress + step).coerceIn(0f, 1f)
                        onProgressChange(newProgress)
                        true
                    }
                    else -> false
                }
            }
            .focusable(enabled = enabled, interactionSource = interactionSource)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .onSizeChanged { componentWidth = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val tapProgress = (offset.x / componentWidth).coerceIn(0f, 1f)
                        onProgressChange(tapProgress)
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragProgress = (offset.x / componentWidth).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            isDragging = false
                            onProgressChange(dragProgress)
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val deltaProgress = dragAmount / componentWidth
                            dragProgress = (dragProgress + deltaProgress).coerceIn(0f, 1f)
                            onProgressChange(dragProgress)
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            ) {
                val centerY = this.size.height / 2f
                val totalWidth = this.size.width
                val trackHeightPx = trackHeight.toPx()
                val cornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

                drawRoundRect(
                    color = Grey450,
                    topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                    size = Size(totalWidth, trackHeightPx),
                    cornerRadius = cornerRadius
                )

                val bufferedWidth = (bufferedProgress.coerceIn(0f, 1f) * totalWidth)
                if (bufferedWidth > 0f) {
                    drawRoundRect(
                        color = Grey50.copy(alpha = 0.65f),
                        topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                        size = Size(bufferedWidth, trackHeightPx),
                        cornerRadius = cornerRadius
                    )
                }

                val playedWidth = (animatedProgress * totalWidth)
                if (playedWidth > 0f) {
                    drawRoundRect(
                        color = PrimaryRed,
                        topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                        size = Size(playedWidth, trackHeightPx),
                        cornerRadius = cornerRadius
                    )
                }

                if (haloRadius.value > 0f) {
                    drawCircle(
                        color = PrimaryRed.copy(alpha = 0.25f),
                        radius = haloRadius.toPx(),
                        center = Offset(playedWidth, centerY)
                    )
                }

                drawCircle(
                    color = PrimaryRed,
                    radius = thumbRadius.toPx(),
                    center = Offset(playedWidth, centerY)
                )

                if (isHovered) {
                    drawCircle(
                        color = PrimaryWhite,
                        radius = (thumbRadius * 0.4f).toPx(),
                        center = Offset(playedWidth, centerY)
                    )
                }
            }
        }

        if (showTimestamps) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = elapsedText,
                    style = CloneflixTheme.typography.regularSmallBody,
                    color = PrimaryWhite
                )
                Text(
                    text = endText,
                    style = CloneflixTheme.typography.regularSmallBody,
                    color = Grey200
                )
            }
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Preview(name = "Progress Indicator Preview", showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun VideoProgressIndicatorPreview() {
    CloneflixTheme {
        Column(
            modifier = Modifier
                .background(Color(0xFF141414))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CloneflixVideoProgressIndicator(
                progress = 0.05f,
                bufferedProgress = 0.25f,
                durationMs = 3600_000L,
                currentPositionMs = 180_000L,
                onProgressChange = {}
            )
            CloneflixVideoProgressIndicator(
                progress = 0.60f,
                bufferedProgress = 0.85f,
                durationMs = 5400_000L,
                currentPositionMs = 3240_000L,
                isHoveredOrActive = true,
                onProgressChange = {}
            )
        }
    }
}
