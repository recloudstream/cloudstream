package com.lagradost.cloudstream3.ui.result.compose.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme
import com.lagradost.cloudstream3.ui.result.compose.theme.PrimaryBlack
import com.lagradost.cloudstream3.ui.result.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.result.compose.theme.TransparentBlack90

object MovieDetailsTokens {
    val ShapeCardSmall = RoundedCornerShape(4.dp)
    val ShapeCardMedium = RoundedCornerShape(8.dp)
    val ShapeCardLarge = RoundedCornerShape(12.dp)

    val FastFocusAnimationSpec = tween<Float>(durationMillis = 150, easing = FastOutSlowInEasing)

    const val FOCUS_SCALE_FACTOR = 1.05f
    const val FOCUS_SCALE_FACTOR_LARGE = 1.10f

    const val ALPHA_HIGH = 0.85f
    const val ALPHA_MEDIUM = 0.60f
    const val ALPHA_LOW = 0.35f
}

enum class MovieCardSize {
    MEDIUM,
    SMALL
}

enum class MovieCardType {
    DEFAULT,
    POSTER,
    MORE_LIKE_THIS,
    MORE_LIKE_THIS_WITH_PLAY,
    EPISODE,
    TRAILER,
    PLAYER_PREVIEW,
    TOP10,
    CONTINUE_WATCHING
}

enum class MovieBadgeType(val stringRes: Int) {
    TOP_10(R.string.badge_top_10),
    RECENTLY_ADDED(R.string.badge_recently_added),
    NEW_SEASON(R.string.badge_new_season),
    NEW_EPISODES(R.string.badge_new_episodes),
    LEAVING_SOON(R.string.badge_leaving_soon),
    MUST_WATCH(R.string.badge_must_watch)
}

@Immutable
data class MovieCardItem(
    val title: String,
    val type: MovieCardType = MovieCardType.DEFAULT,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val progress: Float? = null,
    val badge: MovieBadgeType? = null,
    val matchScore: String? = null,
    val maturityRating: String? = null,
    val duration: String? = null,
    val quality: String? = null,
    val top10Rank: Int? = null,
    val showLogo: Boolean = false,
    val showBottomTitle: Boolean = false,
    val synopsis: String? = null,
    val rawItem: Any? = null
)

object MovieCardDefaults {
    val CardAspectRatioDefault = 16f / 9f
    val CardAspectRatioPoster = 2f / 3f

    val ActionButtonSize = 36.dp

    fun cardWidth(type: MovieCardType, size: MovieCardSize): Dp = when (type) {
        MovieCardType.POSTER -> when (size) {
            MovieCardSize.MEDIUM -> 140.dp
            MovieCardSize.SMALL -> 110.dp
        }
        else -> when (size) {
            MovieCardSize.MEDIUM -> 218.dp
            MovieCardSize.SMALL -> 128.dp
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroPlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = stringResource(id = R.string.play_movie_button),
    progress: Float? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = MovieDetailsTheme.colors

    val scaleState = animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = MovieDetailsTokens.FastFocusAnimationSpec,
        label = "heroPlayBtnScale"
    )

    val background = if (isFocused) colors.primary else colors.primary.copy(alpha = 0.92f)
    val contentColor = colors.onPrimary
    val border = if (isFocused) BorderStroke(2.dp, colors.onBackground) else null

    Box(
        modifier = modifier
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .semantics {
                contentDescription = text
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(42.dp)
                .zIndex(if (isFocused) 10f else 0f)
                .graphicsLayer {
                    scaleX = scaleState.value
                    scaleY = scaleState.value
                }
                .clip(MovieDetailsTokens.ShapeCardSmall)
                .background(background)
                .then(if (border != null) Modifier.border(border, MovieDetailsTokens.ShapeCardSmall) else Modifier)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_play_arrow_24),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = text,
                    color = contentColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (progress != null && progress > 0.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomStart)
                        .background(colors.border)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(contentColor)
                    )
                }
            }
        }
    }
}

@Composable
fun HeroTrailerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = stringResource(id = R.string.play_trailer),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = MovieDetailsTheme.colors

    val scaleState = animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = MovieDetailsTokens.FastFocusAnimationSpec,
        label = "heroTrailerBtnScale"
    )

    val background = if (isFocused) colors.primary.copy(alpha = 0.9f) else colors.surface.copy(alpha = 0.6f)
    val border = if (isFocused) BorderStroke(2.dp, colors.onBackground) else BorderStroke(1.dp, colors.border)

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .semantics {
                contentDescription = text
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .height(42.dp)
                .zIndex(if (isFocused) 10f else 0f)
                .graphicsLayer {
                    scaleX = scaleState.value
                    scaleY = scaleState.value
                }
                .clip(MovieDetailsTokens.ShapeCardSmall)
                .background(background)
                .border(border, MovieDetailsTokens.ShapeCardSmall)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_baseline_ondemand_video_24),
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun CircleActionButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val dimens = MovieDetailsTheme.dimens
    val colors = MovieDetailsTheme.colors

    val bgColor = when {
        isFocused -> colors.primary.copy(alpha = 0.4f)
        isPrimary -> colors.primary
        else -> colors.surface.copy(alpha = 0.6f)
    }

    val iconTint = if (isPrimary && !isFocused) colors.onPrimary else colors.textPrimary
    val border = if (isFocused) {
        BorderStroke(dimens.borderFocus, colors.primary)
    } else {
        BorderStroke(dimens.borderDefault, colors.border)
    }

    Box(
        modifier = modifier
            .size(MovieCardDefaults.ActionButtonSize)
            .clip(CircleShape)
            .background(bgColor)
            .border(border, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .focusable(enabled = enabled, interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(dimens.iconM)
        )
    }
}

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

