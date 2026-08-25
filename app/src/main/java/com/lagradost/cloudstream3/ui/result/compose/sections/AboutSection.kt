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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.compose.components.MovieDetailsTokens
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme

@Composable
fun AboutMetadataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val typography = MovieDetailsTheme.typography
    val colors = MovieDetailsTheme.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (label.endsWith(":")) label else "$label:",
            style = typography.regularCaption1,
            color = colors.textSecondary,
            modifier = Modifier.width(130.dp)
        )
        Text(
            text = value,
            style = typography.regularCaption1,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun AboutSection(
    title: String,
    creator: String?,
    castList: List<String>,
    writers: List<String>,
    genres: List<String>,
    moodTags: List<String>,
    maturityRating: String?,
    advisories: String?,
    modifier: Modifier = Modifier
) {
    val colors = MovieDetailsTheme.colors
    val typography = MovieDetailsTheme.typography
    val dimens = MovieDetailsTheme.dimens
    val aboutInteractionSource = remember { MutableInteractionSource() }
    val isAboutFocused by aboutInteractionSource.collectIsFocusedAsState()
    val aboutBorder = if (isAboutFocused) BorderStroke(dimens.borderFocus, colors.primary) else BorderStroke(1.dp, colors.border)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacing2Xl)
            .padding(top = dimens.spacing3Xl, bottom = dimens.spacing3Xl)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MovieDetailsTokens.ShapeCardMedium)
                .background(if (isAboutFocused) colors.surfaceElevated else colors.surface)
                .border(aboutBorder, MovieDetailsTokens.ShapeCardMedium)
                .focusable(interactionSource = aboutInteractionSource)
                .padding(dimens.spacing2Xl),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingM)
        ) {
            Text(
                text = stringResource(id = R.string.about_title_format, title),
                style = typography.boldTitle2,
                fontSize = 20.sp,
                color = colors.textPrimary
            )

            Spacer(modifier = Modifier.height(dimens.spacingXs))

            if (!creator.isNullOrBlank()) {
                AboutMetadataRow(label = stringResource(id = R.string.creator_label), value = creator)
            }
            if (castList.isNotEmpty()) {
                AboutMetadataRow(label = stringResource(id = R.string.cast_label), value = castList.joinToString(", "))
            }
            if (writers.isNotEmpty()) {
                AboutMetadataRow(label = stringResource(id = R.string.writers_label), value = writers.joinToString(", "))
            }
            if (genres.isNotEmpty()) {
                AboutMetadataRow(label = stringResource(id = R.string.genres_label), value = genres.joinToString(", "))
            }
            if (moodTags.isNotEmpty()) {
                AboutMetadataRow(label = stringResource(id = R.string.mood_tags_label), value = moodTags.joinToString(", "))
            }
            if (!maturityRating.isNullOrBlank()) {
                val ratingDescription = if (!advisories.isNullOrBlank()) {
                    "$maturityRating ($advisories)"
                } else {
                    maturityRating
                }
                AboutMetadataRow(
                    label = stringResource(id = R.string.maturity_rating_label),
                    value = ratingDescription
                )
            }
        }
    }
}
