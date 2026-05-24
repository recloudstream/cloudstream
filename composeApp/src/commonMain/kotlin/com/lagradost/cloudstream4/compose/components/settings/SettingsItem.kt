package com.lagradost.cloudstream4.compose.components.settings

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.compose.components.cloudStreamRipple
import com.lagradost.cloudstream4.compose.components.tvFocusable
import com.lagradost.cloudstream4.compose.theme.CloudStreamTheme
import com.lagradost.cloudstream4.utils.DeviceLayout

@Composable
fun SettingsItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val colors = CloudStreamTheme.colors
    val isTV = remember { DeviceLayout.isLayout(DeviceLayout.TV) }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusable(
                isTV = isTV,
                onClick = onClick,
                focusRequester = focusRequester,
                interactionSource = interactionSource,
            )
            .cloudStreamRipple(interactionSource)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onBackground,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(24.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.onBackground,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = colors.onBackground.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
