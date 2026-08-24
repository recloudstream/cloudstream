package com.lagradost.cloudstream3.ui.revamp.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixButton
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixButtonSize
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixButtonVariant
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCard
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCardElevation
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeader
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixInputField
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixTextField
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme

@Composable
fun CloneflixComponentsComposeScreen(
    onPrimaryClick: () -> Unit = {},
    onSecondaryClick: () -> Unit = {},
    onOutlineClick: () -> Unit = {},
    onGhostClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val typography = CloneflixTheme.typography
    val colors = CloneflixTheme.colors
    val dimens = CloneflixTheme.dimens
    val scrollState = rememberScrollState()

    var emailText by remember { mutableStateOf("") }
    var passwordText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(dimens.spacing2Xl)
    ) {
        CloneflixHeader(
            title = "Components",
            subtitle = "Buttons • Cards • Inputs • Custom Views",
            iconRes = R.drawable.cloneflix_ic_about
        )

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "BUTTON VARIANTS",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixButton(
            text = "Primary Action (Red)",
            onClick = onPrimaryClick,
            modifier = Modifier.fillMaxWidth(),
            variant = CloneflixButtonVariant.PRIMARY,
            size = CloneflixButtonSize.LARGE,
            icon = painterResource(id = R.drawable.cloneflix_ic_open_in_new)
        )

        Spacer(modifier = Modifier.height(dimens.spacingM))

        CloneflixButton(
            text = "Secondary Action (White)",
            onClick = onSecondaryClick,
            modifier = Modifier.fillMaxWidth(),
            variant = CloneflixButtonVariant.SECONDARY,
            size = CloneflixButtonSize.MEDIUM
        )

        Spacer(modifier = Modifier.height(dimens.spacingM))

        CloneflixButton(
            text = "Outlined Action (Border)",
            onClick = onOutlineClick,
            modifier = Modifier.fillMaxWidth(),
            variant = CloneflixButtonVariant.OUTLINE,
            size = CloneflixButtonSize.MEDIUM,
            icon = painterResource(id = R.drawable.cloneflix_ic_copy)
        )

        Spacer(modifier = Modifier.height(dimens.spacingM))

        CloneflixButton(
            text = "Ghost Action (Transparent)",
            onClick = onGhostClick,
            variant = CloneflixButtonVariant.GHOST,
            size = CloneflixButtonSize.SMALL
        )

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))

        Text(
            text = "INPUT FIELDS",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixInputField(
            value = emailText,
            onValueChange = { emailText = it },
            label = "Email address or phone number"
        )

        Spacer(modifier = Modifier.height(dimens.spacingM))

        CloneflixInputField(
            value = passwordText,
            onValueChange = { passwordText = it },
            label = "Password",
            isPassword = true
        )

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))

        Text(
            text = "CARD CONTAINERS",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(dimens.spacingL)) {
                Text(
                    text = "Surface Container (#232323)",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(dimens.spacingXs))
                Text(
                    text = "Standard content card with 16dp rounded corners and subtle border.",
                    style = typography.regularCaption1,
                    color = colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacingM))

        CloneflixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CloneflixCardElevation.ELEVATED
        ) {
            Column(modifier = Modifier.padding(dimens.spacingL)) {
                Text(
                    text = "Elevated Container (#181818)",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(dimens.spacingXs))
                Text(
                    text = "Elevated surface card for modals, tooltips, and bottom sheets.",
                    style = typography.regularCaption1,
                    color = colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))
    }
}

@Preview(name = "Phone Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.PHONE)
@Preview(name = "TV Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.TV_720p)
@Composable
private fun CloneflixComponentsScreenPreview() {
    CloneflixTheme {
        CloneflixComponentsComposeScreen()
    }
}
