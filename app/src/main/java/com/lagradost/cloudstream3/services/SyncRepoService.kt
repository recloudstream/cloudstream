package com.lagradost.cloudstream3.services

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.FirebaseDatabase
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import java.io.File

object SyncRepoService {
    private const val FIREBASE_APP_NAME = "UserSyncApp"
    private var lastSyncAttemptTime = 0L
    private const val MIN_SYNC_INTERVAL = 30_000L // 30 seconds debounce
    const val SYNC_WORK_NAME = "firebase_sync_work"

    fun scheduleSync(context: Context, immediate: Boolean = false, forceUpload: Boolean = false) {
        val appContext = context.applicationContext
        val workManager = androidx.work.WorkManager.getInstance(appContext)

        val data = androidx.work.Data.Builder()
            .putBoolean("force_upload", forceUpload || immediate)
            .build()

        if (immediate) {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<FirebaseSyncWorker>()
                .setInputData(data)
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            
            workManager.enqueueUniqueWork(
                SYNC_WORK_NAME + "_immediate_" + System.currentTimeMillis(),
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d("SyncRepoService", "scheduleSync: Immediate sync enqueued")
        } else {
            // Debounced sync: wait 30 seconds before executing. 
            // If another one is scheduled, it replaces the pending one.
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<FirebaseSyncWorker>()
                .setInitialDelay(30, java.util.concurrent.TimeUnit.SECONDS)
                .setInputData(data)
                .build()

            workManager.enqueueUniqueWork(
                SYNC_WORK_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d("SyncRepoService", "scheduleSync: Delayed sync enqueued (debounce)")
        }
    }

    fun initFirebase(context: Context, pId: String? = null, key: String? = null, dbUrl: String? = null): Boolean {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val projectId = if (!pId.isNullOrEmpty()) pId else settings.getString("firebase_project_id", "") ?: ""
        val apiKey = if (!key.isNullOrEmpty()) key else settings.getString("firebase_api_key", "") ?: ""
        val url = if (!dbUrl.isNullOrEmpty()) dbUrl else settings.getString("firebase_url", "") ?: ""

        if (projectId.isEmpty() || apiKey.isEmpty() || url.isEmpty()) return false

        try {
            // Check if already initialized delete it to reload
            try {
                val existingApp = FirebaseApp.getInstance(FIREBASE_APP_NAME)
                existingApp.delete()
            } catch (e: Exception) {
                // Not exists
            }

            // Extract project ID from URL (e.g. https://xyz-default-rtdb... -> xyz)
            val projectIdFromUrl = url.substringAfter("https://").substringBefore("-default-rtdb").substringBefore(".firebaseio.com").replace("/", "")

            val options = FirebaseOptions.Builder()
                .setApiKey(apiKey)
                .setApplicationId(projectId) // This is actually the App ID (1:...)
                .setDatabaseUrl(url)
                .setProjectId(projectIdFromUrl) // Start routing correctly
                .build()

            FirebaseApp.initializeApp(context, options, FIREBASE_APP_NAME)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun testConnection(context: Context, projectId: String, apiKey: String, url: String, callback: (String?) -> Unit) {
        ioSafe {
            try {
                if (!initFirebase(context, projectId, apiKey, url)) {
                    main { callback("Initialization failed (Check API Key/ID)") }
                    return@ioSafe
                }
                val app = FirebaseApp.getInstance(FIREBASE_APP_NAME)
                val db = FirebaseDatabase.getInstance(app)
                val ref = db.reference.child("test_connection")
                
                try {
                    val task = ref.setValue(System.currentTimeMillis())
                    com.google.android.gms.tasks.Tasks.await(task, 10, java.util.concurrent.TimeUnit.SECONDS)
                    main { callback(null) }
                } catch (e: java.util.concurrent.TimeoutException) {
                    val netCheck = checkInternet()
                    main { callback("Timeout! \nApp ID: $projectId\nNet Check: $netCheck") }
                } catch (e: Exception) {
                    main { callback("Error: ${e.message}\nURL: $url") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                main { callback(e.message ?: "Unknown Error") }
            }
        }
    }

    private fun checkInternet(): String {
        return try {
            val connection = java.net.URL("https://www.google.com").openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 3000
            connection.connect()
            if (connection.responseCode == 200) "Success (200)" else "Failed (Code: ${connection.responseCode})"
        } catch (e: Exception) {
            "Error: ${e.javaClass.simpleName} - ${e.message}"
        }
    }

    private val FIREBASE_KEYS = listOf(
        "firebase_project_id", 
        "firebase_api_key", 
        "firebase_url",
        "download_path_key",
        "download_path_key_visual",
        "backup_path_key",
        "backup_dir_key",
        "biometric_key",
        "battery_optimisation",
        "anilist_cached_list",
        "mal_cached_list",
        "last_firebase_timestamp",
        "last_firebase_data_hash"
    )

    private fun getAllPrefs(context: Context): Map<String, Map<String, Any?>> {
        val allPrefs = mutableMapOf<String, Map<String, Any?>>()
        try {
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            if (prefsDir.exists() && prefsDir.isDirectory) {
                prefsDir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".xml")) {
                        processPrefFile(context, file, allPrefs)
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        
        ensureEssentialPrefs(context, allPrefs)
        return allPrefs
    }

    private fun processPrefFile(context: Context, file: File, allPrefs: MutableMap<String, Map<String, Any?>>) {
        val name = file.name.substringBeforeLast(".xml")
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val data = prefs.all.filterKeys { it !in FIREBASE_KEYS && it != null }
            .mapValues { entry -> 
                if (entry.value is Set<*>) (entry.value as Set<*>).toList() else entry.value
            }
            .mapKeys { sanitizeKey(it.key) }
        
        val nodeName = when(name) {
            "rebuild_preference" -> "app_data"
            context.packageName + "_preferences" -> "ui_settings"
            else -> "prefs_$name"
        }
        allPrefs[nodeName] = data
    }

    private fun ensureEssentialPrefs(context: Context, allPrefs: MutableMap<String, Map<String, Any?>>) {
        if (!allPrefs.containsKey("app_data")) {
            allPrefs["app_data"] = getSanitizedPrefs(context, "rebuild_preference")
        }
        if (!allPrefs.containsKey("ui_settings")) {
            allPrefs["ui_settings"] = getSanitizedPrefs(context, PreferenceManager.getDefaultSharedPreferences(context))
        }
    }

    private fun getSanitizedPrefs(context: Context, name: String): Map<String, Any?> {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        return getSanitizedPrefs(context, prefs)
    }

    private fun getSanitizedPrefs(context: Context, prefs: android.content.SharedPreferences): Map<String, Any?> {
        return prefs.all.filterKeys { it !in FIREBASE_KEYS && it != null }
            .mapValues { if (it.value is Set<*>) (it.value as Set<*>).toList() else it.value }
            .mapKeys { sanitizeKey(it.key) }
    }

    fun uploadAll(activity: android.app.Activity?) {
        if (activity == null) return
        ioSafe {
            if (!initFirebase(activity)) {
                return@ioSafe
            }
            val app = FirebaseApp.getInstance(FIREBASE_APP_NAME)
            val db = FirebaseDatabase.getInstance(app)
            val root = db.reference

            val allPrefs = getAllPrefs(activity.applicationContext)
            val repositories = com.lagradost.cloudstream3.plugins.RepositoryManager.getRepositories().toList()
            val timestamp = System.currentTimeMillis()

            val updates = hashMapOf<String, Any>(
                "repositories" to repositories,
                "timestamp" to timestamp
            )
            updates.putAll(allPrefs)

            root.setValue(updates).addOnSuccessListener {
                PreferenceManager.getDefaultSharedPreferences(activity).edit().putLong("last_firebase_timestamp", timestamp).apply()
            }.addOnFailureListener { e ->
                Log.e("SyncRepoService", "uploadAll: Failed", e)
            }
        }
    }

    fun uploadSilent(context: Context, forceUpload: Boolean = false) {
        ioSafe {
            uploadSync(context, forceUpload)
        }
    }

    suspend fun uploadSync(context: Context, forceUpload: Boolean = false): Boolean {
        val appContext = context.applicationContext
        val currentTime = System.currentTimeMillis()
        
        if (!forceUpload && currentTime - lastSyncAttemptTime < MIN_SYNC_INTERVAL) {
            Log.d("SyncRepoService", "uploadSync: Debounced")
            return true
        }
        lastSyncAttemptTime = currentTime

        return try {
            Log.d("SyncRepoService", "uploadSync: Starting sync")
            val settings = PreferenceManager.getDefaultSharedPreferences(appContext)
            val pId = settings.getString("firebase_project_id", "") ?: ""
            if (pId.isEmpty() || !initFirebase(appContext)) return false

            val allPrefs = gatherImportantPrefs(appContext)
            val repositories = getRepositoriesList()
            val currentDataHash = (allPrefs.hashCode() + repositories.hashCode()).toLong()
            val lastDataHash = settings.getLong("last_firebase_data_hash", 0L)
            
            if (!forceUpload && currentDataHash == lastDataHash) {
                Log.d("SyncRepoService", "uploadSync: No changes detected")
                return true
            }

            performDeltaUpdate(settings, allPrefs, repositories, currentDataHash)
            true
        } catch (e: Exception) {
            Log.e("SyncRepoService", "uploadSync: Critical Error", e)
            false
        }
    }

    private fun gatherImportantPrefs(context: Context): Map<String, Map<String, Any?>> {
        val allPrefs = mutableMapOf<String, Map<String, Any?>>()
        val importantFiles = listOf("rebuild_preference", "${context.packageName}_preferences", "data_store_helper", "search_history", "video_cache")
        importantFiles.forEach { name ->
            try {
                val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                val allEntries = prefs.all
                if (allEntries.isNotEmpty()) {
                    val nodeName = when(name) {
                        "rebuild_preference" -> "app_data"
                        "${context.packageName}_preferences" -> "ui_settings"
                        else -> "prefs_$name"
                    }
                    allPrefs[nodeName] = getSanitizedPrefs(context, prefs)
                }
            } catch (e: Exception) {
                Log.e("SyncRepoService", "Error reading pref file: $name", e)
            }
        }
        return allPrefs
    }

    private fun getRepositoriesList(): List<com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData> {
        return try {
            com.lagradost.cloudstream3.plugins.RepositoryManager.getRepositories().toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun performDeltaUpdate(
        settings: android.content.SharedPreferences,
        allPrefs: Map<String, Map<String, Any?>>,
        repositories: List<Any>,
        currentDataHash: Long
    ) {
        val db = FirebaseDatabase.getInstance(FirebaseApp.getInstance(FIREBASE_APP_NAME))
        val root = db.reference
        val snapshot = try {
            val getTask = root.get()
            com.google.android.gms.tasks.Tasks.await(getTask, 10, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) { null }

        val localTimestamp = settings.getLong("last_firebase_timestamp", 0L)
        val cloudTimestamp = snapshot?.child("timestamp")?.value as? Long ?: 0L

        if (localTimestamp == 0L && cloudTimestamp > 0L) {
            Log.d("SyncRepoService", "uploadSync: Cloud has data but this device is new. Aborting.")
            return
        }

        val timestamp = System.currentTimeMillis()
        val deltaUpdates = calculateDelta(snapshot, allPrefs, repositories, localTimestamp, timestamp)

        if (deltaUpdates.size > 1) {
            val updateTask = root.updateChildren(deltaUpdates)
            com.google.android.gms.tasks.Tasks.await(updateTask, 15, java.util.concurrent.TimeUnit.SECONDS)
        }
        
        settings.edit()
            .putLong("last_firebase_timestamp", timestamp)
            .putLong("last_firebase_data_hash", currentDataHash)
            .apply()
        Log.d("SyncRepoService", "uploadSync: Success")
    }

    private fun calculateDelta(
        snapshot: com.google.firebase.database.DataSnapshot?,
        allPrefs: Map<String, Map<String, Any?>>,
        repositories: List<Any>,
        localTimestamp: Long,
        timestamp: Long
    ): HashMap<String, Any?> {
        val deltaUpdates = hashMapOf<String, Any?>()
        deltaUpdates["timestamp"] = timestamp
        
        if (snapshot != null) {
            allPrefs.forEach { (nodeName, localMap) ->
                @Suppress("UNCHECKED_CAST")
                val cloudMap = snapshot.child(nodeName).value as? Map<String, Any?>
                localMap.forEach { (k, v) -> if (cloudMap?.get(k) != v) deltaUpdates["$nodeName/$k"] = v }
                if (localTimestamp > 0L) {
                    cloudMap?.keys?.forEach { k -> if (!localMap.containsKey(k)) deltaUpdates["$nodeName/$k"] = null }
                }
            }
            deltaUpdates["repositories"] = repositories
        } else {
            deltaUpdates["repositories"] = repositories
            allPrefs.forEach { (node, data) -> data.forEach { (k, v) -> deltaUpdates["$node/$k"] = v } }
        }
        return deltaUpdates
    }

    fun autoSyncCheck(activity: android.app.Activity) {
        val settings = PreferenceManager.getDefaultSharedPreferences(activity)
        val pId = settings.getString("firebase_project_id", "") ?: ""
        if (pId.isEmpty()) {
            Log.d("SyncRepoService", "autoSyncCheck: Firebase not configured, skipping.")
            return
        }

        ioSafe {
            if (!initFirebase(activity)) {
                main { 
                    CommonActivity.showToast(activity, "🔴 Auto-restore: Firebase init failed!", Toast.LENGTH_SHORT)
                }
                return@ioSafe
            }
            val db = FirebaseDatabase.getInstance(FirebaseApp.getInstance(FIREBASE_APP_NAME))
            db.reference.child("timestamp").get().addOnSuccessListener { snapshot ->
                val firebaseTimestamp = snapshot.value as? Long ?: 0L
                val localTimestamp = try {
                    settings.getLong("last_firebase_timestamp", 0L)
                } catch (e: Exception) {
                    settings.getInt("last_firebase_timestamp", 0).toLong()
                }

                Log.d("SyncRepoService", "autoSyncCheck: Firebase timestamp: $firebaseTimestamp, local: $localTimestamp")

                if (firebaseTimestamp > localTimestamp && firebaseTimestamp != 0L) {
                    downloadAll(activity, silent = true) {
                        settings.edit().putLong("last_firebase_timestamp", firebaseTimestamp).apply()
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("SyncRepoService", "autoSyncCheck: Failed", e)
            }
        }
    }

    @Suppress("DEPRECATION")
    fun downloadAll(activity: android.app.Activity?, silent: Boolean = true, onFinished: () -> Unit) {
        if (activity == null) return
        val context = activity.applicationContext
        ioSafe {
            if (!initFirebase(context)) return@ioSafe
            val db = FirebaseDatabase.getInstance(FirebaseApp.getInstance(FIREBASE_APP_NAME))
            db.reference.get().addOnSuccessListener { snapshot ->
                ioSafe {
                    try {
                        restoreAllPrefs(context, snapshot)
                        val restoredOnlinePlugins = getRestoredPlugins(snapshot)
                        cleanupExtraPlugins(context, restoredOnlinePlugins)
                        syncRepositoriesAndPlugins(activity, snapshot, restoredOnlinePlugins, silent)
                        
                        main {
                            finalizeRestore(activity, onFinished)
                        }
                    } catch (e: Exception) {
                        Log.e("SyncRepoService", "downloadAll: Error", e)
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("SyncRepoService", "downloadAll: Connection Failed", e)
            }
        }
    }

    private fun restoreAllPrefs(context: Context, snapshot: com.google.firebase.database.DataSnapshot) {
        snapshot.children.forEach { node ->
            val nodeName = node.key ?: return@forEach
            val prefName = when(nodeName) {
                "app_data" -> "rebuild_preference"
                "ui_settings" -> context.packageName + "_preferences"
                else -> if (nodeName.startsWith("prefs_")) nodeName.removePrefix("prefs_") else null
            } ?: return@forEach

            val editor = context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit()
            node.children.forEach { child ->
                val key = desanitizeKey(child.key ?: return@forEach)
                if (key !in FIREBASE_KEYS) putValue(editor, key, child.value)
            }
            editor.commit()
        }
    }

    private fun getRestoredPlugins(snapshot: com.google.firebase.database.DataSnapshot): List<com.lagradost.cloudstream3.plugins.PluginData> {
        val pluginsJson = snapshot.child("ui_settings").child(sanitizeKey("PLUGINS_KEY")).value as? String
            ?: snapshot.child("app_data").child(sanitizeKey("PLUGINS_KEY")).value as? String
            
        return if (!pluginsJson.isNullOrEmpty()) {
            try {
                com.lagradost.cloudstream3.utils.AppUtils.parseJson<Array<com.lagradost.cloudstream3.plugins.PluginData>>(pluginsJson).toList()
            } catch (e: Exception) { emptyList() }
        } else emptyList()
    }

    private fun cleanupExtraPlugins(context: Context, restoredPlugins: List<com.lagradost.cloudstream3.plugins.PluginData>) {
        val pluginDir = File(context.filesDir, "online_plugins")
        if (pluginDir.exists()) {
            pluginDir.listFiles()?.filter { it.isDirectory }?.forEach { repoFolder ->
                repoFolder.listFiles()?.forEach { pluginFile ->
                    if (restoredPlugins.none { it.filePath == pluginFile.absolutePath }) {
                        com.lagradost.cloudstream3.plugins.PluginManager.unloadPlugin(pluginFile.absolutePath)
                        pluginFile.delete()
                    }
                }
            }
        }
    }

    private suspend fun syncRepositoriesAndPlugins(
        activity: android.app.Activity,
        snapshot: com.google.firebase.database.DataSnapshot,
        restoredPlugins: List<com.lagradost.cloudstream3.plugins.PluginData>,
        silent: Boolean
    ) {
        val context = activity.applicationContext
        val reposSnap = snapshot.child("repositories")
        val restoredRepos = mutableListOf<com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData>()
        reposSnap.children.forEach { child ->
            try {
                @Suppress("UNCHECKED_CAST")
                val map = child.value as? Map<String, Any> ?: return@forEach
                val url = map["url"] as? String ?: return@forEach
                restoredRepos.add(com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData(map["name"] as? String ?: "Unknown", url))
            } catch (e: Exception) { e.printStackTrace() }
        }

        restoredRepos.forEach { repo ->
            try {
                com.lagradost.cloudstream3.plugins.RepositoryManager.getRepoPlugins(repo.url)?.forEach { (url, site) ->
                    val isNeeded = restoredPlugins.any { it.internalName.equals(site.internalName, true) }
                    val local = com.lagradost.cloudstream3.plugins.PluginManager.getPluginPath(context, site.internalName, repo.url)
                    if (isNeeded && !local.exists()) {
                        com.lagradost.cloudstream3.plugins.PluginManager.downloadPlugin(activity, url, site.internalName, repo.url, true, silent)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private suspend fun finalizeRestore(activity: android.app.Activity, onFinished: () -> Unit) {
        @Suppress("DEPRECATION_ERROR")
        com.lagradost.cloudstream3.plugins.PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_manuallyReloadAndUpdatePlugins(activity, true)
        com.lagradost.cloudstream3.utils.DataStore.forceReloadAll()
        com.lagradost.cloudstream3.syncproviders.AccountManager.reloadAll()
        com.lagradost.cloudstream3.MainActivity.afterPluginsLoadedEvent.invoke(true)
        com.lagradost.cloudstream3.MainActivity.reloadHomeEvent.invoke(true)
        com.lagradost.cloudstream3.MainActivity.bookmarksUpdatedEvent.invoke(true)
        com.lagradost.cloudstream3.MainActivity.reloadLibraryEvent.invoke(true)
        onFinished()
        activity.recreate()
    }

    private fun sanitizeKey(key: String): String {
        return key.replace(".", "_DOT_")
            .replace("/", "_SLASH_")
            .replace("#", "_HASH_")
            .replace("$", "_DOLLAR_")
            .replace("[", "_LBR_")
            .replace("]", "_RBR_")
    }

    private fun desanitizeKey(key: String): String {
        return key.replace("_DOT_", ".")
            .replace("_SLASH_", "/")
            .replace("_HASH_", "#")
            .replace("_DOLLAR_", "$")
            .replace("_LBR_", "[")
            .replace("_RBR_", "]")
    }

    @Suppress("UNCHECKED_CAST")
    private fun putValue(editor: android.content.SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> {
                // SharedPreferences distinguishes Int and Long
                if (value > Int.MAX_VALUE || value < Int.MIN_VALUE) {
                    editor.putLong(key, value)
                } else {
                    editor.putInt(key, value.toInt())
                }
            }
            is Double -> editor.putFloat(key, value.toFloat())
            is Float -> editor.putFloat(key, value)
            is String -> {
                // Try to parse back if it was supposed to be a Json/DataStore value
                // Actually, DataStore always writes strings, so this is correct.
                editor.putString(key, value)
            }
            is Map<*, *> -> {
                // If it's a map (Firebase object), it shouldn't normally happen for a single SharedPreferences key 
                // UNLESS it's a serialized object that was uploaded as a map (though we usually upload JSON strings).
                // But for safety, we don't handle it here yet.
            }
            is List<*> -> {
                if (value.all { it is String }) {
                    editor.putStringSet(key, (value as List<String>).toSet())
                }
            }
        }
    }
}
