package com.lagradost.cloudstream3.tv.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.lagradost.cloudstream3.tv.model.TvSettingOption

/**
 * TV list-choice dialog — Back cancels; CENTER on a row confirms that option.
 * Initial focus on currently selected option when possible.
 */
@Composable
fun TvEnumDialog(
    title: String,
    options: List<TvSettingOption>,
    selectedKey: String?,
    onSelect: (TvSettingOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialIndex = options.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val focusRequesters = remember(options.size) {
        List(options.size) { FocusRequester() }
    }
    LaunchedEffect(options, selectedKey) {
        focusRequesters.getOrNull(initialIndex)?.let { runCatching { it.requestFocus() } }
    }
    BackHandler(onBack = onDismiss)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 560.dp)
                .heightIn(max = 520.dp)
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Back cancels · Select confirms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(options, key = { index, o -> o.key.ifEmpty { "empty-$index" } }) { index, option ->
                        val selected = option.key == selectedKey
                        Surface(
                            onClick = { onSelect(option) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    focusRequesters.getOrNull(index)?.let {
                                        Modifier.focusRequester(it)
                                    } ?: Modifier,
                                ),
                            scale = ClickableSurfaceDefaults.scale(
                                focusedScale = TvFocusScale.ButtonFocused,
                            ),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                                focusedContainerColor = MaterialTheme.colorScheme.primary.copy(
                                    alpha = 0.35f,
                                ),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                focusedContentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Text(
                                text = if (selected) "●  ${option.label}" else "○  ${option.label}",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
