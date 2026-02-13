package com.lagradost.cloudstream3.ui.car

import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.coroutineScope
import com.lagradost.cloudstream3.plugins.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CarSession : Session() {
    companion object {
        private const val TAG = "CarSession"
    }

    init {
        lifecycle.coroutineScope.launch(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION_ERROR")
                PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins(carContext)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading plugins", e)
            }
        }
    }

    override fun onCreateScreen(intent: Intent): Screen {
        return MainCarScreen(carContext)
    }
}
