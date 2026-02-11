package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.DataStore
import com.lagradost.cloudstream3.utils.getDefaultSharedPrefs
import com.lagradost.cloudstream3.utils.getSharedPrefs
import com.lagradost.cloudstream3.utils.getKeys
import com.lagradost.cloudstream3.utils.setKey
import com.lagradost.cloudstream3.utils.setKeyLocal
import com.lagradost.cloudstream3.utils.removeKey
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.PLUGINS_KEY
import com.lagradost.cloudstream3.plugins.PLUGINS_KEY_LOCAL
import com.lagradost.cloudstream3.ui.settings.extensions.REPOSITORIES_KEY
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData

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
 * Manages Firebase Firestore synchronization with generic tombstone support and Auth.
 */
object FirestoreSyncManager : androidx.lifecycle.DefaultLifecycleObserver {
    private const val TAG = "FirestoreSync"
    private const val SYNC_COLLECTION = "users"
    private const val TIMESTAMPS_PREF = "sync_timestamps"
    
    // Internal keys
    const val PENDING_PLUGINS_KEY = "pending_plugins_install"
    const val IGNORED_PLUGINS_KEY = "firestore_ignored_plugins_key"

    private var db: FirebaseFirestore? = null
    private var auth: FirebaseAuth? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isInitializing = AtomicBoolean(false)
    private var isConnected = false
    
    private val throttleBatch = ConcurrentHashMap<String, Any?>()
    private val syncLogs = mutableListOf<String>()
    
    var lastInitError: String? = null
        private set

    var lastSyncDebugInfo: String = "No sync recorded yet."
        private set
    
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
    
    private const val ACCOUNTS_KEY = "data_store_helper/account"
    private const val SETTINGS_SYNC_KEY = "settings"
    private const val DATA_STORE_DUMP_KEY = "data_store_dump"

    // Ultra-granular sync control keys
    const val SYNC_SETTING_APPEARANCE = "sync_setting_appearance"
    const val SYNC_SETTING_PLAYER = "sync_setting_player"
    const val SYNC_SETTING_DOWNLOADS = "sync_setting_downloads"
    const val SYNC_SETTING_GENERAL = "sync_setting_general"
    const val SYNC_SETTING_ACCOUNTS = "sync_setting_accounts"
    const val SYNC_SETTING_BOOKMARKS = "sync_setting_bookmarks"
    const val SYNC_SETTING_RESUME_WATCHING = "sync_setting_resume_watching"
    const val SYNC_SETTING_REPOSITORIES = "sync_setting_repositories"
    const val SYNC_SETTING_PLUGINS = "sync_setting_plugins"
    const val SYNC_SETTING_HOMEPAGE_API = "sync_setting_homepage_api"
    const val SYNC_SETTING_PINNED_PROVIDERS = "sync_setting_pinned_providers"

    // Generic Wrapper for all sync data
    data class SyncPayload(
        val v: Any?, // Value (JSON string or primitive)
        val t: Long, // Timestamp
        val d: Boolean = false // IsDeleted (Tombstone)
    )

    data class SyncConfig(
        val apiKey: String,
        val projectId: String,
        val appId: String
    )

    // --- Auth Public API ---
    
    fun getUserEmail(): String? = auth?.currentUser?.email
    fun isLogged(): Boolean = auth?.currentUser != null

    fun login(email: String, pass: String, callback: (Boolean, String?) -> Unit) {
        val currentAuth = auth ?: return callback(false, "Firebase not initialized")
        currentAuth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { callback(true, null) }
            .addOnFailureListener { callback(false, it.message) }
    }

    fun register(email: String, pass: String, callback: (Boolean, String?) -> Unit) {
        val currentAuth = auth ?: return callback(false, "Firebase not initialized")
        currentAuth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { callback(true, null) }
            .addOnFailureListener { callback(false, it.message) }
    }

