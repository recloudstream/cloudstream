package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.Event

/** Only the static events of the Android MainActivity that extensions subscribe to */
class MainActivity {
    companion object {
        /** Invoked with true once all extensions are loaded */
        val afterPluginsLoadedEvent = Event<Boolean>()
    }
}
