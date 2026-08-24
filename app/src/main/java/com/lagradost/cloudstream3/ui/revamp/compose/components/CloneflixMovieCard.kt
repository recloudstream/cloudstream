package com.lagradost.cloudstream3.ui.revamp.compose.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.GreenAccent
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey100
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey50
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey500
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey700
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey800
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey850
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryBlack
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryRed
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentBlack60
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentBlack90

enum class CloneflixMovieCardSize {
    MEDIUM,
    SMALL
}

enum class CloneflixMovieCardType {
    DEFAULT,
    MORE_LIKE_THIS,
    MORE_LIKE_THIS_WITH_PLAY,
    EPISODE,
    TRAILER,
    PLAYER_PREVIEW,
    TOP10,
    CONTINUE_WATCHING
}

/**
 * Main Movie Card Composable from Figma Node 202:17654.
 * Optimized for Google TV / Android TV D-Pad traversal and Mobile touch.
 */
@Composable
fun CloneflixMovieCard(
    title: String,
    modifier: Modifier = Modifier,
    type: CloneflixMovieCardType = CloneflixMovieCardType.DEFAULT,
    size: CloneflixMovieCardSize = CloneflixMovieCardSize.MEDIUM,
    badge: CloneflixBadgeType? = null,
    showLogo: Boolean = false,
    top10Rank: Int? = null,
    progress: Float? = null,
    runtime: String? = null,
    timestamp: String? = null,
    subtitle: String? = null,
    posterUrl: String? = null,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = CloneflixTheme.colors
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens

    val scale by animateFloatAsState(
        targetValue = if (isFocused) CloneflixTokens.FOCUS_SCALE_FACTOR else 1f,
        label = "cardScale"
    )

    val cardWidth: Dp = CloneflixMovieCardDefaults.cardWidth(type, size)
    val cardHeight: Dp = CloneflixMovieCardDefaults.cardHeight(type, size)

    val border = if (isFocused) {
        BorderStroke(dimens.borderFocus, PrimaryWhite)
    } else {
        BorderStroke(dimens.borderSubtle, colors.border)
    }

    Column(
        modifier = modifier
            .width(cardWidth)
            .scale(scale)
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick
                        )
                        .focusable(interactionSource = interactionSource)
                } else {
                    Modifier.focusable(interactionSource = interactionSource)
                }
            )
            .semantics { contentDescription = title }
    ) {
        if (type == CloneflixMovieCardType.TOP10 && top10Rank != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight),
                verticalAlignment = Alignment.Bottom
            ) {
                // Large Stylized Rank Number (1..10)
                Box(
                    modifier = Modifier
                        .width(CloneflixMovieCardDefaults.RankBoxWidth)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = top10Rank.toString(),
                        fontSize = CloneflixMovieCardDefaults.RankFontSize,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        color = Grey800,
                        lineHeight = CloneflixMovieCardDefaults.RankLineHeight,
                        modifier = Modifier.offset(x = CloneflixMovieCardDefaults.RankShadowOffsetX)
                    )
                    Text(
                        text = top10Rank.toString(),
                        fontSize = CloneflixMovieCardDefaults.RankFontSize,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        color = PrimaryBlack,
                        lineHeight = CloneflixMovieCardDefaults.RankLineHeight,
                        modifier = Modifier.offset(x = CloneflixMovieCardDefaults.RankForegroundOffsetX)
                    )
                }

                // Poster Card Thumbnail
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(CloneflixTokens.RadiusCard))
                        .background(Grey800)
                        .border(border, RoundedCornerShape(CloneflixTokens.RadiusCard))
                ) {
                    MovieCardSurface(
                        title = title,
                        showLogo = showLogo,
                        badge = badge,
                        runtime = runtime,
                        timestamp = timestamp,
                        progress = progress,
                        showCenterPlay = false,
                        posterUrl = posterUrl
                    )
                }
            }
        } else {
            // Standard Card Surface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (type == CloneflixMovieCardType.TRAILER) CloneflixMovieCardDefaults.HeightTrailerThumbnail else cardHeight)
                    .clip(RoundedCornerShape(CloneflixTokens.RadiusCard))
                    .background(Grey800)
                    .border(border, RoundedCornerShape(CloneflixTokens.RadiusCard))
            ) {
                MovieCardSurface(
                    title = title,
                    showLogo = showLogo,
                    badge = badge,
                    runtime = runtime,
                    timestamp = timestamp,
                    progress = progress,
                    showCenterPlay = type == CloneflixMovieCardType.MORE_LIKE_THIS_WITH_PLAY ||
                            type == CloneflixMovieCardType.EPISODE,
                    posterUrl = posterUrl
                )
            }

            // Trailer Title/Subtitle below thumbnail
            if (type == CloneflixMovieCardType.TRAILER && !subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(dimens.spacingS))
                Text(
                    text = subtitle,
                    style = typography.mediumSmallBody,
                    color = if (isFocused) PrimaryWhite else Grey100,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MovieCardSurface(
    title: String,
    showLogo: Boolean,
    badge: CloneflixBadgeType?,
    runtime: String?,
    timestamp: String?,
    progress: Float?,
    showCenterPlay: Boolean,
    posterUrl: String? = null
) {
    val typography = CloneflixTheme.typography

    // Background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Grey700,
                        Grey850,
                        PrimaryBlack
                    )
                )
            )
    ) {
        if (!posterUrl.isNullOrBlank()) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { imageView ->
                    imageView.loadImage(posterUrl)
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Title Text in Mockup Center
            Text(
                text = title,
                style = typography.regularCaption1,
                fontWeight = FontWeight.SemiBold,
                color = Grey50,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 8.dp)
            )
        }

        // Top-Left Logo / Lettermark
        if (showLogo) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                CloneflixLogoView(variant = CloneflixLogoVariant.LETTERMARK_SMALL)
            }
        }

        // Top-Right Badge (e.g. Recently Added, New Season)
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                CloneflixContentBadge(type = badge)
            }
        }

        // Center Play Circle Button Overlay
        if (showCenterPlay) {
            Box(
                modifier = Modifier
                    .size(CloneflixMovieCardDefaults.PlayButtonOverlaySize)
                    .clip(CircleShape)
                    .background(TransparentBlack60)
                    .border(BorderStroke(CloneflixTheme.dimens.borderDefault, PrimaryWhite), CircleShape)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.cloneflix_ic_play),
                    contentDescription = stringResource(R.string.cloneflix_cd_play),
                    tint = PrimaryWhite,
                    modifier = Modifier.size(CloneflixMovieCardDefaults.PlayIconOverlaySize)
                )
            }
        }

        // Bottom-Right Runtime Tag (e.g. "2h 18m")
        if (!runtime.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(CloneflixTheme.dimens.spacingS)
                    .clip(RoundedCornerShape(CloneflixTokens.RadiusCard))
                    .background(TransparentBlack90)
                    .padding(horizontal = CloneflixTheme.dimens.spacingXs, vertical = CloneflixTheme.dimens.spacingXxs)
            ) {
                Text(
                    text = runtime,
                    style = typography.regularCaption2,
                    fontWeight = FontWeight.Medium,
                    color = PrimaryWhite
                )
            }
        }

        // Bottom-Right Timestamp Tag for Player Preview (e.g. "51:29")
        if (!timestamp.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(CloneflixTheme.dimens.spacingS)
                    .clip(RoundedCornerShape(CloneflixTokens.RadiusCard))
                    .background(TransparentBlack90)
                    .padding(horizontal = CloneflixTheme.dimens.spacingXs, vertical = CloneflixTheme.dimens.spacingXxs)
            ) {
                Text(
                    text = timestamp,
                    style = typography.regularCaption2,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryWhite
                )
            }
        }

        // Bottom Progress Indicator (Continue Watching)
        if (progress != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CloneflixTheme.dimens.progressHeight)
                    .align(Alignment.BottomCenter)
                    .background(Grey500)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(PrimaryRed)
                )
            }
        }
    }
}

