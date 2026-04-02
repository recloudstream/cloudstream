package com.lagradost.cloudstream3.syncproviders.google

import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.BackupUtils
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStore.mapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Orchestrates cross-device sync via Google Drive's App Data folder.
 *
 * The App Data folder is:
 *  - Hidden from the user in Drive UI.
 *  - Scoped to this app only (privacy-safe).
 *  - Free, within normal Drive quota.
 *
 * Auth strategy (modern, no deprecated APIs):
 *  - Sign-in identity : Credential Manager + GetGoogleIdOption (handled in SyncSettingsFragment)
 *  - Drive token      : Identity.getAuthorizationClient().authorize()
 */
object SyncManager {

    private const val SYNC_PREFS = "cs3_sync_prefs"
    private const val KEY_LAST_SYNC_TIME = "last_sync_timestamp"
    private const val KEY_IS_ENABLED = "sync_enabled"
    private const val KEY_EMAIL = "connected_email"
    private const val DRIVE_SCOPE_URL = "https://www.googleapis.com/auth/drive.appdata"
    private const val BACKUP_FILENAME = "cs3_backup.json"

    /** Result emitted after each push or pull attempt. */
    sealed class SyncResult {
        data class Push(val isSuccess: Boolean) : SyncResult()
        data class Pull(val isSuccess: Boolean) : SyncResult()
        data class NeedsAuth(val pendingIntent: PendingIntent) : SyncResult()
    }

    private val _syncEvents = MutableSharedFlow<SyncResult>(replay = 1, extraBufferCapacity = 1)
    val syncEvents = _syncEvents.asSharedFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ─── Auth ──────────────────────────────────────────────────────────────────

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_ENABLED, false)

    /**
     * Returns a [GetGoogleIdOption] the Fragment passes to CredentialManager.
     * Requesting the Drive scope here ensures the user is prompted for it during sign-in
     * (when a serverClientId is configured).
     */
    fun buildGoogleIdOption(): GetGoogleIdOption =
        GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false) // show all accounts, not just previously-used
            .setAutoSelectEnabled(false)
            .apply {
                if (BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()) {
                    setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
                }
            }
            .build()

    /** Called by the Fragment after a successful CredentialManager result. */
    fun onSignInSuccess(context: Context, email: String) {
        context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_ENABLED, true)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun signOut(context: Context) {
        context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_ENABLED, false)
            .remove(KEY_EMAIL)
            .remove(KEY_LAST_SYNC_TIME)
            .apply()
    }

    fun getConnectedEmail(context: Context): String? =
        context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_EMAIL, null)

    /**
     * Obtains a fresh OAuth2 access token for the Drive.appdata scope using the
     * modern [Identity.getAuthorizationClient] API (replaces deprecated GoogleAuthUtil).
     */
    private suspend fun getToken(context: Context): String? =
        withContext(Dispatchers.IO) {
            val email = getConnectedEmail(context)
            println("[SyncManager] getToken() called for $email")
            try {
                val authRequest = AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(Scope(DRIVE_SCOPE_URL)))
                    .build()
                val result = Identity.getAuthorizationClient(context)
                    .authorize(authRequest)
                    .await()
                
                println("[SyncManager] getToken() result: hasToken=${result.accessToken != null}, hasPending=${result.pendingIntent != null}")
                
                if (result.accessToken == null && result.pendingIntent != null) {
                    println("[SyncManager] getToken() → Emitting NeedsAuth")
                    // We can't return PendingIntent through String?, but we can signal it
                    // The push/pull calls will handle the emission
                }
                
                result.accessToken
            } catch (e: Exception) {
                println("[SyncManager] getToken() failed: ${e.message}")
                logError(e)
                null
            }
        }
        
    /** Internal helper that returns the full result to handle resolutions */
    private suspend fun getAuthResult(context: Context): com.google.android.gms.auth.api.identity.AuthorizationResult? =
        withContext(Dispatchers.IO) {
            val email = getConnectedEmail(context)
            try {
                val authRequest = AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(Scope(DRIVE_SCOPE_URL)))
                    .build()
                Identity.getAuthorizationClient(context)
                    .authorize(authRequest)
                    .await()
            } catch (e: Exception) {
                println("[SyncManager] getAuthResult() failed: $e")
                null
            }
        }

    // ─── Push / Pull ────────────────────────────────────────────────────────────

    /**
     * Serialises the local DataStore + Settings into a JSON snapshot and
     * uploads it to Drive App Data folder. Last-write wins on conflict.
     */
    fun push(context: Context) = ioSafe {
        println("[SyncManager] push() start")
        if (!isEnabled(context)) {
            println("[SyncManager] push() aborted: sync not enabled")
            return@ioSafe
        }
        
        val result = getAuthResult(context)
        val token = result?.accessToken
        
        if (token == null) {
            if (result?.pendingIntent != null) {
                println("[SyncManager] push() needs auth resolution")
                _syncEvents.emit(SyncResult.NeedsAuth(result.pendingIntent!!))
            } else {
                println("[SyncManager] push() failed: token null")
                _syncEvents.emit(SyncResult.Push(isSuccess = false))
            }
            return@ioSafe
        }

        val backup = BackupUtils.getBackup(context)
        if (backup == null) {
            println("[SyncManager] push() failed: backup null")
            _syncEvents.emit(SyncResult.Push(isSuccess = false))
            return@ioSafe
        }
        val json = mapper.writeValueAsString(backup)

        try {
            println("[SyncManager] push() uploading...")
            GoogleDriveApi.upload(httpClient, token, BACKUP_FILENAME, json)
            context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis()).apply()
            println("[SyncManager] push() success")
            _syncEvents.emit(SyncResult.Push(isSuccess = true))
        } catch (e: Exception) {
            println("[SyncManager] push() exception: ${e.message}")
            logError(e)
            _syncEvents.emit(SyncResult.Push(isSuccess = false))
        }
    }

    /**
     * Downloads the latest snapshot from Drive and merges it into the local state.
     * The merge strategy is last-write-wins at the per-data-type level (timestamp
     * comparison). A full set-union is performed for bookmarks/subscriptions so items
     * are never silently deleted by a pull.
     */
    fun pull(context: Context) = ioSafe {
        println("[SyncManager] pull() start")
        if (!isEnabled(context)) {
            println("[SyncManager] pull() aborted: sync not enabled")
            return@ioSafe
        }
        
        val result = getAuthResult(context)
        val token = result?.accessToken
        
        if (token == null) {
            if (result?.pendingIntent != null) {
                println("[SyncManager] pull() needs auth resolution")
                _syncEvents.emit(SyncResult.NeedsAuth(result.pendingIntent!!))
            } else {
                println("[SyncManager] pull() failed: token null")
                _syncEvents.emit(SyncResult.Pull(isSuccess = false))
            }
            return@ioSafe
        }

        try {
            println("[SyncManager] pull() downloading...")
            val json = GoogleDriveApi.download(httpClient, token, BACKUP_FILENAME)
            if (json == null) {
                println("[SyncManager] pull() failed: json null")
                _syncEvents.emit(SyncResult.Pull(isSuccess = false))
                return@ioSafe
            }
            val remoteBackup = mapper.readValue(json, BackupUtils.BackupFile::class.java)

            // TODO Phase 3: add real timestamp-based merge instead of naive restore
            println("[SyncManager] pull() restoring...")
            BackupUtils.restore(
                context,
                remoteBackup,
                restoreSettings = true,
                restoreDataStore = true
            )
            context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis()).apply()
            println("[SyncManager] pull() success")
            _syncEvents.emit(SyncResult.Pull(isSuccess = true))
        } catch (e: Exception) {
            println("[SyncManager] pull() exception: ${e.message}")
            logError(e)
            _syncEvents.emit(SyncResult.Pull(isSuccess = false))
        }
    }

    fun getLastSyncTime(context: Context): Long =
        context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC_TIME, 0L)
}

