package com.lagradost.cloudstream3.tv.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.lagradost.cloudstream3.tv.components.TvConfirmDialog
import com.lagradost.cloudstream3.tv.components.TvEnumDialog
import com.lagradost.cloudstream3.tv.components.TvFocusScale
import com.lagradost.cloudstream3.tv.components.TvOnResume
import com.lagradost.cloudstream3.tv.model.TvSettingControlKind
import com.lagradost.cloudstream3.tv.model.TvSettingItem
import com.lagradost.cloudstream3.tv.model.TvSettingsAction
import com.lagradost.cloudstream3.tv.model.TvSettingsCatalog
import com.lagradost.cloudstream3.tv.model.TvSettingsUiState

/**
 * Phase 11 — small useful TV Settings over EXISTING [com.lagradost.cloudstream4.AppSettings].
 * Same PreferenceManager store as phone; no parallel prefs / new DataStore keys.
 */
@Composable
fun TvSettingsScreen(
    modifier: Modifier = Modifier,
    onOpenFocusProbe: () -> Unit = {},
    viewModel: TvSettingsViewModel = viewModel(),
) {
    val uiState by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    var enumTarget by remember { mutableStateOf<TvSettingItem?>(null) }
    var pendingRestart by remember { mutableStateOf<PendingRestart?>(null) }
    val firstRowFocus = remember { FocusRequester() }
    var sideEffectTick by remember { mutableStateOf(0) }

    TvOnResume { viewModel.onAction(TvSettingsAction.Refresh) }
    LaunchedEffect(Unit) { viewModel.onAction(TvSettingsAction.Refresh) }

    LaunchedEffect(uiState, sideEffectTick) {
        when (val effect = viewModel.consumeSideEffect()) {
            TvSettingsViewModel.SideEffect.RecreateActivity -> {
                // Same as phone locale path — activity.recreate(); no custom restart system.
                activity?.recreate()
            }
            TvSettingsViewModel.SideEffect.OpenFocusProbe -> onOpenFocusProbe()
            null -> Unit
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            TvSettingsUiState.Loading -> {
                Text(
                    text = "Loading settings…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(48.dp),
                )
            }
            is TvSettingsUiState.Ready -> {
                SettingsList(
                    catalog = state.catalog,
                    firstRowFocus = firstRowFocus,
                    onToggle = { item ->
                        viewModel.onAction(TvSettingsAction.ToggleBoolean(item.id))
                    },
                    onOpenEnum = { item -> enumTarget = item },
                    onAction = { item ->
                        viewModel.onAction(TvSettingsAction.InvokeAction(item.id))
                        sideEffectTick += 1
                    },
                )
                LaunchedEffect(state.catalog.sections.firstOrNull()?.items?.firstOrNull()?.id) {
                    runCatching { firstRowFocus.requestFocus() }
                }
            }
        }

        val target = enumTarget
        if (target != null && target.control == TvSettingControlKind.Enum) {
            TvEnumDialog(
                title = target.title,
                options = target.options,
                selectedKey = target.selectedOptionKey,
                onSelect = { option ->
                    enumTarget = null
                    if (target.requiresRestart) {
                        pendingRestart = PendingRestart(target, option.key)
                    } else {
                        viewModel.onAction(TvSettingsAction.SelectEnum(target.id, option.key))
                        sideEffectTick += 1
                    }
                },
                onDismiss = { enumTarget = null },
            )
        }

        val restart = pendingRestart
        if (restart != null) {
            TvConfirmDialog(
                title = "Restart required",
                message = restart.item.restartMessage
                    ?: "This setting is saved now but applies after the activity restarts.",
                confirmLabel = "Save & restart",
                onConfirm = {
                    val item = restart.item
                    val key = restart.optionKey
                    pendingRestart = null
                    viewModel.onAction(TvSettingsAction.SelectEnum(item.id, key))
                    sideEffectTick += 1
                },
                onDismiss = { pendingRestart = null },
            )
        }
    }
}

private data class PendingRestart(
    val item: TvSettingItem,
    val optionKey: String,
)

@Composable
private fun SettingsList(
    catalog: TvSettingsCatalog,
    firstRowFocus: FocusRequester,
    onToggle: (TvSettingItem) -> Unit,
    onOpenEnum: (TvSettingItem) -> Unit,
    onAction: (TvSettingItem) -> Unit,
) {
    val flatRows = remember(catalog) {
        buildList {
            catalog.accountDisplayName?.let { name ->
                add(ListRow.Header("Settings  ·  $name"))
            } ?: add(ListRow.Header("Settings"))
            catalog.sections.forEach { section ->
                add(ListRow.Header(section.category.label))
                section.items.forEach { add(ListRow.Item(it)) }
            }
        }
    }
    val firstItemIndex = remember(flatRows) {
        flatRows.indexOfFirst { it is ListRow.Item }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(
            flatRows,
            key = { index, row ->
                when (row) {
                    is ListRow.Header -> "h-$index-${row.title}"
                    is ListRow.Item -> row.item.id
                }
            },
        ) { index, row ->
            when (row) {
                is ListRow.Header -> {
                    Text(
                        text = row.title,
                        style = if (row.title.startsWith("Settings")) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
                    )
                }
                is ListRow.Item -> {
                    SettingRow(
                        item = row.item,
                        onClick = {
                            when (row.item.control) {
                                TvSettingControlKind.Boolean -> onToggle(row.item)
                                TvSettingControlKind.Enum -> onOpenEnum(row.item)
                                TvSettingControlKind.Action -> onAction(row.item)
                                TvSettingControlKind.ReadOnly -> Unit
                            }
                        },
                        modifier = if (index == firstItemIndex) {
                            Modifier.focusRequester(firstRowFocus)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

private sealed interface ListRow {
    data class Header(val title: String) : ListRow
    data class Item(val item: TvSettingItem) : ListRow
}

@Composable
private fun SettingRow(
    item: TvSettingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = item.control != TvSettingControlKind.ReadOnly
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = buildString {
                    item.summary?.let { append(it) }
                    if (item.requiresRestart) {
                        if (isNotEmpty()) append(" · ")
                        append("Restart required")
                    }
                }
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = when (item.control) {
                    TvSettingControlKind.Boolean -> if (item.booleanValue) "On" else "Off"
                    TvSettingControlKind.Enum -> item.valueLabel.orEmpty()
                    TvSettingControlKind.Action -> item.valueLabel ?: "Open"
                    TvSettingControlKind.ReadOnly -> item.valueLabel.orEmpty()
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
