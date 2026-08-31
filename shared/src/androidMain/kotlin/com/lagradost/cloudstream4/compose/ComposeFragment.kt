package com.lagradost.cloudstream4.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.lagradost.cloudstream4.theme.CloudStreamTheme
import com.lagradost.cloudstream4.theme.loadPrimaryColor
import com.lagradost.cloudstream4.theme.loadThemeMode
import com.mihon.presentation.LocalBackPress

/** Backwards compatible fragment for compose, before we switch entirely to compose navigation */
fun Screen.createComposeView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
): View = ComposeView(inflater.context).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

    setContent {
        CloudStreamTheme(
            mode = LocalContext.current.loadThemeMode(),
            primaryColor = LocalContext.current.loadPrimaryColor(),
        ) {
            val backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current) {
                "No OnBackPressedDispatcherOwner was provided via LocalOnBackPressedDispatcherOwner"
            }.onBackPressedDispatcher

            CompositionLocalProvider(LocalBackPress provides backDispatcher::onBackPressed) {
                this@createComposeView.Content()
            }
        }
    }
}