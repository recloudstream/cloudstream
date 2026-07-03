package com.lagradost.cloudstream3.ui.player.live

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R
import com.github.rubensousa.previewseekbar.media3.PreviewTimeBar
import java.lang.ref.WeakReference


@OptIn(UnstableApi::class)
class LivePreviewTimeBar(val ctx: Context, attrs: AttributeSet) : PreviewTimeBar(ctx, attrs) {

    private var _currentPlayerView: WeakReference<PlayerView>? = null
    val currentPlayer: Player? get() = _currentPlayerView?.get()?.player

    fun registerPlayerView(player: PlayerView?) {
        _currentPlayerView = WeakReference(player)
        val controller =
            _currentPlayerView?.get()?.findViewById<PlayerControlView>(R.id.exo_controller)

        controller?.setProgressUpdateListener { position, bufferedPosition ->
            currentPlayer?.let { player ->
                if (isAtLiveEdge()) {
                    setPosition(player.duration)
                }
            }
        }
    }

    fun isAtLiveEdge(): Boolean {
        return LiveHelper.getLiveManager(currentPlayer)?.isAtLiveEdge() == true
    }
}