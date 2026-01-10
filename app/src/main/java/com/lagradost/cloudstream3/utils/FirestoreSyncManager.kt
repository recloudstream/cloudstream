package com.lagradost.cloudstream3.utils

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.DataStore.getDefaultSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStore.setKeyLocal
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.PLUGINS_KEY
import com.lagradost.cloudstream3.ui.settings.extensions.REPOSITORIES_KEY

import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import kotlin.math.max
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.AutoDownloadMode
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.utils.AppContextUtils.isNetworkAvailable
import androidx.core.content.edit
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import com.lagradost.cloudstream3.plugins.PluginData
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Firebase Firestore synchronization.
 * Follows a "Netflix-style" cross-device sync with conflict resolution.
 */
object FirestoreSyncManager : androidx.lifecycle.DefaultLifecycleObserver {
    private const val TAG = "FirestoreSync"
    private const val SYNC_COLLECTION = "users"
    private const val SYNC_DOCUMENT = "sync_data"
    
    private var db: FirebaseFirestore? = null
    private var userId: String? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isInitializing = AtomicBoolean(false)
    private var isConnected = false
    
    private val throttleJobs = ConcurrentHashMap<String, Job>()
    private val throttleBatch = ConcurrentHashMap<String, Any?>()
    
    private val syncLogs = mutableListOf<String>()
    
    fun getLogs(): String {
        return syncLogs.joinToString("\n")
    }

