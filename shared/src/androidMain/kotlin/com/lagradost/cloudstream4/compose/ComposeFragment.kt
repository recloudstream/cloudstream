package com.lagradost.cloudstream4.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.lagradost.cloudstream4.rememberAppSettings
import com.lagradost.cloudstream4.theme.CloudStreamTheme
import com.lagradost.cloudstream4.theme.perfToColor
import com.lagradost.cloudstream4.theme.perfToMode
import com.mihon.presentation.LocalBackPress
import com.mihon.presentation.settings.collectAsState

/** Backwards compatible fragment for compose, before we switch entirely to compose navigation */
fun Screen.createComposeView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
): View = ComposeView(inflater.context).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

    setContent {
        val settings = rememberAppSettings()
        val mode by settings.ui.theme.collectAsState()
        val primaryColor by settings.ui.primaryColor.collectAsState()

        CloudStreamTheme(
            mode = perfToMode(mode),
            primaryColor = perfToColor(primaryColor),
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