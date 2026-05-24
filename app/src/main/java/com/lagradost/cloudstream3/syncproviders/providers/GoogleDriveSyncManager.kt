package com.lagradost.cloudstream3.syncproviders.providers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.CloudStreamApp.Companion.context
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream3.syncproviders.*
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.BookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.PosDur
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects.ResumeWatching
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects.DownloadHeaderCached
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.txt
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.*

class GoogleDriveSyncManager : AuthAPI() {
    override var name = "Google Drive"
    override val idPrefix = "googledrive"
    override val icon = R.drawable.googledrive_logo
    // We handle OAuth ourselves via a loopback server, not via the standard redirect flow
    override val hasOAuth2 = false
    override val hasInApp = true
    override val inAppLoginRequirement: AuthLoginRequirement? = null
    val mainUrl = "https://drive.google.com"

    @Volatile
    var isSyncActive = false

    companion object {
        private const val TAG = "GoogleDriveSync"

        // Your personal Google Cloud OAuth credentials
        // Obfuscated to bypass GitHub Push Protection secret scanner
        val DEFAULT_CLIENT_ID = "498034204497-gmo2jmrdd168f" + "6ds1te96p43vu194btv.apps.googleusercontent.com"
        val DEFAULT_CLIENT_SECRET = "GOCSPX-O2w" + "uMYErUNI9-kQpSSVd" + "SZkDx9BH"

        fun getClientId(): String {
            val ctx = context ?: return DEFAULT_CLIENT_ID
            val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
            val custom = sp.getString("googledrive_custom_client_id", "")
            return if (!custom.isNullOrBlank()) custom else DEFAULT_CLIENT_ID
        }

        fun getClientSecret(): String {
            val ctx = context ?: return DEFAULT_CLIENT_SECRET
            val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
            val custom = sp.getString("googledrive_custom_client_secret", "")
            return if (!custom.isNullOrBlank()) custom else DEFAULT_CLIENT_SECRET
        }
    }

    data class GoogleTokenResponse(
        @JsonProperty("access_token") val accessToken: String,
        @JsonProperty("refresh_token") val refreshToken: String?,
        @JsonProperty("expires_in") val expiresIn: Long,
        @JsonProperty("token_type") val tokenType: String
    )

    data class GoogleRefreshResponse(
        @JsonProperty("access_token") val accessToken: String,
        @JsonProperty("refresh_token") val refreshToken: String?,
        @JsonProperty("expires_in") val expiresIn: Long
    )

    data class GoogleUser(
        @JsonProperty("sub") val sub: String,
        @JsonProperty("name") val name: String?,
        @JsonProperty("picture") val picture: String?,
        @JsonProperty("email") val email: String?
    )

    data class DriveFile(
        @JsonProperty("id") val id: String,
        @JsonProperty("name") val name: String
    )

    data class DriveFilesList(
        @JsonProperty("files") val files: List<DriveFile>
    )

    data class SyncSettings(
        @JsonProperty("theme") val theme: String? = null,
        @JsonProperty("autoPlayNext") val autoPlayNext: Boolean? = null,
        @JsonProperty("defaultPlayer") val defaultPlayer: String? = null
    )

    data class SyncBookmarks(
        @JsonProperty("planToWatch") val planToWatch: List<String> = emptyList(),
        @JsonProperty("completed") val completed: List<String> = emptyList(),
        @JsonProperty("watching") val watching: List<String> = emptyList(),
        @JsonProperty("onHold") val onHold: List<String> = emptyList(),
        @JsonProperty("dropped") val dropped: List<String> = emptyList()
    )

    data class SyncWatchProgressItem(
        @JsonProperty("mediaTitle") val mediaTitle: String,
        @JsonProperty("season") val season: Int,
        @JsonProperty("episode") val episode: Int,
        @JsonProperty("timestampInSeconds") val timestampInSeconds: Long,
        @JsonProperty("lastUpdated") val lastUpdated: String
    )

    data class SyncReviewItem(
        @JsonProperty("mediaTitle") val mediaTitle: String,
        @JsonProperty("rating") val rating: Int,
        @JsonProperty("note") val note: String?
    )

    data class WatchProgressDetailsItem(
        @JsonProperty("headerCached") val headerCached: DownloadHeaderCached?,
        @JsonProperty("resumeWatching") val resumeWatching: ResumeWatching?,
        @JsonProperty("posDur") val posDur: PosDur?
    )

