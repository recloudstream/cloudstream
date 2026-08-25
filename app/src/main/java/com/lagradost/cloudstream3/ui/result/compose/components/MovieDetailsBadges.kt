package com.lagradost.cloudstream3.ui.result.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
