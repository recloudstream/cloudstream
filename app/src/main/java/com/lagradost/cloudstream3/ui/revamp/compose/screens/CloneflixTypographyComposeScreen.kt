package com.lagradost.cloudstream3.ui.revamp.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCard
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeader
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixTypography

@Composable
fun CloneflixTypographyComposeScreen(
    onStyleClick: (CloneflixTypography.StyleLevel) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens
    val colors = CloneflixTheme.colors

    val regularStyles = listOf(
        CloneflixTypography.StyleLevel.REGULAR_LARGE_TITLE to typography.regularLargeTitle,
        CloneflixTypography.StyleLevel.REGULAR_TITLE1 to typography.regularTitle1,
        CloneflixTypography.StyleLevel.REGULAR_TITLE2 to typography.regularTitle2,
        CloneflixTypography.StyleLevel.REGULAR_TITLE3 to typography.regularTitle3,
        CloneflixTypography.StyleLevel.REGULAR_TITLE4 to typography.regularTitle4,
        CloneflixTypography.StyleLevel.REGULAR_HEADLINE1 to typography.regularHeadline1,
        CloneflixTypography.StyleLevel.REGULAR_HEADLINE2 to typography.regularHeadline2,
        CloneflixTypography.StyleLevel.REGULAR_BODY to typography.regularBody,
        CloneflixTypography.StyleLevel.REGULAR_SMALL_BODY to typography.regularSmallBody,
        CloneflixTypography.StyleLevel.REGULAR_CAPTION1 to typography.regularCaption1,
        CloneflixTypography.StyleLevel.REGULAR_CAPTION2 to typography.regularCaption2
    )

    val mediumStyles = listOf(
        CloneflixTypography.StyleLevel.MEDIUM_LARGE_TITLE to typography.mediumLargeTitle,
        CloneflixTypography.StyleLevel.MEDIUM_TITLE1 to typography.mediumTitle1,
        CloneflixTypography.StyleLevel.MEDIUM_TITLE2 to typography.mediumTitle2,
        CloneflixTypography.StyleLevel.MEDIUM_TITLE3 to typography.mediumTitle3,
        CloneflixTypography.StyleLevel.MEDIUM_TITLE4 to typography.mediumTitle4,
        CloneflixTypography.StyleLevel.MEDIUM_HEADLINE1 to typography.mediumHeadline1,
        CloneflixTypography.StyleLevel.MEDIUM_HEADLINE2 to typography.mediumHeadline2,
        CloneflixTypography.StyleLevel.MEDIUM_BODY to typography.mediumBody,
        CloneflixTypography.StyleLevel.MEDIUM_SMALL_BODY to typography.mediumSmallBody,
        CloneflixTypography.StyleLevel.MEDIUM_CAPTION1 to typography.mediumCaption1,
        CloneflixTypography.StyleLevel.MEDIUM_CAPTION2 to typography.mediumCaption2
    )

    val boldStyles = listOf(
        CloneflixTypography.StyleLevel.BOLD_LARGE_TITLE to typography.boldLargeTitle,
        CloneflixTypography.StyleLevel.BOLD_TITLE1 to typography.boldTitle1,
        CloneflixTypography.StyleLevel.BOLD_TITLE2 to typography.boldTitle2
    )

    val displayStyles = listOf(
        CloneflixTypography.StyleLevel.LOGO_BEBAS to typography.logoBebas,
        CloneflixTypography.StyleLevel.HEADER_DISPLAY to typography.headerDisplay
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = dimens.spacing2Xl)
    ) {
        item {
            Spacer(modifier = Modifier.height(dimens.spacing2Xl))
            CloneflixHeader(
                title = "Typography",
                subtitle = "Netflix Sans • Bebas Neue",
                iconRes = R.drawable.cloneflix_ic_typography
            )
            Spacer(modifier = Modifier.height(dimens.spacing2Xl))
        }

        item {
            Text(
                text = "REGULAR STYLES",
                style = typography.sectionHeader,
                color = colors.primary,
                modifier = Modifier.padding(bottom = dimens.spacingL)
            )
        }
        items(regularStyles) { (level, style) ->
            TypographyStyleCard(level, style, onStyleClick)
            Spacer(modifier = Modifier.height(dimens.spacingM))
        }

        item {
            Spacer(modifier = Modifier.height(dimens.spacing2Xl))
            Text(
                text = "MEDIUM STYLES",
                style = typography.sectionHeader,
                color = colors.primary,
                modifier = Modifier.padding(bottom = dimens.spacingL)
            )
        }
        items(mediumStyles) { (level, style) ->
            TypographyStyleCard(level, style, onStyleClick)
            Spacer(modifier = Modifier.height(dimens.spacingM))
        }

        item {
            Spacer(modifier = Modifier.height(dimens.spacing2Xl))
            Text(
                text = "BOLD STYLES",
                style = typography.sectionHeader,
                color = colors.primary,
                modifier = Modifier.padding(bottom = dimens.spacingL)
            )
        }
        items(boldStyles) { (level, style) ->
            TypographyStyleCard(level, style, onStyleClick)
            Spacer(modifier = Modifier.height(dimens.spacingM))
        }

        item {
            Spacer(modifier = Modifier.height(dimens.spacing2Xl))
            Text(
                text = "LOGO & DISPLAY",
                style = typography.sectionHeader,
                color = colors.primary,
                modifier = Modifier.padding(bottom = dimens.spacingL)
            )
        }
        items(displayStyles) { (level, style) ->
            TypographyStyleCard(level, style, onStyleClick)
            Spacer(modifier = Modifier.height(dimens.spacingM))
        }

        item {
            Spacer(modifier = Modifier.height(dimens.spacing3Xl))
        }
    }
}

@Composable
private fun TypographyStyleCard(
    level: CloneflixTypography.StyleLevel,
    style: TextStyle,
    onStyleClick: (CloneflixTypography.StyleLevel) -> Unit
) {
    val colors = CloneflixTheme.colors
    val dimens = CloneflixTheme.dimens
    val typography = CloneflixTheme.typography

    val sample = when (level) {
        CloneflixTypography.StyleLevel.REGULAR_LARGE_TITLE,
        CloneflixTypography.StyleLevel.BOLD_LARGE_TITLE -> "Unlimited movies, TV shows, and more"
        CloneflixTypography.StyleLevel.LOGO_BEBAS -> "CLONEFLIX"
        else -> "Watch anywhere. Cancel anytime."
    }

    CloneflixCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = { onStyleClick(level) },
                role = Role.Button
            )
            .focusable()
            .semantics {
                contentDescription = "${level.label}, Size: ${level.sizeSp}sp, Sample: $sample"
            }
    ) {
        Column(modifier = Modifier.padding(dimens.spacingL)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = level.label,
                    style = typography.regularCaption1,
                    color = colors.textMuted,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Size: ${level.sizeSp.toInt()}sp • LH: ${level.lineHeightSp.toInt()}sp",
                    style = typography.regularCaption2,
                    color = colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(dimens.spacingS))

            Text(
                text = sample,
                style = style,
                color = colors.textPrimary
            )
        }
    }
}

@Preview(name = "Phone Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.PHONE)
@Preview(name = "TV Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.TV_720p)
@Composable
private fun CloneflixTypographyScreenPreview() {
    CloneflixTheme {
        CloneflixTypographyComposeScreen()
    }
}
