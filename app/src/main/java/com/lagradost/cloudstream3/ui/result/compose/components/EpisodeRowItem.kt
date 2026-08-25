package com.lagradost.cloudstream3.ui.result.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme
import com.lagradost.cloudstream3.ui.result.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.result.compose.theme.TransparentBlack60
import com.lagradost.cloudstream3.ui.result.getWatchProgress

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeRowItem(
    episode: ResultEpisode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val typography = MovieDetailsTheme.typography
    val dimens = MovieDetailsTheme.dimens
    val colors = MovieDetailsTheme.colors

    val scaleState = animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = MovieDetailsTokens.FastFocusAnimationSpec,
        label = "episodeScale"
    )

    val background = if (isFocused) colors.surfaceElevated else colors.surface
    val border = if (isFocused) BorderStroke(dimens.borderFocus, colors.primary) else null
    val progress = remember(episode) { episode.getWatchProgress() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isFocused) 10f else 0f)
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
            }
            .clip(MovieDetailsTokens.ShapeCardMedium)
            .background(background)
            .then(if (border != null) Modifier.border(border, MovieDetailsTokens.ShapeCardMedium) else Modifier)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(dimens.spacingL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingL)
    ) {
        Text(
            text = episode.episode.toString(),
            style = typography.boldTitle1,
            fontSize = 24.sp,
            color = colors.textSecondary,
            modifier = Modifier.width(32.dp)
        )

        Box(
            modifier = Modifier
                .width(160.dp)
                .height(90.dp)
                .clip(MovieDetailsTokens.ShapeCardSmall)
                .background(colors.surface)
        ) {
            if (!episode.poster.isNullOrBlank()) {
                AsyncImage(
                    model = episode.poster,
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(TransparentBlack60)
                    .border(BorderStroke(1.dp, PrimaryWhite), CircleShape)
                    .align(Alignment.Center)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_play_arrow_24),
                    contentDescription = null,
                    tint = PrimaryWhite,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.Center)
                )
            }

            if (progress > 0.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomStart)
                        .background(colors.border)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(colors.primary)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${episode.episode}. ${episode.name ?: episode.headerName}",
                    style = typography.mediumBody,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (episode.runTime != null) "${episode.runTime}m" else "",
                    style = typography.regularCaption1,
                    color = colors.textSecondary
                )
            }

            if (!episode.description.isNullOrBlank()) {
                Text(
                    text = episode.description,
                    style = typography.regularCaption1,
                    color = colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