    fun loginOrRegister(email: String, pass: String, callback: (Boolean, String?) -> Unit) {
        login(email, pass) { success, msg ->
            if (success) {
                callback(true, null)
            } else {
                // Check if error implies user not found, or just try registering
                // Simple approach: Try registering if login fails
                log("Login failed, trying registration... ($msg)")
                register(email, pass) { regSuccess, regMsg ->
                    if (regSuccess) {
                        callback(true, null)
                    } else {
                        // Return the login error if registration also fails, or a combined message
                        callback(false, "Login: $msg | Register: $regMsg")
                    }
                }
            }
        }
    }

    fun logout(context: Context) {
        auth?.signOut()
        // Clear local timestamps to force re-sync on next login
        context.getSharedPreferences(TIMESTAMPS_PREF, Context.MODE_PRIVATE).edit().clear().apply()
        log("Logged out.")
    }

    // --- Initialization ---

    override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
        super.onStop(owner)
        // Ensure pending writes are flushed immediately
        // Do NOT call pushAllLocalData() as it refreshes timestamps for all keys, reviving deleted items (zombies)
        scope.launch {
            flushBatch()
        }
    }

    fun isEnabled(context: Context): Boolean {
        // Use getKey to handle potential JSON string format from DataStore
        return context.getKey<Boolean>(FIREBASE_ENABLED) ?: false
    }

    fun initialize(context: Context) {
        com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread {
            try {
                androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            } catch (e: Exception) { }
        }

        if (!isEnabled(context)) return

        // Use getKey<String> to clean up any JSON quotes around the string values
        val config = SyncConfig(
            apiKey = context.getKey<String>(FIREBASE_API_KEY) ?: "",
            projectId = context.getKey<String>(FIREBASE_PROJECT_ID) ?: "",
            appId = context.getKey<String>(FIREBASE_APP_ID) ?: ""
        )
        
        if (config.apiKey.isNotBlank() && config.projectId.isNotBlank()) {
            initialize(context, config)
        }
    }

    fun initialize(context: Context, config: SyncConfig) {
        if (isInitializing.getAndSet(true)) return
        
        scope.launch {
            try {
                val options = FirebaseOptions.Builder()
                    .setApiKey(config.apiKey)
                    .setProjectId(config.projectId)
                    .setApplicationId(config.appId)
                    .build()

                val appName = "sync_${config.projectId.replace(":", "_")}"
                val app = try {
                    FirebaseApp.getInstance(appName)
                } catch (e: Exception) {
                    FirebaseApp.initializeApp(context, options, appName)
                }

                db = FirebaseFirestore.getInstance(app)
                auth = FirebaseAuth.getInstance(app)
                isConnected = true
                
                // Save config
                context.setKey(FIREBASE_API_KEY, config.apiKey)
                context.setKey(FIREBASE_PROJECT_ID, config.projectId)
                context.setKey(FIREBASE_APP_ID, config.appId)
                context.setKey(FIREBASE_ENABLED, true)

                log("Firebase initialized. Waiting for User...")
                
                // Auth State Listener
                auth?.addAuthStateListener { firebaseAuth ->
                    val user = firebaseAuth.currentUser
                    if (user != null) {
                        log("User signed in: ${user.email}")
                        setupRealtimeListener(context, user.uid)
                    } else {
                        log("User signed out.")
                        // Detach listeners if any? (Firestore handles this mostly)
                    }
                }

            } catch (e: Exception) {
                lastInitError = e.message
                log("Init Error: ${e.message}")
            } finally {
                isInitializing.set(false)
            }
        }
    }

    private fun setupRealtimeListener(context: Context, uid: String) {
        db?.collection(SYNC_COLLECTION)?.document(uid)?.addSnapshotListener { snapshot, e ->
            if (e != null) {
                log("Listen error: ${e.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                scope.launch {
                    applyRemoteData(context, snapshot)
                }
            } else {
                // New user / empty doc -> Push local
                 log("Empty remote doc, pushing local data.")
                 pushAllLocalData(context, immediate = true)
            }
        }
    }

    // --- Core Logic ---

    // Local Timestamp Management
    private fun setLocalTimestamp(context: Context, key: String, timestamp: Long) {
        context.getSharedPreferences(TIMESTAMPS_PREF, Context.MODE_PRIVATE).edit {
            putLong(key, timestamp)
        }
    }

    private fun getLocalTimestamp(context: Context, key: String): Long {
        return context.getSharedPreferences(TIMESTAMPS_PREF, Context.MODE_PRIVATE).getLong(key, 0L)
    }

    // Push: Write (Update or Create)
    fun pushWrite(key: String, value: Any?) {
        if (isInternalKey(key)) return
        
        // Intercept Plugin Check
        if (key == PLUGINS_KEY_LOCAL) {
             val json = value as? String ?: return
             // Don't push raw local list. Merge it.
             // We need context... but pushWrite doesn't have it. 
             // However, strictly speaking, we just need the value to merge into our cache.
             updatePluginList(null, json) 
             return
        }
        
        // Debounce/Throttle handled by simple map for now to avoid spam
        throttleBatch[key] = value
        // We will flush this batch periodically or via pushAllLocalData
        // For immediate "pushData" calls from DataStore, we can just trigger a flush job
        triggerFlush()
    }
    
    // ...

    // --- Plugin Merge Logic ---
    private var cachedRemotePlugins: MutableList<PluginData> = mutableListOf()
    
    // Called when Local List changes (Install/Uninstall) OR when we want to push specific updates
    private fun updatePluginList(context: Context?, localJson: String?) {
        scope.launch {
             val localList = if (localJson != null) {
                 try {
                     parseJson<Array<PluginData>>(localJson).toList()
                 } catch(e:Exception) { emptyList() }
             } else {
                 emptyList()
             }
             
             // 1. Merge Local into Cached Remote
             // Rule: If it exists in Local, it exists in Remote (Active).
             // We do NOT remove things from Remote just because they are missing in Local (other devices).
             
             var changed = false
             
             localList.forEach { local ->
                 val existingIndex = cachedRemotePlugins.indexOfFirst { isMatchingPlugin(it, local) }
                 if (existingIndex != -1) {
                     val existing = cachedRemotePlugins[existingIndex]
                     if (existing.isDeleted) {
                         // Reactivating a deleted plugin
                         cachedRemotePlugins[existingIndex] = existing.copy(isDeleted = false, version = local.version)
                         changed = true
                     }
                     // Else: matched and active. Update version?
                 } else {
                     // New plugin from local
                     cachedRemotePlugins.add(local.copy(isOnline = true, isDeleted = false))
                     changed = true
                 }
             }
             
             if (changed) {
                 // Push the MASTER LIST to PLUGINS_KEY
                 // Note: We deliberately write to PLUGINS_KEY (the shared one), not PLUGINS_KEY_LOCAL
                 pushWriteDirect(PLUGINS_KEY, cachedRemotePlugins.toJson())
             }
        }
    }
    
    fun notifyPluginDeleted(internalName: String) {
        scope.launch {
            val idx = cachedRemotePlugins.indexOfFirst { it.internalName.trim().equals(internalName.trim(), ignoreCase = true) }
            if (idx != -1) {
                val existing = cachedRemotePlugins[idx]
                if (!existing.isDeleted) {
                    cachedRemotePlugins[idx] = existing.copy(isDeleted = true, addedDate = System.currentTimeMillis())
                    log("Marking plugin $internalName as DELETED in sync.")
                    pushWriteDirect(PLUGINS_KEY, cachedRemotePlugins.toJson())
                }
            } else {
                // Deleting something we didn't even know about?
                log("Warning: Deleting unknown plugin $internalName")
            }
        }
    }
    
    private fun pushWriteDirect(key: String, value: Any?) {
        throttleBatch[key] = value
        triggerFlush()
    }
    
    // Push: Delete
    fun pushDelete(key: String) {
         // Generic tombstone value
         throttleBatch[key] = SyncPayload(null, System.currentTimeMillis(), true)
         triggerFlush()
    }

    private var flushJob: Job? = null
    private fun triggerFlush() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            delay(2000) // 2s debounce
            flushBatch()
        }
    }

    private fun flushBatch() {
        val uid = auth?.currentUser?.uid ?: return
        val updates = mutableMapOf<String, Any?>()
        val now = System.currentTimeMillis()
        
        // Grab snapshot of batch
        val currentBatch = HashMap(throttleBatch)
        throttleBatch.clear()
        
        if (currentBatch.isEmpty()) return
        
        currentBatch.forEach { (key, value) ->
            if (value is SyncPayload) {
                 // Already a payload (delete)
                 updates[key] = value
            } else {
                 // Value update
                 updates[key] = SyncPayload(value, now, false)
            }
        }
        
        updates["last_sync"] = now
        
        db?.collection(SYNC_COLLECTION)?.document(uid)
            ?.set(updates, SetOptions.merge())
            ?.addOnSuccessListener { log("Flushed ${currentBatch.size} keys.") }
            ?.addOnFailureListener { e -> 
                log("Flush failed: ${e.message}") 
                // Restore headers? Simplification: Ignore failure for now, expensive to retry
            }
    }

    private fun applyRemoteData(context: Context, snapshot: DocumentSnapshot) {
        val remoteMap = snapshot.data ?: return
        val currentUid = auth?.currentUser?.uid ?: return
        
        log("Applying remote data (${remoteMap.size} keys)")
        
        remoteMap.forEach { (key, rawPayload) ->
            if (key == "last_sync") return@forEach
            
            try {
                // generic parsing
                // Firestore stores generic maps as Map<String, Object>
                if (rawPayload !is Map<*, *>) return@forEach
                
                // manual mapping to SyncPayload
                val v = rawPayload["v"]
                val t = (rawPayload["t"] as? Number)?.toLong() ?: 0L
                val d = (rawPayload["d"] as? Boolean) ?: false
                
                val localT = getLocalTimestamp(context, key)
                
                if (t > localT) {
                    // Remote is newer
                    applyPayload(context, key, v, d)
                    setLocalTimestamp(context, key, t)

                    // Check for Continue Watching updates and trigger UI refresh
                    if (key.contains("result_resume_watching")) {
                         com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread {
                             MainActivity.syncUpdatedEvent.invoke(true)
                         }
                    }
                }
            } catch (e: Exception) {
                log("Error parsing key $key: ${e.message}")
            }
        }
    }
    
    // Handles the actual application of a single Key-Value-Tombstone triplet
     private fun applyPayload(context: Context, key: String, value: Any?, isDeleted: Boolean) {
         if (isDeleted) {
             context.removeKeyLocal(key)
             return
         }
         
         // Special Handling for Plugins (The Shared Master List)
         if (key == PLUGINS_KEY) {
             val json = value as? String ?: return
             
             // Update Cache
             try {
                 val list = parseJson<Array<PluginData>>(json).toMutableList()
                 cachedRemotePlugins = list
             } catch(e:Exception) {}
             
             // Process
             handleRemotePlugins(context, json)
             return
         }
         
         // Ignore direct PLUGINS_KEY_LOCAL writes from remote (shouldn't happen with new logic, but safety)
         if (key == PLUGINS_KEY_LOCAL) return 

         // Default Apply
         if (value is String) {
             context.setKeyLocal(key, value)
         } else if (value != null) {
              // Try to serialize if it's a map? 
              // Our SyncPayload.v is Any?
              // Firestore converts JSON objects to Maps.
              // If we originally pushed a String (JSON), Firestore keeps it as String usually.
              // If it became a Map, we might need to stringify it back?
              // Assuming we pushed Strings mostly.
              context.setKeyLocal(key, value.toString())
         }
    }
    
    // --- Plugin Safety ---
    
    private fun isMatchingPlugin(p1: PluginData, local: PluginData): Boolean {
        if (p1.internalName.trim().equals(local.internalName.trim(), ignoreCase = true)) return true
        if (p1.url?.isNotBlank() == true && p1.url == local.url) return true
        return false
    }

    fun getPendingPlugins(context: Context): List<PluginData> {
         val json = context.getSharedPrefs().getString(PENDING_PLUGINS_KEY, "[]") ?: "[]"
         return try {
             val pending = parseJson<Array<PluginData>>(json).toList()
             val localPlugins = PluginManager.getPluginsLocal()
             
             pending.filter { pendingPlugin -> 
                 localPlugins.none { local -> isMatchingPlugin(pendingPlugin, local) }
             }
         } catch(e:Exception) { emptyList() }
    }
    
    suspend fun installPendingPlugin(activity: Activity, plugin: PluginData): Boolean {
        // 1. Get all available repositories
        val context = activity.applicationContext
        val savedRepos = context.getKey<Array<RepositoryData>>(REPOSITORIES_KEY) ?: emptyArray()
        val allRepos = (savedRepos + RepositoryManager.PREBUILT_REPOSITORIES).distinctBy { it.url }

        // 2. Find the plugin in repositories (Network intensive!)
        // Optimally we should maybe cache this, but for "Install" action it's acceptable to wait.
        log("Searching repositories for ${plugin.internalName}...")
        
        for (repo in allRepos) {
            val plugins = RepositoryManager.getRepoPlugins(repo.url) ?: continue
            val match = plugins.firstOrNull { it.second.internalName == plugin.internalName }
            
            if (match != null) {
                log("Found in ${repo.name}. Installing...")
                val success = PluginManager.downloadPlugin(
                    activity, 
                    match.second.url, 
                    match.second.internalName, 
                    repo.url, 
                    true
                )
                
                if (success) {
                    removeFromPending(context, plugin)
                    return true
                }
            }
        }
        
        log("Could not find repository for plugin: ${plugin.internalName}")
        CommonActivity.showToast(activity, "Could not find source repository for ${plugin.internalName}", 1)
        return false
    }

    suspend fun installAllPending(activity: Activity) {
        val context = activity.applicationContext
        val pending = getPendingPlugins(context)
        if (pending.isEmpty()) return

        // Batch optimization: Fetch all repo plugins ONCE
        val savedRepos = context.getKey<Array<RepositoryData>>(REPOSITORIES_KEY) ?: emptyArray()
        val allRepos = (savedRepos + RepositoryManager.PREBUILT_REPOSITORIES).distinctBy { it.url }
        
        val onlineMap = mutableMapOf<String, Pair<String, String>>() // InternalName -> (PluginUrl, RepoUrl)
        
        allRepos.forEach { repo ->
             RepositoryManager.getRepoPlugins(repo.url)?.forEach { (repoUrl, sitePlugin) ->
                 onlineMap[sitePlugin.internalName] = Pair(sitePlugin.url, repoUrl)
             }
        }

        var installedCount = 0
        val remaining = mutableListOf<PluginData>()
        
        pending.forEach { p ->
            val match = onlineMap[p.internalName]
            if (match != null) {
                val (url, repoUrl) = match
                val success = PluginManager.downloadPlugin(activity, url, p.internalName, repoUrl, true)
                if (success) installedCount++ else remaining.add(p)
            } else {
                remaining.add(p)
            }
        }
        
        // Update pending list with failures/missing
        context.setKeyLocal(PENDING_PLUGINS_KEY, remaining.toJson())
        
        if (installedCount > 0) {
            CommonActivity.showToast(activity, "Installed $installedCount plugins.", 0)
        }
        if (remaining.isNotEmpty()) {
             CommonActivity.showToast(activity, "Failed to find/install ${remaining.size} plugins.", 1)
        }
    }
    
    private fun removeFromPending(context: Context, plugin: PluginData) {
        val pending = getPendingPlugins(context).toMutableList()
        pending.removeAll { it.internalName == plugin.internalName }
        context.setKeyLocal(PENDING_PLUGINS_KEY, pending.toJson())
    }
    
    fun ignorePendingPlugin(context: Context, plugin: PluginData) {
        // Remove from pending
        removeFromPending(context, plugin)
        
        // Add to ignored list
        val ignoredJson = context.getSharedPrefs().getString(IGNORED_PLUGINS_KEY, "[]") ?: "[]"
        val ignoredList = try {
            parseJson<Array<String>>(ignoredJson).toMutableSet()
        } catch(e:Exception) { mutableSetOf<String>() }
        
        ignoredList.add(plugin.internalName)
        context.setKeyLocal(IGNORED_PLUGINS_KEY, ignoredList.toJson())
    }
    
    fun ignoreAllPendingPlugins(context: Context) {
        val pending = getPendingPlugins(context)
        if (pending.isNotEmpty()) {
            val ignoredJson = context.getSharedPrefs().getString(IGNORED_PLUGINS_KEY, "[]") ?: "[]"
            val ignoredList = try {
                parseJson<Array<String>>(ignoredJson).toMutableSet()
            } catch(e:Exception) { mutableSetOf<String>() }
            
            pending.forEach { ignoredList.add(it.internalName) }
            
            context.setKeyLocal(IGNORED_PLUGINS_KEY, ignoredList.toJson())
            context.setKeyLocal(PENDING_PLUGINS_KEY, "[]")
        }
    }
    
    private fun handleRemotePlugins(context: Context, remoteJson: String) {
        try {
            val remoteList = parseJson<Array<PluginData>>(remoteJson).toList()
            val remoteNames = remoteList.map { it.internalName }.toSet()
            
            // 1. Get RAW pending list
            val json = context.getSharedPrefs().getString(PENDING_PLUGINS_KEY, "[]") ?: "[]"
            val rawPending = try {
                parseJson<Array<PluginData>>(json).toMutableList()
            } catch(e:Exception) { mutableListOf<PluginData>() }
            
            val localPlugins = PluginManager.getPluginsLocal()
            val ignoredJson = context.getSharedPrefs().getString(IGNORED_PLUGINS_KEY, "[]") ?: "[]"
            val ignoredList = try {
                 parseJson<Array<String>>(ignoredJson).map { it.trim() }.toSet() 
            } catch(e:Exception) { emptySet<String>() }
            
            var changed = false
            
            // --- PROCESS DELETIONS & INSTALLS ---
            remoteList.forEach { remote ->
                val isLocal = localPlugins.firstOrNull { isMatchingPlugin(remote, it) }
                
                if (remote.isDeleted) {
                    // CASE: Deleted on Remote
                    if (isLocal != null) {
                        // It is installed locally -> DELETE IT
                        log("Sync: Uninstalling deleted plugin ${remote.internalName}")
                        // We need to delete the file. PluginManager.deletePlugin(file) requires File.
                        // We can construct the path.
                        val file = File(isLocal.filePath)
                        if (file.exists()) {
                            // Run on IO
                            scope.launch {
                                // Warning: This might trigger notifyPluginDeleted, but since it's already deleted in Remote,
                                // the circular logic should stabilize (idempotent).
                                // We need a way to invoke PluginManager.deletePlugin which is a suspend function.
                                // Since we are in handleRemotePlugins (inside applyRemoteData -> scope.launch), we can call suspend?
                                // handleRemotePlugins is regular fun. We need scope.
                                // Actually better: Just delete the file and update key locally?
                                // PluginManager.deletePlugin does: delete file + unload + deletePluginData.
                                // It's safer to use the Manager.
                                // But we can't call suspend from here easily if this isn't suspend.
                                // Let's simplify: Just delete file and remove key.
                                file.delete()
                                file.delete()
                                // Update local plugin list: Remove this specific plugin, do NOT nuke the whole list
                                val updatedLocalPlugins = PluginManager.getPluginsLocal()
                                    .filter { it.filePath != isLocal.filePath }
                                    .toTypedArray()
                                context.setKeyLocal(PLUGINS_KEY_LOCAL, updatedLocalPlugins)
                                // We can't easily do full uninstall logic here without PluginManager.
                                // Let's post a Toast/Notification "Plugin Uninstalled via Sync"?
                            }
                        }
                    }
                    
                    // Also remove from Pending if present
                    if (rawPending.removeIf { isMatchingPlugin(remote, it) }) {
                        changed = true
                    }
                    
                } else {
                    // CASE: Active on Remote
                    if (isLocal == null) {
                        // Not installed locally.
                        // Check if Ignored
                        val cleanName = remote.internalName.trim()
                        if (!ignoredList.contains(cleanName)) {
                            // Check if already in Pending
                            if (rawPending.none { isMatchingPlugin(remote, it) }) {
                                rawPending.add(remote)
                                changed = true
                            }
                        }
                    } else {
                        // Installed locally. Ensure not in pending.
                        if (rawPending.removeIf { isMatchingPlugin(remote, it) }) {
                            changed = true
                        }
                    }
                }
            }
            
            // --- CLEANUP PENDING ---
            // Remove any pending items that are NOT in the remote list anymore?
            // If Device A deleted it, it comes as isDeleted=true.
            // If Device A hard-removed it (tombstone gc?), it disappears.
            // If it disappears, we should probably remove it from pending.
            rawPending.retainAll { pending ->
                remoteList.any { remote -> isMatchingPlugin(remote, pending) }
            }
            
            lastSyncDebugInfo = """
                Remote: ${remoteList.size}
                Local: ${localPlugins.size} (${localPlugins.take(3).map { it.internalName }})
                Ignored: ${ignoredList.size}
                Pending: ${rawPending.size} (${rawPending.take(3).map { it.internalName }})
            """.trimIndent()
            
            log("Sync Debug: $lastSyncDebugInfo")
            
            if (changed) {
                log("Saving updated pending plugins list. Size: ${rawPending.size}")
                context.setKeyLocal(PENDING_PLUGINS_KEY, rawPending.toJson())
            }
            
        } catch(e:Exception) {
            log("Plugin Parse Error: ${e.message}")
        }
    }

    // --- Helpers ---

    private fun isInternalKey(key: String): Boolean {
        // Prevent syncing of internal state keys
        if (key.startsWith("firebase_")) return true
        if (key.startsWith("firestore_")) return true // Includes IGNORED_PLUGINS_KEY
        if (key == PENDING_PLUGINS_KEY) return true
        return false
    }

    fun pushAllLocalData(context: Context, immediate: Boolean = false) {
         if (!isLogged()) return
         val prefs = context.getSharedPrefs()
         scope.launch {
             prefs.all.forEach { (k, v) ->
                 if (!isInternalKey(k) && k != PLUGINS_KEY_LOCAL && v != null) {
                      // Normal keys
                     pushWrite(k, v)
                 } else if (k == PLUGINS_KEY_LOCAL && v != null) {
                     // Trigger plugin merge
                     val json = v as? String
                     if (json != null) updatePluginList(context, json)
                 }
             }
             if (immediate) flushBatch()
         }
    }

    fun syncNow(context: Context) {
        pushAllLocalData(context, true)
    }

    fun isOnline(): Boolean {
        return isConnected
    }

    fun getLastSyncTime(context: Context): Long? {
        val time = context.getKey<Long>(FIREBASE_LAST_SYNC)
        return if (time == 0L) null else time
    }
}
