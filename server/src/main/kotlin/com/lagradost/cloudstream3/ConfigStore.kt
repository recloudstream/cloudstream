package com.lagradost.cloudstream3

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.lagradost.api.Log
import java.nio.file.Files
import java.nio.file.Path

class ConfigStore(private val configPath: Path) {
    private val lock = Any()
    private val mapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build()
    private val writer = mapper.writerWithDefaultPrettyPrinter()

    fun load(): ServerConfig = synchronized(lock) {
        loadUnsafe()
    }

    fun save(config: ServerConfig) = synchronized(lock) {
        saveUnsafe(config)
    }

    fun update(block: (ServerConfig) -> ServerConfig): ServerConfig = synchronized(lock) {
        val updated = block(loadUnsafe())
        saveUnsafe(updated)
        updated
    }

    private fun loadUnsafe(): ServerConfig {
        ensureParent()
        if (Files.notExists(configPath)) {
            val config = ServerConfig()
            saveUnsafe(config)
            return config
        }
        return runCatching {
            mapper.readValue(configPath.toFile(), ServerConfig::class.java)
        }.getOrElse { error ->
            Log.e("ServerConfig", "Failed to read config: ${error.message}")
            val config = ServerConfig()
            saveUnsafe(config)
            config
        }
    }

    private fun saveUnsafe(config: ServerConfig) {
        ensureParent()
        writer.writeValue(configPath.toFile(), config)
    }

    private fun ensureParent() {
        configPath.parent?.let { Files.createDirectories(it) }
    }
}
