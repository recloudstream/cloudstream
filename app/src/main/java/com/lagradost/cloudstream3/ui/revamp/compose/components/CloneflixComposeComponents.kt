package com.lagradost.cloudstream3.ui.revamp.compose.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixFontFamily
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey50
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey800
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey850
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryBlack
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryRed
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Red100
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Red300
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentWhite20
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixColors

enum class CloneflixButtonVariant {
    PRIMARY,
    SECONDARY,
    DARK_SECONDARY,
    MORE_INFO,
    OUTLINE,
    GHOST
}

enum class CloneflixButtonSize {
    LARGE,
    MEDIUM,
    SMALL
}

@Composable
fun CloneflixButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: CloneflixButtonVariant = CloneflixButtonVariant.PRIMARY,
    size: CloneflixButtonSize = CloneflixButtonSize.MEDIUM,
    icon: Painter? = null,
    enabled: Boolean = true
) {
    val dimens = CloneflixTheme.dimens
    val colors = CloneflixTheme.colors

    val height = when (size) {
        CloneflixButtonSize.LARGE -> dimens.buttonHeightLarge
        CloneflixButtonSize.MEDIUM -> dimens.buttonHeightMedium
        CloneflixButtonSize.SMALL -> dimens.buttonHeightSmall
    }

    val textSize = when (size) {
        CloneflixButtonSize.LARGE -> 18.sp
        CloneflixButtonSize.MEDIUM -> 16.sp
        CloneflixButtonSize.SMALL -> 14.sp
    }

    val contentPadding = when (size) {
        CloneflixButtonSize.LARGE -> PaddingValues(horizontal = dimens.spacing2Xl, vertical = dimens.spacingM)
        CloneflixButtonSize.MEDIUM -> PaddingValues(horizontal = dimens.spacingL, vertical = dimens.spacingS)
        CloneflixButtonSize.SMALL -> PaddingValues(horizontal = dimens.spacingM, vertical = dimens.spacingXs)
    }

    val containerColor = when (variant) {
        CloneflixButtonVariant.PRIMARY -> colors.primary
        CloneflixButtonVariant.SECONDARY -> PrimaryWhite
        CloneflixButtonVariant.DARK_SECONDARY -> TransparentWhite20
        CloneflixButtonVariant.MORE_INFO -> Color(0xB3333333)
        CloneflixButtonVariant.OUTLINE -> Color.Transparent
        CloneflixButtonVariant.GHOST -> Color.Transparent
    }

    val contentColor = when (variant) {
        CloneflixButtonVariant.PRIMARY -> colors.onPrimary
        CloneflixButtonVariant.SECONDARY -> PrimaryBlack
        CloneflixButtonVariant.DARK_SECONDARY -> PrimaryWhite
        CloneflixButtonVariant.MORE_INFO -> PrimaryWhite
        CloneflixButtonVariant.OUTLINE -> colors.textPrimary
        CloneflixButtonVariant.GHOST -> colors.textPrimary
    }

    val border = when (variant) {
        CloneflixButtonVariant.OUTLINE -> BorderStroke(1.dp, Grey200)
        else -> null
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val activeBorder = if (isFocused) {
        BorderStroke(2.dp, PrimaryWhite)
    } else {
        border
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .height(height)
            .semantics { role = Role.Button },
        shape = CloneflixTheme.shapes.extraSmall,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        border = activeBorder,
        contentPadding = contentPadding
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(dimens.spacingS))
        }
        Text(
            text = text,
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
fun CloneflixCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true
) {
    val colors = CloneflixTheme.colors
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val activeBorder = if (isFocused) BorderStroke(2.dp, PrimaryWhite) else null

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CloneflixTheme.shapes.extraSmall)
            .then(if (activeBorder != null) Modifier.border(activeBorder, CloneflixTheme.shapes.extraSmall) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) }
            )
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .padding(vertical = dimens.spacingXs, horizontal = dimens.spacingXs)
            .semantics { role = Role.Checkbox }
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = PrimaryWhite,
                checkmarkColor = PrimaryBlack,
                uncheckedColor = Grey200,
                disabledCheckedColor = PrimaryWhite.copy(alpha = 0.5f),
                disabledUncheckedColor = Grey200.copy(alpha = 0.5f)
            ),
            modifier = Modifier.size(20.dp)
        )

        if (!label.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(dimens.spacingS))
            Text(
                text = label,
                style = typography.regularBody,
                color = if (isFocused) PrimaryWhite else colors.textPrimary
            )
        }
    }
}

