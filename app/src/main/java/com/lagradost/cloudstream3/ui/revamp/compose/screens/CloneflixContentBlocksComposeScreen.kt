package com.lagradost.cloudstream3.ui.revamp.compose.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixAuthFooter
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixBlockLayout
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCard
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixDivider
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixDownloadProgressCard
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixFaqSection
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixFeatureBlock
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeader
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHomeFooter
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHomeHeader
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixKidsPreviewCard
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixLandingFooter
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixLandingHeader
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMacPreviewCard
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMobilePreviewCard
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixTvPreviewCard
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme

@Composable
fun CloneflixContentBlocksComposeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val typography = CloneflixTheme.typography
    val colors = CloneflixTheme.colors
    val dimens = CloneflixTheme.dimens
    val scrollState = rememberScrollState()

    var selectedLandingLang by remember { mutableStateOf("English") }
    var selectedHomeNavIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(dimens.spacing2Xl)
    ) {
        CloneflixHeader(
            title = "Content Blocks",
            subtitle = "Headers • FAQ Accordion • Screen Previews • Landing Blocks • Footers",
            iconRes = R.drawable.cloneflix_ic_about
        )

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "HEADERS",
            style = typography.sectionHeader,
            color = colors.primary
        )
        Spacer(modifier = Modifier.height(dimens.spacingM))

        Text(
            text = "Landing Page Header",
            style = typography.mediumBody,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(dimens.spacingS))
        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            CloneflixLandingHeader(
                selectedLanguage = selectedLandingLang,
                onLanguageSelected = { selectedLandingLang = it },
                onSignInClick = {
                    Toast.makeText(context, "Sign In clicked", Toast.LENGTH_SHORT).show()
                }
            )
        }

        Spacer(modifier = Modifier.height(dimens.spacingL))

        Text(
            text = "Home Page Header",
            style = typography.mediumBody,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(dimens.spacingS))
        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            CloneflixHomeHeader(
                selectedNavIndex = selectedHomeNavIndex,
                onNavItemSelected = { selectedHomeNavIndex = it },
                onSearchClick = {
                    Toast.makeText(context, "Search action", Toast.LENGTH_SHORT).show()
                },
                onNotificationsClick = {
                    Toast.makeText(context, "Notifications action", Toast.LENGTH_SHORT).show()
                },
                onProfileClick = {
                    Toast.makeText(context, "Profile menu action", Toast.LENGTH_SHORT).show()
                }
            )
        }

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))
        CloneflixDivider()
        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "LANDING PAGE BLOCKS",
            style = typography.sectionHeader,
            color = colors.primary
        )
        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(dimens.spacingL)) {
                CloneflixFeatureBlock(
                    title = "Enjoy on your TV",
                    description = "Watch on Smart TVs, Playstation, Xbox, Chromecast, Apple TV, Blu-ray players, and more.",
                    layout = CloneflixBlockLayout.HEADLINE_IMAGE,
                    previewContent = {
                        CloneflixTvPreviewCard(modifier = Modifier.fillMaxWidth(0.95f))
                    }
                )

                Spacer(modifier = Modifier.height(dimens.spacingL))

                CloneflixFeatureBlock(
                    title = "Watch everywhere",
                    description = "Stream unlimited movies and TV shows on your phone, tablet, laptop, and TV.",
                    layout = CloneflixBlockLayout.IMAGE_HEADLINE,
                    previewContent = {
                        CloneflixMacPreviewCard(modifier = Modifier.fillMaxWidth(0.95f))
                    }
                )

                Spacer(modifier = Modifier.height(dimens.spacingL))

                CloneflixFeatureBlock(
                    title = "Create profiles for kids",
                    description = "Send kids on adventures with their favorite characters in a space made just for them — free with your membership.",
                    layout = CloneflixBlockLayout.HEADLINE_IMAGE,
                    previewContent = {
                        CloneflixKidsPreviewCard(modifier = Modifier.fillMaxWidth(0.95f))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))
        CloneflixDivider()
        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "SCREENS PREVIEWS",
            style = typography.sectionHeader,
            color = colors.primary
        )
        Spacer(modifier = Modifier.height(dimens.spacingL))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingL)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TV Screen Preview",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(dimens.spacingS))
                CloneflixTvPreviewCard(modifier = Modifier.fillMaxWidth())
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mac Computer Screen Preview",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(dimens.spacingS))
                CloneflixMacPreviewCard(modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacingL))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingL)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Kids Profile Preview",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(dimens.spacingS))
                CloneflixKidsPreviewCard(modifier = Modifier.fillMaxWidth())
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Download Status Preview",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(dimens.spacingS))
                CloneflixDownloadProgressCard(
                    title = "Stranger Things",
                    statusText = "Downloading...",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacingL))

        Text(
            text = "iPhone Screen Preview",
            style = typography.mediumBody,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(dimens.spacingS))
        CloneflixMobilePreviewCard()

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))
        CloneflixDivider()
        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "FAQ ACCORDION",
            style = typography.sectionHeader,
            color = colors.primary
        )
        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            CloneflixFaqSection(modifier = Modifier.padding(dimens.spacingL))
        }

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))
        CloneflixDivider()
        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "FOOTERS",
            style = typography.sectionHeader,
            color = colors.primary
        )
        Spacer(modifier = Modifier.height(dimens.spacingM))

        Text(
            text = "Landing Page Footer",
            style = typography.mediumBody,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(dimens.spacingS))
        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            CloneflixLandingFooter(
                onPhoneClick = { Toast.makeText(context, "Call Support clicked", Toast.LENGTH_SHORT).show() },
                onLinkClick = { link -> Toast.makeText(context, "Link clicked: $link", Toast.LENGTH_SHORT).show() }
            )
        }

        Spacer(modifier = Modifier.height(dimens.spacingL))

        Text(
            text = "Authentication Page Footer",
            style = typography.mediumBody,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(dimens.spacingS))
        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            CloneflixAuthFooter(
                onPhoneClick = { Toast.makeText(context, "Call Support clicked", Toast.LENGTH_SHORT).show() },
                onLinkClick = { link -> Toast.makeText(context, "Link clicked: $link", Toast.LENGTH_SHORT).show() }
            )
        }

        Spacer(modifier = Modifier.height(dimens.spacingL))

        Text(
            text = "Home Page Footer",
            style = typography.mediumBody,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(dimens.spacingS))
        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            CloneflixHomeFooter(
                onSocialClick = { platform -> Toast.makeText(context, "$platform clicked", Toast.LENGTH_SHORT).show() },
                onLinkClick = { link -> Toast.makeText(context, "Link clicked: $link", Toast.LENGTH_SHORT).show() },
                onServiceCodeClick = { Toast.makeText(context, "Service Code: 839-204", Toast.LENGTH_SHORT).show() }
            )
        }
    }
}

@Preview(name = "Content Blocks Phone Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.PHONE)
@Preview(name = "Content Blocks TV Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.TV_720p)
@Composable
private fun CloneflixContentBlocksScreenPreview() {
    CloneflixTheme {
        CloneflixContentBlocksComposeScreen()
    }
}
