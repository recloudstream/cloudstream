package com.lagradost.cloudstream3.services

import android.content.Context
import androidx.work.*
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.utils.TestingUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.TimeUnit

class MaintenanceWorkManager(val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    companion object {
        const val MAINTENANCE_WORK_NAME = "work_maintenance"

        fun enqueuePeriodicWork(context: Context?) {
            if (context == null) return

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(requiresBatteryNotLow = true)
                .build()

            val periodicWork =
                PeriodicWorkRequest.Builder(MaintenanceWorkManager::class.java, 24, TimeUnit.HOURS)
                    .addTag(MAINTENANCE_WORK_NAME)
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                MAINTENANCE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )
        }
    }

    override suspend fun doWork(): Result {
        try {
            // Load all plugins
            PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins(context)
            PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllLocalPlugins(
                context = context,
                forceReload = false
            )

            val apis = APIHolder.allProviders.withLock { APIHolder.allProviders.toTypedArray() }
            
            // Map to track successes per plugin path
            val pluginSuccess = mutableMapOf<String, Boolean>()
            val pluginPaths = apis.mapNotNull { it.sourcePlugin }.distinct()
            pluginPaths.forEach { pluginSuccess[it] = false }

            val scope = CoroutineScope(Dispatchers.Default + kotlinx.coroutines.Job())
            TestingUtils.getDeferredProviderTests(scope, apis) { api, result ->
                val path = api.sourcePlugin ?: return@getDeferredProviderTests
                if (result.success) {
                    pluginSuccess[path] = true
                }
            }
            
            // Wait for tests to finish
            scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }

            pluginSuccess.forEach { (path, success) ->
                if (!success) {
                    // All providers in this plugin failed
                    PluginManager.setPluginDisabled(path = path, disabled = true)
                }
            }

            return Result.success()
        } catch (ignored: Throwable) {
            return Result.failure()
        }
    }
}