enum class CloneflixCardElevation {
    SURFACE,
    ELEVATED
}

@Composable
fun CloneflixCard(
    modifier: Modifier = Modifier,
    elevation: CloneflixCardElevation = CloneflixCardElevation.SURFACE,
    content: @Composable () -> Unit
) {
    val colors = CloneflixTheme.colors
    val shapes = CloneflixTheme.shapes
    val bgColor = if (elevation == CloneflixCardElevation.SURFACE) colors.surface else colors.surfaceElevated

    Surface(
        modifier = modifier.clip(shapes.large),
        color = bgColor,
        shape = shapes.large,
        border = BorderStroke(1.dp, colors.border),
        content = content
    )
}

@Composable
fun CloneflixHeader(
    title: String,
    subtitle: String? = null,
    iconRes: Int = R.drawable.cloneflix_ic_about,
    modifier: Modifier = Modifier
) {
    val dimens = CloneflixTheme.dimens
    val colors = CloneflixTheme.colors
    val typography = CloneflixTheme.typography

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimens.spacing2Xl)
        ) {
            Box(
                modifier = Modifier
                    .size(dimens.headerIconSize)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(dimens.headerIconStroke, PrimaryWhite, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = "$title Icon",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.width(dimens.spacing2Xl))

            Column {
                Text(
                    text = title,
                    style = typography.headerDisplay,
                    color = colors.textPrimary
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(dimens.spacingXs))
                    Text(
                        text = subtitle,
                        style = typography.regularSmallBody,
                        color = colors.textSecondary
                    )
                }
            }
        }

        CloneflixDivider()
    }
}

@Composable
fun CloneflixDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = CloneflixTheme.dimens.dividerThickness,
    color: Color = CloneflixTheme.colors.divider
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color)
    )
}

