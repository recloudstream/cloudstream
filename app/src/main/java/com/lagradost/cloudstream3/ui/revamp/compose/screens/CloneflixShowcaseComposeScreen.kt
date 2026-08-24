package com.lagradost.cloudstream3.ui.revamp.compose.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixDivider
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixTheme as LegacyCloneflixTheme

@Composable
fun CloneflixShowcaseComposeScreen() {
    val context = LocalContext.current
    val tabs = listOf("About", "Typography", "Colors", "Buttons", "Components", "Content Blocks", "Icons & Labels", "Dropdowns", "Input Fields", "Hero Banners", "Avatars", "Video Player", "Movie Preview", "Movie Details")
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val typography = CloneflixTheme.typography
    val colors = CloneflixTheme.colors
    val dimens = CloneflixTheme.dimens

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceElevated)
                .padding(horizontal = dimens.spacing2Xl, vertical = dimens.spacingL),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CLONEFLIX",
                style = typography.logoBebas,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.width(dimens.spacingM))
            Text(
                text = "Design System 2024",
                style = typography.mediumBody,
                color = colors.textPrimary
            )
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = colors.surfaceElevated,
            contentColor = colors.textPrimary,
            edgePadding = dimens.spacingL,
            indicator = { tabPositions ->
                if (selectedTabIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = colors.primary,
                        height = 3.dp
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            style = if (selectedTabIndex == index) typography.mediumBody else typography.regularBody,
                            color = if (selectedTabIndex == index) colors.textPrimary else colors.textSecondary
                        )
                    }
                )
            }
        }

        CloneflixDivider()

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTabIndex) {
                0 -> CloneflixAboutComposeScreen(
                    onFollowClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://x.com/ivannaheraskina"))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFeedbackClick = {
                        Toast.makeText(context, "Feedback submitted! Thank you!", Toast.LENGTH_SHORT).show()
                    }
                )
                1 -> CloneflixTypographyComposeScreen(
                    onStyleClick = { styleLevel ->
                        LegacyCloneflixTheme.copyToClipboard(
                            context,
                            styleLevel.label,
                            "fontSize: ${styleLevel.sizeSp}sp, lineHeight: ${styleLevel.lineHeightSp}sp"
                        )
                    }
                )
                2 -> CloneflixColorsComposeScreen(
                    onColorClick = { token ->
                        LegacyCloneflixTheme.copyToClipboard(
                            context,
                            token.name,
                            token.hex
                        )
                    }
                )
                3 -> CloneflixButtonsComposeScreen()
                4 -> CloneflixComponentsComposeScreen(
                    onPrimaryClick = { Toast.makeText(context, "Primary Action Clicked", Toast.LENGTH_SHORT).show() },
                    onSecondaryClick = { Toast.makeText(context, "Secondary Action Clicked", Toast.LENGTH_SHORT).show() },
                    onOutlineClick = { Toast.makeText(context, "Outline Action Clicked", Toast.LENGTH_SHORT).show() },
                    onGhostClick = { Toast.makeText(context, "Ghost Action Clicked", Toast.LENGTH_SHORT).show() }
                )
                5 -> CloneflixContentBlocksComposeScreen()
                6 -> CloneflixIconsLabelsComposeScreen()
                7 -> CloneflixDropdownComposeScreen()
                8 -> CloneflixInputFieldsComposeScreen()
                9 -> CloneflixHeroBannersComposeScreen()
                10 -> CloneflixAvatarsComposeScreen()
                11 -> CloneflixVideoPlayerComposeScreen()
                12 -> CloneflixMoviePreviewComposeScreen()
                13 -> CloneflixMovieDetailsComposeScreen()
            }
        }
    }
}

@Preview(name = "Phone Showcase Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.PHONE)
@Preview(name = "TV Showcase Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.TV_720p)
@Composable
private fun CloneflixShowcasePreview() {
    CloneflixTheme {
        CloneflixShowcaseComposeScreen()
    }
}