/**
 * List of Episodes Item Pattern from Figma Section 120:3230.
 * Supports Current (active/highlighted) vs Other episode states with D-pad navigation.
 */
@Composable
fun CloneflixEpisodeItem(
    episodeNumber: Int,
    title: String,
    duration: String,
    synopsis: String,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val colors = CloneflixTheme.colors
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgColor = when {
        isFocused -> Grey700
        isCurrent -> Grey800
        else -> colors.surface
    }

    val border = if (isFocused) {
        BorderStroke(dimens.borderFocus, PrimaryWhite)
    } else if (isCurrent) {
        BorderStroke(dimens.borderDefault, PrimaryWhite.copy(alpha = CloneflixTokens.ALPHA_SUBTLE))
    } else {
        BorderStroke(dimens.borderSubtle, colors.border)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CloneflixDrawerDefaults.EpisodeItemCornerRadius))
            .background(bgColor)
            .border(border, RoundedCornerShape(CloneflixDrawerDefaults.EpisodeItemCornerRadius))
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick
                        )
                        .focusable(interactionSource = interactionSource)
                } else {
                    Modifier.focusable(interactionSource = interactionSource)
                }
            )
            .padding(dimens.spacingL),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Episode Number (e.g. "1", "2")
        Text(
            text = episodeNumber.toString(),
            style = typography.boldTitle2,
            color = if (isFocused || isCurrent) PrimaryWhite else Grey200,
            modifier = Modifier.width(CloneflixDrawerDefaults.EpisodeNumberWidth)
        )

        Spacer(modifier = Modifier.width(dimens.spacingM))

        // Episode Thumbnail with Play Button Overlay (128x72dp)
        Box(
            modifier = Modifier
                .width(CloneflixMovieCardDefaults.EpisodeThumbnailWidth)
                .height(CloneflixMovieCardDefaults.EpisodeThumbnailHeight)
                .clip(RoundedCornerShape(CloneflixTokens.RadiusCard))
                .background(Grey850)
                .border(BorderStroke(dimens.borderSubtle, colors.border), RoundedCornerShape(CloneflixTokens.RadiusCard)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.cloneflix_label_episode_prefix, episodeNumber),
                style = typography.regularCaption2,
                color = Grey50
            )

            // Play circle button overlay
            Box(
                modifier = Modifier
                    .size(CloneflixMovieCardDefaults.EpisodeItemPlayButtonSize)
                    .clip(CircleShape)
                    .background(TransparentBlack60)
                    .border(BorderStroke(dimens.borderDefault, PrimaryWhite), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.cloneflix_ic_play),
                    contentDescription = stringResource(R.string.cloneflix_cd_play_episode, episodeNumber),
                    tint = PrimaryWhite,
                    modifier = Modifier.size(CloneflixMovieCardDefaults.EpisodeItemPlayIconSize)
                )
            }
        }

        Spacer(modifier = Modifier.width(dimens.spacingL))

        // Episode Title, Duration & Description
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = typography.mediumHeadline2,
                    color = if (isFocused || isCurrent) PrimaryWhite else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(dimens.spacingS))

                Text(
                    text = duration,
                    style = typography.regularCaption1,
                    color = Grey100
                )
            }

            Spacer(modifier = Modifier.height(dimens.spacingXs))

            Text(
                text = synopsis,
                style = typography.regularSmallBody,
                color = if (isFocused) PrimaryWhite else Grey200,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Movie Block Carousel / Row Pattern (Figma Section 202:19015).
 */
@Composable
fun CloneflixMovieBlockRow(
    title: String,
    items: List<CloneflixMovieCardItem>,
    modifier: Modifier = Modifier,
    onItemClick: ((CloneflixMovieCardItem) -> Unit)? = null
) {
    val typography = CloneflixTheme.typography
    val colors = CloneflixTheme.colors
    val dimens = CloneflixTheme.dimens

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = typography.mediumTitle3,
            fontSize = 18.sp,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = dimens.spacing2Xl, vertical = dimens.spacingS)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = dimens.spacing2Xl, vertical = dimens.spacingS),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(items) { index, item ->
                CloneflixMovieCard(
                    title = item.title,
                    type = item.type,
                    size = item.size,
                    badge = item.badge,
                    showLogo = item.showLogo,
                    top10Rank = item.top10Rank,
                    progress = item.progress,
                    runtime = item.runtime,
                    timestamp = item.timestamp,
                    subtitle = item.subtitle,
                    posterUrl = item.posterUrl,
                    onClick = { onItemClick?.invoke(item) }
                )
            }
        }
    }
}