@Composable
fun CloneflixColorSwatch(
    token: CloneflixColors.ColorToken,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = CloneflixTheme.colors
    val dimens = CloneflixTheme.dimens
    val typography = CloneflixTheme.typography

    var isFocused by remember { mutableStateOf(false) }
    val activeBorder = if (isFocused) {
        BorderStroke(2.dp, PrimaryWhite)
    } else {
        BorderStroke(1.dp, colors.border)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CloneflixTheme.shapes.large)
            .background(colors.surface)
            .border(activeBorder, CloneflixTheme.shapes.large)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                onClick = onClick,
                role = Role.Button
            )
            .focusable()
            .padding(dimens.spacingL)
            .semantics {
                contentDescription = "${token.name}, Hex: ${token.hex}, ${token.rgbDescription}"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(dimens.swatchSize)
                .clip(CloneflixTheme.shapes.small)
                .background(Color(token.colorInt))
                .border(1.dp, colors.border, CloneflixTheme.shapes.small)
        )

        Spacer(modifier = Modifier.width(dimens.spacingL))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = token.name,
                style = typography.mediumBody,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(dimens.spacingXxs))
            Text(
                text = "${token.hex}  •  ${token.rgbDescription}",
                style = typography.regularCaption1,
                color = colors.textSecondary
            )
        }

        Icon(
            painter = painterResource(id = R.drawable.cloneflix_ic_copy),
            contentDescription = "Copy Color Hex",
            tint = colors.textSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

enum class CloneflixInputFieldSize {
    LARGE,
    MEDIUM
}

enum class CloneflixInputFieldState {
    DEFAULT,
    FOCUSED,
    ERROR
}

@Composable
fun CloneflixInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    size: CloneflixInputFieldSize = CloneflixInputFieldSize.LARGE,
    isError: Boolean = false,
    errorMessage: String? = null,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens

    var isFocused by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(!isPassword) }

    val hasFloating = isFocused || value.isNotEmpty()

    val containerHeight = when (size) {
        CloneflixInputFieldSize.LARGE -> 56.dp
        CloneflixInputFieldSize.MEDIUM -> 48.dp
    }

    val labelOffsetY by animateDpAsState(
        targetValue = if (hasFloating) 7.dp else if (size == CloneflixInputFieldSize.LARGE) 16.dp else 12.dp,
        animationSpec = tween(durationMillis = 150),
        label = "labelOffsetY"
    )

    val labelFontSize by animateFloatAsState(
        targetValue = if (hasFloating) 12f else 16f,
        animationSpec = tween(durationMillis = 150),
        label = "labelFontSize"
    )

    val labelColor by animateColorAsState(
        targetValue = when {
            isError -> Red100
            isFocused -> Grey50
            else -> Grey50
        },
        animationSpec = tween(durationMillis = 150),
        label = "labelColor"
    )

    val borderColor = when {
        isError -> Red100
        isFocused -> PrimaryWhite
        else -> Grey200
    }

    val borderWidth = if (isFocused) 2.dp else 1.dp
    val shape = RoundedCornerShape(4.dp)

    val visualTransformation = if (isPassword && !passwordVisible) {
        PasswordVisualTransformation()
    } else {
        VisualTransformation.None
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(containerHeight)
                .clip(shape)
                .background(Color(0x80000000))
                .border(BorderStroke(borderWidth, borderColor), shape)
                .focusable()
                .onFocusChanged { isFocused = it.isFocused }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(containerHeight)
                ) {
                    Text(
                        text = label,
                        color = labelColor,
                        fontFamily = CloneflixFontFamily,
                        fontWeight = if (hasFloating) FontWeight.Medium else FontWeight.Normal,
                        fontSize = labelFontSize.sp,
                        modifier = Modifier
                            .offset(y = labelOffsetY)
                    )

                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        singleLine = true,
                        visualTransformation = visualTransformation,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        textStyle = TextStyle(
                            fontFamily = CloneflixFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 16.sp,
                            color = PrimaryWhite
                        ),
                        cursorBrush = SolidColor(PrimaryWhite),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (hasFloating) 20.dp else 0.dp)
                            .align(if (hasFloating) Alignment.TopStart else Alignment.CenterStart)
                    )
                }

                if (isPassword) {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (passwordVisible) R.drawable.cloneflix_ic_visibility else R.drawable.cloneflix_ic_visibility_off
                            ),
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = Grey50,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (trailingIcon != null) {
                    trailingIcon()
                }
            }
        }

        if (isError && !errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(dimens.spacingXs))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = dimens.spacingXs)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.cloneflix_ic_circle_error),
                    contentDescription = "Error",
                    tint = Red100,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = errorMessage,
                    style = typography.regularCaption1,
                    color = Red100
                )
            }
        }
    }
}

@Composable
fun CloneflixTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    CloneflixInputField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        isError = isError,
        errorMessage = errorMessage
    )
}

@Composable
fun CloneflixGetStartedRow(
    emailValue: String,
    onEmailChange: (String) -> Unit,
    onGetStartedClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    val dimens = CloneflixTheme.dimens

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        CloneflixInputField(
            value = emailValue,
            onValueChange = onEmailChange,
            label = "Email address",
            size = CloneflixInputFieldSize.LARGE,
            isError = isError,
            errorMessage = errorMessage,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(dimens.spacingS))

        CloneflixButton(
            text = "Get Started",
            onClick = onGetStartedClick,
            variant = CloneflixButtonVariant.PRIMARY,
            size = CloneflixButtonSize.LARGE,
            icon = painterResource(id = R.drawable.cloneflix_ic_chevron_right),
            modifier = Modifier.height(56.dp)
        )
    }
}

