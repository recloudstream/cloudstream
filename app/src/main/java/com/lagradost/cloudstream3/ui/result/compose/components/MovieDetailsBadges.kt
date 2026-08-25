package com.lagradost.cloudstream3.ui.result.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.ui.result.compose.model.AiringScheduleUiState
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme

@Composable
fun MaturityRatingBadge(
    rating: String,
    modifier: Modifier = Modifier
) {
    val colors = MovieDetailsTheme.colors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(colors.surface)
            .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = rating,
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun VideoQualityBadge(
    quality: String,
    modifier: Modifier = Modifier
) {
    val colors = MovieDetailsTheme.colors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = quality,
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun OngoingStatusBadge(
    modifier: Modifier = Modifier,
    statusText: String? = null,
    isOngoing: Boolean = true
) {
    val colors = MovieDetailsTheme.colors
    val isOngoingState = isOngoing || statusText?.contains("ongoing", ignoreCase = true) == true
    val badgeColor = if (isOngoingState) colors.greenAccent else colors.textSecondary
    val displayText = (statusText ?: if (isOngoingState) "Ongoing" else "Completed").uppercase()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(badgeColor.copy(alpha = 0.15f))
            .border(BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)), RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
            Text(
                text = displayText,
                color = badgeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AiringCountdownBadge(
    airingSchedule: AiringScheduleUiState,
    modifier: Modifier = Modifier
) {
    val colors = MovieDetailsTheme.colors
    val countdown = rememberAiringCountdown(airingSchedule.targetUnixTimeSeconds)
        ?: airingSchedule.fallbackDateText
        ?: return

    val badgeText = buildString {
        if (!airingSchedule.dayOfWeek.isNullOrBlank()) {
            append("${airingSchedule.dayOfWeek} • ")
        }
        if (!airingSchedule.displayEpisodeShort.isNullOrBlank()) {
            append("${airingSchedule.displayEpisodeShort} • ")
        }
        append(countdown)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(colors.orangeAccent.copy(alpha = 0.15f))
            .border(BorderStroke(1.dp, colors.orangeAccent.copy(alpha = 0.5f)), RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "⏱️",
                fontSize = 10.sp
            )
            Text(
                text = badgeText,
                color = colors.orangeAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
