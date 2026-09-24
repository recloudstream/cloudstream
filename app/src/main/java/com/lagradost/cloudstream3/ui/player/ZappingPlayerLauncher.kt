package com.lagradost.cloudstream3.ui.player

import android.os.Bundle

/**
 * Creates a normal GeneratorPlayer session and attaches optional live-zapping state to the exact
 * same UUID. Keeping this as a small wrapper means existing movie/episode call sites remain
 * untouched and live entry points can opt in explicitly.
 */
object ZappingPlayerLauncher {
    fun newInstance(
        generator: VideoGenerator<*>,
        index: Int,
        syncData: HashMap<String, String>? = null,
        zappingContext: ZappingContext? = null,
    ): Bundle {
        val bundle = GeneratorPlayer.newInstance(generator, index, syncData)
        val uuid = bundle.getString("uuid")

        if (uuid != null && zappingContext != null) {
            ZappingSession(uuid, zappingContext).register()
        }

        return bundle
    }

    /**
     * Launches a player for a result that may have originated from a live Home category.
     * Pending state is consumed only when the player is actually created, so backing out of the
     * result page does not create a player session and the full channel list never enters a Bundle.
     */
    fun newInstanceFromPending(
        generator: VideoGenerator<*>,
        index: Int,
        syncData: HashMap<String, String>? = null,
        url: String,
        apiName: String,
        channelName: String? = null,
    ): Bundle {
        val context = PendingZappingStore.consume(url, apiName, channelName)
        return newInstance(generator, index, syncData, context)
    }

    fun session(bundle: Bundle?): ZappingSession? {
        val uuid = bundle?.getString("uuid") ?: return null
        val context = ZappingSessionStore.get(uuid) ?: return null
        return ZappingSession(uuid, context)
    }
}
