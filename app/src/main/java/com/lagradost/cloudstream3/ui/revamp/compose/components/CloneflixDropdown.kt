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

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "DropdownScale"
    )

    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "DropdownChevronRotation"
    )

    val borderStroke = when {
        isFocused || expanded -> BorderStroke(2.dp, PrimaryWhite)
        variant == CloneflixDropdownVariant.OUTLINE -> BorderStroke(1.dp, PrimaryWhite)
        else -> BorderStroke(1.dp, Color(0xFF666666))
    }

    val backgroundColor = when {
        isFocused || expanded -> Color(0xFF333333)
        else -> Color(0xFF1E1E1E)
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
                    .scale(scale)
                    .clip(shapes.extraSmall)
                    .background(backgroundColor)
                    .border(borderStroke, shapes.extraSmall)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        role = Role.DropdownList,
                        onClick = { expanded = !expanded }
                    )
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
                    fontWeight = if (isFocused || expanded) FontWeight.Bold else FontWeight.Normal,
                    color = PrimaryWhite,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )

                Spacer(modifier = Modifier.width(dimens.spacingS))

                Icon(
                    painter = painterResource(id = R.drawable.cloneflix_ic_dropdown),
                    contentDescription = null,
                    tint = PrimaryWhite,
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
                    .heightIn(max = 300.dp)
                    .background(Color(0xF0181818))
                    .border(BorderStroke(1.dp, PrimaryWhite), shapes.extraSmall)
            ) {
                options.forEach { option ->
                    val isSelected = option == selectedOption
                    val itemInteractionSource = remember { MutableInteractionSource() }
                    val isItemFocused by itemInteractionSource.collectIsFocusedAsState()

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                style = typography.regularBody,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected || isItemFocused) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.primary else if (isItemFocused) PrimaryWhite else colors.textPrimary
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
                            .height(40.dp)
                            .background(if (isItemFocused) Color(0xFF333333) else Color.Transparent),
                        interactionSource = itemInteractionSource
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
    modifier: Modifier = Modifier,
    options: List<String> = emptyList(),
    variant: CloneflixDropdownVariant = CloneflixDropdownVariant.DEFAULT,
    isHoverState: Boolean = false,
    isExpandedState: Boolean = false,
    width: Dp? = null
) {
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens
    val shapes = CloneflixTheme.shapes

    val borderStroke = when {
        isHoverState || isExpandedState -> BorderStroke(2.dp, PrimaryWhite)
        variant == CloneflixDropdownVariant.OUTLINE -> BorderStroke(1.dp, PrimaryWhite)
        else -> BorderStroke(1.dp, Color(0xFF666666))
    }

    val backgroundColor = when {
        isHoverState || isExpandedState -> Color(0xFF333333)
        else -> Color(0xFF1E1E1E)
    }

    val height = when (variant) {
        CloneflixDropdownVariant.COMPACT -> 32.dp
        else -> 40.dp
    }

    val widthModifier = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()

    Column(modifier = modifier) {
        Text(
            text = title,
            style = typography.boldTitle2,
            fontSize = 16.sp,
            color = PrimaryWhite,
            modifier = Modifier.padding(bottom = dimens.spacingXs)
        )

        Box(modifier = widthModifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(shapes.extraSmall)
                    .background(backgroundColor)
                    .border(borderStroke, shapes.extraSmall)
                    .padding(horizontal = dimens.spacingM, vertical = dimens.spacingS)
            ) {
                Text(
                    text = selectedOption,
                    style = typography.regularBody,
                    fontSize = 14.sp,
                    fontWeight = if (isHoverState || isExpandedState) FontWeight.Bold else FontWeight.Normal,
                    color = PrimaryWhite,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )

                Spacer(modifier = Modifier.width(dimens.spacingS))

                Icon(
                    painter = painterResource(id = R.drawable.cloneflix_ic_dropdown),
                    contentDescription = null,
                    tint = PrimaryWhite,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(if (isExpandedState) 180f else 0f)
                )
            }
        }
    }
}