    private fun log(message: String) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val entry = "[${sdf.format(Date())}] $message"
        syncLogs.add(entry)
        if (syncLogs.size > 100) syncLogs.removeAt(0)
        Log.d(TAG, entry)
    }
    
    // Config keys in local DataStore
    const val FIREBASE_API_KEY = "firebase_api_key"
    const val FIREBASE_PROJECT_ID = "firebase_project_id"
    const val FIREBASE_APP_ID = "firebase_app_id"
    const val FIREBASE_ENABLED = "firebase_sync_enabled"
    const val FIREBASE_LAST_SYNC = "firebase_last_sync"
    const val DEFAULT_USER_ID = "mirror_account" // Hardcoded for 100% mirror sync
    private const val ACCOUNTS_KEY = "data_store_helper/account"
    private const val SETTINGS_SYNC_KEY = "settings"
    private const val DATA_STORE_DUMP_KEY = "data_store_dump"

    data class SyncConfig(
        val apiKey: String,
        val projectId: String,
        val appId: String
    )

    override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
        super.onStop(owner)
        log("App backgrounded/stopped. Triggering sync...")
        CommonActivity.activity?.let { pushAllLocalData(it) }
    }

    fun isEnabled(context: Context): Boolean {
        return context.getKey(FIREBASE_ENABLED, false) ?: false
    }

    fun isOnline(): Boolean {
        return isConnected && db != null
    }

    fun initialize(context: Context) {
        // Register lifecycle observer
        com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread {
            try {
                androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            } catch (e: Exception) {
                log("Failed to register lifecycle observer: ${e.message}")
            }
        }

        log("Auto-initializing sync...")
        val isNetwork = context.isNetworkAvailable()
        log("Network available: $isNetwork")
        
        val prefs = context.getSharedPrefs()
        log("Raw API Key: '${prefs.getString(FIREBASE_API_KEY, null)}'")
        log("Raw project: '${prefs.getString(FIREBASE_PROJECT_ID, null)}'")
        log("Raw app ID: '${prefs.getString(FIREBASE_APP_ID, null)}'")
        val enabled = isEnabled(context)
        log("Sync enabled: $enabled")
        
        if (!enabled) {
            log("Sync is disabled in settings.")
            return
        }

        // Debugging Config Parsing
        val rawApiKey = prefs.getString(FIREBASE_API_KEY, "") ?: ""
        val rawProjId = prefs.getString(FIREBASE_PROJECT_ID, "") ?: ""
        val rawAppId = prefs.getString(FIREBASE_APP_ID, "") ?: ""
        
        log("Debug - Raw Prefs: API='$rawApiKey', Proj='$rawProjId', App='$rawAppId'")
        
        val keyFromStore = context.getKey<String>(FIREBASE_API_KEY)
        log("Debug - DataStore.getKey: '$keyFromStore'")

        // Manual cleanup as fallback if DataStore fails
        fun cleanVal(raw: String): String {
            var v = raw.trim()
            if (v.startsWith("\"") && v.endsWith("\"") && v.length >= 2) {
                v = v.substring(1, v.length - 1)
            }
            return v
        }

        val config = SyncConfig(
            apiKey = if (!keyFromStore.isNullOrBlank()) keyFromStore else cleanVal(rawApiKey),
            projectId = context.getKey(FIREBASE_PROJECT_ID, "") ?: cleanVal(rawProjId),
            appId = context.getKey(FIREBASE_APP_ID, "") ?: cleanVal(rawAppId)
        )
        log("Parsed config: API='${config.apiKey}', Proj='${config.projectId}', App='${config.appId}'")
        
        if (config.apiKey.isBlank() || config.projectId.isBlank() || config.appId.isBlank()) {
            log("Sync config is incomplete: API Key=${config.apiKey.isNotBlank()}, project=${config.projectId.isNotBlank()}, app=${config.appId.isNotBlank()}")
            return
        }
        initialize(context, config)
    }

    /**
     * Initializes Firebase with custom options provided by the user.
     */
    fun initialize(context: Context, config: SyncConfig) {
        log("Initialize(config) called. Proj=${config.projectId}")
        userId = DEFAULT_USER_ID // Set to hardcoded mirror ID
        
        if (isInitializing.getAndSet(true)) {
            log("Initialization already IN PROGRESS (isInitializing=true).")
            return
        }
        
        scope.launch {
            log("Coroutine launch started...")
            try {
                val options = FirebaseOptions.Builder()
                    .setApiKey(config.apiKey)
                    .setProjectId(config.projectId)
                    .setApplicationId(config.appId)
                    .build()

                // Use project ID as app name to avoid collisions
                val appName = "sync_${config.projectId.replace(":", "_")}"
                val app = try {
                    FirebaseApp.getInstance(appName)
                } catch (e: Exception) {
                    FirebaseApp.initializeApp(context, options, appName)
                }

                db = FirebaseFirestore.getInstance(app)
                isConnected = true
                log("Firestore instance obtained. UID: $userId")
                
                // Save config
                log("Saving config to DataStore...")
                context.setKey(FIREBASE_API_KEY, config.apiKey)
                context.setKey(FIREBASE_PROJECT_ID, config.projectId)
                context.setKey(FIREBASE_APP_ID, config.appId)
                context.setKey(FIREBASE_ENABLED, true)

                // Start initial sync
                handleInitialSync(context)
                // Start listening for changes (Mirroring)
                setupRealtimeListener(context)
                
                Log.d(TAG, "Firebase initialized successfully")
                log("Initialization SUCCESSFUL.")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize Firebase: ${e.message}")
                log("Initialization EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                isConnected = false
            } finally {
                log("Setting isInitializing to false (finally).")
                isInitializing.set(false)
            }
        }
    }

    private fun handleInitialSync(context: Context) {
        val currentUserId = userId
        val currentDb = db
        if (currentUserId == null || currentDb == null) {
            log("Cannot handle initial sync: userId or db is null")
            return
        }
        log("Starting initial sync for user: $currentUserId")
        
        val userDoc = currentDb.collection(SYNC_COLLECTION).document(currentUserId)
        
        userDoc.get().addOnSuccessListener { document ->
            if (document.exists()) {
                log("Remote data exists. Applying to local.")
                applyRemoteData(context, document)
            } else {
                log("Remote database is empty. Uploading local data as baseline.")
                pushAllLocalData(context)
            }
        }.addOnFailureListener { e ->
            log("Initial sync FAILED: ${e.message}")
        }.addOnCompleteListener {
            log("Initial sync task completed.")
            updateLastSyncTime(context)
        }
    }

    private fun updateLastSyncTime(context: Context) {
        val now = System.currentTimeMillis()
        context.setKeyLocal(FIREBASE_LAST_SYNC, now)
    }

    fun getLastSyncTime(context: Context): Long? {
        return context.getKey(FIREBASE_LAST_SYNC, 0L).let { if (it == 0L) null else it }
    }

    private fun setupRealtimeListener(context: Context) {
        val currentUserId = userId
        val currentDb = db
        if (currentUserId == null || currentDb == null) {
            Log.e(TAG, "Cannot setup listener: userId and/or db is null")
            return
        }
        
        currentDb.collection(SYNC_COLLECTION).document(currentUserId).addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                Log.d(TAG, "Current data: ${snapshot.data}")
                scope.launch {
                    applyRemoteData(context, snapshot)
                }
            }
        }
    }

    /**
     * Pushes specific data to Firestore with a server timestamp.
     */
    fun pushData(key: String, data: Any?) {
        val currentDb = db ?: return
        val currentUserId = userId ?: return
        
        scope.launch {
            try {
                val update = hashMapOf<String, Any?>(
                    key to data,
                    "${key}_updated" to FieldValue.serverTimestamp(),
                    "last_sync" to FieldValue.serverTimestamp()
                )

                currentDb.collection(SYNC_COLLECTION).document(currentUserId)
                    .set(update, SetOptions.merge())
                    .addOnSuccessListener { 
                        Log.d(TAG, "Successfully pushed $key")
                        log("Pushed key: $key")
                    }
                    .addOnFailureListener { e -> 
                        Log.e(TAG, "Error pushing $key: ${e.message}")
                        log("FAILED to push $key: ${e.message}")
                    }
            } catch (e: Throwable) {
                log("PushData throw: ${e.message}")
            }
        }
    }

    private var debounceJob: Job? = null

    fun pushAllLocalData(context: Context) {
        if (isInitializing.get()) {
            log("Sync is initializing, skipping immediate push.")
            return
        }

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(5000) // Debounce for 5 seconds
            performPushAllLocalData(context)
        }
    }

    private suspend fun performPushAllLocalData(context: Context) {
        log("Pushing all local data (background)...")
        val currentUserId = userId
        val currentDb = db
        if (currentUserId == null || currentDb == null) {
            log("Cannot push all data: userId or db is null")
            return
        }

        try {
            val allData = extractAllLocalData(context)
            val update = mutableMapOf<String, Any?>()
            allData.forEach { (key, value) ->
                update[key] = value
                update["${key}_updated"] = FieldValue.serverTimestamp()
            }
            update["last_sync"] = FieldValue.serverTimestamp()

            currentDb.collection(SYNC_COLLECTION).document(currentUserId).set(update, SetOptions.merge())
                .addOnSuccessListener {
                    log("Successfully pushed all local data.")
                    updateLastSyncTime(context)
                }
                .addOnFailureListener { e ->
                    log("Failed to push all local data: ${e.message}")
                }
        } catch (e: Throwable) {
            log("PushAllLocalData error: ${e.message}")
        }
    }

    private fun extractAllLocalData(context: Context): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>()
        val sensitiveKeys = setOf(
            FIREBASE_API_KEY, FIREBASE_PROJECT_ID, 
            FIREBASE_APP_ID, FIREBASE_ENABLED, 
            FIREBASE_LAST_SYNC,
            "firebase_sync_enabled" // Just in case of legacy names
        )
        
        // 1. Settings (PreferenceManager's default prefs)
        val settingsMap = context.getDefaultSharedPrefs().all.filter { entry ->
            !sensitiveKeys.contains(entry.key)
        }
        data[SETTINGS_SYNC_KEY] = settingsMap.toJson()

        // 2. Repositories
        data[REPOSITORIES_KEY] = context.getSharedPrefs().getString(REPOSITORIES_KEY, null)

        // 3. Accounts (DataStore rebuild_preference)
        data[ACCOUNTS_KEY] = context.getSharedPrefs().getString(ACCOUNTS_KEY, null)

        // 4. Generic DataStore Keys (Resume Watching, Watch State, etc.)
        // This captures everything in the DataStore preferences that we haven't explicitly handled
        val dataStoreMap = context.getSharedPrefs().all.filter { (key, value) ->
            !sensitiveKeys.contains(key) && 
            key != REPOSITORIES_KEY &&
            key != ACCOUNTS_KEY &&
            key != PLUGINS_KEY &&
            !key.contains(RESULT_RESUME_WATCHING) &&
            !key.contains(RESULT_RESUME_WATCHING_DELETED) &&
            value is String // DataStore saves as JSON Strings
        }
        data[DATA_STORE_DUMP_KEY] = dataStoreMap.toJson()

        // 5. Home Settings (Search for home related keys in DataStore)
        val homeKeys = context.getKeys("home")
        val homeData = homeKeys.associateWith { context.getSharedPrefs().all[it] }
        data["home_settings"] = homeData.toJson()

        // 6. Plugins (Online ones)
        data["plugins_online"] = context.getSharedPrefs().getString(PLUGINS_KEY, null)

        // 7. Resume Watching (CRDT)
        val resumeIds = DataStoreHelper.getAllResumeStateIds() ?: emptyList()
        val resumeData = resumeIds.mapNotNull { DataStoreHelper.getLastWatched(it) }
        data["resume_watching"] = resumeData.toJson()

        val deletedResumeIds = DataStoreHelper.getAllResumeStateDeletionIds() ?: emptyList()
        val deletedResumeData = deletedResumeIds.associateWith { DataStoreHelper.getLastWatchedDeletionTime(it) ?: 0L }
        data["resume_watching_deleted"] = deletedResumeData.toJson()

        return data
    }

    private fun applyRemoteData(context: Context, snapshot: DocumentSnapshot) {
        val remoteData = snapshot.data ?: return
        val lastSyncTime = getLastSyncTime(context) ?: 0L

        applySettings(context, remoteData)
        applyDataStoreDump(context, remoteData)
        applyRepositories(context, remoteData)
        applyAccounts(context, remoteData)
        applyHomeSettings(context, remoteData)
        applyPlugins(context, remoteData, lastSyncTime)
        applyResumeWatching(context, remoteData)
        
        log("Remote data alignment finished successfully.")
    }

    private fun applySettings(context: Context, remoteData: Map<String, Any?>) {
        (remoteData[SETTINGS_SYNC_KEY] as? String)?.let { json ->
            try {
                val settingsMap = parseJson<Map<String, Any?>>(json)
                var hasChanges = false
                val prefs = context.getDefaultSharedPrefs()
                val editor = prefs.edit()
                
                settingsMap.forEach { (key, value) ->
                    val currentVal = prefs.all[key]
                    if (currentVal != value) {
                        hasChanges = true
                        when (value) {
                            is Boolean -> editor.putBoolean(key, value)
                            is Int -> editor.putInt(key, value)
                            is String -> editor.putString(key, value)
                            is Float -> editor.putFloat(key, value)
                            is Long -> editor.putLong(key, value)
                        }
                    }
                }
                
                if (hasChanges) {
                    editor.apply()
                    log("Settings applied (changed).")
                    MainActivity.reloadHomeEvent(true)
                }
            } catch (e: Exception) { log("Failed to apply settings: ${e.message}") }
        }
    }

    private fun applyDataStoreDump(context: Context, remoteData: Map<String, Any?>) {
        (remoteData[DATA_STORE_DUMP_KEY] as? String)?.let { json ->
            try {
                val dataStoreMap = parseJson<Map<String, Any?>>(json)
                val prefs = context.getSharedPrefs()
                val editor = prefs.edit()
                var hasChanges = false

                dataStoreMap.forEach { (key, value) ->
                    if (value is String) {
                        val currentVal = prefs.getString(key, null)
                        if (currentVal != value) {
                            editor.putString(key, value)
                            hasChanges = true
                        }
                    }
                }
                if (hasChanges) {
                    editor.apply()
                    log("DataStore dump applied (changed).")
                }
            } catch (e: Exception) { log("Failed to apply DataStore dump: ${e.message}") }
        }
    }

    private fun applyRepositories(context: Context, remoteData: Map<String, Any?>) {
        (remoteData[REPOSITORIES_KEY] as? String)?.let { json ->
            try {
                val current = context.getSharedPrefs().getString(REPOSITORIES_KEY, null)
                if (current != json) {
                    log("Applying remote repositories (changed)...")
                    context.getSharedPrefs().edit {
                        putString(REPOSITORIES_KEY, json)
                    }
                }
            } catch (e: Exception) { log("Failed to apply repos: ${e.message}") }
        }
    }

    private fun applyAccounts(context: Context, remoteData: Map<String, Any?>) {
        (remoteData[ACCOUNTS_KEY] as? String)?.let { json ->
            try {
                val current = context.getSharedPrefs().getString(ACCOUNTS_KEY, null)
                if (current != json) {
                    log("Applying remote accounts (changed)...")
                    context.getSharedPrefs().edit {
                        putString(ACCOUNTS_KEY, json)
                    }
                    MainActivity.reloadAccountEvent(true)
                    MainActivity.bookmarksUpdatedEvent(true)
                }
            } catch (e: Exception) { log("Failed to apply accounts: ${e.message}") }
        }
    }

    private fun applyHomeSettings(context: Context, remoteData: Map<String, Any?>) {
        (remoteData["home_settings"] as? String)?.let { json ->
            try {
                val homeMap = parseJson<Map<String, Any?>>(json)
                val prefs = context.getSharedPrefs()
                val editor = prefs.edit()
                var hasChanges = false

                homeMap.forEach { (key, value) ->
                    val currentVal = prefs.all[key]
                    if (currentVal != value) {
                        hasChanges = true
                        when (value) {
                            is Boolean -> editor.putBoolean(key, value)
                            is Int -> editor.putInt(key, value)
                            is String -> editor.putString(key, value)
                            is Float -> editor.putFloat(key, value)
                            is Long -> editor.putLong(key, value)
                        }
                    }
                }
                
                if (hasChanges) {
                    editor.apply()
                    log("Home settings applied (changed).")
                    MainActivity.reloadHomeEvent(true)
                }
            } catch (e: Exception) { log("Failed to apply home settings: ${e.message}") }
        }
    }

    private fun applyPlugins(context: Context, remoteData: Map<String, Any?>, lastSyncTime: Long) {
        (remoteData["plugins_online"] as? String)?.let { json ->
            try {
                // Parse lists
                val remoteList = parseJson<Array<PluginData>>(json).toList()
                val localJson = context.getSharedPrefs().getString(PLUGINS_KEY, "[]")
                val localList = try { parseJson<Array<PluginData>>(localJson ?: "[]").toList() } catch(e:Exception) { emptyList() }

                // Merge Maps
                val remoteMap = remoteList.associateBy { it.internalName }
                val localMap = localList.associateBy { it.internalName }
                val allKeys = (remoteMap.keys + localMap.keys).toSet()

                val mergedList = allKeys.mapNotNull { key ->
                    val remote = remoteMap[key]
                    val local = localMap[key]

                    when {
                        remote != null && local != null -> {
                            // Conflict: Last Write Wins based on addedDate
                            if (remote.addedDate >= local.addedDate) remote else local
                        }
                        remote != null -> {
                            // only remote knows about it
                            remote
                        }
                        local != null -> {
                            // only local knows about it
                            if (local.addedDate > lastSyncTime) {
                                // New local addition not yet synced
                                local
                            } else {
                                // Old local, missing from remote -> Treat as Remote Deletion (Legacy/Reset)
                                local.copy(isDeleted = true, addedDate = System.currentTimeMillis())
                            }
                        }
                        else -> null
                    }
                }

                if (mergedList != localList) {
                    log("Sync applied (CRDT merge). Total: ${mergedList.size}")
                    
                    // Actuate Deletions
                    mergedList.filter { it.isDeleted }.forEach { p ->
                        try {
                             val file = File(p.filePath)
                             if (file.exists()) {
                                 log("Deleting plugin (Tombstone): ${p.internalName}")
                                 PluginManager.unloadPlugin(p.filePath)
                                 file.delete()
                             }
                        } catch(e: Exception) { log("Failed to delete ${p.internalName}: ${e.message}") }
                    }

                    context.getSharedPrefs().edit {
                        putString(PLUGINS_KEY, mergedList.toJson())
                    }

                    // Trigger Download for Alive plugins
                    if (mergedList.any { !it.isDeleted }) {
                        CommonActivity.activity?.let { act ->
                            scope.launch {
                                try {
                                    @Suppress("DEPRECATION_ERROR")
                                    PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_downloadNotExistingPluginsAndLoad(
                                        act, 
                                        AutoDownloadMode.All
                                    )
                                } catch (e: Exception) { log("Plugin download error: ${e.message}") }
                            }
                        }
                    }
                }
            } catch (e: Exception) { log("Failed to apply plugins: ${e.message}") }
        }
    }

    private fun applyResumeWatching(context: Context, remoteData: Map<String, Any?>) {
        val remoteResumeJson = remoteData["resume_watching"] as? String
        val remoteDeletedJson = remoteData["resume_watching_deleted"] as? String
        
        if (remoteResumeJson != null || remoteDeletedJson != null) {
             try {
                 val remoteAlive = if (remoteResumeJson != null) parseJson<List<VideoDownloadHelper.ResumeWatching>>(remoteResumeJson) else emptyList()
                 val remoteDeleted = if (remoteDeletedJson != null) parseJson<Map<String, Long>>(remoteDeletedJson) else emptyMap()

                 val localAliveIds = DataStoreHelper.getAllResumeStateIds() ?: emptyList()
                 val localAliveMap = localAliveIds.mapNotNull { DataStoreHelper.getLastWatched(it) }.associateBy { it.parentId.toString() }
                 
                 val localDeletedIds = DataStoreHelper.getAllResumeStateDeletionIds() ?: emptyList()
                 val localDeletedMap = localDeletedIds.associate { it.toString() to (DataStoreHelper.getLastWatchedDeletionTime(it) ?: 0L) }

                 // 1. Merge Deletions (Max Timestamp wins)
                 val allDelKeys = remoteDeleted.keys + localDeletedMap.keys
                 val mergedDeleted = allDelKeys.associateWith { key ->
                     maxOf(remoteDeleted[key] ?: 0L, localDeletedMap[key] ?: 0L)
                 }

                 handleResumeZombies(mergedDeleted, localAliveMap)
                 handleResumeAlive(remoteAlive, mergedDeleted, localAliveMap)
                 
             } catch(e: Exception) { log("Failed to apply resume watching: ${e.message}") }
        }
    }

    private fun handleResumeZombies(
        mergedDeleted: Map<String, Long>, 
        localAliveMap: Map<String, VideoDownloadHelper.ResumeWatching>
    ) {
         // 2. Identify Zombies (Local Alive but Merged Deleted is newer)
         mergedDeleted.forEach { (id, delTime) ->
             val alive = localAliveMap[id]
             if (alive != null) {
                 // If Deletion is NEWER than Alive Update -> KILL
                 if (delTime >= alive.updateTime) {
                     log("CRDT: Killing Zombie ResumeWatching $id")
                     com.lagradost.cloudstream3.CloudStreamApp.removeKey("${DataStoreHelper.currentAccount}/$com.lagradost.cloudstream3.utils.DataStoreHelper.RESULT_RESUME_WATCHING", id)
                     // Ensure tombstone is up to date
                     DataStoreHelper.setLastWatchedDeletionTime(id.toIntOrNull(), delTime) 
                 } else {
                     // Alive is newer. Re-vivified. Un-delete locally if deleted record exists.
                     com.lagradost.cloudstream3.CloudStreamApp.removeKey("${DataStoreHelper.currentAccount}/$com.lagradost.cloudstream3.utils.DataStoreHelper.RESULT_RESUME_WATCHING_DELETED", id)
                 }
             } else {
                 // Ensure tombstone is present locally
                 DataStoreHelper.setLastWatchedDeletionTime(id.toIntOrNull(), delTime)
             }
         }
    }

    private fun handleResumeAlive(
        remoteAlive: List<VideoDownloadHelper.ResumeWatching>,
        mergedDeleted: Map<String, Long>,
        localAliveMap: Map<String, VideoDownloadHelper.ResumeWatching>
    ) {
         // 3. Process Remote Alive
         remoteAlive.forEach { remoteItem ->
             val id = remoteItem.parentId.toString()
             val delTime = mergedDeleted[id] ?: 0L
             
             // If Remote Alive is OLDER than Deletion -> Ignore (it's dead)
             if (remoteItem.updateTime <= delTime) return@forEach

             val localItem = localAliveMap[id]
             if (localItem == null) {
                 // New Item!
                 log("CRDT: Adding ResumeWatching $id")
                 DataStoreHelper.setLastWatched(remoteItem.parentId, remoteItem.episodeId, remoteItem.episode, remoteItem.season, remoteItem.isFromDownload, remoteItem.updateTime)
             } else {
                 // Conflict: LWW (Timestamp)
                 if (remoteItem.updateTime > localItem.updateTime) {
                     log("CRDT: Updating ResumeWatching $id (Remote Newer)")
                     DataStoreHelper.setLastWatched(remoteItem.parentId, remoteItem.episodeId, remoteItem.episode, remoteItem.season, remoteItem.isFromDownload, remoteItem.updateTime)
                 }
             }
         }
    }
}
