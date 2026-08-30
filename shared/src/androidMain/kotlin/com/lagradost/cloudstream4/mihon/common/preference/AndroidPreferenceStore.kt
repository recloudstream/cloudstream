package com.lagradost.cloudstream4.mihon.common.preference

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.mihon.common.preference.PreferenceData
import com.mihon.common.preference.PreferenceStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

class AndroidPreferenceStore(private val sharedPreferences: SharedPreferences) : PreferenceStore {
    constructor(context: Context) : this(PreferenceManager.getDefaultSharedPreferences(context))

    private val keyFlow = sharedPreferences.keyFlow

    override fun getString(key: String, defaultValue: String): PreferenceData<String> {
        return AndroidPreference.StringPrimitive(sharedPreferences, keyFlow, key, defaultValue)
    }

    override fun getLong(key: String, defaultValue: Long): PreferenceData<Long> {
        return AndroidPreference.LongPrimitive(sharedPreferences, keyFlow, key, defaultValue)
    }

    override fun getInt(key: String, defaultValue: Int): PreferenceData<Int> {
        return AndroidPreference.IntPrimitive(sharedPreferences, keyFlow, key, defaultValue)
    }

    override fun getFloat(key: String, defaultValue: Float): PreferenceData<Float> {
        return AndroidPreference.FloatPrimitive(sharedPreferences, keyFlow, key, defaultValue)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): PreferenceData<Boolean> {
        return AndroidPreference.BooleanPrimitive(sharedPreferences, keyFlow, key, defaultValue)
    }

    override fun getStringSet(key: String, defaultValue: Set<String>): PreferenceData<Set<String>> {
        return AndroidPreference.StringSetPrimitive(sharedPreferences, keyFlow, key, defaultValue)
    }

    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): PreferenceData<T> {
        return AndroidPreference.ObjectAsString(
            preferences = sharedPreferences,
            keyFlow = keyFlow,
            key = key,
            defaultValue = defaultValue,
            serializer = serializer,
            deserializer = deserializer,
        )
    }

    override fun <T> getObjectFromInt(
        key: String,
        defaultValue: T,
        serializer: (T) -> Int,
        deserializer: (Int) -> T,
    ): PreferenceData<T> {
        return AndroidPreference.ObjectAsInt(
            preferences = sharedPreferences,
            keyFlow = keyFlow,
            key = key,
            defaultValue = defaultValue,
            serializer = serializer,
            deserializer = deserializer,
        )
    }

    override fun <T> getObjectSetFromStringSet(
        key: String,
        defaultValue: Set<T>,
        serializer: (T) -> String,
        deserializer: (String) -> T?,
    ): PreferenceData<Set<T>> {
        return AndroidPreference.ObjectSetAsStringSet(
            preferences = sharedPreferences,
            keyFlow = keyFlow,
            key = key,
            defaultValue = defaultValue,
            serializer = serializer,
            deserializer = deserializer,
        )
    }

    override fun getAll(): Map<String, *> {
        return sharedPreferences.all ?: emptyMap<String, Any>()
    }
}

private val SharedPreferences.keyFlow
    get() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key: String? ->
            trySend(
                key,
            )
        }
        registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            unregisterOnSharedPreferenceChangeListener(listener)
        }
    }