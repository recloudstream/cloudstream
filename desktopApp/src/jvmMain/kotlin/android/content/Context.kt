@file:Suppress("unused")

package android.content

import com.lagradost.cloudstream4.desktop.data.AppDirs
import com.lagradost.cloudstream4.desktop.data.JsonSharedPreferences
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop stand-in for android.content.Context.
 *
 * Extensions built only for Android override `Plugin.load(context: Context)`, so this class must exist
 * with the same name. Only the parts extensions realistically touch during load are implemented.
 */
open class Context {
    open fun getFilesDir(): File = AppDirs.dataDir
    open fun getCacheDir(): File = AppDirs.cacheDir
    open fun getApplicationContext(): Context = this
    open fun getPackageName(): String = "com.lagradost.cloudstream3"

    /** Only [ACTIVITY_SERVICE] exists on desktop, other services (keyboard, clipboard...) are Android UI */
    open fun getSystemService(name: String): Any? = when (name) {
        ACTIVITY_SERVICE -> activityManager
        else -> null
    }

    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        preferences.getOrPut(name) { JsonSharedPreferences(File(preferencesDir, "$name.json")) }

    companion object {
        const val MODE_PRIVATE = 0
        const val ACTIVITY_SERVICE = "activity"
        private val activityManager by lazy { android.app.ActivityManager() }
        private val preferences = ConcurrentHashMap<String, SharedPreferences>()
        private val preferencesDir get() = File(AppDirs.dataDir, "prefs")

        /** Names of every preference store extensions have created, for the settings editor */
        fun preferenceNames(): List<String> =
            (preferencesDir.listFiles { f -> f.extension == "json" }.orEmpty().map { it.nameWithoutExtension } + preferences.keys)
                .distinct().sorted()
    }
}

/** The single context handed to every extension on desktop. */
object DesktopContext : Context()
