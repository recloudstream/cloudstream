package com.lagradost.cloudstream3.tv.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Invokes [onResume] on Activity/Fragment [Lifecycle.Event.ON_RESUME].
 * Used for enter/resume reloads without polling or recomposition DataStore spam.
 */
@Composable
fun TvOnResume(onResume: () -> Unit) {
    val context = LocalContext.current
    val owner = context as? LifecycleOwner ?: return
    val latestOnResume by rememberUpdatedState(onResume)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) latestOnResume()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
