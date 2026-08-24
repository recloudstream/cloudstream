package com.lagradost.cloudstream3.ui.revamp.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCard
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixDropdown
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixDropdownStaticShowcase
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixDropdownVariant
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeader
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme

@Composable
fun CloneflixDropdownComposeScreen(
    modifier: Modifier = Modifier
) {
    val typography = CloneflixTheme.typography
    val colors = CloneflixTheme.colors
    val dimens = CloneflixTheme.dimens
    val scrollState = rememberScrollState()

    val viewingPreferencesOptions = listOf("Original Language", "Dubbing", "Subtitles")
    var selectedViewingPref by remember { mutableStateOf("Original Language") }

    val languageOptions = listOf(
        "Arabic", "Cantonese", "Danish", "Dutch", "English",
        "Filipino", "Flemish", "French", "French Canadian",
        "German", "Greek", "Hebrew", "Hindi", "Hungarian",
        "Indonesian", "Italian", "Japanese", "Korean",
        "Malay", "Mandarin", "Polish", "Portuguese",
        "Romanian", "Russian", "Spanish", "Swedish",
        "Thai", "Turkish", "Ukrainian", "Vietnamese"
    )
    var selectedLanguage by remember { mutableStateOf("English") }

    val suggestionOptions = listOf(
        "Suggestions For you",
        "Year Released",
        "A-Z",
        "Z-A"
    )
    var selectedSuggestion by remember { mutableStateOf("Suggestions For you") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(dimens.spacing2Xl)
    ) {
        CloneflixHeader(
            title = "Dropdowns",
            subtitle = "Viewing Preferences • Language Options • Suggestions For You",
            iconRes = R.drawable.cloneflix_ic_dropdown
        )

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "VIEWING PREFERENCES",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(dimens.spacingL)) {
                Text(
                    text = "Interactive Dropdown",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(dimens.spacingXs))
                Text(
                    text = "Select audio/subtitle preference mode.",
                    style = typography.regularCaption1,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(dimens.spacingM))
                CloneflixDropdown(
                    options = viewingPreferencesOptions,
                    selectedOption = selectedViewingPref,
                    onOptionSelected = { selectedViewingPref = it },
                    variant = CloneflixDropdownVariant.OUTLINE,
                    width = 220.dp
                )

                Spacer(modifier = Modifier.height(dimens.spacingL))

                Text(
                    text = "Figma Design States (Default • Hover • Click/Expanded)",
                    style = typography.regularCaption1,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(dimens.spacingS))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
                ) {
                    CloneflixDropdownStaticShowcase(
                        title = "Default",
                        selectedOption = "Original Language",
                        options = viewingPreferencesOptions,
                        modifier = Modifier.weight(1f)
                    )
                    CloneflixDropdownStaticShowcase(
                        title = "Hover",
                        selectedOption = "Original Language",
                        options = viewingPreferencesOptions,
                        isHoverState = true,
                        modifier = Modifier.weight(1f)
                    )
                    CloneflixDropdownStaticShowcase(
                        title = "Click / Expanded",
                        selectedOption = "Original Language",
                        options = viewingPreferencesOptions,
                        isExpandedState = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "LANGUAGE OPTIONS",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(dimens.spacingL)) {
                Text(
                    text = "Interactive Language Selector",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(dimens.spacingXs))
                Text(
                    text = "Comprehensive language picker with scrollable option menu.",
                    style = typography.regularCaption1,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(dimens.spacingM))
                CloneflixDropdown(
                    options = languageOptions,
                    selectedOption = selectedLanguage,
                    onOptionSelected = { selectedLanguage = it },
                    variant = CloneflixDropdownVariant.OUTLINE,
                    width = 220.dp
                )

                Spacer(modifier = Modifier.height(dimens.spacingL))

                Text(
                    text = "Figma Design States (Default • Hover • Click/Expanded)",
                    style = typography.regularCaption1,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(dimens.spacingS))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
                ) {
                    CloneflixDropdownStaticShowcase(
                        title = "Default",
                        selectedOption = "English",
                        options = languageOptions.take(5),
                        modifier = Modifier.weight(1f)
                    )
                    CloneflixDropdownStaticShowcase(
                        title = "Hover",
                        selectedOption = "English",
                        options = languageOptions.take(5),
                        isHoverState = true,
                        modifier = Modifier.weight(1f)
                    )
                    CloneflixDropdownStaticShowcase(
                        title = "Click / Expanded",
                        selectedOption = "English",
                        options = languageOptions.take(6),
                        isExpandedState = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "SUGGESTIONS FOR YOU",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(dimens.spacingL)) {
                Text(
                    text = "Interactive Sort & Filter Filter",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(dimens.spacingXs))
                Text(
                    text = "Browse feed filter dropdown with sorting options.",
                    style = typography.regularCaption1,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(dimens.spacingM))
                CloneflixDropdown(
                    options = suggestionOptions,
                    selectedOption = selectedSuggestion,
                    onOptionSelected = { selectedSuggestion = it },
                    variant = CloneflixDropdownVariant.OUTLINE,
                    width = 240.dp
                )

                Spacer(modifier = Modifier.height(dimens.spacingL))

                Text(
                    text = "Figma Design States (Default • Hover • Click/Expanded)",
                    style = typography.regularCaption1,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(dimens.spacingS))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
                ) {
                    CloneflixDropdownStaticShowcase(
                        title = "Default",
                        selectedOption = "Suggestions For you",
                        options = suggestionOptions,
                        modifier = Modifier.weight(1f)
                    )
                    CloneflixDropdownStaticShowcase(
                        title = "Hover",
                        selectedOption = "Suggestions For you",
                        options = suggestionOptions,
                        isHoverState = true,
                        modifier = Modifier.weight(1f)
                    )
                    CloneflixDropdownStaticShowcase(
                        title = "Click / Expanded",
                        selectedOption = "Suggestions For you",
                        options = suggestionOptions,
                        isExpandedState = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))
    }
}

@Preview(name = "Phone Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.PHONE)
@Preview(name = "TV Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.TV_720p)
@Composable
private fun CloneflixDropdownScreenPreview() {
    CloneflixTheme {
        CloneflixDropdownComposeScreen()
    }
}
