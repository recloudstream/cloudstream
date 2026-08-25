package com.lagradost.cloudstream3.ui.result.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme

@Composable
private fun SeasonDropdownMenuItem(
    option: String,
    isSelected: Boolean,
    onOptionSelected: (String) -> Unit
) {
    val colors = MovieDetailsTheme.colors
    val optionInteractionSource = remember(option) { MutableInteractionSource() }
    val isOptionFocused by optionInteractionSource.collectIsFocusedAsState()
    val focusAlpha = if (colors.surface.luminance() < 0.5f) 0.22f else 0.12f
    val focusColor = colors.onSurface.copy(alpha = focusAlpha)
    val focusBorder = colors.onSurface.copy(alpha = 0.72f)
    val itemTextColor = if (isSelected) colors.primary else colors.textPrimary

    DropdownMenuItem(
        text = {
            Text(
                text = option,
                color = itemTextColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp
            )
        },
        onClick = {
            onOptionSelected(option)
        },
        modifier = Modifier
            .clip(MovieDetailsTokens.ShapeCardSmall)
            .background(if (isOptionFocused) focusColor else Color.Transparent)
            .then(
                if (isOptionFocused) {
                    Modifier.border(
                        BorderStroke(2.dp, focusBorder),
                        MovieDetailsTokens.ShapeCardSmall
                    )
                } else {
                    Modifier
                }
            ),
        interactionSource = optionInteractionSource,
        colors = MenuDefaults.itemColors(
            textColor = itemTextColor
        )
    )
}

@Composable
fun SeasonDropdown(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = MovieDetailsTheme.colors

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "DropdownScale"
    )

    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "DropdownChevronRotation"
    )

    val borderStroke = when {
        isFocused || expanded -> BorderStroke(2.dp, colors.primary)
        else -> BorderStroke(1.dp, colors.border)
    }

    val backgroundColor = when {
        isFocused || expanded -> colors.surface.copy(alpha = 0.9f)
        else -> colors.surface.copy(alpha = 0.6f)
    }

    val baseModifier = if (width != null) modifier.width(width) else modifier
    val defaultSeasonText = stringResource(id = R.string.select_season)

    Box(
        modifier = baseModifier
            .scale(scale)
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .border(borderStroke, RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.DropdownList,
                onClick = { expanded = !expanded }
            )
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedOption.ifEmpty { defaultSeasonText },
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Icon(
                painter = painterResource(id = R.drawable.ic_baseline_keyboard_arrow_down_24),
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotationAngle)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(colors.surface)
                .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(4.dp))
                .heightIn(max = 280.dp)
        ) {
            options.forEach { option ->
                SeasonDropdownMenuItem(
                    option = option,
                    isSelected = option == selectedOption,
                    onOptionSelected = {
                        onOptionSelected(it)
                        expanded = false
                    }
                )
            }
        }
    }
}
