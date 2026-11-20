package com.mihon.common.preference

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.atomic.AtomicInteger

sealed class AndroidAccountPreference<T>(
    private val preferences: SharedPreferences,
    protected val keyFlow: MutableSharedFlow<String?>,
    private val account: AtomicInteger,
    private val key: String,
    private val defaultValue: T,
) : PreferenceData<T> {
    abstract fun read(preferences: SharedPreferences, key: String, defaultValue: T): T

    abstract fun write(key: String, value: T): Editor.() -> Unit

    override fun key(): String {
        return "$account/$key"
    }

    override fun get(): T {
        return try {
            read(preferences, key(), defaultValue)
        } catch (e: ClassCastException) {
            //logcat { "Invalid value for $key; deleting" }
            delete()
            defaultValue
        }
    }

    override fun set(value: T) {
        preferences.edit(action = write(key(), value))
        keyFlow.tryEmit(key())
    }

    override fun isSet(): Boolean {
        return preferences.contains(key())
    }

    override fun delete() {
        preferences.edit {
            remove(key())
        }
    }

    override fun defaultValue(): T {
        return defaultValue
    }

    override fun changes(): Flow<T> {
        return keyFlow
            .filter { it == key() || it == null }
            .onStart { emit("ignition") }
            .map { get() }
            .conflate()
    }

    override fun stateIn(scope: CoroutineScope): StateFlow<T> {
        return changes().stateIn(scope, SharingStarted.Eagerly, get())
    }

    class StringPrimitive(
        preferences: SharedPreferences,
        keyFlow: MutableSharedFlow<String?>,
        account: AtomicInteger,
        key: String,
        defaultValue: String,
    ) : AndroidAccountPreference<String>(preferences, keyFlow, account, key, defaultValue) {
        override fun read(
            preferences: SharedPreferences,
            key: String,
            defaultValue: String,
        ): String {
            return preferences.getString(key, defaultValue) ?: defaultValue
        }

        override fun write(key: String, value: String): Editor.() -> Unit = {
            putString(key, value)
        }
    }

    class LongPrimitive(
        preferences: SharedPreferences,
        keyFlow: MutableSharedFlow<String?>,
        account: AtomicInteger,
        key: String,
        defaultValue: Long,
    ) : AndroidAccountPreference<Long>(preferences, keyFlow, account, key, defaultValue) {
        override fun read(preferences: SharedPreferences, key: String, defaultValue: Long): Long {
            return preferences.getLong(key, defaultValue)
        }

        override fun write(key: String, value: Long): Editor.() -> Unit = {
            putLong(key, value)
        }
    }

    class IntPrimitive(
        preferences: SharedPreferences,
        keyFlow: MutableSharedFlow<String?>,
        account: AtomicInteger,
        key: String,
        defaultValue: Int,
    ) : AndroidAccountPreference<Int>(preferences, keyFlow, account, key, defaultValue) {
        override fun read(preferences: SharedPreferences, key: String, defaultValue: Int): Int {
            return preferences.getInt(key, defaultValue)
        }

        override fun write(key: String, value: Int): Editor.() -> Unit = {
            putInt(key, value)
        }
    }

    class FloatPrimitive(
        preferences: SharedPreferences,
        keyFlow: MutableSharedFlow<String?>,
        account: AtomicInteger,
        key: String,
        defaultValue: Float,
    ) : AndroidAccountPreference<Float>(preferences, keyFlow, account, key, defaultValue) {
        override fun read(preferences: SharedPreferences, key: String, defaultValue: Float): Float {
            return preferences.getFloat(key, defaultValue)
        }

        override fun write(key: String, value: Float): Editor.() -> Unit = {
            putFloat(key, value)
        }
    }

    class BooleanPrimitive(
        preferences: SharedPreferences,
        keyFlow: MutableSharedFlow<String?>,
        account: AtomicInteger,
        key: String,
        defaultValue: Boolean,
    ) : AndroidAccountPreference<Boolean>(preferences, keyFlow, account, key, defaultValue) {
        override fun read(
            preferences: SharedPreferences,
            key: String,
            defaultValue: Boolean,
        ): Boolean {
            return preferences.getBoolean(key, defaultValue)
        }

        override fun write(key: String, value: Boolean): Editor.() -> Unit = {
            putBoolean(key, value)
        }
    }

    class StringSetPrimitive(
        preferences: SharedPreferences,
        keyFlow: MutableSharedFlow<String?>,
        account: AtomicInteger,
        key: String,
        defaultValue: Set<String>,
    ) : AndroidAccountPreference<Set<String>>(preferences, keyFlow, account, key, defaultValue) {
        override fun read(
            preferences: SharedPreferences,
            key: String,
            defaultValue: Set<String>,
        ): Set<String> {
            return preferences.getStringSet(key, defaultValue) ?: defaultValue
        }

        override fun write(key: String, value: Set<String>): Editor.() -> Unit = {
            putStringSet(key, value)
        }
    }

    class ObjectAsString<T>(
        preferences: SharedPreferences,
        keyFlow: MutableSharedFlow<String?>,
        account: AtomicInteger,
        key: String,
        defaultValue: T,
        private val serializer: (T) -> String,
        private val deserializer: (String) -> T,
    ) : AndroidAccountPreference<T>(preferences, keyFlow, account, key, defaultValue) {
        override fun read(preferences: SharedPreferences, key: String, defaultValue: T): T {
            return try {
                preferences.getString(key, null)?.let(deserializer) ?: defaultValue
            } catch (e: Exception) {
                defaultValue
            }
        }

        override fun write(key: String, value: T): Editor.() -> Unit = {
            putString(key, serializer(value))
        }
    }

    class ObjectAsInt<T>(
        preferences: SharedPreferences,
        keyFlow: MutableSharedFlow<String?>,
        account: AtomicInteger,
        key: String,
        defaultValue: T,
        private val serializer: (T) -> Int,
        private val deserializer: (Int) -> T,
    ) : AndroidAccountPreference<T>(preferences, keyFlow, account, key, defaultValue) {
        override fun read(preferences: SharedPreferences, key: String, defaultValue: T): T {
            return try {
                if (preferences.contains(key)) preferences.getInt(key, 0)
                    .let(deserializer) else defaultValue
            } catch (e: Exception) {
                defaultValue
            }
        }

        override fun write(key: String, value: T): Editor.() -> Unit = {
            putInt(key, serializer(value))
        }
    }

    class ObjectSetAsStringSet<T>(
        preferences: SharedPreferences,
        keyFlow: MutableSharedFlow<String?>,
        account: AtomicInteger,
        key: String,
        defaultValue: Set<T>,
        private val serializer: (T) -> String,
        private val deserializer: (String) -> T?,
    ) : AndroidAccountPreference<Set<T>>(preferences, keyFlow, account, key, defaultValue) {
        override fun read(
            preferences: SharedPreferences,
            key: String,
            defaultValue: Set<T>
        ): Set<T> {
            return try {
                preferences.getStringSet(key, null)?.mapNotNull(deserializer)?.toSet()
                    ?: defaultValue
            } catch (_: Exception) {
                defaultValue
            }
        }

        override fun write(key: String, value: Set<T>): Editor.() -> Unit = {
            putStringSet(key, value.map(serializer).toSet())
        }
    }
}
