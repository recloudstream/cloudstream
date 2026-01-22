package com.lagradost.cloudstream3

import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI

class ProviderRegistry {
    fun registerFromConfig(config: ServerConfig): List<MainAPI> {
        val registered = mutableListOf<MainAPI>()
        for (className in config.providerClasses) {
            registerByClassName(className)?.let { registered.add(it) }
        }
        APIHolder.initAll()
        return registered
    }

    fun registerByClassName(className: String): MainAPI? {
        return runCatching {
            if (isClassRegistered(className)) return null
            val clazz = Class.forName(className)
            if (!MainAPI::class.java.isAssignableFrom(clazz)) {
                Log.w("Providers", "Class $className does not extend MainAPI")
                return null
            }
            val instance = clazz.getDeclaredConstructor().newInstance() as MainAPI
            addProvider(instance)
            instance
        }.getOrElse { error ->
            Log.e("Providers", "Failed to register $className: ${error.message}")
            null
        }
    }

    fun listProviders(): List<MainAPI> = synchronized(APIHolder.allProviders) {
        APIHolder.allProviders.toList()
    }

    fun removeByName(name: String): Boolean {
        val api = APIHolder.getApiFromNameNull(name) ?: return false
        APIHolder.removePluginMapping(api)
        synchronized(APIHolder.allProviders) {
            APIHolder.allProviders.removeIf { it == api }
        }
        return true
    }

    private fun addProvider(api: MainAPI) {
        synchronized(APIHolder.allProviders) {
            if (APIHolder.allProviders.any { it.name == api.name }) return
            APIHolder.allProviders.add(api)
        }
        APIHolder.addPluginMapping(api)
        api.init()
    }

    private fun isClassRegistered(className: String): Boolean =
        synchronized(APIHolder.allProviders) {
            APIHolder.allProviders.any { it::class.qualifiedName == className }
        }
}
