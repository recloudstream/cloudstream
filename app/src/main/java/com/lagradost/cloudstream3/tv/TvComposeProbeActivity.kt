package com.lagradost.cloudstream3.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Isolated Compose-for-TV probe activity (Phase 1).
 *
 * Not registered as MAIN / LEANBACK_LAUNCHER — default phone + legacy TV startup unchanged.
 *
 * Launch (debug / stableDebug package id suffix `.debug`):
 * ```
 * adb shell am start -n com.lagradost.cloudstream3.debug/com.lagradost.cloudstream3.tv.TvComposeProbeActivity
 * ```
 *
 * Launch (release / no debug suffix):
 * ```
 * adb shell am start -n com.lagradost.cloudstream3/.tv.TvComposeProbeActivity
 * ```
 *
 * Note: activity is `android:exported="false"`; adb can still start it on debuggable builds.
 */
class TvComposeProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TvTheme {
                TvProbeScreen()
            }
        }
    }
}