data class CloneflixMovieCardItem(
    val title: String,
    val type: CloneflixMovieCardType = CloneflixMovieCardType.DEFAULT,
    val size: CloneflixMovieCardSize = CloneflixMovieCardSize.MEDIUM,
    val badge: CloneflixBadgeType? = null,
    val showLogo: Boolean = false,
    val top10Rank: Int? = null,
    val progress: Float? = null,
    val runtime: String? = null,
    val timestamp: String? = null,
    val subtitle: String? = null,
    val posterUrl: String? = null
)

/**
 * Expanded Movie Preview Banner / Card (Figma Section 109:9726).
 */
@Composable
fun CloneflixExpandedMoviePreview(
    title: String = CloneflixSampleData.SAMPLE_TITLE_HOUSE_OF_NINJAS,
    matchScore: String = CloneflixSampleData.SAMPLE_MATCH_NEW,
    maturityRating: String = CloneflixSampleData.SAMPLE_MATURITY_RATING,
    duration: String = CloneflixSampleData.SAMPLE_DURATION_SEASONS,
    quality: String = CloneflixSampleData.SAMPLE_QUALITY_HD,
    genres: List<String> = CloneflixSampleData.SAMPLE_GENRES,
    synopsis: String = CloneflixSampleData.SAMPLE_SYNOPSIS,
    onPlayClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    onThumbUpClick: () -> Unit = {},
    onMuteClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = CloneflixTheme.colors
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CloneflixTokens.RadiusCardMedium))
            .background(Grey850)
            .border(BorderStroke(dimens.borderDefault, colors.border), RoundedCornerShape(CloneflixTokens.RadiusCardMedium))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Backdrop Header with Action Buttons
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CloneflixMovieCardDefaults.ExpandedPreviewHeaderHeight)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Grey700,
                                Grey850,
                                PrimaryBlack
                            )
                        )
                    )
            ) {
                // Backdrop Title & Logo
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(dimens.spacing2Xl)
                ) {
                    CloneflixLogoView(variant = CloneflixLogoVariant.WORDMARK_MEDIUM)
                    Spacer(modifier = Modifier.height(dimens.spacingXs))
                    Text(
                        text = title,
                        style = typography.boldTitle1,
                        fontSize = 28.sp,
                        color = PrimaryWhite
                    )

                    Spacer(modifier = Modifier.height(dimens.spacingM))

                    // Circular Action Buttons (Play, Add, ThumbUp, Mute)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
                    ) {
                        // Play Button
                        CloneflixCircleActionButton(
                            icon = painterResource(id = R.drawable.cloneflix_ic_play),
                            contentDescription = stringResource(R.string.cloneflix_cd_play),
                            isPrimary = true,
                            onClick = onPlayClick
                        )

                        // Add to List Button (+)
                        CloneflixCircleActionButton(
                            icon = painterResource(id = R.drawable.cloneflix_ic_plus),
                            contentDescription = stringResource(R.string.cloneflix_cd_add_to_list),
                            onClick = onAddClick
                        )

                        // Thumb Up Button
                        CloneflixCircleActionButton(
                            icon = painterResource(id = R.drawable.cloneflix_ic_thumb_up),
                            contentDescription = stringResource(R.string.cloneflix_cd_like),
                            onClick = onThumbUpClick
                        )

                        // Mute / Volume Button
                        CloneflixCircleActionButton(
                            icon = painterResource(id = R.drawable.cloneflix_ic_mute),
                            contentDescription = stringResource(R.string.cloneflix_cd_mute),
                            onClick = onMuteClick
                        )
                    }
                }
            }

            // Info Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacing2Xl)
            ) {
                // Metadata Row (Match, Rating, Duration, Quality)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
                ) {
                    Text(
                        text = matchScore,
                        color = GreenAccent,
                        style = typography.mediumBody,
                        fontWeight = FontWeight.Bold
                    )

                    CloneflixMaturityRating(rating = maturityRating)

                    Text(
                        text = duration,
                        color = PrimaryWhite,
                        style = typography.regularBody
                    )

                    CloneflixVideoQualityBadge(quality = quality)
                }

                Spacer(modifier = Modifier.height(dimens.spacingM))

                // Genres separated by dots
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingS)
                ) {
                    genres.forEachIndexed { index, genre ->
                        Text(
                            text = genre,
                            style = typography.regularCaption1,
                            color = PrimaryWhite
                        )
                        if (index < genres.size - 1) {
                            Box(
                                modifier = Modifier
                                    .size(dimens.spacingXs)
                                    .clip(CircleShape)
                                    .background(Grey200)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(dimens.spacingM))

                Text(
                    text = synopsis,
                    style = typography.regularBody,
                    color = Grey100,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

/**
 * Circular action button for media preview overlays.
 */
@Composable
fun CloneflixCircleActionButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val dimens = CloneflixTheme.dimens

    val bgColor = when {
        isPrimary -> PrimaryWhite
        isFocused -> PrimaryWhite.copy(alpha = CloneflixTokens.ALPHA_MUTED)
        else -> TransparentBlack60
    }

    val iconTint = if (isPrimary) PrimaryBlack else PrimaryWhite
    val border = if (isFocused) {
        BorderStroke(dimens.borderFocus, PrimaryWhite)
    } else {
        BorderStroke(dimens.borderDefault, PrimaryWhite.copy(alpha = CloneflixTokens.ALPHA_HIGH))
    }

    Box(
        modifier = modifier
            .size(CloneflixMovieCardDefaults.ActionButtonSize)
            .clip(CircleShape)
            .background(bgColor)
            .border(border, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource),
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

@Preview(name = "Movie Cards Preview", showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun MovieCardsPreview() {
    CloneflixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CloneflixMovieCard(
                    title = "House of Ninjas",
                    type = CloneflixMovieCardType.DEFAULT,
                    showLogo = true,
                    badge = CloneflixBadgeType.NEW_SEASON
                )

                CloneflixMovieCard(
                    title = "The Witcher",
                    type = CloneflixMovieCardType.CONTINUE_WATCHING,
                    progress = 0.65f,
                    showLogo = true
                )
            }

            CloneflixEpisodeItem(
                episodeNumber = 1,
                title = "The Offer",
                duration = "55m",
                synopsis = "While Haru Tawara develops a crush on a mysterious young woman at work...",
                isCurrent = true
            )
        }
    }
}
