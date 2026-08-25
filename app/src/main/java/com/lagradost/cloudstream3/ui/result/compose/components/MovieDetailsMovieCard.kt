package com.lagradost.cloudstream3.ui.result.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme
import com.lagradost.cloudstream3.ui.result.compose.theme.TransparentBlack90

@Composable
private fun CardImageOrPlaceholder(title: String, imageUrl: String?) {
    val colors = MovieDetailsTheme.colors
    if (!imageUrl.isNullOrBlank()) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title.take(2).uppercase(),
                color = colors.textSecondary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BoxScope.CardPosterBottomTitle(title: String) {
    val colors = MovieDetailsTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, TransparentBlack90),
                    startY = 0f
                )
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BoxScope.CardProgressBar(progress: Float) {
    val dimens = MovieDetailsTheme.dimens
    val colors = MovieDetailsTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimens.progressHeight)
            .align(Alignment.BottomCenter)
            .background(colors.border)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(colors.primary)
        )
    }
}

@Composable
private fun BoxScope.CardBadge(badge: MovieBadgeType) {
    val colors = MovieDetailsTheme.colors
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.primary)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = stringResource(id = badge.stringRes),
            color = colors.onPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun resolveImageUrl(type: MovieCardType, posterUrl: String?, backdropUrl: String?): String? {
    return if (type == MovieCardType.POSTER) {
        posterUrl ?: backdropUrl
    } else {
        backdropUrl ?: posterUrl
    }
}

private fun Modifier.applyCardClickable(
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: (() -> Unit)?
): Modifier {
    return if (onClick != null) {
        this
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .focusable(enabled = enabled, interactionSource = interactionSource)
    } else {
        this.focusable(enabled = enabled, interactionSource = interactionSource)
    }
}

@Composable
private fun BoxScope.CardOverlays(
    type: MovieCardType,
    showBottomTitle: Boolean,
    title: String,
    progress: Float?,
    badge: MovieBadgeType?
) {
    if (type == MovieCardType.POSTER && showBottomTitle) {
        CardPosterBottomTitle(title = title)
    }
    if (progress != null && progress > 0f) {
        CardProgressBar(progress = progress)
    }
    if (badge != null) {
        CardBadge(badge = badge)
    }
}

@Composable
private fun CardExternalTitle(title: String, isFocused: Boolean) {
    val colors = MovieDetailsTheme.colors
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = title,
        color = if (isFocused) colors.textPrimary else colors.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun MovieDetailsMovieCard(
    title: String,
    modifier: Modifier = Modifier,
    type: MovieCardType = MovieCardType.POSTER,
    size: MovieCardSize = MovieCardSize.MEDIUM,
    posterUrl: String? = null,
    backdropUrl: String? = null,
    progress: Float? = null,
    badge: MovieBadgeType? = null,
    matchScore: String? = null,
    maturityRating: String? = null,
    duration: String? = null,
    quality: String? = null,
    showLogo: Boolean = false,
    showBottomTitle: Boolean = false,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val dimens = MovieDetailsTheme.dimens
    val colors = MovieDetailsTheme.colors

    val scaleState = animateFloatAsState(
        targetValue = if (isFocused) MovieDetailsTokens.FOCUS_SCALE_FACTOR else 1f,
        animationSpec = MovieDetailsTokens.FastFocusAnimationSpec,
        label = "cardScale"
    )

    val aspectRatio = if (type == MovieCardType.POSTER) {
        MovieCardDefaults.CardAspectRatioPoster
    } else {
        MovieCardDefaults.CardAspectRatioDefault
    }

    val cardWidth = MovieCardDefaults.cardWidth(type, size)
    val imageUrl = resolveImageUrl(type, posterUrl, backdropUrl)

    val border = if (isFocused) {
        BorderStroke(dimens.borderFocus, colors.primary)
    } else {
        BorderStroke(dimens.borderSubtle, colors.border)
    }

    Box(
        modifier = modifier
            .width(cardWidth)
            .applyCardClickable(enabled, interactionSource, onClick)
            .semantics {
                contentDescription = title
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(if (isFocused) 10f else 0f)
                .graphicsLayer {
                    scaleX = scaleState.value
                    scaleY = scaleState.value
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(MovieDetailsTokens.ShapeCardSmall)
                    .background(colors.surface)
                    .border(border, MovieDetailsTokens.ShapeCardSmall)
            ) {
                CardImageOrPlaceholder(title = title, imageUrl = imageUrl)
                CardOverlays(
                    type = type,
                    showBottomTitle = showBottomTitle,
                    title = title,
                    progress = progress,
                    badge = badge
                )
            }

            if (type != MovieCardType.POSTER && showBottomTitle) {
                CardExternalTitle(title = title, isFocused = isFocused)
            }
        }
    }
}
