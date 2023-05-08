package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.BackupAPI

const val DOWNLOAD_HEADER_CACHE = "download_header_cache"

//const val WATCH_HEADER_CACHE = "watch_header_cache"
const val DOWNLOAD_EPISODE_CACHE = "download_episode_cache"
const val VIDEO_PLAYER_BRIGHTNESS = "video_player_alpha_key"
const val USER_SELECTED_HOMEPAGE_API = "home_api_used"
const val USER_PROVIDER_API = "user_custom_sites"

const val PREFERENCES_NAME = "rebuild_preference"
const val SYNC_PREFERENCES_NAME = "rebuild_sync_preference"

object DataStore {
    val mapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.USE_LONG_FOR_INTS, true)
        .build()

    private val backupScheduler = Scheduler.createBackupScheduler()

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    private fun getSyncPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(SYNC_PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun Context.getSyncPrefs(): SharedPreferences {
        return getSyncPreferences(this)
    }

    fun Context.getSharedPrefs(): SharedPreferences {
        return getPreferences(this)
    }

    fun getFolderName(folder: String, path: String): String {
        return "${folder}/${path}"
    }
    fun <T> Context.setKeyRaw(path: String, value: T, restoreSource: BackupUtils.RestoreSource) {
        try {
            val editor = when (restoreSource) {
                BackupUtils.RestoreSource.DATA -> getSharedPrefs().edit()
                BackupUtils.RestoreSource.SETTINGS -> getDefaultSharedPrefs().edit()
                BackupUtils.RestoreSource.SYNC -> getSyncPrefs().edit()
            }

            when (value) {
                is Boolean -> editor.putBoolean(path, value)
                is Int -> editor.putInt(path, value)
                is String -> editor.putString(path, value)
                is Float -> editor.putFloat(path, value)
                is Long -> editor.putLong(path, value)
                (value as? Set<String> != null) -> editor.putStringSet(path, value as Set<String>)
            }
            editor.apply()
        } catch (e: Exception) {
            logError(e)
        }
    }
    fun Context.removeKeyRaw(path: String,  restoreSource: BackupUtils.RestoreSource) {
        try {
            when (restoreSource) {
                BackupUtils.RestoreSource.DATA -> getSharedPrefs().edit()
                BackupUtils.RestoreSource.SETTINGS -> getDefaultSharedPrefs().edit()
                BackupUtils.RestoreSource.SYNC -> getSyncPrefs().edit()
            }.remove(path).apply()
        } catch (e: Exception) {
            logError(e)
        }
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
                val oldValueExists = prefs.getString(path, null) != null

                val editor: SharedPreferences.Editor = prefs.edit()
                editor.remove(path)
                editor.apply()

                backupScheduler.work(
                    BackupAPI.PreferencesSchedulerData(
                        getSyncPrefs(),
                        path,
                        oldValueExists,
                        false,
                        BackupUtils.RestoreSource.DATA
                    )
                )
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun Context.removeKeys(folder: String): Int {
        val keys = getKeys(folder)
        keys.forEach { value ->
            removeKey(value)
        }
        return keys.size
    }

    fun <T> Context.setKey(path: String, value: T) {
        try {
            val prefs = getSharedPrefs()
            val oldValue = prefs.getString(path, null)
            val newValue = mapper.writeValueAsString(value)

            val editor: SharedPreferences.Editor = prefs.edit()
            editor.putString(path, newValue)
            editor.apply()

            backupScheduler.work(
                BackupAPI.PreferencesSchedulerData(
                    getSyncPrefs(),
                    path,
                    oldValue,
                    newValue,
                    BackupUtils.RestoreSource.DATA
                )
            )
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun <T> Context.setKey(folder: String, path: String, value: T) {
        setKey(getFolderName(folder, path), value)
    }


    inline fun <reified T : Any> String.toKotlinObject(): T {
        return mapper.readValue(this, T::class.java)
    }

    // GET KEY GIVEN PATH AND DEFAULT VALUE, NULL IF ERROR
    inline fun <reified T : Any> Context.getKey(path: String, defVal: T?): T? {
        try {
            val json: String = getSharedPrefs().getString(path, null) ?: return defVal
            return json.toKotlinObject()
        } catch (e: Exception) {
            return null
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
}