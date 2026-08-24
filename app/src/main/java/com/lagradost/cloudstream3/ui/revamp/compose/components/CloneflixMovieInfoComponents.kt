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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.GreenAccent
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey100
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite

/**
 * Text / MovieInfo Inline Row Pattern from Figma Section 12.
 */
@Composable
fun CloneflixMovieInfoRow(
    matchText: String = "New",
    maturityRating: String = "TV-MA",
    duration: String = "3 Seasons",
    quality: String = "HD",
    hasAudioDescription: Boolean = false,
    modifier: Modifier = Modifier
) {
    val typography = CloneflixTheme.typography

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = matchText,
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

        if (hasAudioDescription) {
            Icon(
                painter = painterResource(id = R.drawable.cloneflix_ic_ad),
                contentDescription = "Audio Description",
                tint = PrimaryWhite,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * MovieInfo Block Pattern from Figma Section 13 (Detailed header, advisories, Top 10 rank).
 */
@Composable
fun CloneflixMovieInfoBlock(
    matchScore: String = "98% Match",
    releaseYear: String = "2024",
    duration: String = "3 Seasons",
    maturityRating: String = "TV-MA",
    advisories: String = "smoking, violence",
    quality: String = "HD",
    top10RankText: String? = "#2 in TV Shows Today",
    hasAudioDescription: Boolean = true,
    modifier: Modifier = Modifier
) {
    val typography = CloneflixTheme.typography

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Line 1: Match Score, Year, Duration, Quality, AD Icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = matchScore,
                color = GreenAccent,
                style = typography.mediumBody,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = releaseYear,
                color = PrimaryWhite,
                style = typography.regularBody
            )

            Text(
                text = duration,
                color = PrimaryWhite,
                style = typography.regularBody
            )

            CloneflixVideoQualityBadge(quality = quality)

            if (hasAudioDescription) {
                Icon(
                    painter = painterResource(id = R.drawable.cloneflix_ic_ad),
                    contentDescription = "Audio Description Available",
                    tint = PrimaryWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Line 2: Rating badge with advisory keywords
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CloneflixMaturityRating(rating = maturityRating)

            if (advisories.isNotBlank()) {
                Text(
                    text = advisories,
                    color = Grey200,
                    style = typography.regularSmallBody
                )
            }
        }

        // Line 3: Top 10 rank banner if present
        if (!top10RankText.isNullOrBlank()) {
            CloneflixTop10RankBanner(
                rankText = top10RankText,
                size = CloneflixTop10Size.MEDIUM
            )
        }
    }
}

/**
 * Complete MovieInfo Overview Card from Figma Section 14 (Synopsis, Block & Metadata columns).
 */
@Composable
fun CloneflixMovieInfoOverview(
    title: String = "House of Ninjas",
    synopsis: String = "Years after retiring from their formidable ninja lives, a dysfunctional family must return to shadowy missions to counteract a string of looming threats.",
    cast: String = "Kento Kaku, Yosuke Eguchi, Tae Kimura, more",
    genres: String = "TV Dramas, Japanese, TV Thrillers",
    moodTags: String = "Dark, Suspenseful, Exciting",
    matchScore: String = "New",
    releaseYear: String = "2024",
    duration: String = "3 Seasons",
    maturityRating: String = "TV-MA",
    advisories: String = "smoking, violence",
    quality: String = "HD",
    top10RankText: String? = "#2 in TV Shows Today",
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = CloneflixTheme.colors
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val activeBorder = if (isFocused) {
        BorderStroke(2.dp, PrimaryWhite)
    } else {
        BorderStroke(1.dp, colors.border)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CloneflixTheme.shapes.large)
            .background(colors.surface)
            .border(activeBorder, CloneflixTheme.shapes.large)
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
            .padding(dimens.spacing2Xl)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CloneflixMovieInfoBlock(
                matchScore = matchScore,
                releaseYear = releaseYear,
                duration = duration,
                maturityRating = maturityRating,
                advisories = advisories,
                quality = quality,
                top10RankText = top10RankText,
                hasAudioDescription = true
            )

            Spacer(modifier = Modifier.height(dimens.spacingL))

            Text(
                text = synopsis,
                style = typography.regularBody,
                color = colors.textPrimary,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(dimens.spacingL))

            // Metadata columns: Cast, Genres, Mood tags
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Grey100, fontWeight = FontWeight.Normal)) {
                            append("Cast: ")
                        }
                        withStyle(SpanStyle(color = PrimaryWhite, fontWeight = FontWeight.Medium)) {
                            append(cast)
                        }
                    },
                    style = typography.regularSmallBody
                )

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Grey100, fontWeight = FontWeight.Normal)) {
                            append("Genres: ")
                        }
                        withStyle(SpanStyle(color = PrimaryWhite, fontWeight = FontWeight.Medium)) {
                            append(genres)
                        }
                    },
                    style = typography.regularSmallBody
                )

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Grey100, fontWeight = FontWeight.Normal)) {
                            append("This show is: ")
                        }
                        withStyle(SpanStyle(color = PrimaryWhite, fontWeight = FontWeight.Medium)) {
                            append(moodTags)
                        }
                    },
                    style = typography.regularSmallBody
                )
            }
        }
    }
}

@Preview(name = "Movie Info Preview", showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun MovieInfoComponentsPreview() {
    CloneflixTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            CloneflixMovieInfoRow()
            Spacer(modifier = Modifier.height(24.dp))
            CloneflixMovieInfoOverview()
        }
    }
}
