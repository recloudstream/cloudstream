@file:Suppress("unused")

package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJsonLiteral

const val PREFERENCES_NAME = "rebuild_preference"

/**
 * Desktop copy of the Android app's DataStore. Extensions store their settings (tokens, toggles)
 * through it, mostly via the inline `getKey`/`setKey` helpers that get compiled into the extension.
 */
object DataStore {
    fun Context.getSharedPrefs(): SharedPreferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun Context.getDefaultSharedPrefs(): SharedPreferences = getSharedPreferences("default", Context.MODE_PRIVATE)

    fun getFolderName(folder: String, path: String): String = "$folder/$path"

    fun Context.getKeys(folder: String): List<String> {
        val fixedFolder = folder.trimEnd('/') + "/"
        return getSharedPrefs().getAll().keys.filter { it.startsWith(fixedFolder) }
    }

    fun Context.removeKey(folder: String, path: String) = removeKey(getFolderName(folder, path))

    fun Context.containsKey(folder: String, path: String): Boolean = containsKey(getFolderName(folder, path))

    fun Context.containsKey(path: String): Boolean = getSharedPrefs().contains(path)

    fun Context.removeKey(path: String) {
        try {
            getSharedPrefs().edit().remove(path).apply()
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun Context.removeKeys(folder: String): Int {
        val keys = getKeys("$folder/")
        val editor = getSharedPrefs().edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
        return keys.size
    }

    fun <T> Context.setKey(path: String, value: T) {
        try {
            getSharedPrefs().edit().putString(path, value?.toJsonLiteral()).apply()
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun <T : Any> Context.getKey(path: String, valueType: Class<T>): T? = try {
        getSharedPrefs().getString(path, null)?.let { parseJson(it, valueType.kotlin) }
    } catch (_: Exception) {
        null
    }

    fun <T> Context.setKey(folder: String, path: String, value: T) = setKey(getFolderName(folder, path), value)

    inline fun <reified T : Any> Context.getKey(path: String, defVal: T?): T? = try {
        val json: String = getSharedPrefs().getString(path, null) ?: return defVal
        parseJson<T>(json)
    } catch (_: Exception) {
        null
    }

    inline fun <reified T : Any> Context.getKey(path: String): T? = getKey(path, null)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String): T? = getKey(getFolderName(folder, path), null)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String, defVal: T?): T? =
        getKey(getFolderName(folder, path), defVal) ?: defVal
}
