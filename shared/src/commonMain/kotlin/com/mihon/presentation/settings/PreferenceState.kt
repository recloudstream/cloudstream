package com.mihon.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.unit.dp
import com.mihon.common.preference.PreferenceData

/** State used by the extension/theme UI, independent of the removed full settings framework. */
val LocalPreferenceHighlighted = compositionLocalOf(structuralEqualityPolicy()) { false }
val LocalPreferenceMinHeight = compositionLocalOf(structuralEqualityPolicy()) { 56.dp }

@Composable
fun <T> PreferenceData<T>.collectAsState(): State<T> {
    val flow = remember(this) { changes() }
    return flow.collectAsState(initial = get())
}
