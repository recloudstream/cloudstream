package com.lagradost.cloudstream4.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.check
import org.jetbrains.compose.resources.painterResource

@Composable
fun WhiteFilterChip(
    selected: Boolean,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val canHaveFocus = LocalFocusOutlineDefault.current
    FilterChip(
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            selectedBorderColor = Color.Transparent,
            disabledSelectedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.onFocusChanged { newFocus ->
            hasFocus = canHaveFocus && newFocus.hasFocus
        },
        selected = selected,
        colors = Colors.whiteChip,
        label = {
            Text(label)
        },
        leadingIcon = {
            AnimatedVisibility(hasFocus) {
                Icon(
                    painter = painterResource(Res.drawable.check),
                    contentDescription = null
                )
            }
        },
        onClick = onClick
    )
}