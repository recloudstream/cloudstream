package com.lagradost.cloudstream3.ui.result.compose.sections

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.request.crossfade
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.compose.components.CircleActionButton
import com.lagradost.cloudstream3.ui.result.compose.components.HeroPlayButton
import com.lagradost.cloudstream3.ui.result.compose.components.HeroTrailerButton
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme

@Composable
private fun HeroBackdrop(
    backdropUrl: String?,
) {
    if (backdropUrl.isNullOrBlank()) return
    val alpha = remember(backdropUrl) { Animatable(0f) }

    LaunchedEffect(backdropUrl) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 350,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    val context = LocalContext.current
    val backdropRequest = remember(backdropUrl) {
        ImageRequest.Builder(context)
            .data(backdropUrl)
            .size(1280, 720)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .crossfade(true)
            .build()
    }

    AsyncImage(
        model = backdropRequest,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer {
                this.alpha = alpha.value
            },
    )
}

@Composable
private fun BoxScope.HeroGradientOverlay() {
    val colors = MovieDetailsTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f)
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
}

@Composable
private fun BoxScope.HeroProviderBadge(providerName: String?) {
    if (providerName.isNullOrBlank()) return
    val colors = MovieDetailsTheme.colors
    val typography = MovieDetailsTheme.typography
    val dimens = MovieDetailsTheme.dimens

    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(dimens.spacing2Xl)
    ) {
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

@Composable
private fun HeroTitleOrLogo(title: String, logoUrl: String?) {
    val colors = MovieDetailsTheme.colors
    val typography = MovieDetailsTheme.typography

    if (!logoUrl.isNullOrBlank()) {
        val context = LocalContext.current
        val logoRequest = remember(logoUrl) {
            ImageRequest.Builder(context)
                .data(logoUrl)
                .size(480, 240)
                .crossfade(true)
                .build()
        }
        Box(
            modifier = Modifier
                .height(68.dp)
                .width(240.dp)
        ) {
            AsyncImage(
                model = logoRequest,
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
}

@Composable
private fun HeroActionButtons(
    isInWatchList: Boolean,
    isFavorite: Boolean,
    onAddToListClick: () -> Unit,
    onLikeClick: () -> Unit,
    onSearchClick: (() -> Unit)?,
    inMyListInteractionSource: MutableInteractionSource,
    likeInteractionSource: MutableInteractionSource,
    searchInteractionSource: MutableInteractionSource
) {
    val dimens = MovieDetailsTheme.dimens
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoxScope.HeroFocusOverlay(
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
    hasTrailers: Boolean
) {
    val dimens = MovieDetailsTheme.dimens
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
}

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
    val dimens = MovieDetailsTheme.dimens

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clipToBounds()
    ) {
        HeroBackdrop(
            backdropUrl = backdropUrl,
        )
        HeroGradientOverlay()
        HeroFocusOverlay(
            playButtonFocusRequester = playButtonFocusRequester,
            playInteractionSource = playInteractionSource,
            inMyListInteractionSource = inMyListInteractionSource,
            likeInteractionSource = likeInteractionSource,
            trailerInteractionSource = trailerInteractionSource,
            searchInteractionSource = searchInteractionSource,
            onPlayClick = onPlayClick,
            onPlayLongClick = onPlayLongClick,
            onAddToListClick = onAddToListClick,
            onLikeClick = onLikeClick,
            onTrailerClick = onTrailerClick,
            onSearchClick = onSearchClick,
            hasTrailers = hasTrailers
        )
        HeroProviderBadge(providerName = providerName)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(horizontal = dimens.spacing2Xl, vertical = dimens.spacingL),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                HeroTitleOrLogo(title = title, logoUrl = logoUrl)

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

                HeroActionButtons(
                    isInWatchList = isInWatchList,
                    isFavorite = isFavorite,
                    onAddToListClick = onAddToListClick,
                    onLikeClick = onLikeClick,
                    onSearchClick = onSearchClick,
                    inMyListInteractionSource = inMyListInteractionSource,
                    likeInteractionSource = likeInteractionSource,
                    searchInteractionSource = searchInteractionSource
                )
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
