package com.lagradost.cloudstream3.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.CommonActivity.loadThemes
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.tv.model.TvPlaybackRequest
import com.lagradost.cloudstream3.tv.navigation.TvNavigationShell
import com.lagradost.cloudstream3.tv.playback.TvPlaybackBridge
import kotlinx.coroutines.launch

/**
 * Compose-for-TV host activity (Phase 10: CW polish / Hero Watch Now → existing GeneratorPlayer).
 *
 * Layout: [R.layout.activity_tv_compose_probe] —
 * Compose shell + [R.id.tv_player_container] Fragment boundary for GeneratorPlayer.
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
class TvComposeProbeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        loadThemes(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        CommonActivity.init(this)
        setContentView(R.layout.activity_tv_compose_probe)
        findViewById<ComposeView>(R.id.tv_compose_host).setContent {
            TvTheme {
                TvNavigationShell(
                    onPlaybackRequest = ::onPlaybackRequest,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CommonActivity.setActivityInstance(this)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        CommonActivity.dispatchKeyEvent(this, event) ?: super.dispatchKeyEvent(event)

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        CommonActivity.onKeyDown(this, keyCode, event) ?: super.onKeyDown(keyCode, event)

    /**
     * Activity-level callback from Home CW Resume / Details Watch Now / Play Episode.
     * Request stays immutable; mock rejected by bridge. No Compose seek / PosDur writes here.
     * After GeneratorPlayer pops, Home re-reads CW (read-only) and restores focus.
     */
    private fun onPlaybackRequest(request: TvPlaybackRequest) {
        lifecycleScope.launch {
            val result = TvPlaybackBridge.launch(this@TvComposeProbeActivity, request)
            TvPlaybackBridge.report(this@TvComposeProbeActivity, result)
        }
    }
}
