package com.lagradost.cloudstream3.ui.revamp.compose.components

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey10
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey25
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey300T40
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryBlack
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryRed
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Red100
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Red300

/**
 * Maturity Rating Badges from Figma Design System (TV-Y, TV-Y7, G, TV-G, PG, TV-PG, PG-13, TV-14, R, TV-MA, NC-17).
 */
@Composable
fun CloneflixMaturityRating(
    rating: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = if (isFocused) PrimaryWhite else Grey25
    val borderWidth = if (isFocused) 1.8.dp else 1.dp
    val textColor = if (isFocused) PrimaryWhite else Grey25

    Box(
        modifier = modifier
            .height(22.dp)
            .clip(RoundedCornerShape(2.dp))
            .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(2.dp))
            .background(if (isFocused) Grey300T40 else Color.Transparent)
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
            .padding(horizontal = 6.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = rating,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                contentDescription = "Maturity Rating $rating"
            }
        )
    }
}

/**
 * Video Quality Badges (HD, 4K, HDR, UltraHD 4K, Dolby Vision).
 */
@Composable
fun CloneflixVideoQualityBadge(
    quality: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = if (isFocused) PrimaryWhite else Grey200
    val borderWidth = if (isFocused) 1.5.dp else 1.dp
    val textColor = if (isFocused) PrimaryWhite else Grey10

    Box(
        modifier = modifier
            .height(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(4.dp))
            .background(if (isFocused) Grey300T40 else Color.Transparent)
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
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = quality,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                contentDescription = "Video Quality $quality"
            }
        )
    }
}

enum class CloneflixTop10Size {
    LARGE,
    MEDIUM,
    SMALL
}

/**
 * Iconic Top 10 Red Stacked Badge.
 */
@Composable
fun CloneflixTop10Badge(
    modifier: Modifier = Modifier,
    size: CloneflixTop10Size = CloneflixTop10Size.MEDIUM
) {
    val badgeSize: Dp = when (size) {
        CloneflixTop10Size.LARGE -> 32.dp
        CloneflixTop10Size.MEDIUM -> 28.dp
        CloneflixTop10Size.SMALL -> 24.dp
    }

    val topTextSize = when (size) {
        CloneflixTop10Size.LARGE -> 10.sp
        CloneflixTop10Size.MEDIUM -> 9.sp
        CloneflixTop10Size.SMALL -> 7.5.sp
    }

    val numTextSize = when (size) {
        CloneflixTop10Size.LARGE -> 16.sp
        CloneflixTop10Size.MEDIUM -> 14.sp
        CloneflixTop10Size.SMALL -> 11.5.sp
    }

    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(RoundedCornerShape(4.dp))
            .background(PrimaryRed)
            .semantics { contentDescription = "Top 10 Badge" },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "TOP",
                color = PrimaryWhite,
                fontSize = topTextSize,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                lineHeight = topTextSize
            )
            Text(
                text = "10",
                color = PrimaryWhite,
                fontSize = numTextSize,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                lineHeight = numTextSize
            )
        }
    }
}

/**
 * Top 10 with Rank text (e.g. "#2 in TV Shows Today").
 */
@Composable
fun CloneflixTop10RankBanner(
    rankText: String,
    modifier: Modifier = Modifier,
    size: CloneflixTop10Size = CloneflixTop10Size.MEDIUM,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val textFontSize = when (size) {
        CloneflixTop10Size.LARGE -> 22.sp
        CloneflixTop10Size.MEDIUM -> 18.sp
        CloneflixTop10Size.SMALL -> 15.sp
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                if (isFocused) BorderStroke(2.dp, PrimaryWhite) else BorderStroke(0.dp, Color.Transparent),
                RoundedCornerShape(8.dp)
            )
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
            .padding(4.dp)
    ) {
        CloneflixTop10Badge(size = size)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = rankText,
            color = PrimaryWhite,
            fontSize = textFontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = CloneflixTheme.typography.regularBody.fontFamily
        )
    }
}

