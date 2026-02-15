package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.Coroutines.main

import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.PLUGINS_KEY
import com.lagradost.cloudstream3.plugins.PLUGINS_KEY_LOCAL
import com.lagradost.cloudstream3.ui.settings.extensions.REPOSITORIES_KEY
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData

import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.plugins.PluginData
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

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
    const val FIREBASE_PLUGINS_KEY = "firebase_plugins_list"

    private var db: FirebaseFirestore? = null
    private var auth: FirebaseAuth? = null
    private var syncListener: com.google.firebase.firestore.ListenerRegistration? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isInitializing = AtomicBoolean(false)
    private var isConnected = false
    
    private var appContext: Context? = null
    private val throttleBatch = ConcurrentHashMap<String, Any?>()
    
    var lastInitError: String? = null
        private set
    
    private var isPluginsInitialized = false
    private var isApplyingRemoteData = false
    private var pendingRemotePluginJson: String? = null
    
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
    
    // Auth is now handled by AccountManager (FirebaseApi)
    // We just listen to the state in initialize()

    fun isLogged(): Boolean = auth?.currentUser != null

    fun getUserEmail(): String? = auth?.currentUser?.email

    fun getFirebaseAuth(): FirebaseAuth {
        return auth ?: FirebaseAuth.getInstance()
    }


    // --- Initialization ---

    override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
        super.onStop(owner)
        val ctx = appContext ?: return
        if (isEnabled(ctx)) {
            scope.launch {
                flushBatch()
            }
        }
    }

    fun isEnabled(context: Context): Boolean {
        return context.getKey<Boolean>(FIREBASE_ENABLED) ?: false
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext

        com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread {
            try {
                androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            } catch (_: Exception) { }
        }

        if (!isEnabled(context)) return

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
        appContext = context.applicationContext
        
        scope.launch {
            initializeInternal(context, config)
        }
    }

    private suspend fun initializeInternal(context: Context, config: SyncConfig) {
        try {
            val options = FirebaseOptions.Builder()
                .setApiKey(config.apiKey)
                .setProjectId(config.projectId)
                .setApplicationId(config.appId)
                .build()

            val appName = "sync_${config.projectId.replace(":", "_")}"
            val app = try {
                FirebaseApp.getInstance(appName)
            } catch (_: Exception) {
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
            
            // Auth State Listener
            auth?.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    setupRealtimeListener(context, user.uid)
                }
                // Refresh UI when auth state changes
                main { MainActivity.syncUpdatedEvent.invoke(true) }
            }

        } catch (e: Exception) {
            lastInitError = e.message
            // log("Init Error: ${e.message}")
        } finally {
            isInitializing.set(false)
        }
    }

    fun switchAccount(uid: String) {
        val context = appContext ?: return
        // log("Switching sync account to UID: $uid")
        
        // Stop current listener
        syncListener?.remove()
        syncListener = null
        
        // Overwrite local data with a clean slate
        DataStoreHelper.deleteAllSyncableData()
        
        // Start listener for new account if not logging out
        if (uid.isNotBlank()) {
            setupRealtimeListener(context, uid)
        }
    }

    private fun isPluginManagerReady(): Boolean {
        return PluginManager.loadedLocalPlugins && PluginManager.loadedOnlinePlugins
    }

    private fun setupRealtimeListener(context: Context, uid: String) {
        syncListener?.remove()
        syncListener = db?.collection(SYNC_COLLECTION)?.document(uid)?.addSnapshotListener { snapshot, e ->
            if (e != null) {
                // log("Listen error: ${e.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                scope.launch {
                    applyRemoteData(context, snapshot)
                }
            } else {
                // New user / empty doc -> Push local
                 // Only allow initialization if the scan is actually done
                 if (isPluginManagerReady()) {
                     isPluginsInitialized = true 
                     scope.launch {
                         pushAllLocalData(context, immediate = true)
                     }
                 }
            }
        }
    }

    // --- Core Logic ---

    // Local Timestamp Management
    private fun setLocalTimestamp(context: Context, key: String, timestamp: Long) {
        context.getSharedPreferences(TIMESTAMPS_PREF, Context.MODE_PRIVATE).edit()
            .putLong(key, timestamp)
            .apply()
    }

    private fun getLocalTimestamp(context: Context, key: String): Long {
        return context.getSharedPreferences(TIMESTAMPS_PREF, Context.MODE_PRIVATE).getLong(key, 0L)
    }

    // Push: Write (Update or Create)
    // Called from DataStore.setKey without context parameter
    fun pushWrite(key: String, value: Any?) {
        val ctx = appContext ?: return
        if (!isEnabled(ctx)) return
        if (isApplyingRemoteData) return
        
        // Intercept Plugin Check - MUST handle this before isInternalKey
        if (key == PLUGINS_KEY_LOCAL || key == PLUGINS_KEY) {
             if (!isPluginManagerReady()) return
             scope.launch {
                updatePluginList(ctx) 
             }
             return
        }

        if (isInternalKey(key)) return
        if (!shouldSync(ctx, key)) return
        
        throttleBatch[key] = value
        triggerFlush()
    }

    private fun shouldSync(context: Context, key: String): Boolean {
        // Essential toggles themselves always sync
        if (key.startsWith("sync_setting_")) return true
        if (key == FIREBASE_LAST_SYNC) return true
        
        // Granular toggles
        if (key.startsWith("app_") || key.startsWith("ui_")) return context.getKey<Boolean>(SYNC_SETTING_APPEARANCE) ?: true
        if (key.startsWith("player_")) return context.getKey<Boolean>(SYNC_SETTING_PLAYER) ?: true
        if (key.startsWith("download_")) return context.getKey<Boolean>(SYNC_SETTING_DOWNLOADS) ?: true
        if (key.startsWith("data_store_helper/account")) return context.getKey<Boolean>(SYNC_SETTING_ACCOUNTS) ?: true
        if (key.startsWith("result_resume_watching")) return context.getKey<Boolean>(SYNC_SETTING_RESUME_WATCHING) ?: true
        if (key.contains("bookmark")) return context.getKey<Boolean>(SYNC_SETTING_BOOKMARKS) ?: true
        if (key == REPOSITORIES_KEY) return context.getKey<Boolean>(SYNC_SETTING_REPOSITORIES) ?: true
        if (key == FIREBASE_PLUGINS_KEY) return context.getKey<Boolean>(SYNC_SETTING_PLUGINS) ?: true
        if (key == USER_SELECTED_HOMEPAGE_API) return context.getKey<Boolean>(SYNC_SETTING_HOMEPAGE_API) ?: true
        
        return context.getKey<Boolean>(SYNC_SETTING_GENERAL) ?: true
    }

    // --- Plugin Merge Logic ---
    private var cachedRemotePlugins: MutableList<PluginData> = mutableListOf()
    
    // Called when Any Local List changes (Install/Uninstall) OR when we want to push specific updates
    private suspend fun updatePluginList(context: Context) {
         if (!isPluginManagerReady()) {
             // log("Sync: Skipping plugin update push (PluginManager not ready)")
             return
         }
         val localList = PluginManager.getPluginsLocal(includeDeleted = true).toList()
         val onlineList = PluginManager.getPluginsOnline(includeDeleted = true).toList()
         val allLocal = localList + onlineList
          
          // 1. Merge Local into Cached Remote (Additions/Updates)
          var changed = false
          
          allLocal.forEach { local ->
              if (local.url.isNullOrBlank()) return@forEach

              val existingIndex = cachedRemotePlugins.indexOfFirst { isMatchingPlugin(it, local) }
              if (existingIndex != -1) {
                  val existing = cachedRemotePlugins[existingIndex]
                  // If local is active but remote is deleted, reactivate remote
                  if (existing.isDeleted && !local.isDeleted) {
                      cachedRemotePlugins[existingIndex] = existing.copy(isDeleted = false, version = local.version, addedDate = System.currentTimeMillis())
                      changed = true
                  } else if (!existing.isDeleted && local.isDeleted) {
                      // If local is deleted but remote is active, mark remote as deleted
                      cachedRemotePlugins[existingIndex] = existing.copy(isDeleted = true, addedDate = System.currentTimeMillis())
                      changed = true
                  }
              } else if (!local.isDeleted) {
                  // New plugin, not in cloud yet
                  cachedRemotePlugins.add(local.copy(isOnline = true, isDeleted = false))
                  changed = true
              }
          }
          
          // 2. Sync deletions from local metadata (plugins explicitly marked deleted)
          allLocal.filter { it.isDeleted }.forEach { local ->
              val existingIndex = cachedRemotePlugins.indexOfFirst { isMatchingPlugin(it, local) }
              if (existingIndex != -1 && !cachedRemotePlugins[existingIndex].isDeleted) {
                  // log("Sync: Syncing local uninstall for ${local.internalName} to cloud")
                  cachedRemotePlugins[existingIndex] = cachedRemotePlugins[existingIndex].copy(isDeleted = true, addedDate = System.currentTimeMillis())
                  changed = true
              }
          }
          
          if (changed) {
               if (!isPluginsInitialized) {
                   // log("Sync: Ignoring plugin list push (not initialized yet)")
                   return
               }
               // log("Sync: Pushing updated plugin list to cloud (${cachedRemotePlugins.count { !it.isDeleted }} active).")
               pushWriteDirect(FIREBASE_PLUGINS_KEY, cachedRemotePlugins.toJson())
           } else {
               // log("Sync: No changes to plugin list push.")
           }
    }
    
    suspend fun notifyPluginDeleted(internalName: String) {
        val idx = cachedRemotePlugins.indexOfFirst { it.internalName.trim().equals(internalName.trim(), ignoreCase = true) }
        if (idx != -1) {
            val existing = cachedRemotePlugins[idx]
            if (!existing.isDeleted) {
                cachedRemotePlugins[idx] = existing.copy(isDeleted = true, addedDate = System.currentTimeMillis())
                // log("Marking plugin $internalName as DELETED in sync.")
                if (!isPluginsInitialized) {
                    // log("Sync: Ignoring plugin list push (not initialized yet)")
                    return
                }
               pushWriteDirect(FIREBASE_PLUGINS_KEY, cachedRemotePlugins.toJson())
            }
        }
    }
    
    private fun pushWriteDirect(key: String, value: Any?) {
        // log("Sync: Queuing direct push for $key")
        throttleBatch[key] = value
        triggerFlush()
    }
    
    // Push: Delete
    // Called from DataStore.removeKey without context parameter
    fun pushDelete(key: String) {
         val ctx = appContext ?: return
         if (!isEnabled(ctx) || isInternalKey(key)) return
         if (!shouldSync(ctx, key)) return
         
         // Intercept Plugin Check
         if (key == PLUGINS_KEY_LOCAL) return

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

    private suspend fun flushBatch() {
        val uid = auth?.currentUser?.uid ?: return
        val ctx = appContext ?: return
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
        ctx.setKey(FIREBASE_LAST_SYNC, now)
        
        db?.collection(SYNC_COLLECTION)?.document(uid)
            ?.set(updates, SetOptions.merge())
    }
    private suspend fun applyRemoteData(context: Context, snapshot: DocumentSnapshot) {
        if (isApplyingRemoteData) return
        isApplyingRemoteData = true
        try {
            val remoteMap = snapshot.data ?: return
            
            hydrateCachedPlugins(context, remoteMap)
            applyRemotePayloads(context, remoteMap)
            
            val remoteSyncTime = (remoteMap["last_sync"] as? Number)?.toLong() ?: 0L
            if (remoteSyncTime > 0) {
                 context.setKey(FIREBASE_LAST_SYNC, remoteSyncTime)
            }
            
            if (pendingRemotePluginJson == null) {
                isPluginsInitialized = true
            }

            main {
                MainActivity.syncUpdatedEvent.invoke(true)
            }
        } finally {
            isApplyingRemoteData = false
        }
    }

    private fun hydrateCachedPlugins(context: Context, remoteMap: Map<String, Any?>) {
        if (!remoteMap.containsKey(FIREBASE_PLUGINS_KEY)) return
        try {
            val rawPayload = remoteMap[FIREBASE_PLUGINS_KEY]
            if (rawPayload is Map<*, *>) {
                val v = rawPayload["v"] as? String
                if (v != null) {
                    cachedRemotePlugins = parseJson<Array<PluginData>>(v).toMutableList()
                    handleRemotePlugins(context, v)
                }
            }
        } catch (_: Exception) { }
    }

    private fun applyRemotePayloads(context: Context, remoteMap: Map<String, Any?>) {
        remoteMap.forEach { (key, rawPayload) ->
            if (key == "last_sync" || key == FIREBASE_PLUGINS_KEY) return@forEach
            
            try {
                if (rawPayload !is Map<*, *>) return@forEach
                
                val v = rawPayload["v"]
                val t = (rawPayload["t"] as? Number)?.toLong() ?: 0L
                val d = rawPayload["d"] as? Boolean ?: false
                
                val localT = getLocalTimestamp(context, key)
                
                if (t > localT) {
                    applyPayload(context, key, v, d)
                    setLocalTimestamp(context, key, t)
                }
            } catch (_: Exception) { }
        }
    }
    
     // Handles the actual application of a single Key-Value-Tombstone triplet
     private fun applyPayload(context: Context, key: String, value: Any?, isDeleted: Boolean) {
         if (isDeleted) {
             context.removeKeyLocal(key)
             return
         }
         
         // Special Handling for Plugins (The Shared Master List)
         if (key == FIREBASE_PLUGINS_KEY) {
             val json = value as? String ?: return
             
             // Update Cache
             try {
                 val list = parseJson<Array<PluginData>>(json).toMutableList()
                 cachedRemotePlugins = list
             } catch(_:Exception) {}
             
             // Process
             handleRemotePlugins(context, json)
             return
         }
         
         // Ignore direct PLUGINS_KEY_LOCAL writes from remote
         if (key == PLUGINS_KEY_LOCAL) return 
 
         // Default Apply
         if (value is String) {
             context.setKeyLocal(key, value)
         } else if (value != null) {
              context.setKeyLocal(key, value.toString())
         }
    }
    
    // --- Plugin Safety ---
    
    private fun isMatchingPlugin(p1: PluginData, local: PluginData): Boolean {
        val name1 = p1.internalName.trim()
        val name2 = local.internalName.trim()
        
        // Match by internal name (case-insensitive)
        if (name1.equals(name2, ignoreCase = true)) return true
        
        // Secondary match by URL if available (must be exact)
        if (p1.url?.isNotBlank() == true && p1.url == local.url) return true
        
        return false
    }

    fun getPendingPlugins(context: Context): List<PluginData> {
         val json = context.getSharedPrefs().getString(PENDING_PLUGINS_KEY, "[]") ?: "[]"
         return try {
             val pending = parseJson<Array<PluginData>>(json).toList()
             val localPlugins = PluginManager.getPluginsLocal()
             val onlinePlugins = PluginManager.getPluginsOnline()
             val allLocal = localPlugins + onlinePlugins
             
             val res = pending.filter { pendingPlugin -> 
                 allLocal.none { local -> isMatchingPlugin(pendingPlugin, local) }
             }
             // if (res.isNotEmpty()) log("Sync: detected ${res.size} pending plugins not installed locally.")
             res
         } catch(e:Exception) { emptyList() }
    }
    
    suspend fun installPendingPlugin(activity: Activity, plugin: PluginData): Boolean {
        val context = activity.applicationContext
        val savedRepos = context.getKey<Array<RepositoryData>>(REPOSITORIES_KEY) ?: emptyArray()
        val allRepos = (savedRepos + RepositoryManager.PREBUILT_REPOSITORIES).distinctBy { it.url }

        // log("Searching repositories for ${plugin.internalName}...")
        
        for (repo in allRepos) {
            val plugins = RepositoryManager.getRepoPlugins(repo.url) ?: continue
            val match = plugins.firstOrNull { it.second.internalName == plugin.internalName }
            
            if (match != null) {
                // log("Found in ${repo.name}. Installing...")
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
        
        // log("Could not find repository for plugin: ${plugin.internalName}")
        CommonActivity.showToast(activity, activity.getString(com.lagradost.cloudstream3.R.string.sync_plugin_repo_not_found, plugin.internalName), 1)
        return false
    }

    suspend fun installAllPending(activity: Activity) {
        val context = activity.applicationContext
        val pending = getPendingPlugins(context)
        if (pending.isEmpty()) return

        val savedRepos = context.getKey<Array<RepositoryData>>(REPOSITORIES_KEY) ?: emptyArray()
        val allRepos = (savedRepos + RepositoryManager.PREBUILT_REPOSITORIES).distinctBy { it.url }
        
        val onlineMap = mutableMapOf<String, Pair<String, String>>()
        
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
            CommonActivity.showToast(activity, activity.getString(com.lagradost.cloudstream3.R.string.sync_plugins_installed, installedCount), 0)
        }
        if (remaining.isNotEmpty()) {
             CommonActivity.showToast(activity, activity.getString(com.lagradost.cloudstream3.R.string.sync_plugins_install_failed, remaining.size), 1)
        }
    }
    
    private fun removeFromPending(context: Context, plugin: PluginData) {
        val pending = getPendingPlugins(context).toMutableList()
        pending.removeAll { it.internalName == plugin.internalName }
        context.setKeyLocal(PENDING_PLUGINS_KEY, pending.toJson())
    }
    
    fun ignorePendingPlugin(context: Context, plugin: PluginData) {
        removeFromPending(context, plugin)
        
        val ignoredJson = context.getSharedPrefs().getString(IGNORED_PLUGINS_KEY, "[]") ?: "[]"
        val ignoredList = try {
            parseJson<Array<String>>(ignoredJson).toMutableSet()
        } catch(_:Exception) { mutableSetOf<String>() }
        
        ignoredList.add(plugin.internalName)
        context.setKeyLocal(IGNORED_PLUGINS_KEY, ignoredList.toJson())
    }
    
    fun ignoreAllPendingPlugins(context: Context) {
        val pending = getPendingPlugins(context)
        if (pending.isNotEmpty()) {
            val ignoredJson = context.getSharedPrefs().getString(IGNORED_PLUGINS_KEY, "[]") ?: "[]"
            val ignoredList = try {
                parseJson<Array<String>>(ignoredJson).toMutableSet()
            } catch(_:Exception) { mutableSetOf<String>() }
            
            pending.forEach { ignoredList.add(it.internalName) }
            
            context.setKeyLocal(IGNORED_PLUGINS_KEY, ignoredList.toJson())
            context.setKeyLocal(PENDING_PLUGINS_KEY, "[]")
        }
    }
    
    private fun handleRemotePlugins(context: Context, remoteJson: String) {
        try {
            val remoteList = parseJson<Array<PluginData>>(remoteJson).toList()
            cachedRemotePlugins = remoteList.toMutableList()

            val json = context.getSharedPrefs().getString(PENDING_PLUGINS_KEY, "[]") ?: "[]"
            val rawPending = try {
                parseJson<Array<PluginData>>(json).toMutableList()
            } catch(_:Exception) { mutableListOf<PluginData>() }
            
            val installedPlugins = (PluginManager.getPluginsLocal() + PluginManager.getPluginsOnline()).toList()
            val ignoredList = getIgnoredPlugins(context)
            
            val changed = processRemotePluginChanges(context, remoteList, installedPlugins, rawPending, ignoredList)
            cleanupPendingPlugins(remoteList, rawPending)
            
            if (changed) {
                context.setKeyLocal(PENDING_PLUGINS_KEY, rawPending.toJson())
                main { MainActivity.syncUpdatedEvent.invoke(true) }
            }
            
            updateOnlinePluginList(context, remoteList)
        } catch (_: Exception) { }
    }

    private fun getIgnoredPlugins(context: Context): Set<String> {
        val ignoredJson = context.getSharedPrefs().getString(IGNORED_PLUGINS_KEY, "[]") ?: "[]"
        return try {
            parseJson<Array<String>>(ignoredJson).map { it.trim() }.toSet()
        } catch(_:Exception) { emptySet() }
    }

    private fun processRemotePluginChanges(
        context: Context,
        remoteList: List<PluginData>,
        installedPlugins: List<PluginData>,
        rawPending: MutableList<PluginData>,
        ignoredList: Set<String>
    ): Boolean {
        var changed = false
        remoteList.forEach { remote ->
            val isLocal = installedPlugins.firstOrNull { isMatchingPlugin(remote, it) }
            if (remote.isDeleted) {
                if (isLocal != null) {
                    val file = File(isLocal.filePath)
                    if (file.exists()) {
                        file.delete()
                        val updatedLocalPlugins = PluginManager.getPluginsLocal()
                            .filter { it.filePath != isLocal.filePath }
                            .toTypedArray()
                        context.setKeyLocal(PLUGINS_KEY_LOCAL, updatedLocalPlugins)
                    }
                }
                if (rawPending.removeIf { isMatchingPlugin(remote, it) }) changed = true
            } else {
                if (isLocal == null) {
                    val cleanName = remote.internalName.trim()
                    if (!ignoredList.contains(cleanName) && rawPending.none { isMatchingPlugin(remote, it) }) {
                        rawPending.add(remote)
                        changed = true
                    }
                } else if (rawPending.removeIf { isMatchingPlugin(remote, it) }) {
                    changed = true
                }
            }
        }
        return changed
    }

    private fun cleanupPendingPlugins(remoteList: List<PluginData>, rawPending: MutableList<PluginData>) {
        rawPending.retainAll { pending ->
            remoteList.any { remote -> isMatchingPlugin(remote, pending) }
        }
    }

    private fun updateOnlinePluginList(context: Context, remoteList: List<PluginData>) {
        val onlinePlugins = PluginManager.getPluginsOnline()
        val currentOnlinePlugins = onlinePlugins.toMutableList()
        remoteList.forEach { remote ->
            val match = currentOnlinePlugins.indexOfFirst { isMatchingPlugin(remote, it) }
            if (remote.isDeleted) {
                if (match != -1) currentOnlinePlugins.removeAt(match)
            } else if (match != -1) {
                currentOnlinePlugins[match] = remote.copy(
                    filePath = currentOnlinePlugins[match].filePath,
                    isOnline = true
                )
            }
        }
        
        if (remoteList.isNotEmpty()) {
            context.setKeyLocal(PLUGINS_KEY, currentOnlinePlugins.toTypedArray())
        }
        
        // Signal initialized
        isPluginsInitialized = true
    }

    /**
     * Called by MainActivity after various plugin loading events.
     * Applies any deferred remote plugin data that arrived before PluginManager was ready.
     */
    fun onPluginsReady(context: Context) {
        if (!PluginManager.loadedLocalPlugins || !PluginManager.loadedOnlinePlugins) {
             // log("Sync: Plugins not fully ready yet (Local: ${PluginManager.loadedLocalPlugins}, Online: ${PluginManager.loadedOnlinePlugins})")
             return
        }

        if (isPluginsInitialized) return // Already signaled

        // Crucial: Mark as initialized so we can now push local changes to cloud
        isPluginsInitialized = true
        // log("Sync: Plugins are now fully ready (Both Local and Online loaded).")
        
        val pending = pendingRemotePluginJson
        if (pending != null) {
            // log("Applying deferred remote plugin data.")
            pendingRemotePluginJson = null
            handleRemotePlugins(context, pending)
        } else {
            // Even if no remote data, we should now push our local list to ensure cloud is synced
            scope.launch {
                updatePluginList(context)
            }
        }
    }
 
    // --- Helpers ---
 
    private fun isInternalKey(key: String): Boolean {
        if (key == FIREBASE_PLUGINS_KEY) return false // Explicitly allow sync list
        if (key.startsWith("firebase_")) return true
        if (key.startsWith("firestore_")) return true
        if (key == PENDING_PLUGINS_KEY) return true
        if (key == PLUGINS_KEY) return true // PLUGINS_KEY is managed via FIREBASE_PLUGINS_KEY
        return false
    }
 
    suspend fun pushAllLocalData(context: Context, immediate: Boolean = false) {
         if (auth?.currentUser == null) return
         val prefs = context.getSharedPrefs()
         prefs.all.forEach { (k, v) ->
             if (!isInternalKey(k) && k != PLUGINS_KEY_LOCAL && k != PLUGINS_KEY && v != null) {
                 pushWrite(k, v)
             }
         }
         
         // Unified Plugin Push
         updatePluginList(context)
         
         if (immediate) flushBatch()
    }
 
    fun syncNow(context: Context) {
        scope.launch {
            pushAllLocalData(context, true)
        }
    }
 
    fun isOnline(): Boolean {
        return isConnected
    }
 
    fun getLastSyncTime(context: Context): Long? {
        val time = context.getKey<Long>(FIREBASE_LAST_SYNC)
        return if (time == 0L) null else time
    }
}
