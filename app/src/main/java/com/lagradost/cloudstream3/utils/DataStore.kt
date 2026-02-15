package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKeyClass
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKeyClass
import com.lagradost.cloudstream3.mvvm.logError
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import androidx.core.content.edit

const val DOWNLOAD_HEADER_CACHE = "download_header_cache"

//const val WATCH_HEADER_CACHE = "watch_header_cache"
const val DOWNLOAD_EPISODE_CACHE = "download_episode_cache"
const val VIDEO_PLAYER_BRIGHTNESS = "video_player_alpha_key"
const val USER_SELECTED_HOMEPAGE_API = "home_api_used"
const val USER_PROVIDER_API = "user_custom_sites"
const val PREFERENCES_NAME = "rebuild_preference"

class PreferenceDelegate<T : Any>(
    val key: String, val default: T //, private val klass: KClass<T>
) {
    private val klass: KClass<out T> = default::class

    // simple cache to make it not get the key every time it is accessed, however this requires
    // that ONLY this changes the key
    private var cache: T? = null

    operator fun getValue(self: Any?, property: KProperty<*>) =
        cache ?: getKeyClass(key, klass.java).also { newCache -> cache = newCache } ?: default

    operator fun setValue(
        self: Any?,
        property: KProperty<*>,
        t: T?
    ) {
        cache = t
        if (t == null) {
            removeKey(key)
        } else {
            setKeyClass(key, t)
        }
    }
}

/** When inserting many keys use this function, this is because apply for every key is very expensive on memory */
fun editor(context: Context, isEditingAppSettings: Boolean = false): SharedPreferences.Editor {
    return if (isEditingAppSettings) context.getDefaultSharedPrefs()
        .edit() else context.getSharedPrefs().edit()
}

fun <T> SharedPreferences.Editor.setKeyRaw(path: String, value: T) {
    @Suppress("UNCHECKED_CAST")
    if (value is Set<*>) {
        putStringSet(path, value as Set<String>)
    } else {
        when (value) {
            is Boolean -> putBoolean(path, value)
            is Int -> putInt(path, value)
            is String -> putString(path, value)
            is Float -> putFloat(path, value)
            is Long -> putLong(path, value)
        }
    }
}

