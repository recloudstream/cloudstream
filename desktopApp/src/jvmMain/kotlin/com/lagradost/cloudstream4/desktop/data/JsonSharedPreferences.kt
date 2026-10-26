package com.lagradost.cloudstream4.desktop.data

import android.content.SharedPreferences
import com.fasterxml.jackson.databind.JsonNode
import java.io.File

/** [SharedPreferences] for extensions, persisted as JSON. */
class JsonSharedPreferences(file: File) : SharedPreferences {
    private val store = JsonFileStore(file)

    private fun raw(key: String): JsonNode? = store.node(key)?.takeUnless { it.isNull }

    override fun getAll(): Map<String, *> = store.keys().associateWith { store.get<Any>(it) }
    override fun getString(key: String, defValue: String?): String? = raw(key)?.asText() ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        store.get<Set<String>>(key) ?: defValues

    override fun getInt(key: String, defValue: Int): Int = raw(key)?.asInt(defValue) ?: defValue
    override fun getLong(key: String, defValue: Long): Long = raw(key)?.asLong(defValue) ?: defValue
    override fun getFloat(key: String, defValue: Float): Float =
        raw(key)?.asDouble(defValue.toDouble())?.toFloat() ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = raw(key)?.asBoolean(defValue) ?: defValue
    override fun contains(key: String): Boolean = raw(key) != null

    override fun edit(): SharedPreferences.Editor = Editor()

    private inner class Editor : SharedPreferences.Editor {
        private val changes = LinkedHashMap<String, Any?>()
        private var clear = false

        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { changes[key] = values }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun remove(key: String) = apply { changes[key] = null }
        override fun clear() = apply { clear = true }

        override fun commit(): Boolean {
            if (clear) store.clear()
            changes.forEach { (key, value) -> store.set(key, value) }
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
