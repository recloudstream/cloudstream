package com.lagradost.cloudstream3.syncproviders.providers

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.syncproviders.*
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.BookmarkedData
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects.DownloadHeaderCached
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class FirebaseSyncManager : AuthAPI() {
    override var name = "Firebase Sync"
    override val idPrefix = "firebase"
    override val icon = R.drawable.googledrive_logo // Reuse logo or use standard ic_baseline_sync
    override val hasOAuth2 = false
    override val hasInApp = true
    override val inAppLoginRequirement: AuthLoginRequirement? = null

    companion object {
        const val TAG = "FirebaseSyncManager"
    }

    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private val _currentProfile = MutableStateFlow<SyncProfile?>(null)
    val currentProfile: StateFlow<SyncProfile?> = _currentProfile

    private var syncListener: ListenerRegistration? = null
    @Volatile var isSyncActive = false

    // AuthAPI Implementations
    override fun loginRequest(): AuthLoginPage? = null
    override suspend fun login(redirectUrl: String, payload: String?): AuthToken? = null
    override suspend fun login(form: AuthLoginResponse): AuthToken? = null
    override suspend fun refreshToken(token: AuthToken): AuthToken? = null

    override suspend fun user(token: AuthToken?): AuthUser? {
        val currentUser = auth.currentUser ?: return null
        return AuthUser(
            id = currentUser.uid.hashCode(),
            name = currentUser.displayName ?: currentUser.email ?: "Firebase User",
            profilePicture = currentUser.photoUrl?.toString()
        )
    }

    fun selectProfile(profile: SyncProfile?) {
        _currentProfile.value = profile
        if (profile != null) {
            startRealtimeSync(profile.id)
        } else {
            stopRealtimeSync()
        }
    }

    // Profiles CRUD
    suspend fun getProfiles(): List<SyncProfile> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snapshot = firestore.collection("users").document(uid)
                .collection("profiles").get().await()
            snapshot.toObjects(SyncProfile::class.java).sortedByDescending { it.lastUsed }
        } catch (e: Exception) {
            logError(e)
            emptyList()
        }
    }

    suspend fun saveProfile(profile: SyncProfile): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            if (profile.id.isEmpty()) {
                profile.id = UUID.randomUUID().toString()
            }
            firestore.collection("users").document(uid)
                .collection("profiles").document(profile.id).set(profile).await()
            true
        } catch (e: Exception) {
            logError(e)
            false
        }
    }

    suspend fun deleteProfile(profileId: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            firestore.collection("users").document(uid)
                .collection("profiles").document(profileId).delete().await()
            true
        } catch (e: Exception) {
            logError(e)
            false
        }
    }

    // Real-time Sync
    private fun startRealtimeSync(profileId: String) {
        stopRealtimeSync()
        val uid = auth.currentUser?.uid ?: return

        syncListener = firestore.collection("users").document(uid)
            .collection("profiles").document(profileId)
            .collection("data").document("syncData")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    logError(e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val remoteData = snapshot.toObject(SyncData::class.java)
                    if (remoteData != null) {
                        // Launch in background to prevent blocking UI
                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                            if (!isSyncActive) {
                                isSyncActive = true
                                try {
                                    mergeAndSaveLocalData(remoteData, null)
                                } finally {
                                    isSyncActive = false
                                }
                            }
                        }
                    }
                }
            }
    }

    private fun stopRealtimeSync() {
        syncListener?.remove()
        syncListener = null
    }

    suspend fun syncLocalToFirestore(context: Context): Boolean {
        if (isSyncActive) return false
        isSyncActive = true
        try {
            val uid = auth.currentUser?.uid ?: return false
            val profileId = _currentProfile.value?.id ?: return false

            return withContext(Dispatchers.IO) {
                try {
                    val docRef = firestore.collection("users").document(uid)
                        .collection("profiles").document(profileId)
                        .collection("data").document("syncData")
                    
                    val remoteSnapshot = docRef.get().await()
                    val remoteData = if (remoteSnapshot.exists()) {
                        remoteSnapshot.toObject(SyncData::class.java) ?: SyncData()
                    } else {
                        SyncData()
                    }

                    val mergedData = mergeAndSaveLocalData(remoteData, context)

                    docRef.set(mergedData, SetOptions.merge()).await()
                    
                    val sp = PreferenceManager.getDefaultSharedPreferences(context)
                    sp.edit().putLong("firebase_last_synced_time", System.currentTimeMillis()).apply()

                    // Trigger async download of missing plugins
                    val missingPlugins = mergedData.plugins.filter { pluginName ->
                        !PluginManager.getPluginsLocal().any { it.internalName == pluginName } &&
                        !PluginManager.getPluginsOnline().any { it.internalName == pluginName }
                    }
                    val activity = context.getActivity()
                    if (missingPlugins.isNotEmpty() && activity != null) {
                        val urls = (getKey<Array<RepositoryData>>(com.lagradost.cloudstream3.ui.settings.extensions.REPOSITORIES_KEY) ?: emptyArray()) + RepositoryManager.PREBUILT_REPOSITORIES
                        val onlinePlugins = urls.toList().amap {
                            RepositoryManager.getRepoPlugins(it.url)?.toList() ?: emptyList()
                        }.flatten().distinctBy { it.second.url }
                        
                        var downloadedCount = 0
                        for (pluginInternalName in missingPlugins) {
                            val onlineData = onlinePlugins.firstOrNull { it.second.internalName == pluginInternalName }
                            if (onlineData != null) {
                                val success = PluginManager.downloadPlugin(
                                    activity,
                                    onlineData.second.url,
                                    onlineData.second.fileHash,
                                    onlineData.second.internalName,
                                    onlineData.first,
                                    true
                                )
                                if (success) downloadedCount++
                            }
                        }
                        if (downloadedCount > 0) {
                            withContext(Dispatchers.Main) {
                                com.lagradost.cloudstream3.CommonActivity.showToast(activity, "Downloaded $downloadedCount plugins from Firebase Backup", android.widget.Toast.LENGTH_LONG)
                                com.lagradost.cloudstream3.MainActivity.afterPluginsLoadedEvent.invoke(true)
                            }
                        }
                    }

                    true
                } catch (e: Exception) {
                    logError(e)
                    false
                }
            }
        } finally {
            isSyncActive = false
        }
    }

    private suspend fun mergeAndSaveLocalData(remote: SyncData, context: Context?): SyncData {
        val sp = if (context != null) PreferenceManager.getDefaultSharedPreferences(context) else null
        
        // 1. Settings
        val localTheme = sp?.getString("app_theme", "AmoledLight") ?: "AmoledLight"
        val lastSyncedTheme = sp?.getString("firebase_last_synced_theme", null)
        val mergedTheme = if (lastSyncedTheme != null && localTheme != lastSyncedTheme) localTheme else remote.userSettings?.theme ?: localTheme

        val localAutoplay = sp?.getBoolean("autoplay_next", true) ?: true
        val lastSyncedAutoplay = if (sp?.contains("firebase_last_synced_autoplay") == true) sp.getBoolean("firebase_last_synced_autoplay", true) else null
        val mergedAutoplay = if (lastSyncedAutoplay != null && localAutoplay != lastSyncedAutoplay) localAutoplay else remote.userSettings?.autoPlayNext ?: localAutoplay

        val localPlayer = sp?.getString("player_default", "") ?: ""
        val lastSyncedPlayer = sp?.getString("firebase_last_synced_player", null)
        val mergedPlayer = if (lastSyncedPlayer != null && localPlayer != lastSyncedPlayer) localPlayer else remote.userSettings?.defaultPlayer ?: localPlayer

        val localHomePage = DataStoreHelper.currentHomePage
        val lastSyncedHomePage = sp?.getString("firebase_last_synced_homepage", null)
        val mergedHomePage = if (lastSyncedHomePage != null && localHomePage != lastSyncedHomePage) localHomePage else remote.userSettings?.currentHomePage ?: localHomePage

        val localAppLayout = sp?.getInt("app_layout_key", -1)?.takeIf { it != -1 }
        val lastSyncedAppLayout = if (sp?.contains("firebase_last_synced_applayout") == true) sp.getInt("firebase_last_synced_applayout", -1) else null
        val mergedAppLayout = if (lastSyncedAppLayout != null && localAppLayout != lastSyncedAppLayout) localAppLayout else remote.userSettings?.appLayout ?: localAppLayout

        if (mergedHomePage != null) {
            DataStoreHelper.currentHomePage = mergedHomePage
        }

        if (sp != null) {
            withContext(Dispatchers.Main) {
                sp.edit().apply {
                    putString("app_theme", mergedTheme)
                    putBoolean("autoplay_next", mergedAutoplay)
                    putString("player_default", mergedPlayer)
                    if (mergedAppLayout != null) putInt("app_layout_key", mergedAppLayout)
                    
                    // Save last synced values
                    putString("firebase_last_synced_theme", mergedTheme)
                    putBoolean("firebase_last_synced_autoplay", mergedAutoplay)
                    putString("firebase_last_synced_player", mergedPlayer)
                    if (mergedHomePage != null) putString("firebase_last_synced_homepage", mergedHomePage)
                    if (mergedAppLayout != null) putInt("firebase_last_synced_applayout", mergedAppLayout)
                    apply()
                }
            }
        }

        // 2. Repositories
        val localRepos = RepositoryManager.getRepositories()
        val localRepoUrls = localRepos.map { it.url }
        val remoteRepoUrls = remote.repositories
        val mergedRepoUrls = (localRepoUrls + remoteRepoUrls).distinct()

        for (url in mergedRepoUrls) {
            if (!localRepoUrls.contains(url)) {
                var repoName = url.substringAfterLast("/").substringBefore(".json").ifBlank { "Remote Repo" }
                try {
                    val parsed = RepositoryManager.parseRepository(url)
                    if (parsed != null) repoName = parsed.name
                } catch (t: Throwable) { logError(t) }
                RepositoryManager.addRepository(RepositoryData(null, repoName, url))
            }
        }

        // 2.5 Plugins
        val localPlugins = (PluginManager.getPluginsLocal() + PluginManager.getPluginsOnline()).map { it.internalName }
        val remotePlugins = remote.plugins
        val mergedPlugins = (localPlugins + remotePlugins).distinct()

        // 3. Bookmarks
        val localBookmarks = DataStoreHelper.getAllBookmarkedData()
        val localBookmarkMap = localBookmarks.associateBy { it.name }
        val remoteBookmarkMap = remote.bookmarkDetails

        val mergedBookmarkDetails = mutableMapOf<String, BookmarkedData>()
        val allBookmarkNames = (localBookmarkMap.keys + remoteBookmarkMap.keys).distinct()

        val planToWatchList = mutableListOf<String>()
        val completedList = mutableListOf<String>()
        val watchingList = mutableListOf<String>()
        val onHoldList = mutableListOf<String>()
        val droppedList = mutableListOf<String>()

        for (name in allBookmarkNames) {
            val local = localBookmarkMap[name]
            val remoteItem = remoteBookmarkMap[name]

            val winner: BookmarkedData
            val winnerWatchType: WatchType

            if (local != null && remoteItem != null) {
                if (local.bookmarkedTime >= remoteItem.bookmarkedTime) {
                    winner = local
                    winnerWatchType = DataStoreHelper.getResultWatchState(local.id ?: 0)
                } else {
                    winner = remoteItem
                    winnerWatchType = getRemoteWatchType(remote, name)
                }
            } else if (local != null) {
                winner = local
                winnerWatchType = DataStoreHelper.getResultWatchState(local.id ?: 0)
            } else {
                val lastSyncedTime = sp?.getLong("firebase_last_synced_time", 0L) ?: 0L
                if (lastSyncedTime > 0L && remoteItem!!.bookmarkedTime < lastSyncedTime) {
                    continue
                }
                winner = remoteItem!!
                winnerWatchType = getRemoteWatchType(remote, name)
            }

            mergedBookmarkDetails[name] = winner

            when (winnerWatchType) {
                WatchType.PLANTOWATCH -> planToWatchList.add(name)
                WatchType.COMPLETED -> completedList.add(name)
                WatchType.WATCHING -> watchingList.add(name)
                WatchType.ONHOLD -> onHoldList.add(name)
                WatchType.DROPPED -> droppedList.add(name)
                else -> {}
            }

            if (winner == remoteItem || DataStoreHelper.getResultWatchState(winner.id ?: 0) != winnerWatchType) {
                if (winner.id != null) {
                    DataStoreHelper.setBookmarkedData(winner.id, winner)
                    DataStoreHelper.setResultWatchState(winner.id, winnerWatchType.internalId)
                }
            }
        }

        // 4. Watch Progress
        val localProgressMap = mutableMapOf<String, WatchProgressDetailsItem>()
        val localProgressItems = mutableListOf<SyncWatchProgressItem>()

        val dateIsoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val resumeIds = DataStoreHelper.getAllResumeStateIds() ?: emptyList()
        for (parentId in resumeIds) {
            val resume = DataStoreHelper.getLastWatched(parentId)
            if (resume != null) {
                val header = getKey<DownloadHeaderCached>(DOWNLOAD_HEADER_CACHE, parentId.toString())
                val posDur = resume.episodeId?.let { DataStoreHelper.getViewPos(it) }
                val showName = header?.name ?: "Show $parentId"
                val key = "${showName}_S${resume.season ?: 0}_E${resume.episode ?: 0}"

                val seconds = (posDur?.position ?: 0L) / 1000L
                val dateStr = dateIsoFormatter.format(Date(resume.updateTime))

                localProgressItems.add(SyncWatchProgressItem(showName, resume.season ?: 0, resume.episode ?: 0, seconds, dateStr))
                localProgressMap[key] = WatchProgressDetailsItem(header, resume, posDur)
            }
        }

        val remoteProgressMap = remote.watchProgressDetails
        val allProgressKeys = (localProgressMap.keys + remoteProgressMap.keys).distinct()
        val mergedProgressDetails = mutableMapOf<String, WatchProgressDetailsItem>()
        val mergedProgressItems = mutableListOf<SyncWatchProgressItem>()

        for (key in allProgressKeys) {
            val local = localProgressMap[key]
            val remoteItem = remoteProgressMap[key]

            val winner: WatchProgressDetailsItem
            val localTime = local?.resumeWatching?.updateTime ?: 0L
            val remoteTime = try {
                val remoteLastUpdated = remote.watchProgress.firstOrNull {
                    it.mediaTitle == (remoteItem?.headerCached?.name ?: "") &&
                            it.season == (remoteItem?.resumeWatching?.season ?: 0) &&
                            it.episode == (remoteItem?.resumeWatching?.episode ?: 0)
                }?.lastUpdated
                if (remoteLastUpdated != null) dateIsoFormatter.parse(remoteLastUpdated)?.time ?: 0L else 0L
            } catch (e: Exception) { 0L }

            winner = if (local != null && remoteItem != null) {
                if (localTime >= remoteTime) local else remoteItem
            } else if (local != null) {
                local
            } else {
                val lastSyncedTime = sp?.getLong("firebase_last_synced_time", 0L) ?: 0L
                if (lastSyncedTime > 0L && remoteTime < lastSyncedTime) {
                    continue
                }
                remoteItem!!
            }

            mergedProgressDetails[key] = winner

            val showName = winner.headerCached?.name ?: "Unknown"
            val seconds = (winner.posDur?.position ?: 0L) / 1000L
            val updateTime = winner.resumeWatching?.updateTime ?: System.currentTimeMillis()

            mergedProgressItems.add(
                SyncWatchProgressItem(showName, winner.resumeWatching?.season ?: 0, winner.resumeWatching?.episode ?: 0, seconds, dateIsoFormatter.format(Date(updateTime)))
            )

            if (winner == remoteItem) {
                val head = winner.headerCached
                val res = winner.resumeWatching
                val pos = winner.posDur
                if (head != null && res != null) {
                    setKey(DOWNLOAD_HEADER_CACHE, head.id.toString(), head)
                    DataStoreHelper.setLastWatched(res.parentId, res.episodeId, res.episode, res.season, res.isFromDownload, res.updateTime)
                    if (res.episodeId != null && pos != null) {
                        DataStoreHelper.setViewPos(res.episodeId, pos.position, pos.duration)
                    }
                }
            }
        }

        // 5. Reviews
        val localReviews = getKey<List<SyncReviewItem>>("user_reviews") ?: emptyList()
        val remoteReviews = remote.reviews
        val mergedReviews = (localReviews + remoteReviews).distinctBy { it.mediaTitle }
        setKey("user_reviews", mergedReviews)

        return SyncData(
            userSettings = SyncSettings(mergedTheme, mergedAutoplay, mergedPlayer, mergedHomePage, mergedAppLayout),
            repositories = mergedRepoUrls,
            bookmarks = SyncBookmarks(planToWatchList, completedList, watchingList, onHoldList, droppedList),
            watchProgress = mergedProgressItems,
            reviews = mergedReviews,
            bookmarkDetails = mergedBookmarkDetails,
            watchProgressDetails = mergedProgressDetails,
            plugins = mergedPlugins
        )
    }

    private fun getRemoteWatchType(remote: SyncData, name: String): WatchType {
        return when {
            remote.bookmarks.planToWatch.contains(name) -> WatchType.PLANTOWATCH
            remote.bookmarks.completed.contains(name) -> WatchType.COMPLETED
            remote.bookmarks.watching.contains(name) -> WatchType.WATCHING
            remote.bookmarks.onHold.contains(name) -> WatchType.ONHOLD
            remote.bookmarks.dropped.contains(name) -> WatchType.DROPPED
            else -> WatchType.NONE
        }
    }
}
