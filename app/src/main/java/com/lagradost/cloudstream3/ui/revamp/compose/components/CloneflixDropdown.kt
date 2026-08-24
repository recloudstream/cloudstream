package com.lagradost.cloudstream3.ui.revamp.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryBlack
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite

enum class CloneflixDropdownVariant {
    DEFAULT,
    OUTLINE,
    COMPACT
}

@Composable
fun CloneflixDropdown(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    variant: CloneflixDropdownVariant = CloneflixDropdownVariant.DEFAULT,
    width: Dp? = null,
    enabled: Boolean = true
) {
    val colors = CloneflixTheme.colors
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens
    val shapes = CloneflixTheme.shapes

    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "DropdownChevronRotation"
    )

    val borderStroke = when {
        isFocused || expanded -> BorderStroke(2.dp, PrimaryWhite)
        variant == CloneflixDropdownVariant.OUTLINE -> BorderStroke(1.dp, PrimaryWhite)
        else -> BorderStroke(1.dp, colors.border)
    }

    val backgroundColor = when {
        expanded -> Color(0xFF2A2A2A)
        isFocused -> Color(0xFF222222)
        else -> PrimaryBlack
    }

    val height = when (variant) {
        CloneflixDropdownVariant.COMPACT -> 32.dp
        else -> 40.dp
    }

    val widthModifier = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()

    Column(modifier = modifier) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = typography.regularCaption1,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = dimens.spacingXs)
            )
        }

        Box(modifier = widthModifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(shapes.extraSmall)
                    .background(backgroundColor)
                    .border(borderStroke, shapes.extraSmall)
                    .clickable(
                        enabled = enabled,
                        role = Role.DropdownList,
                        onClick = { expanded = !expanded }
                    )
                    .focusable(interactionSource = interactionSource)
                    .padding(horizontal = dimens.spacingM, vertical = dimens.spacingS)
                    .semantics {
                        role = Role.DropdownList
                        contentDescription = "$label: $selectedOption, ${if (expanded) "Expanded" else "Collapsed"}"
                    }
            ) {
                Text(
                    text = selectedOption,
                    style = typography.regularBody,
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )

                Spacer(modifier = Modifier.width(dimens.spacingS))

                Icon(
                    painter = painterResource(id = R.drawable.cloneflix_ic_dropdown),
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotationAngle)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .wrapContentHeight()
                    .heightIn(max = 280.dp)
                    .background(Color(0xE6000000))
                    .border(BorderStroke(1.dp, PrimaryWhite), shapes.extraSmall)
            ) {
                options.forEach { option ->
                    val isSelected = option == selectedOption
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                style = typography.regularBody,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.primary else colors.textPrimary
                            )
                        },
                        trailingIcon = if (isSelected) {
                            {
                                Icon(
                                    painter = painterResource(id = R.drawable.cloneflix_ic_check),
                                    contentDescription = "Selected",
                                    tint = colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = colors.textPrimary,
                            trailingIconColor = colors.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CloneflixDropdownStaticShowcase(
    title: String,
    selectedOption: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    isHoverState: Boolean = false,
    isExpandedState: Boolean = false,
    onOptionSelected: (String) -> Unit = {}
) {
    val colors = CloneflixTheme.colors
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens
    val shapes = CloneflixTheme.shapes

    val borderStroke = when {
        isExpandedState || isHoverState -> BorderStroke(1.dp, PrimaryWhite)
        else -> BorderStroke(1.dp, colors.border)
    }

    val backgroundColor = when {
        isExpandedState -> Color(0xFF2A2A2A)
        isHoverState -> Color(0xFF222222)
        else -> PrimaryBlack
    }

    Column(modifier = modifier) {
        Text(
            text = title,
            style = typography.mediumCaption1,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = dimens.spacingXs)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(shapes.extraSmall)
                .background(backgroundColor)
                .border(borderStroke, shapes.extraSmall)
                .padding(horizontal = dimens.spacingM, vertical = dimens.spacingS)
        ) {
            Text(
                text = selectedOption,
                style = typography.regularBody,
                fontSize = 14.sp,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )

            Icon(
                painter = painterResource(id = R.drawable.cloneflix_ic_dropdown),
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(if (isExpandedState) 180f else 0f)
            )
        }

        if (isExpandedState) {
            Spacer(modifier = Modifier.height(2.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shapes.extraSmall)
                    .border(BorderStroke(1.dp, colors.border), shapes.extraSmall),
                color = Color(0xE6000000)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(dimens.spacingS)
                ) {
                    options.forEach { option ->
                        val isSelected = option == selectedOption
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shapes.extraSmall)
                                .clickable { onOptionSelected(option) }
                                .padding(horizontal = dimens.spacingS, vertical = 6.dp)
                        ) {
                            Text(
                                text = option,
                                style = typography.regularBody,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.primary else colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    painter = painterResource(id = R.drawable.cloneflix_ic_check),
                                    contentDescription = "Selected",
                                    tint = colors.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