val mapper: JsonMapper = JsonMapper.builder().addModule(kotlinModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

fun getFolderName(folder: String, path: String): String {
    return "${folder}/${path}"
}

object DataStore {
    fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
}


// Top-level extension functions

fun Context.getSharedPrefs(): SharedPreferences {
    return DataStore.getPreferences(this)
}

fun Context.getDefaultSharedPrefs(): SharedPreferences {
    return PreferenceManager.getDefaultSharedPreferences(this)
}

fun Context.getKeys(folder: String): List<String> {
    return this.getSharedPrefs().all.keys.filter { it.startsWith(folder) }
}

fun Context.removeKey(folder: String, path: String) {
    removeKey(getFolderName(folder, path))
}

fun Context.containsKey(folder: String, path: String): Boolean {
    return containsKey(getFolderName(folder, path))
}

fun Context.containsKey(path: String): Boolean {
    val prefs = getSharedPrefs()
    return prefs.contains(path)
}

fun Context.removeKey(path: String) {
    try {
        val prefs = getSharedPrefs()
        if (prefs.contains(path)) {
            prefs.edit {
                remove(path)
            }
        }
        // Hook for Sync: Delete
        FirestoreSyncManager.pushDelete(path)
    } catch (e: Exception) {
        logError(e)
    }
}

fun Context.removeKeys(folder: String): Int {
    val keys = getKeys("$folder/")
    try {
        getSharedPrefs().edit {
            keys.forEach { value ->
                remove(value)
            }
        }
        // Sync hook for bulk delete? Maybe difficult, ignoring for now or iterate
        keys.forEach { FirestoreSyncManager.pushDelete(it) }
        return keys.size
    } catch (e: Exception) {
        logError(e)
        return 0
    }
}

fun <T> Context.setKey(path: String, value: T, commit: Boolean = false) {
    try {
        val json = mapper.writeValueAsString(value)
        val current = getSharedPrefs().getString(path, null)
        if (current == json) return

        getSharedPrefs().edit(commit = commit) {
            putString(path, json)
        }
        // Hook for Sync: Write
        FirestoreSyncManager.pushWrite(path, json)
    } catch (e: Exception) {
        logError(e)
    }
}

// Internal local set without sync hook (used by sync manager to avoid loops)
fun <T> Context.setKeyLocal(path: String, value: T, commit: Boolean = false) {
    try {
        // Handle generic value or raw string
        val stringValue = if (value is String) value else mapper.writeValueAsString(value)
        getSharedPrefs().edit(commit = commit) {
            putString(path, stringValue)
        }
    } catch (e: Exception) {
        logError(e)
    }
}

fun <T> Context.setKeyLocal(folder: String, path: String, value: T, commit: Boolean = false) {
    setKeyLocal(getFolderName(folder, path), value, commit)
}

fun Context.removeKeyLocal(path: String) {
    try {
        getSharedPrefs().edit {
            remove(path)
        }
    } catch (e: Exception) {
        logError(e)
    }
}

fun <T> Context.getKey(path: String, valueType: Class<T>): T? {
    try {
        val json: String = getSharedPrefs().getString(path, null) ?: return null
        Log.d("DataStore", "getKey(Class) $path raw: '$json'")
        return json.toKotlinObject(valueType)
    } catch (e: Exception) {
        Log.e("DataStore", "getKey(Class) $path error: ${e.message}")
        return null
    }
}

fun <T> Context.setKey(folder: String, path: String, value: T, commit: Boolean = false) {
    setKey(getFolderName(folder, path), value, commit)
}

inline fun <reified T : Any> String.toKotlinObject(): T {
    return mapper.readValue(this, T::class.java)
}

fun <T> String.toKotlinObject(valueType: Class<T>): T {
    return mapper.readValue(this, valueType)
}

// GET KEY GIVEN PATH AND DEFAULT VALUE, NULL IF ERROR
inline fun <reified T : Any> Context.getKey(path: String, defVal: T?): T? {
    try {
        val json: String = getSharedPrefs().getString(path, null) ?: return defVal
        // Log.d("DataStore", "getKey(Reified) $path raw: '$json' target: ${T::class.java.simpleName}")
        return try {
            val res = json.toKotlinObject<T>()
            // Log.d("DataStore", "getKey(Reified) $path parsed: '$res'")
            res
        } catch (e: Exception) {
            // Log.w("DataStore", "getKey(Reified) $path parse fail: ${e.message}, trying fallback")
            // FALLBACK: If JSON parsing fails, try manual conversion for common types
            val fallback: T? = when {
                T::class == String::class -> {
                    // If it's a string, try removing literal double quotes if they exist at start/end
                    if (json.startsWith("\"") && json.endsWith("\"") && json.length >= 2) {
                        json.substring(1, json.length - 1) as T
                    } else {
                        json as T
                    }
                }
                T::class == Boolean::class -> {
                    (json.lowercase() == "true" || json == "1") as T
                }
                T::class == Long::class -> {
                    json.toLongOrNull() as? T ?: defVal
                }
                T::class == Int::class -> {
                    json.toIntOrNull() as? T ?: defVal
                }
                else -> defVal
            }
            // Log.d("DataStore", "getKey(Reified) $path fallback: '$fallback'")
            fallback
        }
    } catch (e: Exception) {
        Log.e("DataStore", "getKey(Reified) $path total fail: ${e.message}")
        return defVal
    }
}

inline fun <reified T : Any> Context.getKey(path: String): T? {
    return getKey(path, null)
}

inline fun <reified T : Any> Context.getKey(folder: String, path: String): T? {
    return getKey(getFolderName(folder, path), null)
}

inline fun <reified T : Any> Context.getKey(folder: String, path: String, defVal: T?): T? {
    return getKey(getFolderName(folder, path), defVal) ?: defVal
}