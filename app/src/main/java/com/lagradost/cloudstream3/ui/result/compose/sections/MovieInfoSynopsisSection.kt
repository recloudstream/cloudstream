package com.lagradost.cloudstream3.ui.result.compose.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.compose.components.MaturityRatingBadge
import com.lagradost.cloudstream3.ui.result.compose.components.MovieDetailsTokens
import com.lagradost.cloudstream3.ui.result.compose.components.VideoQualityBadge
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme
import com.lagradost.cloudstream3.ui.result.compose.theme.getRatingScoreColor

@Composable
fun MovieInfoSynopsisSection(
    matchScore: String?,
    releaseYear: String?,
    seasonsCount: String?,
    quality: String?,
    maturityRating: String?,
    advisories: String?,
    top10RankText: String?,
    synopsis: String,
    castList: List<String>,
    genres: List<String>,
    moodTags: List<String>,
    modifier: Modifier = Modifier
) {
    val colors = MovieDetailsTheme.colors
    val typography = MovieDetailsTheme.typography
    val dimens = MovieDetailsTheme.dimens
    val hasRightColumn = castList.isNotEmpty() || genres.isNotEmpty() || moodTags.isNotEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacing2Xl, vertical = dimens.spacingL),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing2Xl)
    ) {
        Column(
            modifier = Modifier
                .weight(if (hasRightColumn) 0.65f else 1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingM)
        ) {
            val hasLine1 = !matchScore.isNullOrBlank() || !releaseYear.isNullOrBlank() ||
                    !seasonsCount.isNullOrBlank() || !quality.isNullOrBlank()

            if (hasLine1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
                ) {
                    if (!matchScore.isNullOrBlank()) {
                        Text(
                            text = matchScore,
                            color = getRatingScoreColor(matchScore),
                            style = typography.mediumBody,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!releaseYear.isNullOrBlank()) {
                        Text(
                            text = releaseYear,
                            color = colors.textPrimary,
                            style = typography.regularBody
                        )
                    }

                    if (!seasonsCount.isNullOrBlank()) {
                        Text(
                            text = seasonsCount,
                            color = colors.textPrimary,
                            style = typography.regularBody
                        )
                    }

                    if (!quality.isNullOrBlank()) {
                        VideoQualityBadge(quality = quality)
                    }
                }
            }

            if (!maturityRating.isNullOrBlank() || !advisories.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingS)
                ) {
                    if (!maturityRating.isNullOrBlank()) {
                        MaturityRatingBadge(rating = maturityRating)
                    }
                    if (!advisories.isNullOrBlank()) {
                        Text(
                            text = advisories,
                            color = colors.textMuted,
                            style = typography.regularCaption1
                        )
                    }
                }
            }

            if (!top10RankText.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingS),
                    modifier = Modifier.padding(vertical = dimens.spacingXs)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(MovieDetailsTokens.ShapeCardSmall)
                            .background(colors.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.top_10_badge_text),
                            style = typography.regularCaption2,
                            color = colors.onPrimary,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = top10RankText,
                        style = typography.mediumBody,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }
            }

            if (synopsis.isNotBlank()) {
                val synopsisInteractionSource = remember { MutableInteractionSource() }
                val isSynopsisFocused by synopsisInteractionSource.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MovieDetailsTokens.ShapeCardSmall)
                        .background(if (isSynopsisFocused) colors.surfaceElevated.copy(alpha = 0.5f) else Color.Transparent)
                        .then(
                            if (isSynopsisFocused) {
                                Modifier.border(
                                    BorderStroke(dimens.borderFocus, colors.primary),
                                    MovieDetailsTokens.ShapeCardSmall
                                )
                            } else Modifier
                        )
                        .focusable(interactionSource = synopsisInteractionSource)
                        .padding(dimens.spacingS)
                ) {
                    Text(
                        text = synopsis,
                        style = typography.regularBody,
                        color = if (isSynopsisFocused) colors.textPrimary else colors.textSecondary,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        if (hasRightColumn) {
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingM)
            ) {
                if (castList.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(id = R.string.cast_label),
                            style = typography.regularCaption2,
                            color = colors.textSecondary
                        )
                        Text(
                            text = castList.joinToString(", "),
                            style = typography.regularCaption1,
                            color = colors.textPrimary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (genres.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(id = R.string.genres_label),
                            style = typography.regularCaption2,
                            color = colors.textSecondary
                        )
                        Text(
                            text = genres.joinToString(", "),
                            style = typography.regularCaption1,
                            color = colors.textPrimary
                        )
                    }
                }

                if (moodTags.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(id = R.string.mood_tags_label),
                            style = typography.regularCaption2,
                            color = colors.textSecondary
                        )
                        Text(
                            text = moodTags.joinToString(", "),
                            style = typography.regularCaption1,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}