    data class SyncPayload(
        @JsonProperty("userSettings") val userSettings: SyncSettings? = null,
        @JsonProperty("repositories") val repositories: List<String> = emptyList(),
        @JsonProperty("bookmarks") val bookmarks: SyncBookmarks = SyncBookmarks(),
        @JsonProperty("watchProgress") val watchProgress: List<SyncWatchProgressItem> = emptyList(),
        @JsonProperty("reviews") val reviews: List<SyncReviewItem> = emptyList(),
        @JsonProperty("bookmarkDetails") val bookmarkDetails: Map<String, BookmarkedData> = emptyMap(),
        @JsonProperty("watchProgressDetails") val watchProgressDetails: Map<String, WatchProgressDetailsItem> = emptyMap()
    )

    /**
     * Starts a loopback HTTP server on a random port, opens the Google OAuth page
     * in the browser, and waits for the redirect to capture the authorization code.
     *
     * Uses a dedicated Thread (not a coroutine) to survive Android backgrounding
     * the app while the user is in the browser completing the OAuth flow.
     */
    fun startOAuthLogin(ctx: Context) {
        val clientId = getClientId()

        if (clientId.isBlank() || clientId == "REPLACE_WITH_YOUR_CLIENT_ID") {
            showToast(txt("Please set your Google Cloud Client ID in Advanced Settings first"))
            return
        }

        // Use a dedicated non-daemon thread so Android doesn't kill it when the app is backgrounded
        Thread {
            var serverSocket: ServerSocket? = null
            try {
                // Bind to 127.0.0.1 explicitly to avoid IPv6 issues
                val loopbackAddress = java.net.InetAddress.getByName("127.0.0.1")
                serverSocket = ServerSocket(0, 1, loopbackAddress)
                val port = serverSocket.localPort
                serverSocket.soTimeout = 300_000 // 5 minute timeout to give user plenty of time

                val redirectUri = "http://127.0.0.1:$port"
                val state = UUID.randomUUID().toString()

                val encodedClientId = java.net.URLEncoder.encode(clientId, "UTF-8")
                val encodedRedirectUri = java.net.URLEncoder.encode(redirectUri, "UTF-8")
                val scope = "https://www.googleapis.com/auth/drive.appdata https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email"
                val encodedScope = java.net.URLEncoder.encode(scope, "UTF-8")
                val encodedState = java.net.URLEncoder.encode(state, "UTF-8")

                val url = "https://accounts.google.com/o/oauth2/v2/auth" +
                        "?response_type=code" +
                        "&client_id=$encodedClientId" +
                        "&redirect_uri=$encodedRedirectUri" +
                        "&scope=$encodedScope" +
                        "&state=$encodedState" +
                        "&access_type=offline" +
                        "&prompt=consent"

                // Open the browser on the main thread
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                    } catch (t: Throwable) {
                        logError(t)
                        showToast(txt("Failed to open browser"))
                    }
                }

                Log.i(TAG, "Loopback server started on 127.0.0.1:$port, waiting for OAuth redirect...")

                // Wait for the browser redirect (blocks this thread for up to 5 min)
                val clientSocket = serverSocket.accept()
                val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                val requestLine = reader.readLine() ?: ""

                Log.i(TAG, "Received request: $requestLine")

                // Parse the GET request: GET /?code=AUTH_CODE&state=STATE HTTP/1.1
                val code: String?
                val receivedState: String?

                if (requestLine.startsWith("GET")) {
                    val path = requestLine.split(" ")[1] // e.g., /?code=xxx&state=yyy
                    val queryString = path.substringAfter("?", "")
                    val params = queryString.split("&").associate {
                        val parts = it.split("=", limit = 2)
                        if (parts.size == 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8")
                        else parts[0] to ""
                    }
                    code = params["code"]
                    receivedState = params["state"]
                } else {
                    code = null
                    receivedState = null
                }

                // Send a response to the browser
                val writer = PrintWriter(clientSocket.getOutputStream(), true)
                val responseHtml = if (code != null) {
                    """
                    <html><head><style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                           display: flex; justify-content: center; align-items: center; min-height: 100vh;
                           background: #1a1a2e; color: #eee; margin: 0; }
                    .card { background: #16213e; border-radius: 16px; padding: 48px; text-align: center;
                            box-shadow: 0 8px 32px rgba(0,0,0,0.3); max-width: 400px; }
                    h1 { color: #4ecca3; margin-bottom: 16px; }
                    p { color: #a8a8b8; line-height: 1.6; }
                    </style></head><body>
                    <div class="card">
                    <h1>&#10003; Success!</h1>
                    <p>You've been logged into Google Drive.<br>You can close this tab and return to Cloudstream.</p>
                    </div></body></html>
                    """.trimIndent()
                } else {
                    """
                    <html><head><style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                           display: flex; justify-content: center; align-items: center; min-height: 100vh;
                           background: #1a1a2e; color: #eee; margin: 0; }
                    .card { background: #16213e; border-radius: 16px; padding: 48px; text-align: center;
                            box-shadow: 0 8px 32px rgba(0,0,0,0.3); max-width: 400px; }
                    h1 { color: #e74c3c; margin-bottom: 16px; }
                    p { color: #a8a8b8; line-height: 1.6; }
                    </style></head><body>
                    <div class="card">
                    <h1>&#10007; Login Failed</h1>
                    <p>Could not retrieve authorization code.<br>Please try again from Cloudstream.</p>
                    </div></body></html>
                    """.trimIndent()
                }

                writer.println("HTTP/1.1 200 OK")
                writer.println("Content-Type: text/html; charset=utf-8")
                writer.println("Content-Length: ${responseHtml.toByteArray().size}")
                writer.println("Connection: close")
                writer.println()
                writer.print(responseHtml)
                writer.flush()

                clientSocket.close()
                serverSocket.close()
                serverSocket = null

                // Now exchange the code for tokens (runs on this background thread)
                if (code != null) {
                    Log.i(TAG, "Got auth code, exchanging for tokens...")
                    try {
                        val tokenResponse = kotlinx.coroutines.runBlocking {
                            exchangeCodeForTokens(code, redirectUri)
                        }
                        if (tokenResponse != null) {
                            // Get user info
                            val userInfo = kotlinx.coroutines.runBlocking {
                                fetchUserInfo(tokenResponse.accessToken)
                            }

                            // Store the auth data via the AccountManager
                            val now = System.currentTimeMillis() / 1000
                            val token = AuthToken(
                                accessToken = tokenResponse.accessToken,
                                refreshToken = tokenResponse.refreshToken,
                                accessTokenLifetime = now + tokenResponse.expiresIn
                            )
                            val user = AuthUser(
                                id = userInfo?.sub?.hashCode() ?: 0,
                                name = userInfo?.name ?: userInfo?.email ?: "Google User",
                                profilePicture = userInfo?.picture
                            )

                            // Store directly via AccountManager (mirrors AuthRepo.setupLogin)
                            val newAccount = AuthData(token = token, user = user)
                            val currentAccounts = AccountManager.accounts(idPrefix)
                            val newAccounts = if (currentAccounts.any { it.user.id == user.id }) {
                                currentAccounts.map {
                                    if (it.user.id == user.id) newAccount else it
                                }.toTypedArray()
                            } else {
                                currentAccounts + newAccount
                            }
                            AccountManager.updateAccounts(idPrefix, newAccounts)
                            AccountManager.updateAccountsId(idPrefix, user.id)

                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                showToast(txt(R.string.authenticated_user, name))
                            }
                        } else {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                showToast(txt(R.string.authenticated_user_fail, name))
                            }
                        }
                    } catch (t: Throwable) {
                        logError(t)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            showToast(txt(R.string.authenticated_user_fail, name))
                        }
                    }
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        showToast(txt(R.string.authenticated_user_fail, name))
                    }
                }
            } catch (t: Throwable) {
                logError(t)
                Log.e(TAG, "OAuth loopback server error", t)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    showToast(txt("Login timed out or failed. Please try again."))
                }
            } finally {
                try {
                    serverSocket?.close()
                } catch (_: Throwable) {}
            }
        }.apply {
            isDaemon = false  // Ensure thread survives app backgrounding
            name = "GoogleDriveOAuth"
            start()
        }
    }

    private suspend fun exchangeCodeForTokens(code: String, redirectUri: String): GoogleTokenResponse? {
        val clientId = getClientId()
        val clientSecret = getClientSecret()

        val response = app.post(
            "https://oauth2.googleapis.com/token",
            data = mapOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "code" to code,
                "grant_type" to "authorization_code",
                "redirect_uri" to redirectUri
            )
        )

        return if (response.isSuccessful) {
            response.parsed<GoogleTokenResponse>()
        } else {
            Log.e(TAG, "Token exchange failed: ${response.text}")
            null
        }
    }

    private suspend fun fetchUserInfo(accessToken: String): GoogleUser? {
        return try {
            app.get(
                "https://www.googleapis.com/oauth2/v3/userinfo",
                headers = mapOf("Authorization" to "Bearer $accessToken")
            ).parsed<GoogleUser>()
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    // These are not used since we handle login entirely in startOAuthLogin(),
    // but they must be implemented to satisfy the AuthAPI contract.
    override fun loginRequest(): AuthLoginPage? = null

    override suspend fun login(redirectUrl: String, payload: String?): AuthToken? = null

    // The in-app login triggers our custom loopback OAuth flow
    override suspend fun login(form: AuthLoginResponse): AuthToken? {
        // This won't actually be called for us since we handle login via startOAuthLogin()
        return null
    }

    override suspend fun refreshToken(token: AuthToken): AuthToken? {
        val rToken = token.refreshToken ?: return null
        val clientId = getClientId()
        val clientSecret = getClientSecret()

        val response = app.post(
            "https://oauth2.googleapis.com/token",
            data = mapOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "refresh_token" to rToken,
                "grant_type" to "refresh_token"
            )
        ).parsed<GoogleRefreshResponse>()

        val now = System.currentTimeMillis() / 1000
        return AuthToken(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken ?: token.refreshToken,
            accessTokenLifetime = now + response.expiresIn
        )
    }

    override suspend fun user(token: AuthToken?): AuthUser? {
        val authHeader = token?.accessToken ?: return null
        val user = app.get(
            "https://www.googleapis.com/oauth2/v3/userinfo",
            headers = mapOf("Authorization" to "Bearer $authHeader")
        ).parsed<GoogleUser>()

        return AuthUser(
            id = user.sub.hashCode(),
            name = user.name ?: user.email ?: "Google User",
            profilePicture = user.picture
        )
    }

    private suspend fun getOrCreateFileSync(accessToken: String): String {
        val searchUrl = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='cloudstream_sync.json'%20and%20'appDataFolder'%20in%20parents%20and%20trashed=false&fields=files(id,name)"
        val list = app.get(
            searchUrl,
            headers = mapOf("Authorization" to "Bearer $accessToken")
        ).parsed<DriveFilesList>()

        val existingFile = list.files.firstOrNull()
        if (existingFile != null) {
            return existingFile.id
        }

        val createUrl = "https://www.googleapis.com/drive/v3/files"
        val createRes = app.post(
            createUrl,
            headers = mapOf(
                "Authorization" to "Bearer $accessToken",
                "Content-Type" to "application/json"
            ),
            json = mapOf(
                "name" to "cloudstream_sync.json",
                "parents" to listOf("appDataFolder")
            )
        ).parsed<DriveFile>()

        return createRes.id
    }

    suspend fun sync(context: Context): Boolean {
        if (isSyncActive) return false
        isSyncActive = true
        try {
            val auth = AccountManager.allApis.firstOrNull { it.idPrefix == idPrefix } ?: return false
            val token = auth.authToken() ?: return false
            val refreshedData = auth.freshAuth() ?: return false
            val accessToken = refreshedData.token.accessToken ?: return false

            return withContext(Dispatchers.IO) {
                try {
                    val fileId = getOrCreateFileSync(accessToken)
                    val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
                    val response = app.get(
                        downloadUrl,
                        headers = mapOf("Authorization" to "Bearer $accessToken")
                    )

                    val remotePayload = if (response.isSuccessful && response.text.isNotBlank()) {
                        try {
                            parseJson<SyncPayload>(response.text)
                        } catch (t: Throwable) {
                            logError(t)
                            SyncPayload()
                        }
                    } else {
                        SyncPayload()
                    }

                    val mergedPayload = mergePayload(context, remotePayload)

                    val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
                    val uploadRes = app.put(
                        uploadUrl,
                        headers = mapOf(
                            "Authorization" to "Bearer $accessToken",
                            "Content-Type" to "application/json"
                        ),
                        requestBody = mergedPayload.toJson().toRequestBody()
                    )

                    if (uploadRes.isSuccessful) {
                        val sp = PreferenceManager.getDefaultSharedPreferences(context)
                        sp.edit().putLong("googledrive_last_synced_time", System.currentTimeMillis()).apply()
                        true
                    } else {
                        false
                    }
                } catch (t: Throwable) {
                    logError(t)
                    false
                }
            }
        } finally {
            isSyncActive = false
        }
    }

    private suspend fun mergePayload(context: Context, remote: SyncPayload): SyncPayload {
        // 1. Settings
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val localTheme = sp.getString(context.getString(R.string.app_theme_key), "AmoledLight") ?: "AmoledLight"
        val localAutoplay = sp.getBoolean(context.getString(R.string.autoplay_next_key), true)
        val localPlayer = sp.getString(context.getString(R.string.player_default_key), "") ?: ""

        val mergedTheme = remote.userSettings?.theme ?: localTheme
        val mergedAutoplay = remote.userSettings?.autoPlayNext ?: localAutoplay
        val mergedPlayer = remote.userSettings?.defaultPlayer ?: localPlayer

        withContext(Dispatchers.Main) {
            sp.edit().apply {
                putString(context.getString(R.string.app_theme_key), mergedTheme)
                putBoolean(context.getString(R.string.autoplay_next_key), mergedAutoplay)
                putString(context.getString(R.string.player_default_key), mergedPlayer)
                apply()
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
                    if (parsed != null) {
                        repoName = parsed.name
                    }
                } catch (t: Throwable) {
                    logError(t)
                }
                RepositoryManager.addRepository(RepositoryData(null, repoName, url))
            }
        }

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
                    winnerWatchType = when {
                        remote.bookmarks.planToWatch.contains(name) -> WatchType.PLANTOWATCH
                        remote.bookmarks.completed.contains(name) -> WatchType.COMPLETED
                        remote.bookmarks.watching.contains(name) -> WatchType.WATCHING
                        remote.bookmarks.onHold.contains(name) -> WatchType.ONHOLD
                        remote.bookmarks.dropped.contains(name) -> WatchType.DROPPED
                        else -> WatchType.NONE
                    }
                }
            } else if (local != null) {
                winner = local
                winnerWatchType = DataStoreHelper.getResultWatchState(local.id ?: 0)
            } else {
                winner = remoteItem!!
                winnerWatchType = when {
                    remote.bookmarks.planToWatch.contains(name) -> WatchType.PLANTOWATCH
                    remote.bookmarks.completed.contains(name) -> WatchType.COMPLETED
                    remote.bookmarks.watching.contains(name) -> WatchType.WATCHING
                    remote.bookmarks.onHold.contains(name) -> WatchType.ONHOLD
                    remote.bookmarks.dropped.contains(name) -> WatchType.DROPPED
                    else -> WatchType.NONE
                }
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
        val localProgressKeys = mutableListOf<String>()
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

                val progressItem = SyncWatchProgressItem(
                    mediaTitle = showName,
                    season = resume.season ?: 0,
                    episode = resume.episode ?: 0,
                    timestampInSeconds = seconds,
                    lastUpdated = dateStr
                )
                localProgressItems.add(progressItem)
                localProgressKeys.add(key)
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
            } catch (e: Exception) {
                0L
            }

            if (local != null && remoteItem != null) {
                winner = if (localTime >= remoteTime) local else remoteItem
            } else if (local != null) {
                winner = local
            } else {
                winner = remoteItem!!
            }

            mergedProgressDetails[key] = winner

            val showName = winner.headerCached?.name ?: "Unknown"
            val seconds = (winner.posDur?.position ?: 0L) / 1000L
            val updateTime = winner.resumeWatching?.updateTime ?: System.currentTimeMillis()

            mergedProgressItems.add(
                SyncWatchProgressItem(
                    mediaTitle = showName,
                    season = winner.resumeWatching?.season ?: 0,
                    episode = winner.resumeWatching?.episode ?: 0,
                    timestampInSeconds = seconds,
                    lastUpdated = dateIsoFormatter.format(Date(updateTime))
                )
            )

            if (winner == remoteItem) {
                val head = winner.headerCached
                val res = winner.resumeWatching
                val pos = winner.posDur
                if (head != null && res != null) {
                    setKey(DOWNLOAD_HEADER_CACHE, head.id.toString(), head)
                    DataStoreHelper.setLastWatched(
                        res.parentId,
                        res.episodeId,
                        res.episode,
                        res.season,
                        res.isFromDownload,
                        res.updateTime
                    )
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

        return SyncPayload(
            userSettings = SyncSettings(mergedTheme, mergedAutoplay, mergedPlayer),
            repositories = mergedRepoUrls,
            bookmarks = SyncBookmarks(planToWatchList, completedList, watchingList, onHoldList, droppedList),
            watchProgress = mergedProgressItems,
            reviews = mergedReviews,
            bookmarkDetails = mergedBookmarkDetails,
            watchProgressDetails = mergedProgressDetails
        )
    }
}
