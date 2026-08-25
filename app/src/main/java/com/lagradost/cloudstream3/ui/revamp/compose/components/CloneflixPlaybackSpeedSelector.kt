package com.lagradost.cloudstream3.ui.revamp.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey450
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryRed
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentBlack90

enum class CloneflixPlaybackSpeed(val speedMultiplier: Float, val label: String) {
    SPEED_0_5X(0.5f, "0.5x"),
    SPEED_0_75X(0.75f, "0.75x"),
    SPEED_1_0X(1.0f, "1x (Normal)"),
    SPEED_1_25X(1.25f, "1.25x"),
    SPEED_1_5X(1.5f, "1.5x")
}

@Composable
fun CloneflixPlaybackSpeedSelector(
    currentSpeed: CloneflixPlaybackSpeed,
    onSpeedSelected: (CloneflixPlaybackSpeed) -> Unit,
    modifier: Modifier = Modifier,
    onCloseClick: (() -> Unit)? = null
) {
    val speeds = CloneflixPlaybackSpeed.entries
    val currentIndex = speeds.indexOf(currentSpeed).coerceAtLeast(0)

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        modifier = modifier
            .width(520.dp)
            .border(
                border = if (isFocused) BorderStroke(2.dp, PrimaryWhite) else BorderStroke(1.dp, Color(0x33FFFFFF)),
                shape = RoundedCornerShape(16.dp)
            )
            .onKeyEvent { keyEvent ->
                when (keyEvent.key) {
                    Key.DirectionLeft -> {
                        if (currentIndex > 0) {
                            onSpeedSelected(speeds[currentIndex - 1])
                            true
                        } else false
                    }
                    Key.DirectionRight -> {
                        if (currentIndex < speeds.size - 1) {
                            onSpeedSelected(speeds[currentIndex + 1])
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = interactionSource)
            .semantics {
                role = Role.Button
                contentDescription = "Playback Speed: ${currentSpeed.label}"
            },
        shape = RoundedCornerShape(16.dp),
        color = TransparentBlack90
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Playback Speed",
                    style = CloneflixTheme.typography.mediumHeadline2,
                    fontSize = 18.sp,
                    color = PrimaryWhite
                )

                if (onCloseClick != null) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF))
                            .clickable(onClick = onCloseClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.cloneflix_ic_close),
                            contentDescription = "Close",
                            tint = PrimaryWhite,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .padding(horizontal = 24.dp)
                ) {
                    val y = size.height / 2f
                    drawLine(
                        color = Grey450,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 3.dp.toPx()
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    speeds.forEachIndexed { index, speed ->
                        val isSelected = speed == currentSpeed
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.25f else 1f,
                            label = "speedPointScale"
                        )

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .clickable { onSpeedSelected(speed) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryRed.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryRed)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Grey200)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                speeds.forEach { speed ->
                    val isSelected = speed == currentSpeed
                    Text(
                        text = speed.label,
                        style = if (isSelected) CloneflixTheme.typography.mediumBody else CloneflixTheme.typography.regularSmallBody,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) PrimaryWhite else Grey200,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable { onSpeedSelected(speed) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Preview(name = "Playback Speed Selector Preview", showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun PlaybackSpeedSelectorPreview() {
    CloneflixTheme {
        Box(
            modifier = Modifier
                .background(Color(0xFF141414))
                .padding(24.dp)
        ) {
            CloneflixPlaybackSpeedSelector(
                currentSpeed = CloneflixPlaybackSpeed.SPEED_1_0X,
                onSpeedSelected = {},
                onCloseClick = {}
            )
        }
    }
}
