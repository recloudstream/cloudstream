package com.lagradost.cloudstream4.compose

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.check
import com.lagradost.cloudstream4.theme.CloudStreamTheme.colors
import com.mihon.material.padding
import org.jetbrains.compose.resources.painterResource

@Composable
fun ActionDialog(
    title: String,
    text: String,
    icon: Painter? = null,

    confirmText: String,
    dismissText: String,

    dismiss: () -> Unit,
    confirm: () -> Unit
) {
    AlertDialog(
        containerColor = colors.background,
        onDismissRequest = dismiss,
        title = {
            if (icon == null) {
                Text(text = title)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = icon, modifier = Modifier.size(36.dp), contentDescription = null)
                    Spacer(modifier = Modifier.size(MaterialTheme.padding.medium))
                    Text(text = title)
                }
            }
        },
        text = { Text(text = text) },
        confirmButton = {
            Button(
                onClick = confirm,
                colors = Colors.whiteButton
            ) { Text(text = confirmText) }
        },
        dismissButton = {
            Button(
                onClick = dismiss, colors = Colors.blackButton
            ) { Text(text = dismissText) }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleSelectDialog(
    modifier: Modifier = Modifier,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = colors.background,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    icon: @Composable (() -> Unit)? = null,
    title: String? = null,
    properties: DialogProperties = DialogProperties(),
    /* visual */
    entries: List<String>,
    /* Selected keys used in entries */
    selectedIndex: Int,
    confirmText: String? = null,
    dismissText: String? = null,
    dismiss: () -> Unit,
    confirm: (Int) -> Unit
) {
    SingleSelectDialog(
        modifier = modifier,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        tonalElevation = tonalElevation,
        icon = icon,
        title = title,
        properties = properties,
        entries = entries.mapIndexed { index, string -> index to string }.associate { it },
        selectedKey = selectedIndex,
        confirmText = confirmText,
        dismissText = dismissText,
        dismiss = dismiss,
        confirm = confirm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SingleSelectDialog(
    modifier: Modifier = Modifier,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = colors.background,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    icon: @Composable (() -> Unit)? = null,
    title: String? = null,
    properties: DialogProperties = DialogProperties(),
    /* key - visual */
    entries: Map<out T, String>,
    /* Selected keys used in entries */
    selectedKey: T,
    confirmText: String? = null,
    dismissText: String? = null,
    dismiss: () -> Unit,
    confirm: (T) -> Unit,
    iconProvider: (@Composable (key: T, value: String) -> Unit)? = null
) {
    var selected by remember { mutableStateOf(selectedKey) }
    val selectedPainter = painterResource(Res.drawable.check)

    AlertDialog(
        properties = properties,
        tonalElevation = tonalElevation,
        modifier = modifier,
        icon = icon,
        shape = shape,
        iconContentColor = iconContentColor,
        containerColor = containerColor,
        onDismissRequest = dismiss,
        title = title?.let { { Text(text = it) } },
        text = {
            LazyColumn {
                entries.forEach { (key, value) ->
                    item(key = key) {
                        val isSelected = selected == key
                        SingleSelectionItem(isSelected, key, value, iconProvider, selectedPainter) {
                            if (confirmText == null) {
                                confirm(key)
                            } else {
                                selected = key
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (confirmText != null) {
                Button(
                    onClick = {
                        confirm(selected)
                    },
                    colors = Colors.whiteButton
                ) { Text(text = confirmText) }
            }
        },
        dismissButton = {
            if (dismissText != null) {
                Button(
                    onClick = dismiss, colors = Colors.blackButton
                ) { Text(text = dismissText) }
            }
        }
    )
}

/*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SinglePairSelectDialog(
    modifier: Modifier = Modifier,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = colors.background,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    icon: @Composable (() -> Unit)? = null,
    title: String? = null,
    properties: DialogProperties = DialogProperties(),
    /* key - visual */
    entries: Map<Pair<T, T>, String>,
    /* Selected keys used in entries */
    selectedKey: T,
    confirmText: String? = null,
    dismissText: String? = null,
    dismiss: () -> Unit,
    confirm: (T) -> Unit,
    iconProvider: (@Composable (key: T, value: String) -> Unit)? = null
) {
    val selected = remember { mutableStateOf(selectedKey) }

    AlertDialog(
        properties = properties,
        tonalElevation = tonalElevation,
        modifier = modifier,
        icon = icon,
        shape = shape,
        iconContentColor = iconContentColor,
        containerColor = containerColor,
        onDismissRequest = dismiss,
        title = title?.let { { Text(text = it) } },
        text = {
            LazyColumn {
                entries.forEach { (keyPair, value) ->
                    item(key = keyPair) {
                        val (a, b) = keyPair
                        val isSelected = selected.value == a || selected.value == b

                        val painter = if (a == b)  {
                            R.drawable.ic_baseline_check_24_listview
                        } else if(selected.value == a) {
                            R.drawable.ic_baseline_arrow_downward_24
                        } else {
                            R.drawable.ic_baseline_arrow_upward_24
                        }

                        val nextKey = if (selected.value != a) a else b
                        SingleSelectionItem(
                            isSelected = isSelected,
                            key = nextKey,
                            text = value,
                            iconProvider = iconProvider,
                            selectIcon = painter
                        ) {
                            if (confirmText == null) {
                                confirm(nextKey)
                            } else {
                                selected.value = nextKey
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (confirmText != null) {
                Button(
                    onClick = {
                        confirm(selected.value)
                    },
                    colors = Colors.whiteButton
                ) { Text(text = confirmText) }
            }
        },
        dismissButton = {
            if (dismissText != null) {
                Button(
                    onClick = dismiss, colors = Colors.blackButton
                ) { Text(text = dismissText) }
            }
        }
    )
}*/

@Composable
fun <T> SingleSelectionItem(
    isSelected: Boolean,
    key: T,
    text: String,
    iconProvider: (@Composable (key: T, value: String) -> Unit)? = null,
    selectedPainter: Painter,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                }
            )
            .rounded()
            .ripple(interactionSource),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Icon(
                painter = selectedPainter,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = colors.onBackground
            )
            iconProvider?.invoke(key, text)
            Text(
                text,
                color = colors.onBackground,
                modifier = Modifier.padding(15.dp)
            )
        } else {
            Spacer(modifier = Modifier.size(24.dp))
            iconProvider?.invoke(key, text)
            Text(
                text,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(15.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MultiSelectDialog(
    modifier: Modifier = Modifier,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = colors.background,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    icon: @Composable (() -> Unit)? = null,
    title: String? = null,
    properties: DialogProperties = DialogProperties(),
    /* key - visual */
    entries: Map<out T, String>,
    /* Selected keys used in entries */
    selectedKeys: Set<T>,
    confirmText: String? = null,
    dismissText: String? = null,
    dismiss: () -> Unit,
    confirm: (Set<T>) -> Unit,
    iconProvider: (@Composable (key: T, value: String) -> Unit)? = null
) {
    val selected = remember {
        entries.keys
            .filter { selectedKeys.contains(it) }
            .toMutableStateList()
    }

    val selectedPainter = painterResource(Res.drawable.check)

    AlertDialog(
        properties = properties,
        tonalElevation = tonalElevation,
        modifier = modifier,
        icon = icon,
        shape = shape,
        iconContentColor = iconContentColor,
        containerColor = containerColor,
        onDismissRequest = {
            if (confirmText == null) {
                confirm(selected.toSet())
            } else {
                dismiss()
            }
        },
        title = title?.let { { Text(text = it) } },
        text = {
            LazyColumn {
                entries.forEach { (key, value) ->
                    item(key = key) {
                        val isSelected = selected.contains(key)
                        SingleSelectionItem(
                            isSelected,
                            key,
                            value,
                            iconProvider,
                            selectedPainter = selectedPainter
                        ) {
                            if (isSelected) {
                                selected.remove(key)
                            } else {
                                selected.add(key)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (confirmText != null) {
                Button(
                    onClick = {
                        confirm(selected.toSet())
                    },
                    colors = Colors.whiteButton
                ) { Text(text = confirmText) }
            }
        },
        dismissButton = {
            if (dismissText != null) {
                Button(
                    onClick = dismiss, colors = Colors.blackButton
                ) { Text(text = dismissText) }
            }
        }
    )
}