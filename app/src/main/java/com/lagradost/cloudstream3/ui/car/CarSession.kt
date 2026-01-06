package com.lagradost.cloudstream3.ui.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import com.lagradost.cloudstream3.plugins.PluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CarSession : Session() {
    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                @Suppress("DEPRECATION_ERROR")
                PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins(carContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreateScreen(intent: Intent): Screen {
        return MainCarScreen(carContext)
    }
}
