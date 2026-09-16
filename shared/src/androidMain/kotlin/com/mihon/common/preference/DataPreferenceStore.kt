package com.mihon.common.preference

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJsonLiteral
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Same as AndroidPreferenceStore for DataStore but with some optimization to only see changes when values are
 * changed by this store. This is done to avoid having a global key listener where we might pollute
 * the setKey from other places.
 * */
class DataPreferenceStore(private val sharedPreferences: SharedPreferences) : PreferenceStore {
    constructor(context: Context) : this(
        context.getSharedPreferences(
            "rebuild_preference",
            Context.MODE_PRIVATE
        )
    )

    companion object {
        private val keyFlow: MutableSharedFlow<String?> =
            MutableSharedFlow(extraBufferCapacity = 16)
    }

    override fun getString(key: String, defaultValue: String): PreferenceData<String> {
        return DataPreference.StringPrimitive(sharedPreferences, keyFlow, key, defaultValue)
    }

    override fun getLong(key: String, defaultValue: Long): PreferenceData<Long> {
        return DataPreference.LongPrimitive(sharedPreferences, keyFlow, key, defaultValue)
    }

    override fun getInt(key: String, defaultValue: Int): PreferenceData<Int> {
        return DataPreference.IntPrimitive(sharedPreferences, keyFlow, key, defaultValue)
    }

    override fun getFloat(key: String, defaultValue: Float): PreferenceData<Float> {
        return DataPreference.FloatPrimitive(sharedPreferences, keyFlow, key, defaultValue)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): PreferenceData<Boolean> {
        return DataPreference.BooleanPrimitive(sharedPreferences, keyFlow, key, defaultValue)
    }

    override fun getStringSet(key: String, defaultValue: Set<String>): PreferenceData<Set<String>> {
        return DataPreference.StringSetPrimitive(sharedPreferences, keyFlow, key, defaultValue)
    }

    inline fun <reified T : Any> getJson(key: String, defaultValue: T): PreferenceData<T> =
        getObjectFromString(key, defaultValue, serializer = { obj ->
            obj.toJsonLiteral()
        }, deserializer = { json ->
            parseJson<T>(json)
        })

    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): PreferenceData<T> {
        return DataPreference.ObjectAsString(
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
        return DataPreference.ObjectAsInt(
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
        return DataPreference.ObjectSetAsStringSet(
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
