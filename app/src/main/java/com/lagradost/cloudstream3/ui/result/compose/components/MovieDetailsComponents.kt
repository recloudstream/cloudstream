package com.lagradost.cloudstream3.ui.result.compose.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.compose.theme.GreenAccent
import com.lagradost.cloudstream3.ui.result.compose.theme.Grey10
import com.lagradost.cloudstream3.ui.result.compose.theme.Grey100
import com.lagradost.cloudstream3.ui.result.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.result.compose.theme.Grey50
import com.lagradost.cloudstream3.ui.result.compose.theme.Grey500
import com.lagradost.cloudstream3.ui.result.compose.theme.Grey600
import com.lagradost.cloudstream3.ui.result.compose.theme.Grey700
import com.lagradost.cloudstream3.ui.result.compose.theme.Grey800
import com.lagradost.cloudstream3.ui.result.compose.theme.Grey850
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme
import com.lagradost.cloudstream3.ui.result.compose.theme.PrimaryBlack
import com.lagradost.cloudstream3.ui.result.compose.theme.PrimaryRed
import com.lagradost.cloudstream3.ui.result.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.result.compose.theme.TransparentBlack60
import com.lagradost.cloudstream3.ui.result.compose.theme.TransparentBlack90
import com.lagradost.cloudstream3.ui.result.compose.theme.getRatingScoreColor

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

@Composable
fun HeroPlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Play",
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true
) {
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scaleState = animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = MovieDetailsTokens.FastFocusAnimationSpec,
        label = "heroPlayBtnScale"
    )

    val background = if (isFocused) PrimaryWhite else PrimaryWhite.copy(alpha = 0.95f)
    val border = if (isFocused) BorderStroke(2.dp, PrimaryRed) else null

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
                contentDescription = "$text Video"
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
                .then(if (border != null) Modifier.border(border, MovieDetailsTokens.ShapeCardSmall) else Modifier)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_baseline_play_arrow_24),
                contentDescription = null,
                tint = PrimaryBlack,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                color = PrimaryBlack,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun HeroTrailerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Trailer",
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true
) {
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scaleState = animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = MovieDetailsTokens.FastFocusAnimationSpec,
        label = "heroTrailerBtnScale"
    )

    val background = if (isFocused) Grey600.copy(alpha = 0.9f) else Grey700.copy(alpha = 0.6f)
    val border = if (isFocused) BorderStroke(2.dp, PrimaryWhite) else BorderStroke(1.dp, PrimaryWhite.copy(alpha = 0.3f))

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
                contentDescription = "$text Video"
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
                tint = PrimaryWhite,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                color = PrimaryWhite,
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

    val bgColor = when {
        isFocused -> PrimaryWhite.copy(alpha = 0.3f)
        isPrimary -> PrimaryWhite
        else -> TransparentBlack60
    }

    val iconTint = if (isPrimary && !isFocused) PrimaryBlack else PrimaryWhite
    val border = if (isFocused) {
        BorderStroke(dimens.borderFocus, PrimaryWhite)
    } else {
        BorderStroke(dimens.borderDefault, PrimaryWhite.copy(alpha = MovieDetailsTokens.ALPHA_HIGH))
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(Grey800)
            .border(BorderStroke(1.dp, Grey500), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = rating,
            color = PrimaryWhite,
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .border(BorderStroke(1.dp, Grey500), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = quality,
            color = Grey200,
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
                DetailsLogoVariant.FULL_COLOR -> PrimaryRed
                DetailsLogoVariant.WHITE_MONO -> PrimaryWhite
                DetailsLogoVariant.MINIMAL_ICON -> PrimaryWhite
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

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "DropdownScale"
    )

    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "DropdownChevronRotation"
    )

    val borderStroke = when {
        isFocused || expanded -> BorderStroke(2.dp, PrimaryWhite)
        else -> BorderStroke(1.dp, Color(0xFF666666))
    }

    val backgroundColor = when {
        isFocused || expanded -> Color(0xFF333333)
        else -> Color(0xFF1E1E1E)
    }

    val baseModifier = if (width != null) modifier.width(width) else modifier

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
                text = selectedOption.ifEmpty { "Select Season" },
                color = PrimaryWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Icon(
                painter = painterResource(id = R.drawable.ic_baseline_keyboard_arrow_down_24),
                contentDescription = null,
                tint = PrimaryWhite,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotationAngle)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Color(0xFF232323))
                .border(BorderStroke(1.dp, Color(0xFF444444)), RoundedCornerShape(4.dp))
                .heightIn(max = 280.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (isSelected) PrimaryRed else PrimaryWhite,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = PrimaryWhite
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
    showBottomTitle: Boolean = false,
    onClick: () -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val dimens = MovieDetailsTheme.dimens

    val scale by animateFloatAsState(
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

    Column(
        modifier = modifier
            .width(cardWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .zIndex(if (isFocused) 5f else 0f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .semantics {
                contentDescription = title
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(MovieDetailsTokens.ShapeCardSmall)
                .background(Grey850)
                .then(
                    if (isFocused) {
                        Modifier.border(BorderStroke(dimens.borderFocus, PrimaryWhite), MovieDetailsTokens.ShapeCardSmall)
                    } else {
                        Modifier.border(BorderStroke(dimens.borderSubtle, Color.Transparent), MovieDetailsTokens.ShapeCardSmall)
                    }
                )
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
                        .background(Grey800),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title.take(2).uppercase(),
                        color = Grey200,
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
                        color = PrimaryWhite,
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
                        .background(Grey700)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(PrimaryRed)
                    )
                }
            }

            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PrimaryRed)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(id = badge.stringRes),
                        color = PrimaryWhite,
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
                color = if (isFocused) PrimaryWhite else Grey200,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
