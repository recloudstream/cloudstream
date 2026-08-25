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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.secondsToReadable
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme
import com.lagradost.cloudstream3.ui.result.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.result.compose.theme.TransparentBlack60
import com.lagradost.cloudstream3.ui.result.getDisplayPosition
import com.lagradost.cloudstream3.ui.result.getWatchProgress
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Immutable
data class EpisodeRowUiState(
    val episodeNumberText: String,
    val title: String,
    val description: String?,
    val isFiller: Boolean,
    val isWatched: Boolean,
    val isUpcoming: Boolean,
    val watchProgress: Float,
    val ratingText: String?,
    val runtimeText: String?,
    val airDateText: String?
) {
    val hasMeta: Boolean
        get() = ratingText != null || !runtimeText.isNullOrBlank() || !airDateText.isNullOrBlank()
}

@Composable
fun rememberEpisodeRowUiState(episode: ResultEpisode): EpisodeRowUiState {
    val context = LocalContext.current
    return remember(episode, context) {
        val nowMs = APIHolder.unixTimeMS
        val isWatched = episode.videoWatchState == VideoWatchState.Watched ||
                (episode.getDisplayPosition() >= episode.duration && episode.getDisplayPosition() > 0L)
        val isUpcoming = episode.airDate != null && nowMs < episode.airDate
        val progress = episode.getWatchProgress()

        val rating10p = episode.score?.toFloat(10)
        val ratingText = if (rating10p != null && rating10p > 0.1f) {
            context.getString(R.string.rated_format).format(rating10p)
        } else {
            null
        }

        val runtimeText = episode.runTime?.times(60L)?.toInt()?.let { secondsToReadable(it, "") }

        val airDateText = if (episode.airDate != null) {
            if (isUpcoming) {
                val diffSec = (episode.airDate - nowMs) / 1000
                context.getString(
                    R.string.episode_upcoming_format,
                    secondsToReadable(diffSec.toInt(), "")
                )
            } else {
                SimpleDateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault()).format(Date(episode.airDate))
            }
        } else {
            null
        }

        val title = if (episode.name == null) {
            "${context.getString(R.string.episode)} ${episode.episode}"
        } else {
            "${episode.episode}. ${episode.name}"
        }

        val description = episode.description?.html()?.toString()?.takeIf { it.isNotBlank() }

        EpisodeRowUiState(
            episodeNumberText = episode.episode.toString(),
            title = title,
            description = description,
            isFiller = episode.isFiller == true,
            isWatched = isWatched,
            isUpcoming = isUpcoming,
            watchProgress = progress,
            ratingText = ratingText,
            runtimeText = runtimeText,
            airDateText = airDateText
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeRowItem(
    episode: ResultEpisode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val state = rememberEpisodeRowUiState(episode)
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
            text = state.episodeNumberText,
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
                val iconRes = when {
                    state.isUpcoming -> R.drawable.hourglass_24
                    state.isWatched -> R.drawable.ic_baseline_check_24
                    else -> R.drawable.ic_baseline_play_arrow_24
                }
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = PrimaryWhite,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.Center)
                )
            }

            if (!state.isUpcoming && !state.isWatched && state.watchProgress > 0.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomStart)
                        .background(colors.border)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(state.watchProgress.coerceIn(0f, 1f))
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.isFiller) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.surfaceElevated)
                            .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.filler),
                            style = typography.regularCaption2,
                            color = colors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = state.title,
                    style = typography.mediumBody,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            if (state.hasMeta) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.ratingText != null) {
                        Text(
                            text = state.ratingText,
                            style = typography.regularCaption1,
                            color = colors.textSecondary
                        )
                    }
                    if (!state.runtimeText.isNullOrBlank()) {
                        Text(
                            text = state.runtimeText,
                            style = typography.regularCaption1,
                            color = colors.textSecondary
                        )
                    }
                    if (!state.airDateText.isNullOrBlank()) {
                        Text(
                            text = state.airDateText,
                            style = typography.regularCaption1,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            if (!state.description.isNullOrBlank()) {
                Text(
                    text = state.description,
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
