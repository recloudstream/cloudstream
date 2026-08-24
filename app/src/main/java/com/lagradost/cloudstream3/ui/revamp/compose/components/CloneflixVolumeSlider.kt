package com.lagradost.cloudstream3.ui.revamp.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey450
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryRed
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.revamp.compose.theme.SurfaceDark
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentBlack90
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentWhite20

enum class CloneflixVolumeLevel {
    HIGH,
    MIDDLE,
    MUTE
}

@Composable
fun CloneflixVerticalVolumeBar(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    height: Float = 100f,
    width: Float = 6f,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    var trackHeightPx by remember { mutableFloatStateOf(1f) }
    val animatedVolume by animateFloatAsState(
        targetValue = volume.coerceIn(0f, 1f),
        label = "animatedVolume"
    )

    Box(
        modifier = modifier
            .width(36.dp)
            .height(110.dp)
            .onSizeChanged { trackHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = volume,
                    range = 0f..1f
                )
            }
            .onKeyEvent { keyEvent ->
                if (!enabled) return@onKeyEvent false
                when (keyEvent.key) {
                    Key.DirectionUp -> {
                        onVolumeChange((volume + 0.1f).coerceIn(0f, 1f))
                        true
                    }
                    Key.DirectionDown -> {
                        onVolumeChange((volume - 0.1f).coerceIn(0f, 1f))
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures { change, _ ->
                    val y = change.position.y
                    val newVol = (1f - (y / trackHeightPx)).coerceIn(0f, 1f)
                    onVolumeChange(newVol)
                }
            }
            .focusable(enabled = enabled, interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(width = 36.dp, height = 100.dp)) {
            val totalH = size.height
            val totalW = size.width
            val barW = 6.dp.toPx()
            val barX = (totalW - barW) / 2f
            val corner = CornerRadius(barW / 2f, barW / 2f)

            // Background bar (grey)
            drawRoundRect(
                color = Grey450,
                topLeft = Offset(barX, 0f),
                size = Size(barW, totalH),
                cornerRadius = corner
            )

            // Active bar (red) from bottom upwards
            val fillH = animatedVolume * totalH
            if (fillH > 0f) {
                drawRoundRect(
                    color = PrimaryRed,
                    topLeft = Offset(barX, totalH - fillH),
                    size = Size(barW, fillH),
                    cornerRadius = corner
                )
            }

            // Scrubber thumb circle
            val thumbY = totalH - fillH
            val thumbRadius = 7.dp.toPx()
            drawCircle(
                color = PrimaryWhite,
                radius = thumbRadius,
                center = Offset(totalW / 2f, thumbY.coerceIn(thumbRadius, totalH - thumbRadius))
            )
        }
    }
}

@Composable
fun CloneflixVolumeControlPopup(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = true
) {
    val volumeLevel = when {
        isMuted || volume <= 0.01f -> CloneflixVolumeLevel.MUTE
        volume < 0.5f -> CloneflixVolumeLevel.MIDDLE
        else -> CloneflixVolumeLevel.HIGH
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = TransparentBlack90,
        border = BorderStroke(1.dp, Color(0x33FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CloneflixVerticalVolumeBar(
                volume = if (isMuted) 0f else volume,
                onVolumeChange = {
                    if (isMuted) onToggleMute()
                    onVolumeChange(it)
                }
            )

            val iconRes = when (volumeLevel) {
                CloneflixVolumeLevel.HIGH -> R.drawable.ic_baseline_volume_up_24
                CloneflixVolumeLevel.MIDDLE -> R.drawable.ic_baseline_volume_up_24
                CloneflixVolumeLevel.MUTE -> R.drawable.ic_baseline_volume_mute_24
            }

            val iconColor = if (volumeLevel == CloneflixVolumeLevel.MUTE) Grey200 else PrimaryWhite

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleMute)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = "Toggle Mute",
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Preview(name = "Volume Slider Preview", showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun VolumeSliderPreview() {
    CloneflixTheme {
        Row(
            modifier = Modifier
                .background(Color(0xFF141414))
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            CloneflixVolumeControlPopup(
                volume = 0.85f,
                onVolumeChange = {},
                isMuted = false,
                onToggleMute = {}
            )
            CloneflixVolumeControlPopup(
                volume = 0.40f,
                onVolumeChange = {},
                isMuted = false,
                onToggleMute = {}
            )
            CloneflixVolumeControlPopup(
                volume = 0f,
                onVolumeChange = {},
                isMuted = true,
                onToggleMute = {}
            )
        }
    }
}
