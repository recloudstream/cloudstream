package com.lagradost.cloudstream3

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class ServerContext(
    private val configStore: ConfigStore,
    private val pluginKey: String,
    private val filesDir: File,
) : Context() {
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        return ConfigSharedPreferences(configStore, pluginKey, name)
    }

    override fun getApplicationContext(): Context = this

    override fun getFilesDir(): File = filesDir
}

private class ConfigSharedPreferences(
    private val configStore: ConfigStore,
    private val pluginKey: String,
    private val prefsName: String,
) : SharedPreferences {
    override fun getAll(): Map<String, *> = readPrefs()

    override fun getString(key: String, defValue: String?): String? =
        readPrefs()[key] as? String ?: defValue

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        val value = readPrefs()[key] ?: return defValues
        return when (value) {
            is Set<*> -> value.filterIsInstance<String>().toSet()
            is Collection<*> -> value.filterIsInstance<String>().toSet()
            else -> defValues
        }
    }

    override fun getInt(key: String, defValue: Int): Int =
        coerceInt(readPrefs()[key], defValue)

    override fun getLong(key: String, defValue: Long): Long =
        coerceLong(readPrefs()[key], defValue)

    override fun getFloat(key: String, defValue: Float): Float =
        coerceFloat(readPrefs()[key], defValue)

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        coerceBoolean(readPrefs()[key], defValue)

    override fun contains(key: String): Boolean = readPrefs().containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor(configStore, pluginKey, prefsName)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
    }

    private fun readPrefs(): Map<String, Any?> {
        val config = configStore.load()
        val pluginSettings = config.pluginSettings[pluginKey] ?: return emptyMap()
        val prefs = pluginSettings[prefsName] ?: return emptyMap()
        return prefs.toMap()
    }

    private fun coerceInt(value: Any?, defValue: Int): Int = when (value) {
        is Int -> value
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: defValue
        else -> defValue
    }

    private fun coerceLong(value: Any?, defValue: Long): Long = when (value) {
        is Long -> value
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: defValue
        else -> defValue
    }

    private fun coerceFloat(value: Any?, defValue: Float): Float = when (value) {
        is Float -> value
        is Number -> value.toFloat()
        is String -> value.toFloatOrNull() ?: defValue
        else -> defValue
    }

    private fun coerceBoolean(value: Any?, defValue: Boolean): Boolean = when (value) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true) ||
            (value == "1")
        is Number -> value.toInt() != 0
        else -> defValue
    }

    private class Editor(
        private val configStore: ConfigStore,
        private val pluginKey: String,
        private val prefsName: String,
    ) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            pending[key] = values
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            removals.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            configStore.update { config ->
                val pluginSettings = config.pluginSettings.getOrPut(pluginKey) { mutableMapOf() }
                val prefs = pluginSettings.getOrPut(prefsName) { mutableMapOf() }
                if (clear) prefs.clear()
                removals.forEach { prefs.remove(it) }
                pending.forEach { (key, value) ->
                    if (value == null) {
                        prefs.remove(key)
                    } else {
                        prefs[key] = value
                    }
                }
                config
            }
        }
    }
}