enum class DetailsLogoVariant {
    FULL_COLOR,
    WHITE_MONO,
    MINIMAL_ICON
}

@Composable
fun DetailsLogoView(
    modifier: Modifier = Modifier,
    logoUrl: String? = null,
    titleFallback: String = "",
    variant: DetailsLogoVariant = DetailsLogoVariant.FULL_COLOR,
    height: Dp = 48.dp
) {
    val typography = MovieDetailsTheme.typography
    val colors = MovieDetailsTheme.colors

    if (!logoUrl.isNullOrBlank()) {
        AsyncImage(
            model = logoUrl,
            contentDescription = titleFallback,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = modifier
                .height(height)
                .fillMaxWidth(0.6f)
        )
    } else {
        Text(
            text = titleFallback,
            style = typography.boldTitle1,
            color = when (variant) {
                DetailsLogoVariant.FULL_COLOR -> colors.primary
                DetailsLogoVariant.WHITE_MONO -> colors.textPrimary
                DetailsLogoVariant.MINIMAL_ICON -> colors.textPrimary
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    }
}

@Composable
fun SeasonDropdown(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = MovieDetailsTheme.colors

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "DropdownScale"
    )

    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "DropdownChevronRotation"
    )

    val borderStroke = when {
        isFocused || expanded -> BorderStroke(2.dp, colors.primary)
        else -> BorderStroke(1.dp, colors.border)
    }

    val backgroundColor = when {
        isFocused || expanded -> colors.surface.copy(alpha = 0.9f)
        else -> colors.surface.copy(alpha = 0.6f)
    }

    val baseModifier = if (width != null) modifier.width(width) else modifier
    val defaultSeasonText = stringResource(id = R.string.select_season)

    Box(
        modifier = baseModifier
            .scale(scale)
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .border(borderStroke, RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.DropdownList,
                onClick = { expanded = !expanded }
            )
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedOption.ifEmpty { defaultSeasonText },
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Icon(
                painter = painterResource(id = R.drawable.ic_baseline_keyboard_arrow_down_24),
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotationAngle)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(colors.surface)
                .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(4.dp))
                .heightIn(max = 280.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                val optionInteractionSource = remember(option) { MutableInteractionSource() }
                val isOptionFocused by optionInteractionSource.collectIsFocusedAsState()
                val focusColor = colors.onSurface.copy(
                    alpha = if (colors.surface.luminance() < 0.5f) 0.22f else 0.12f
                )
                val focusBorder = colors.onSurface.copy(alpha = 0.72f)

                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (isSelected) colors.primary else colors.textPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                    modifier = Modifier
                        .clip(MovieDetailsTokens.ShapeCardSmall)
                        .background(if (isOptionFocused) focusColor else Color.Transparent)
                        .then(
                            if (isOptionFocused) {
                                Modifier.border(
                                    BorderStroke(2.dp, focusBorder),
                                    MovieDetailsTokens.ShapeCardSmall
                                )
                            } else {
                                Modifier
                            }
                        ),
                    interactionSource = optionInteractionSource,
                    colors = MenuDefaults.itemColors(
                        textColor = if (isSelected) colors.primary else colors.textPrimary
                    )
                )
            }
        }
    }
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
    val imageUrl = if (type == MovieCardType.POSTER) (posterUrl ?: backdropUrl) else (backdropUrl ?: posterUrl)

    val border = if (isFocused) {
        BorderStroke(dimens.borderFocus, colors.primary)
    } else {
        BorderStroke(dimens.borderSubtle, colors.border)
    }

    Box(
        modifier = modifier
            .width(cardWidth)
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            role = Role.Button,
                            onClick = onClick
                        )
                        .focusable(enabled = enabled, interactionSource = interactionSource)
                } else {
                    Modifier.focusable(enabled = enabled, interactionSource = interactionSource)
                }
            )
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

                if (type == MovieCardType.POSTER && showBottomTitle) {
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

                if (progress != null && progress > 0f) {
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

                if (badge != null) {
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
            }

            if (type != MovieCardType.POSTER && showBottomTitle) {
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
        }
    }
}
