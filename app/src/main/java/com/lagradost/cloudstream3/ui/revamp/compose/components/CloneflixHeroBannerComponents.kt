package com.lagradost.cloudstream3.ui.revamp.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey10
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey100
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey300T40
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey300T70
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey600
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey600T60
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey700
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey800
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey850
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryBlack
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryRed
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Red300
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentBlack60
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentBlack90
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentWhite20
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentWhite70

enum class CloneflixTitlePreviewSize {
    LARGE,
    MEDIUM,
    SMALL1,
    SMALL2,
    SMALL3
}

enum class CloneflixHeroBannerType {
    LANDING_PAGE,
    AUTHENTICATION_PAGE,
    HOME_PAGE,
    MOVIE_PREVIEW
}

/**
 * Standard Play Button used in Hero Banners and Title Previews.
 */
@Composable
fun CloneflixHeroPlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Play"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val background = if (isFocused) PrimaryWhite else PrimaryWhite.copy(alpha = 0.95f)
    val border = if (isFocused) BorderStroke(2.dp, PrimaryRed) else null

    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .then(if (border != null) Modifier.border(border, RoundedCornerShape(4.dp)) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .semantics {
                contentDescription = "$text Video"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.cloneflix_ic_play),
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

/**
 * Standard More Info Button used in Hero Banners and Title Previews.
 */
@Composable
fun CloneflixHeroMoreInfoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "More Info"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val background = if (isFocused) Grey300T70 else Grey600T60
    val border = if (isFocused) BorderStroke(2.dp, PrimaryWhite) else BorderStroke(1.dp, TransparentWhite20)

    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(border, RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 22.dp, vertical = 8.dp)
            .semantics {
                contentDescription = text
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.cloneflix_ic_info),
            contentDescription = null,
            tint = PrimaryWhite,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = text,
            color = PrimaryWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Circular Action Button used in Hero Banners (Replay, Volume, Plus, ThumbUp, Close).
 */
@Composable
fun CloneflixHeroCircleButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 18.dp,
    backgroundColor: Color = Grey700.copy(alpha = 0.8f)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val activeBorder = if (isFocused) {
        BorderStroke(2.dp, PrimaryWhite)
    } else {
        BorderStroke(1.dp, TransparentWhite20)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isFocused) Grey600 else backgroundColor)
            .border(activeBorder, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .semantics {
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = PrimaryWhite,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Hero Banner Right-Edge Maturity Rating Badge (e.g. "16+", "TV-MA").
 */
@Composable
fun CloneflixHeroMaturityRating(
    rating: String = "16+",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp))
            .background(Grey600T60)
            .border(
                width = 1.dp,
                color = TransparentWhite20,
                shape = RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp)
            )
            .drawBehind {
                // Red left accent bar
                drawRect(
                    color = PrimaryRed,
                    size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)
                )
            }
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rating,
            color = PrimaryWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 16.sp
        )
    }
}

/**
 * Title Preview Component from Figma Section 15 (Sizes: Large, Medium, Small1, Small2, Small3).
 */
