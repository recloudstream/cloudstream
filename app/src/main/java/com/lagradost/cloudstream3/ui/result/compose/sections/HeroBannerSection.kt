package com.lagradost.cloudstream3.ui.result.compose.sections

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.compose.components.CircleActionButton
import com.lagradost.cloudstream3.ui.result.compose.components.HeroPlayButton
import com.lagradost.cloudstream3.ui.result.compose.components.HeroTrailerButton
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroBannerSection(
    title: String,
    providerName: String?,
    backdropUrl: String?,
    logoUrl: String?,
    heroHeight: Dp,
    playButtonText: String,
    resumeProgressFraction: Float?,
    isInWatchList: Boolean,
    isFavorite: Boolean,
    hasTrailers: Boolean,
    playButtonFocusRequester: FocusRequester,
    playInteractionSource: MutableInteractionSource,
    inMyListInteractionSource: MutableInteractionSource,
    likeInteractionSource: MutableInteractionSource,
    trailerInteractionSource: MutableInteractionSource,
    searchInteractionSource: MutableInteractionSource,
    onPlayClick: () -> Unit,
    onPlayLongClick: (() -> Unit)?,
    onAddToListClick: () -> Unit,
    onLikeClick: () -> Unit,
    onTrailerClick: () -> Unit,
    onSearchClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = MovieDetailsTheme.colors
    val typography = MovieDetailsTheme.typography
    val dimens = MovieDetailsTheme.dimens

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clipToBounds()
            .background(colors.background)
    ) {
        if (!backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            colors.background.copy(alpha = 0.5f),
                            colors.background.copy(alpha = 0.92f),
                            colors.background
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(horizontal = dimens.spacing2Xl, vertical = dimens.spacing2Xl)
                .zIndex(50f),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingM)
        ) {
            Box(
                modifier = Modifier
                    .height(42.dp)
                    .width(110.dp)
                    .focusRequester(playButtonFocusRequester)
                    .combinedClickable(
                        interactionSource = playInteractionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onPlayClick,
                        onLongClick = onPlayLongClick
                    )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(
                                interactionSource = inMyListInteractionSource,
                                indication = null,
                                role = Role.Button,
                                onClick = onAddToListClick
                            )
                    )

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(
                                interactionSource = likeInteractionSource,
                                indication = null,
                                role = Role.Button,
                                onClick = onLikeClick
                            )
                    )

                    if (onSearchClick != null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable(
                                    interactionSource = searchInteractionSource,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = onSearchClick
                                )
                        )
                    }
                }

                if (hasTrailers) {
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .width(120.dp)
                            .clickable(
                                interactionSource = trailerInteractionSource,
                                indication = null,
                                role = Role.Button,
                                onClick = onTrailerClick
                            )
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(dimens.spacing2Xl)
        ) {
            if (!providerName.isNullOrBlank()) {
                Text(
                    text = providerName.uppercase(),
                    style = typography.boldTitle2.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.8).sp
                    ),
                    color = colors.primary
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(horizontal = dimens.spacing2Xl, vertical = dimens.spacingL),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                if (!logoUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .height(68.dp)
                            .width(240.dp)
                    ) {
                        AsyncImage(
                            model = logoUrl,
                            contentDescription = title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Text(
                        text = title,
                        style = typography.boldTitle1,
                        fontSize = 32.sp,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(dimens.spacingL))

                HeroPlayButton(
                    onClick = onPlayClick,
                    onLongClick = onPlayLongClick,
                    text = playButtonText,
                    progress = resumeProgressFraction,
                    interactionSource = playInteractionSource,
                    enabled = false
                )

                Spacer(modifier = Modifier.height(dimens.spacingM))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
                ) {
                    CircleActionButton(
                        icon = painterResource(
                            id = if (isInWatchList) R.drawable.ic_baseline_check_24 else R.drawable.ic_baseline_add_24
                        ),
                        contentDescription = if (isInWatchList) {
                            stringResource(id = R.string.in_my_list)
                        } else {
                            stringResource(id = R.string.add_to_my_list)
                        },
                        onClick = onAddToListClick,
                        interactionSource = inMyListInteractionSource,
                        enabled = false
                    )

                    CircleActionButton(
                        icon = painterResource(
                            id = if (isFavorite) R.drawable.ic_baseline_favorite_24 else R.drawable.ic_baseline_favorite_border_24
                        ),
                        contentDescription = if (isFavorite) {
                            stringResource(id = R.string.unfavorite)
                        } else {
                            stringResource(id = R.string.favorite)
                        },
                        onClick = onLikeClick,
                        interactionSource = likeInteractionSource,
                        enabled = false
                    )

                    if (onSearchClick != null) {
                        CircleActionButton(
                            icon = painterResource(id = R.drawable.search_icon),
                            contentDescription = stringResource(id = R.string.title_search),
                            onClick = onSearchClick,
                            interactionSource = searchInteractionSource,
                            enabled = false
                        )
                    }
                }
            }

            if (hasTrailers) {
                HeroTrailerButton(
                    text = stringResource(id = R.string.play_trailer),
                    onClick = onTrailerClick,
                    interactionSource = trailerInteractionSource,
                    enabled = false
                )
            }
        }
    }
}