enum class CloneflixBadgeType(@androidx.annotation.StringRes val stringRes: Int) {
    RECENTLY_ADDED(com.lagradost.cloudstream3.R.string.cloneflix_badge_recently_added),
    NEW_SEASON(com.lagradost.cloudstream3.R.string.cloneflix_badge_new_season),
    TOP_10(com.lagradost.cloudstream3.R.string.cloneflix_badge_top_10),
    LEAVING_SOON(com.lagradost.cloudstream3.R.string.cloneflix_badge_leaving_soon)
}

/**
 * Content Status Pill Badges (Recently Added, New Season, Top 10, Leaving Soon).
 */
@Composable
fun CloneflixContentBadge(
    type: CloneflixBadgeType,
    modifier: Modifier = Modifier,
    customText: String? = null
) {
    val text = customText ?: androidx.compose.ui.res.stringResource(id = type.stringRes)

    val backgroundColor = when (type) {
        CloneflixBadgeType.RECENTLY_ADDED -> Red100
        CloneflixBadgeType.NEW_SEASON -> PrimaryRed
        CloneflixBadgeType.TOP_10 -> Red300
        CloneflixBadgeType.LEAVING_SOON -> Red300
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CloneflixTokens.RadiusCard))
            .background(backgroundColor)
            .padding(horizontal = CloneflixTheme.dimens.spacingS, vertical = CloneflixTheme.dimens.spacingXxs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = PrimaryWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

enum class CloneflixLogoVariant {
    WORDMARK_MEDIUM,
    WORDMARK_SMALL,
    LETTERMARK_LARGE,
    LETTERMARK_MEDIUM,
    LETTERMARK_SMALL
}

/**
 * Cloneflix Brand Logo Composable (Wordmark & Lettermark).
 * Optionally displays provider/plugin name or custom text in brand styling.
 */
@Composable
fun CloneflixLogoView(
    variant: CloneflixLogoVariant = CloneflixLogoVariant.WORDMARK_MEDIUM,
    text: String? = null,
    modifier: Modifier = Modifier
) {
    val logoText = (if (!text.isNullOrBlank()) text else "CLONEFLIX").uppercase()
    when (variant) {
        CloneflixLogoVariant.WORDMARK_MEDIUM -> {
            Text(
                text = logoText,
                color = PrimaryRed,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 1.5.sp,
                modifier = modifier.semantics { contentDescription = "$logoText Logo" }
            )
        }
        CloneflixLogoVariant.WORDMARK_SMALL -> {
            Text(
                text = logoText,
                color = PrimaryRed,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 1.sp,
                modifier = modifier.semantics { contentDescription = "$logoText Logo Small" }
            )
        }
        CloneflixLogoVariant.LETTERMARK_LARGE -> {
            val letter = logoText.firstOrNull()?.toString() ?: "C"
            Box(
                modifier = modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PrimaryBlack),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter,
                    color = PrimaryRed,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
        CloneflixLogoVariant.LETTERMARK_MEDIUM -> {
            val letter = logoText.firstOrNull()?.toString() ?: "C"
            Box(
                modifier = modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(PrimaryBlack),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter,
                    color = PrimaryRed,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
        CloneflixLogoVariant.LETTERMARK_SMALL -> {
            val letter = logoText.firstOrNull()?.toString() ?: "C"
            Box(
                modifier = modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(PrimaryBlack),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter,
                    color = PrimaryRed,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}

@Preview(name = "Badges & Logos Preview", showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun BadgesAndLogosPreview() {
    CloneflixTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CloneflixMaturityRating(rating = "TV-MA")
                CloneflixMaturityRating(rating = "PG-13")
                CloneflixVideoQualityBadge(quality = "4K")
                CloneflixVideoQualityBadge(quality = "HD")
                CloneflixVideoQualityBadge(quality = "Dolby Vision")
            }
            Spacer(modifier = Modifier.height(16.dp))
            CloneflixTop10RankBanner(rankText = "#2 in TV Shows Today")
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CloneflixContentBadge(type = CloneflixBadgeType.RECENTLY_ADDED)
                CloneflixContentBadge(type = CloneflixBadgeType.NEW_SEASON)
                CloneflixContentBadge(type = CloneflixBadgeType.TOP_10)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CloneflixLogoView(variant = CloneflixLogoVariant.WORDMARK_MEDIUM)
                CloneflixLogoView(variant = CloneflixLogoVariant.LETTERMARK_MEDIUM)
            }
        }
    }
}