@Composable
fun CloneflixTitlePreview(
    size: CloneflixTitlePreviewSize = CloneflixTitlePreviewSize.LARGE,
    title: String = "HOUSE OF NINJAS",
    subtitle: String = "忍びの家",
    synopsis: String = "Years after retiring from their formidable ninja lives, a dysfunctional family must return to shadowy missions to counteract a string of looming threats.",
    top10RankText: String? = "#2 in TV Shows Today",
    onPlayClick: () -> Unit = {},
    onMoreInfoClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val typography = CloneflixTheme.typography
    val colors = CloneflixTheme.colors

    when (size) {
        CloneflixTitlePreviewSize.LARGE -> {
            Column(
                modifier = modifier.widthIn(max = 540.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title Logo Box
                CloneflixTitleLogoView(title = title, subtitle = subtitle)

                // Top 10 Rank Banner
                if (!top10RankText.isNullOrBlank()) {
                    CloneflixTop10RankBanner(
                        rankText = top10RankText,
                        size = CloneflixTop10Size.LARGE
                    )
                }

                // Synopsis
                Text(
                    text = synopsis,
                    style = typography.regularBody,
                    color = colors.textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp
                )

                // Action Buttons Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloneflixHeroPlayButton(onClick = onPlayClick)
                    CloneflixHeroMoreInfoButton(onClick = onMoreInfoClick)
                }
            }
        }

        CloneflixTitlePreviewSize.MEDIUM -> {
            Column(
                modifier = modifier.widthIn(max = 518.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CloneflixTitleLogoView(title = title, subtitle = subtitle)

                Text(
                    text = synopsis,
                    style = typography.regularBody,
                    color = colors.textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloneflixHeroPlayButton(onClick = onPlayClick)
                    CloneflixHeroMoreInfoButton(onClick = onMoreInfoClick)
                }
            }
        }

        CloneflixTitlePreviewSize.SMALL1 -> {
            Column(
                modifier = modifier.widthIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CloneflixTitleLogoView(title = title, subtitle = subtitle)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloneflixHeroPlayButton(onClick = onPlayClick)
                    CloneflixHeroMoreInfoButton(onClick = onMoreInfoClick)
                }
            }
        }

        CloneflixTitlePreviewSize.SMALL2 -> {
            Box(
                modifier = modifier
                    .width(518.dp)
                    .height(207.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1E2229), Color(0xFF0F1115))
                        )
                    )
                    .border(BorderStroke(1.dp, TransparentWhite20), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                CloneflixTitleLogoView(title = title, subtitle = subtitle, isLarge = true)
            }
        }

        CloneflixTitlePreviewSize.SMALL3 -> {
            Box(
                modifier = modifier
                    .width(340.dp)
                    .height(136.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1E2229), Color(0xFF0F1115))
                        )
                    )
                    .border(BorderStroke(1.dp, TransparentWhite20), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                CloneflixTitleLogoView(title = title, subtitle = subtitle, isLarge = false)
            }
        }
    }
}

/**
 * Stylized Cinematic Title Logo View.
 */
@Composable
fun CloneflixTitleLogoView(
    title: String,
    subtitle: String? = null,
    isLarge: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            fontFamily = com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = if (isLarge) 40.sp else 32.sp,
            color = PrimaryWhite,
            letterSpacing = 2.sp,
            lineHeight = if (isLarge) 44.sp else 36.sp
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = if (isLarge) 16.sp else 13.sp,
                fontWeight = FontWeight.Normal,
                color = Grey200,
                letterSpacing = 4.sp
            )
        }
    }
}

/**
 * Versatile Hero Banner Component supporting LandingPage, AuthenticationPage, HomePage, and MoviePreview.
 */
