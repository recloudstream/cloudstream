package com.lagradost.cloudstream3.ui.revamp.compose.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixButton
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixButtonSize
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixButtonVariant
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCard
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCardElevation
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeader
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme

@Composable
fun CloneflixAboutComposeScreen(
    onFollowClick: () -> Unit = {},
    onFeedbackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dimens = CloneflixTheme.dimens
    val colors = CloneflixTheme.colors
    val typography = CloneflixTheme.typography
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(dimens.spacing2Xl)
    ) {
        CloneflixHeader(
            title = "About",
            subtitle = "Cloneflix Design System 2024 (Website Version)",
            iconRes = R.drawable.cloneflix_ic_about
        )

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(dimens.spacing2Xl)) {
                Text(
                    text = "Hello! I'm Ivanna Heraskina, a Product Designer from San Francisco 🌉.",
                    style = typography.regularHeadline1,
                    color = colors.textPrimary
                )

                Spacer(modifier = Modifier.height(dimens.spacingL))

                Text(
                    text = "The Cloneflix Design System 2024 (Website Version) was created with love and dedication. Whether you're a designer, developer, or just interested in UI/UX, feel free to use this toolkit. Use it to bring your creative ideas to life.",
                    style = typography.regularBody,
                    color = colors.textPrimary,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(dimens.spacingL))

                Text(
                    text = "If you have any thoughts or feedback, or simply want to say hi, add me on X.com (@ivannaheraskina).",
                    style = typography.regularSmallBody,
                    color = colors.textDimmed,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(dimens.spacingM))

                Text(
                    text = "If it was useful, your support would mean a lot!",
                    style = typography.regularSmallBody,
                    color = colors.textMuted
                )

                Spacer(modifier = Modifier.height(dimens.spacing2Xl))

                Row(modifier = Modifier.fillMaxWidth()) {
                    CloneflixButton(
                        text = "Follow @ivannaheraskina",
                        onClick = onFollowClick,
                        modifier = Modifier.weight(1f),
                        variant = CloneflixButtonVariant.PRIMARY,
                        size = CloneflixButtonSize.MEDIUM,
                        icon = painterResource(id = R.drawable.cloneflix_ic_open_in_new)
                    )

                    Spacer(modifier = Modifier.width(dimens.spacingM))

                    CloneflixButton(
                        text = "Feedback",
                        onClick = onFeedbackClick,
                        variant = CloneflixButtonVariant.OUTLINE,
                        size = CloneflixButtonSize.MEDIUM,
                        icon = painterResource(id = R.drawable.cloneflix_ic_feedback)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        CloneflixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CloneflixCardElevation.ELEVATED
        ) {
            Column(modifier = Modifier.padding(dimens.spacing2Xl)) {
                Text(
                    text = "Design System Architecture",
                    style = typography.mediumHeadline2,
                    color = colors.textPrimary
                )

                Spacer(modifier = Modifier.height(dimens.spacingS))

                Text(
                    text = "• Pure Jetpack Compose UI with reactive theme\n• 33+ Figma color tokens\n• Netflix Sans & Bebas Neue typography hierarchy\n• Reusable modular components with D-pad & touch compatibility",
                    style = typography.regularSmallBody,
                    color = colors.textMuted,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Preview(name = "Phone Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.PHONE)
@Preview(name = "TV Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.TV_720p)
@Composable
private fun CloneflixAboutScreenPreview() {
    CloneflixTheme {
        CloneflixAboutComposeScreen()
    }
}
