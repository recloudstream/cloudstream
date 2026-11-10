package com.lagradost.cloudstream3.lab

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.ui.settings.Globals.updateTv
import com.lagradost.cloudstream3.plugins.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Extension-only host. No accounts, media browsing, downloads, cast or playback entry points. */
class ExtensionLabActivity : AppCompatActivity() {
    var pluginsLoading: Boolean = false
        private set
    var pluginLoadError: String? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        CommonActivity.loadThemes(this)
        super.onCreate(savedInstanceState)
        CommonActivity.setActivityInstance(this)
        updateTv()
        app.initClient(this, ignoreSSL = false)
        setContentView(R.layout.activity_extension_lab)
        val root = findViewById<android.view.View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val host = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        findViewById<BottomNavigationView>(R.id.nav_view).setupWithNavController(host.navController)
        pluginsLoading = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins(this@ExtensionLabActivity)
                    PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllLocalPlugins(this@ExtensionLabActivity, false)
                    APIHolder.initAll()
                }
                MainActivity.afterPluginsLoadedEvent.invoke(true)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                pluginLoadError = error.stackTraceToString()
            } finally {
                pluginsLoading = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CommonActivity.setActivityInstance(this)
    }

    override fun onDestroy() {
        if (CommonActivity.activity === this) CommonActivity.setActivityInstance(null)
        super.onDestroy()
    }
}
