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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixGetStartedRow
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeader
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixInputField
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixInputFieldSize
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixSignInCard
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme

@Composable
fun CloneflixInputFieldsComposeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val typography = CloneflixTheme.typography
    val colors = CloneflixTheme.colors
    val dimens = CloneflixTheme.dimens
    val scrollState = rememberScrollState()

    var emailLarge by remember { mutableStateOf("") }
    var emailOrPhoneMedium by remember { mutableStateOf("user@example.com") }
    var passwordMedium by remember { mutableStateOf("") }

    var defaultField by remember { mutableStateOf("") }
    var focusedField by remember { mutableStateOf("active.typing@netflix.com") }
    var errorField by remember { mutableStateOf("invalid-email") }

    var getStartedEmail by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(dimens.spacing2Xl)
    ) {
        CloneflixHeader(
            title = "Input Fields",
            subtitle = "Netflix Design System 2024 • Floating labels, sizes & form components",
            iconRes = R.drawable.cloneflix_ic_input_field
        )

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "FIELD SIZES & TYPES",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixInputField(
            value = emailLarge,
            onValueChange = { emailLarge = it },
            label = "Email address (Large - 56dp)",
            size = CloneflixInputFieldSize.LARGE
        )

        Spacer(modifier = Modifier.height(dimens.spacingM))

        CloneflixInputField(
            value = emailOrPhoneMedium,
            onValueChange = { emailOrPhoneMedium = it },
            label = "Email or phone number (Medium - 48dp)",
            size = CloneflixInputFieldSize.MEDIUM
        )

        Spacer(modifier = Modifier.height(dimens.spacingM))

        CloneflixInputField(
            value = passwordMedium,
            onValueChange = { passwordMedium = it },
            label = "Password (Password Type with Reveal)",
            isPassword = true,
            size = CloneflixInputFieldSize.MEDIUM
        )

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))

        Text(
            text = "FIELD STATES (DEFAULT, FOCUSED, ERROR)",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixInputField(
            value = defaultField,
            onValueChange = { defaultField = it },
            label = "Default State (Empty / Centered Placeholder)",
            size = CloneflixInputFieldSize.LARGE
        )

        Spacer(modifier = Modifier.height(dimens.spacingM))

        CloneflixInputField(
            value = focusedField,
            onValueChange = { focusedField = it },
            label = "Focused / Active Typing State (Floating Top Label)",
            size = CloneflixInputFieldSize.LARGE
        )

        Spacer(modifier = Modifier.height(dimens.spacingM))

        CloneflixInputField(
            value = errorField,
            onValueChange = { errorField = it },
            label = "Error State with Validation Message",
            size = CloneflixInputFieldSize.LARGE,
            isError = true,
            errorMessage = "Please enter a valid email address or phone number."
        )

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))

        Text(
            text = "GET STARTED CALLOUT ROW",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixGetStartedRow(
            emailValue = getStartedEmail,
            onEmailChange = { getStartedEmail = it },
            onGetStartedClick = {
                if (getStartedEmail.isBlank() || !getStartedEmail.contains("@")) {
                    Toast.makeText(context, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Get Started: $getStartedEmail", Toast.LENGTH_SHORT).show()
                }
            }
        )

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))

        Text(
            text = "SIGN-IN COMPOUND FORM",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixSignInCard(
            onSignInClick = { email, _ ->
                Toast.makeText(context, "Signing in with: $email", Toast.LENGTH_SHORT).show()
            },
            onUseCodeClick = {
                Toast.makeText(context, "Sign-in Code requested", Toast.LENGTH_SHORT).show()
            },
            onForgotPasswordClick = {
                Toast.makeText(context, "Forgot Password clicked", Toast.LENGTH_SHORT).show()
            },
            onSignUpClick = {
                Toast.makeText(context, "Sign Up clicked", Toast.LENGTH_SHORT).show()
            }
        )

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))
    }
}

@Preview(name = "Phone Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.PHONE)
@Preview(name = "TV Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.TV_720p)
@Composable
private fun CloneflixInputFieldsScreenPreview() {
    CloneflixTheme {
        CloneflixInputFieldsComposeScreen()
    }
}