@Composable
fun CloneflixSignInCard(
    modifier: Modifier = Modifier,
    initialEmail: String = "",
    onSignInClick: (String, String) -> Unit = { _, _ -> },
    onUseCodeClick: () -> Unit = {},
    onForgotPasswordClick: () -> Unit = {},
    onSignUpClick: () -> Unit = {}
) {
    val typography = CloneflixTheme.typography
    val colors = CloneflixTheme.colors
    val dimens = CloneflixTheme.dimens

    var emailOrPhone by remember { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    CloneflixCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CloneflixCardElevation.ELEVATED
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacing3Xl)
        ) {
            Text(
                text = "Sign In",
                style = typography.boldTitle1,
                color = colors.textPrimary
            )

            Spacer(modifier = Modifier.height(dimens.spacing2Xl))

            CloneflixInputField(
                value = emailOrPhone,
                onValueChange = { emailOrPhone = it },
                label = "Email or phone number",
                size = CloneflixInputFieldSize.LARGE
            )

            Spacer(modifier = Modifier.height(dimens.spacingL))

            CloneflixInputField(
                value = password,
                onValueChange = {
                    password = it
                    if (it.isNotEmpty() && (it.length < 4 || it.length > 60)) {
                        passwordError = "Your password must contain between 4 and 60 characters."
                    } else {
                        passwordError = null
                    }
                },
                label = "Password",
                isPassword = true,
                size = CloneflixInputFieldSize.LARGE,
                isError = passwordError != null,
                errorMessage = passwordError
            )

            Spacer(modifier = Modifier.height(dimens.spacing2Xl))

            CloneflixButton(
                text = "Sign In",
                onClick = {
                    if (password.length < 4 || password.length > 60) {
                        passwordError = "Your password must contain between 4 and 60 characters."
                    } else {
                        onSignInClick(emailOrPhone, password)
                    }
                },
                variant = CloneflixButtonVariant.PRIMARY,
                size = CloneflixButtonSize.LARGE,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(dimens.spacingL))

            Text(
                text = "OR",
                style = typography.regularBody,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(dimens.spacingL))

            Button(
                onClick = onUseCodeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TransparentWhite20,
                    contentColor = PrimaryWhite
                )
            ) {
                Text(
                    text = "Use a Sign-In Code",
                    style = typography.mediumBody,
                    color = PrimaryWhite
                )
            }

            Spacer(modifier = Modifier.height(dimens.spacingL))

            Text(
                text = "Forgot password?",
                style = typography.mediumBody,
                color = PrimaryWhite,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onForgotPasswordClick)
            )

            Spacer(modifier = Modifier.height(dimens.spacingL))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = rememberMe,
                    onCheckedChange = { rememberMe = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = PrimaryWhite,
                        checkmarkColor = PrimaryBlack,
                        uncheckedColor = Grey200
                    )
                )
                Text(
                    text = "Remember me",
                    style = typography.regularCaption1,
                    color = colors.textPrimary,
                    modifier = Modifier.clickable { rememberMe = !rememberMe }
                )
            }

            Spacer(modifier = Modifier.height(dimens.spacingL))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "New to Cloneflix? ",
                    style = typography.regularBody,
                    color = colors.textSecondary
                )
                Text(
                    text = "Sign up now.",
                    style = typography.mediumBody,
                    color = PrimaryWhite,
                    modifier = Modifier.clickable(onClick = onSignUpClick)
                )
            }
        }
    }
}

@Preview(name = "Cloneflix Buttons Preview", showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun CloneflixButtonsPreview() {
    CloneflixTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            CloneflixButton(text = "Primary Button", onClick = {}, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            CloneflixButton(text = "Secondary Button", onClick = {}, variant = CloneflixButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            CloneflixButton(text = "Outline Button", onClick = {}, variant = CloneflixButtonVariant.OUTLINE, modifier = Modifier.fillMaxWidth())
        }
    }
}

