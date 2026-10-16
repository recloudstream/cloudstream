package com.lagradost.cloudstream4.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Android app's buttons and chips, from styles.xml. Material's own buttons are pills in the
 * primary colour; the app's are square-cornered, white for the main action and dark for the others.
 */
object AppShapes {
    /** rounded_button_radius in dimens.xml */
    val button = RoundedCornerShape(4.dp)

    /** dialog__window_background.xml */
    val dialog = RoundedCornerShape(10.dp)
}

/** WhiteButton in styles.xml: the main action, such as Play */
@Composable
fun WhiteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = NiceButton(
    onClick, modifier, enabled, contentPadding,
    container = MaterialTheme.colorScheme.onBackground,
    // iconGrayBackground
    contentColor = MaterialTheme.colorScheme.surface,
    content = content,
)

/** BlackButton in styles.xml: every other action, such as Download */
@Composable
fun BlackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = NiceButton(
    onClick, modifier, enabled, contentPadding,
    // iconGrayBackground
    container = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onBackground,
    content = content,
)

/** NiceButton in styles.xml: 40 dp high, no shadow, bold 15 sp text */
@Composable
private fun NiceButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    contentPadding: PaddingValues,
    container: Color,
    contentColor: Color,
    content: @Composable RowScope.() -> Unit,
) = Button(
    onClick = onClick,
    modifier = modifier.defaultMinSize(minHeight = 40.dp),
    enabled = enabled,
    shape = AppShapes.button,
    colors = ButtonDefaults.buttonColors(
        containerColor = container,
        contentColor = contentColor,
        disabledContainerColor = container.copy(alpha = 0.38f),
        disabledContentColor = contentColor.copy(alpha = 0.6f),
    ),
    elevation = null,
    contentPadding = contentPadding,
) {
    ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp)) {
        content()
    }
}

/** ChipFilled in styles.xml: grey without an outline, and the primary colour once selected */
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            // primaryGrayBackground
            containerColor = colors.surfaceVariant,
            labelColor = colors.onBackground,
            iconColor = colors.onBackground,
            selectedContainerColor = colors.primary,
            selectedLabelColor = colors.onPrimary,
            selectedLeadingIconColor = colors.onPrimary,
            selectedTrailingIconColor = colors.onPrimary,
        ),
    )
}

/**
 * Switches and sliders in the theme's colours, flat as in streaming apps: Material's own have an
 * outline round an off switch, and dots along a slider for every step.
 */
object AppControls {
    @Composable
    fun switchColors(): SwitchColors {
        val colors = MaterialTheme.colorScheme
        return SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = colors.primary,
            checkedBorderColor = Color.Transparent,
            checkedIconColor = colors.primary,
            // iconGrayBackground, a step off the page
            uncheckedThumbColor = colors.onSurfaceVariant,
            uncheckedTrackColor = colors.surface,
            uncheckedBorderColor = Color.Transparent,
        )
    }

    @Composable
    fun sliderColors(): SliderColors {
        val colors = MaterialTheme.colorScheme
        return SliderDefaults.colors(
            thumbColor = colors.primary,
            activeTrackColor = colors.primary,
            inactiveTrackColor = colors.surface,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        )
    }
}
