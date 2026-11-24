package com.mihon.common.preference

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.atomic.AtomicInteger

class AndroidAccountPreferenceStore(private val sharedPreferences: SharedPreferences) :
    PreferenceStore {
    constructor(context: Context) : this(context.getSharedPreferences(
        "rebuild_preference",
        Context.MODE_PRIVATE
    ))

    companion object {
        private val keyFlow: MutableSharedFlow<String?> =
            MutableSharedFlow(extraBufferCapacity = 16)
        private val activeAccount = AtomicInteger(0)
        fun setAccount(account: Int) {
            activeAccount.set(account)
            keyFlow.tryEmit(null)
        }
    }

    override fun getString(key: String, defaultValue: String): PreferenceData<String> {
        return AndroidAccountPreference.StringPrimitive(
            sharedPreferences,
            keyFlow,
            activeAccount,
            key,
            defaultValue
        )
    }

    override fun getLong(key: String, defaultValue: Long): PreferenceData<Long> {
        return AndroidAccountPreference.LongPrimitive(
            sharedPreferences,
            keyFlow,
            activeAccount,
            key,
            defaultValue
        )
    }

    override fun getInt(key: String, defaultValue: Int): PreferenceData<Int> {
        return AndroidAccountPreference.IntPrimitive(
            sharedPreferences,
            keyFlow,
            activeAccount,
            key,
            defaultValue
        )
    }

    override fun getFloat(key: String, defaultValue: Float): PreferenceData<Float> {
        return AndroidAccountPreference.FloatPrimitive(
            sharedPreferences,
            keyFlow,
            activeAccount,
            key,
            defaultValue
        )
    }

    override fun getBoolean(key: String, defaultValue: Boolean): PreferenceData<Boolean> {
        return AndroidAccountPreference.BooleanPrimitive(
            sharedPreferences,
            keyFlow,
            activeAccount,
            key,
            defaultValue
        )
    }

    override fun getStringSet(key: String, defaultValue: Set<String>): PreferenceData<Set<String>> {
        return AndroidAccountPreference.StringSetPrimitive(
            sharedPreferences,
            keyFlow,
            activeAccount,
            key,
            defaultValue
        )
    }

    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): PreferenceData<T> {
        return AndroidAccountPreference.ObjectAsString(
            account = activeAccount,
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
        return AndroidAccountPreference.ObjectAsInt(
            account = activeAccount,
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
        return AndroidAccountPreference.ObjectSetAsStringSet(
            account = activeAccount,
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
