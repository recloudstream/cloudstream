package com.lagradost.cloudstream4.desktop.data

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.convertValue
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.mvvm.logError
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A small persistent key-value store backed by a single JSON file.
 * Every write is flushed to disk atomically, the data sets are small enough for this to be fine.
 */
class JsonFileStore(private val file: File) {
    private val lock = Any()
    private val root: ObjectNode = synchronized(lock) {
        try {
            if (file.exists()) mapper.readTree(file) as? ObjectNode else null
        } catch (t: Throwable) {
            logError(t)
            // Keep the broken file around instead of silently discarding the user's data
            file.copyTo(File(file.path + ".broken"), overwrite = true)
            null
        } ?: mapper.createObjectNode()
    }

    fun keys(): List<String> = synchronized(lock) { root.fieldNames().asSequence().toList() }

    fun node(key: String): JsonNode? = synchronized(lock) { root.get(key) }

    inline fun <reified T> get(key: String): T? {
        val value = node(key) ?: return null
        return try {
            mapper.convertValue<T>(value)
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    inline fun <reified T> get(key: String, default: T): T = get<T>(key) ?: default

    fun set(key: String, value: Any?) {
        synchronized(lock) {
            if (value == null) root.remove(key) else root.set<JsonNode>(key, mapper.valueToTree(value))
            save()
        }
    }

    fun remove(key: String) = set(key, null)

    fun clear() {
        synchronized(lock) {
            root.removeAll()
            save()
        }
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            mapper.writerWithDefaultPrettyPrinter().writeValue(temp, root)
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (t: Throwable) {
            logError(t)
        }
    }
}
