package com.lagradost.cloudstream4.compose

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
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
        val layout by settings.ui.layout.collectAsState()
        val layoutFlag = DeviceLayout.layoutToFlag(LocalContext.current, layout)

        CloudStreamTheme(
            mode = perfToMode(mode),
            primaryColor = perfToColor(primaryColor),
        ) {
            val backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current) {
                "No OnBackPressedDispatcherOwner was provided via LocalOnBackPressedDispatcherOwner"
            }.onBackPressedDispatcher

            /** Sync status bar and background color with optional HasStatusBarColor
             * or else colors might be messed up. */
            val statusBarColor = (this@createComposeView as? HasStatusBarColor)?.statusBarColor()
            if (statusBarColor != null) {
                SideEffect {
                    setBackgroundColor(statusBarColor.toArgb())
                    (context as? Activity)?.window?.let {
                        WindowCompat.getInsetsController(
                            it,
                            this@apply
                        ).isAppearanceLightStatusBars =
                            statusBarColor.luminance() > 0.5f
                    }
                }
            }

            CompositionLocalProvider(
                LocalBackPress provides backDispatcher::onBackPressed,
                DeviceLayout.LocalLayout provides layoutFlag
            ) {
                this@createComposeView.Content()
            }
        }
    }
}