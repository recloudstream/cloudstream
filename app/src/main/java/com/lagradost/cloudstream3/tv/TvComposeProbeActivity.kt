package com.lagradost.cloudstream3.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lagradost.cloudstream3.tv.navigation.TvNavigationShell

/**
 * Compose-for-TV host activity (Phase 3: read-only Home catalog bridge).
 *
 * Not registered as MAIN / LEANBACK_LAUNCHER — default phone + legacy TV startup unchanged.
 *
 * Launch (debug / stableDebug package id suffix `.debug`):
 * ```
 * adb shell am start -n com.lagradost.cloudstream3.debug/com.lagradost.cloudstream3.tv.TvComposeProbeActivity
 * ```
 *
 * Also available from Settings → Updates → Actions → "Compose TV (debug)" when BuildConfig.DEBUG.
 */
class TvComposeProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TvTheme {
                TvNavigationShell()
            }
        }
    }
}