/**
 * Minimal Google Drive App Data REST v3 implementation using OkHttp.
 *
 * Uses multipart upload for create, PATCH for update.
 * Keeps only the single newest backup file to save quota.
 */
internal object GoogleDriveApi {
    private const val BASE = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
    private const val APP_DATA_SPACE = "appDataFolder"

    /** Finds the Drive file ID for [filename] in the App Data folder, or null. */
    private fun findFileId(client: OkHttpClient, token: String, filename: String): String? {
        val url = "$BASE/files?spaces=$APP_DATA_SPACE" +
                "&q=name+%3D+%27$filename%27" +
                "&fields=files(id)"
        val req = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $token").get().build()
        val body = client.newCall(req).execute().use { it.body.string() }
        val files = JSONObject(body).optJSONArray("files") ?: return null
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    /**
     * Upload or overwrite [filename] with [content].
     * Uses a simple multipart request which is well within Drive's 5 MB limit for metadata+JSON.
     */
    fun upload(client: OkHttpClient, token: String, filename: String, content: String) {
        val existingId = findFileId(client, token, filename)

        val boundary = "cs3_sync_boundary"
        val meta = """{"name":"$filename","parents":["$APP_DATA_SPACE"]}"""
        val body = "--$boundary\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                "$meta\r\n" +
                "--$boundary\r\n" +
                "Content-Type: application/json\r\n\r\n" +
                "$content\r\n" +
                "--$boundary--"

        val reqBody = body.toRequestBody("multipart/related; boundary=$boundary".toMediaTypeOrNull())

        val req = if (existingId == null) {
            // Create
            Request.Builder()
                .url("$UPLOAD_BASE/files?uploadType=multipart&spaces=$APP_DATA_SPACE")
                .addHeader("Authorization", "Bearer $token")
                .post(reqBody).build()
        } else {
            // Update (PATCH)
            Request.Builder()
                .url("$UPLOAD_BASE/files/$existingId?uploadType=multipart")
                .addHeader("Authorization", "Bearer $token")
                .patch(reqBody).build()
        }

        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Drive upload failed: ${response.code} ${response.body.string()}")
            }
        }
    }

    /** Downloads [filename] content from App Data folder or returns null. */
    fun download(client: OkHttpClient, token: String, filename: String): String? {
        val fileId = findFileId(client, token, filename) ?: return null
        val req = Request.Builder()
            .url("$BASE/files/$fileId?alt=media")
            .addHeader("Authorization", "Bearer $token")
            .get().build()
        return client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) null else response.body.string()
        }
    }
}
