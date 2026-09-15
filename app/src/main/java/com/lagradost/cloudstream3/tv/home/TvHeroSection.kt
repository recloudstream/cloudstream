package com.lagradost.cloudstream3.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.tv.components.TvFocusScale
import com.lagradost.cloudstream3.tv.model.TvMockMediaItem

@Composable
fun TvHeroSection(
    hero: TvMockMediaItem,
    watchFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onWatchNow: () -> Unit = {},
    onDetails: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp),
    ) {
        AsyncImage(
            model = hero.backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.88f),
                        0.45f to Color.Black.copy(alpha = 0.55f),
                        0.85f to Color.Transparent,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.7f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 560.dp)
                .padding(start = 8.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = "FEATURED",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = hero.title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = buildMetadataLine(hero),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = hero.synopsis,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 480.dp),
            )
            Spacer(Modifier.height(22.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onWatchNow,
                    modifier = Modifier.focusRequester(watchFocusRequester),
                    scale = ButtonDefaults.scale(focusedScale = TvFocusScale.HeroButtonFocused),
                    glow = ButtonDefaults.glow(
                        focusedGlow = Glow(
                            elevationColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            elevation = 12.dp,
                        ),
                    ),
                ) {
                    Text("Watch Now")
                }
                Button(
                    onClick = onDetails,
                    scale = ButtonDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
                ) {
                    Text("Details")
                }
            }
        }
    }
}

private fun buildMetadataLine(hero: TvMockMediaItem): String {
    val parts = buildList {
        hero.year?.let { add(it.toString()) }
        hero.rating?.let { add("★ $it") }
        hero.runtime?.let { add(it) }
        if (hero.genres.isNotEmpty()) add(hero.genres.take(3).joinToString(" · "))
    }
    return parts.joinToString("  •  ")
}
