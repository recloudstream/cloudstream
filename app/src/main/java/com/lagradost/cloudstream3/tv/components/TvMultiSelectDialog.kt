package com.lagradost.cloudstream3.tv.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.lagradost.cloudstream3.tv.model.TvLanguageOption

/**
 * TV multi-select dialog — D-pad / CENTER toggles rows in a temp set;
 * Apply commits once; Back / Cancel discards (never partial-writes).
 * Empty Apply asks for confirm (download-none is a real but ambiguous choice).
 * Lazy list for large language catalogs (Phase 2 focus).
 */
@Composable
fun TvMultiSelectDialog(
    title: String,
    options: List<TvLanguageOption>,
    initiallySelected: Set<String>,
    onApply: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    emptyConfirmTitle: String = "Download no subtitle languages?",
    emptyConfirmMessage: String =
        "An empty selection means downloads will not fetch any subtitles " +
            "(same as phone). Confirm to save empty, or cancel to keep editing.",
    modifier: Modifier = Modifier,
) {
    var selected by remember(options, initiallySelected) {
        mutableStateOf(initiallySelected.toMutableSet())
    }
    var showEmptyConfirm by remember { mutableStateOf(false) }

    val firstSelectedIndex = remember(options, initiallySelected) {
        options.indexOfFirst { it.value in initiallySelected }.coerceAtLeast(0)
    }
    val listFocus = remember { FocusRequester() }

    LaunchedEffect(options) {
        runCatching { listFocus.requestFocus() }
    }

    fun orderedSelection(): List<String> {
        val ordered = options
            .filter { it.value in selected }
            .map { it.value }
        val extras = selected.filter { value -> options.none { it.value == value } }
        return ordered + extras
    }

    fun attemptApply() {
        val result = orderedSelection()
        if (result.isEmpty()) {
            showEmptyConfirm = true
            return
        }
        onApply(result)
    }

    if (!showEmptyConfirm) {
        BackHandler(onBack = onDismiss)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 400.dp, max = 640.dp)
                .heightIn(max = 560.dp)
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
                    text = "CENTER toggles · Clear selected · Apply saves · Back cancels",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${selected.size} selected",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                ) {
                    itemsIndexed(
                        options,
                        key = { index, o -> o.value.ifEmpty { "empty-$index" } },
                    ) { index, option ->
                        val isSelected = option.value in selected
                        Surface(
                            onClick = {
                                selected = selected.toMutableSet().also { set ->
                                    if (isSelected) {
                                        set.remove(option.value)
                                    } else {
                                        set.add(option.value)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (index == firstSelectedIndex) {
                                        Modifier.focusRequester(listFocus)
                                    } else {
                                        Modifier
                                    },
                                ),
                            scale = ClickableSurfaceDefaults.scale(
                                focusedScale = TvFocusScale.ButtonFocused,
                            ),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) {
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (isSelected) {
                                        "☑  ${option.displayName}"
                                    } else {
                                        "☐  ${option.displayName}"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (option.isUnknown) {
                                    Text(
                                        text = "Current",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onDismiss,
                        scale = ButtonDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { selected = mutableSetOf() },
                        enabled = selected.isNotEmpty(),
                        scale = ButtonDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
                    ) {
                        Text("Clear selected")
                    }
                    Button(
                        onClick = { attemptApply() },
                        scale = ButtonDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
                    ) {
                        Text("Apply")
                    }
                }
            }
        }

        if (showEmptyConfirm) {
            TvConfirmDialog(
                title = emptyConfirmTitle,
                message = emptyConfirmMessage,
                confirmLabel = "Save empty",
                onConfirm = {
                    showEmptyConfirm = false
                    onApply(emptyList())
                },
                onDismiss = { showEmptyConfirm = false },
            )
        }
    }
}