@Composable
fun CloneflixHeroBanner(
    type: CloneflixHeroBannerType = CloneflixHeroBannerType.HOME_PAGE,
    title: String = "HOUSE OF NINJAS",
    synopsis: String = "Years after retiring from their formidable ninja lives, a dysfunctional family must return to shadowy missions to counteract a string of looming threats.",
    top10RankText: String? = "#2 in TV Shows Today",
    maturityRating: String = "16+",
    onPlayClick: () -> Unit = {},
    onMoreInfoClick: () -> Unit = {},
    onAudioToggle: (() -> Unit)? = null,
    onCloseClick: (() -> Unit)? = null,
    onAddToListClick: (() -> Unit)? = null,
    onThumbsUpClick: (() -> Unit)? = null,
    isMuted: Boolean = false,
    modifier: Modifier = Modifier
) {
    when (type) {
        CloneflixHeroBannerType.HOME_PAGE -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181C24))
            ) {
                // Background artistic backdrop gradient simulating hero wallpaper
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF2C3E50), Color(0xFF0F141D), Color(0xFF0B0E14)),
                                radius = 1200f
                            )
                        )
                )

                // Top Vignette Gradient (for navigation bar contrast)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(TransparentBlack90, Color.Transparent)
                            )
                        )
                )

                // Left Scrim Gradient (for title readability)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF0B0E14).copy(alpha = 0.95f),
                                    Color(0xFF0B0E14).copy(alpha = 0.6f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Bottom Fade Scrim Gradient (seamless merge into movie rows)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xFF141414))
                            )
                        )
                )

                // Title Preview Overlay (Left)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 48.dp, bottom = 48.dp)
                ) {
                    CloneflixTitlePreview(
                        size = CloneflixTitlePreviewSize.LARGE,
                        title = title,
                        synopsis = synopsis,
                        top10RankText = top10RankText,
                        onPlayClick = onPlayClick,
                        onMoreInfoClick = onMoreInfoClick
                    )
                }

                // Controls Overlay (Right: Audio toggle + Maturity Rating)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 60.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (onAudioToggle != null) {
                        CloneflixHeroCircleButton(
                            iconRes = if (isMuted) R.drawable.cloneflix_ic_mute else R.drawable.cloneflix_ic_volume,
                            contentDescription = if (isMuted) "Unmute Audio" else "Mute Audio",
                            onClick = onAudioToggle,
                            size = 40.dp
                        )
                    }

                    CloneflixHeroMaturityRating(rating = maturityRating)
                }
            }
        }

        CloneflixHeroBannerType.MOVIE_PREVIEW -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181C24))
            ) {
                // Backdrop Gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF34495E), Color(0xFF1A252F), Color(0xFF11171E)),
                                radius = 900f
                            )
                        )
                )

                // Bottom Fade Scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xFF181818))
                            )
                        )
                )

                // Top Right Close Button
                if (onCloseClick != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        CloneflixHeroCircleButton(
                            iconRes = R.drawable.cloneflix_ic_close,
                            contentDescription = "Close Preview",
                            onClick = onCloseClick,
                            size = 36.dp,
                            iconSize = 16.dp,
                            backgroundColor = Grey850
                        )
                    }
                }

                // Bottom-Left Title & Actions
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 32.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CloneflixTitleLogoView(title = title, subtitle = "忍びの家")

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CloneflixHeroPlayButton(onClick = onPlayClick)

                        if (onAddToListClick != null) {
                            CloneflixHeroCircleButton(
                                iconRes = R.drawable.cloneflix_ic_plus,
                                contentDescription = "Add to My List",
                                onClick = onAddToListClick,
                                size = 42.dp,
                                iconSize = 18.dp,
                                backgroundColor = Grey700
                            )
                        }

                        if (onThumbsUpClick != null) {
                            CloneflixHeroCircleButton(
                                iconRes = R.drawable.cloneflix_ic_thumb_up,
                                contentDescription = "I like this",
                                onClick = onThumbsUpClick,
                                size = 42.dp,
                                iconSize = 18.dp,
                                backgroundColor = Grey700
                            )
                        }
                    }
                }

                // Bottom-Right Audio Toggle
                if (onAudioToggle != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 32.dp, bottom = 28.dp)
                    ) {
                        CloneflixHeroCircleButton(
                            iconRes = if (isMuted) R.drawable.cloneflix_ic_mute else R.drawable.cloneflix_ic_volume,
                            contentDescription = if (isMuted) "Unmute Audio" else "Mute Audio",
                            onClick = onAudioToggle,
                            size = 40.dp
                        )
                    }
                }
            }
        }

        CloneflixHeroBannerType.LANDING_PAGE -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(440.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F1115))
            ) {
                // Vignette background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF2C3E50).copy(alpha = 0.7f), Color(0xFF0F1115), Color(0xFF000000)),
                                radius = 1000f
                            )
                        )
                )

                // Header Navigation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CLONEFLIX",
                        fontFamily = com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryRed,
                        fontSize = 28.sp,
                        letterSpacing = 2.sp
                    )

                    CloneflixButton(
                        text = "Sign In",
                        onClick = onPlayClick,
                        variant = CloneflixButtonVariant.PRIMARY,
                        size = CloneflixButtonSize.SMALL
                    )
                }

                // Central Hero Content
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Unlimited movies, TV shows, and more",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryWhite,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Watch anywhere. Cancel anytime.",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        color = Grey10,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Ready to watch? Enter your email to create or restart your membership.",
                        fontSize = 15.sp,
                        color = Grey200,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CloneflixButton(
                            text = "Get Started >",
                            onClick = onPlayClick,
                            variant = CloneflixButtonVariant.PRIMARY,
                            size = CloneflixButtonSize.LARGE
                        )
                    }
                }
            }
        }

        CloneflixHeroBannerType.AUTHENTICATION_PAGE -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(440.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F1115))
            ) {
                // Vignette background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF1E272E), Color(0xFF0F1115), Color(0xFF000000)),
                                radius = 900f
                            )
                        )
                )

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CLONEFLIX",
                        fontFamily = com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryRed,
                        fontSize = 28.sp,
                        letterSpacing = 2.sp
                    )
                }

                // Auth Card in Center
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(360.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TransparentBlack90)
                        .border(BorderStroke(1.dp, TransparentWhite20), RoundedCornerShape(8.dp))
                        .padding(28.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Sign In",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryWhite
                        )

                        CloneflixButton(
                            text = "Sign In with Account",
                            onClick = onPlayClick,
                            modifier = Modifier.fillMaxWidth(),
                            variant = CloneflixButtonVariant.PRIMARY,
                            size = CloneflixButtonSize.MEDIUM
                        )

                        Text(
                            text = "New to Cloneflix? Sign up now.",
                            fontSize = 13.sp,
                            color = Grey200
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full Composed Home Page Hero Pattern from Figma Section 15 (Frame 314).
 * Includes Top App Navigation Bar, Hero Backdrop with Title Preview, Rating, and Bottom Shelf Preview.
 */
@Composable
fun CloneflixHomePageHeroPattern(
    onPlayClick: () -> Unit = {},
    onMoreInfoClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onCardClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val navItems = listOf("Home", "TV Shows", "Movies", "New & Popular", "My List", "Browse by Languages")
    var selectedNavIndex by remember { mutableStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141414))
    ) {
        // 1. Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0F1115), Color(0xCC0F1115))
                    )
                )
                .padding(horizontal = 32.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Logo & Nav items
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "CLONEFLIX",
                    fontFamily = com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryRed,
                    fontSize = 24.sp,
                    letterSpacing = 2.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navItems.forEachIndexed { index, item ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()
                        val isSelected = selectedNavIndex == index

                        Text(
                            text = item,
                            color = when {
                                isFocused -> PrimaryWhite
                                isSelected -> PrimaryWhite
                                else -> Grey200
                            },
                            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { selectedNavIndex = index }
                                )
                                .focusable(interactionSource = interactionSource)
                                .then(
                                    if (isFocused) Modifier.border(
                                        BorderStroke(1.dp, PrimaryWhite),
                                        RoundedCornerShape(2.dp)
                                    ).padding(horizontal = 4.dp, vertical = 2.dp) else Modifier
                                )
                        )
                    }
                }
            }

            // Right: Actions (Search, Notification, Profile)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.cloneflix_ic_search),
                    contentDescription = "Search",
                    tint = PrimaryWhite,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onSearchClick)
                )

                Icon(
                    painter = painterResource(id = R.drawable.cloneflix_ic_notification),
                    contentDescription = "Notifications",
                    tint = PrimaryWhite,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onNotificationClick)
                )

                // Profile Avatar with Dropdown Arrow
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable(onClick = onProfileClick)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Blue100),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "C",
                            color = PrimaryWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.cloneflix_ic_arrow_down),
                        contentDescription = "Profile Menu",
                        tint = PrimaryWhite,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        // 2. Hero Banner Body
        CloneflixHeroBanner(
            type = CloneflixHeroBannerType.HOME_PAGE,
            title = "HOUSE OF NINJAS",
            synopsis = "Years after retiring from their formidable ninja lives, a dysfunctional family must return to shadowy missions to counteract a string of looming threats.",
            top10RankText = "#2 in TV Shows Today",
            maturityRating = "16+",
            onPlayClick = onPlayClick,
            onMoreInfoClick = onMoreInfoClick,
            onAudioToggle = { isMuted = !isMuted },
            isMuted = isMuted
        )

        // 3. Bottom Trending Shelf Row Preview
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-40).dp)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Trending Now",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryWhite
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(
                    Triple("House of Ninjas", "Recently Added", "TOP 10"),
                    Triple("Avatar: The Last Airbender", "New Season", "TOP 10"),
                    Triple("One Piece", "Recently Added", "TOP 10"),
                    Triple("Stranger Things", "Leaving Soon", "TOP 10")
                ).forEach { (movieTitle, tag, badge) ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF2C3E50), Color(0xFF1A252F))
                                )
                            )
                            .border(
                                BorderStroke(
                                    if (isFocused) 2.dp else 1.dp,
                                    if (isFocused) PrimaryWhite else TransparentWhite20
                                ),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onCardClick?.invoke(movieTitle) }
                            )
                            .focusable(interactionSource = interactionSource)
                            .padding(8.dp)
                    ) {
                        // Badge Tag
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Red300)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = tag,
                                color = PrimaryWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = movieTitle,
                            color = PrimaryWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomStart)
                        )
                    }
                }
            }
        }
    }
}

val Blue100 = Color(0xFF0071EB)

@Preview(name = "Hero Banners Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.TV_720p)
@Composable
private fun HeroBannersPreview() {
    CloneflixTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            CloneflixTitlePreview(size = CloneflixTitlePreviewSize.LARGE)
            CloneflixHeroBanner(type = CloneflixHeroBannerType.HOME_PAGE)
        }
    }
}
