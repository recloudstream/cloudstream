package com.lagradost.cloudstream3.syncproviders.google

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.BackupUtils
import com.lagradost.cloudstream3.utils.BackupUtils.isTransferable
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStore
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.mapper
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.RESULT_FAVORITES_STATE_DATA
import com.lagradost.cloudstream3.utils.RESULT_SUBSCRIBED_STATE_DATA
import com.lagradost.cloudstream3.utils.RESULT_WATCH_STATE
import com.lagradost.cloudstream3.utils.RESULT_WATCH_STATE_DATA
import com.lagradost.cloudstream3.utils.VIDEO_POS_DUR
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.AutoDownloadMode
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

object SyncManager {
    private const val TAG = "SyncManager"
    private const val SYNC_PREFS = "cs3_sync_prefs"
    const val KEY_LAST_SYNC_TIME = "sync_last_time"
    private const val KEY_IS_ENABLED = "sync_enabled"
    private const val KEY_EMAIL = "sync_email"
    const val KEY_SILENTLY_CONNECTED = "sync_silently_connected"
    private const val DRIVE_SCOPE_URL = "https://www.googleapis.com/auth/drive.appdata"
    
    private const val META_FILE = "sync_meta.json"
    private const val SHARD_DATASTORE = "shard_datastore.json"
    private const val SHARD_SETTINGS = "shard_settings.json"
    private const val LEGACY_BACKUP = "cloudstream-backup.json"

    data class Shard(
        val version: Int,
        val data: Map<String, Any>
    )

    sealed class SyncResult {
        data class Push(val isSuccess: Boolean, val error: String? = null) : SyncResult()
        data class Pull(val isSuccess: Boolean, val error: String? = null) : SyncResult()
        data class NeedsAuth(val pendingIntent: PendingIntent) : SyncResult()
    }

    private val _syncEvents = MutableSharedFlow<SyncResult>(replay = 0, extraBufferCapacity = 1)
    val syncEvents = _syncEvents.asSharedFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_EMAIL, null) != null || prefs.getBoolean(KEY_SILENTLY_CONNECTED, false)
    }

    fun buildGoogleIdOption(): GetGoogleIdOption =
        GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .apply {
                if (BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()) {
                    setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
                }
            }
            .build()

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
            .remove(KEY_SILENTLY_CONNECTED)
            .apply()
    }

    fun getConnectedEmail(context: Context): String? =
        context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_EMAIL, null) ?: if (isEnabled(context)) "Connected Account" else null

    private suspend fun getAuthResult(context: Context): com.google.android.gms.auth.api.identity.AuthorizationResult? = withContext(Dispatchers.IO) {
        try {
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_SCOPE_URL)))
                .build()
            val result = Identity.getAuthorizationClient(context)
                .authorize(authRequest)
                .await()
            
            if (result.accessToken != null) {
                context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE).edit {
                    putBoolean(KEY_SILENTLY_CONNECTED, true)
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Auth failed: ${e.message}")
            null
        }
    }

    private suspend fun getAuthToken(context: Context): String? {
        return getAuthResult(context)?.accessToken
    }

    fun trySilentAuth(context: Context) = ioSafe {
        if (!isEnabled(context)) return@ioSafe
        getAuthToken(context)
    }

    fun push(context: Context) = ioSafe {
        if (!isEnabled(context)) return@ioSafe
        
        val token = getAuthToken(context) ?: return@ioSafe

        try {
            val datastoreKeys = listOf(
                RESULT_WATCH_STATE_DATA,
                RESULT_FAVORITES_STATE_DATA,
                RESULT_SUBSCRIBED_STATE_DATA,
                RESULT_WATCH_STATE,
                VIDEO_POS_DUR
            )
            val datastorePrefs = context.getSharedPrefs()
            val datastoreMap = mutableMapOf<String, Any>()
            datastoreKeys.forEach { folder ->
                DataStore.run {
                    context.getKeys("${DataStoreHelper.currentAccount}/$folder").forEach { key ->
                        datastorePrefs.getString(key, null)?.let { datastoreMap[key] = it }
                    }
                }
            }
            
            listOf("PLUGINS_KEY", "REPOSITORIES_KEY").forEach { key ->
                if (key.isTransferable(context)) {
                    datastorePrefs.getString(key, null)?.let { datastoreMap[key] = it }
                }
            }

            
            val settingsPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val settingsMap = mutableMapOf<String, Any>()
            settingsPrefs.all.forEach { (k, v) ->
                if (v != null && k.isTransferable(context)) {
                    settingsMap[k] = v
                }
            }

            Log.d(TAG, "Pushing shards: datastore(${datastoreMap.size} keys), settings(${settingsMap.size} keys)")
            val now = System.currentTimeMillis()
            GoogleDriveApi.upload(httpClient, token, SHARD_DATASTORE, mapper.writeValueAsString(Shard(1, datastoreMap)))
            GoogleDriveApi.upload(httpClient, token, SHARD_SETTINGS, mapper.writeValueAsString(Shard(1, settingsMap)))

            val meta = SyncMetadata(updatedAt = now)
            GoogleDriveApi.upload(httpClient, token, META_FILE, mapper.writeValueAsString(meta))

            Log.d(TAG, "Push successful at $now")
            context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_SYNC_TIME, now).apply()
            
            _syncEvents.emit(SyncResult.Push(isSuccess = true))
        } catch (e: Exception) {
            logError(e)
            _syncEvents.emit(SyncResult.Push(isSuccess = false, error = e.message))
        }
    }

    fun pull(context: Context) = ioSafe {
        val token = getAuthToken(context) ?: return@ioSafe
        Log.d(TAG, "Starting pull...")

        try {
            val metaJson = GoogleDriveApi.download(httpClient, token, META_FILE)
            if (metaJson == null) {
                Log.d(TAG, "No metadata found, checking legacy backup...")
                val legacyJson = GoogleDriveApi.download(httpClient, token, LEGACY_BACKUP)
                if (legacyJson != null) {
                    Log.d(TAG, "Found legacy backup, converting...")
                    val legacyBackup = mapper.readValue(legacyJson, BackupUtils.BackupFile::class.java)
                    val (dataShard, settingsShard) = SyncUtils.convertLegacyToShards(legacyBackup)
                    applyShard(context, dataShard, isDataStore = true)
                    applyShard(context, settingsShard, isDataStore = false)
                    Log.d(TAG, "Legacy migration successful")
                    _syncEvents.emit(SyncResult.Pull(isSuccess = true))
                    return@ioSafe
                }
                Log.d(TAG, "No remote data found at all")
                _syncEvents.emit(SyncResult.Pull(isSuccess = false, error = "No backup found on cloud"))
                return@ioSafe
            }

            val remoteMeta = mapper.readValue(metaJson, SyncMetadata::class.java)
            val lastSync = context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_SYNC_TIME, 0L)
            
            Log.d(TAG, "Cloud update time: ${remoteMeta.updatedAt}, Local sync time: $lastSync")
            if (remoteMeta.updatedAt <= lastSync) {
                Log.d(TAG, "Local version is up to date")
                _syncEvents.emit(SyncResult.Pull(isSuccess = true))
                return@ioSafe
            }

            Log.d(TAG, "Pulling new shards...")
            GoogleDriveApi.download(httpClient, token, SHARD_DATASTORE)?.let { json ->
                val shard = parseShard(json)
                Log.d(TAG, "Applying datastore shard (${shard.data.size} keys)")
                applyShard(context, shard, isDataStore = true)
            }

            GoogleDriveApi.download(httpClient, token, SHARD_SETTINGS)?.let { json ->
                val shard = parseShard(json)
                Log.d(TAG, "Applying settings shard (${shard.data.size} keys)")
                applyShard(context, shard, isDataStore = false)
            }

            try {
                @Suppress("DEPRECATION_ERROR")
                PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_restoreSyncPlugins(context)
            } catch (e: Exception) {
                Log.e(TAG, "Plugin restore failed: ${e.message}")
            }

            context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_SYNC_TIME, remoteMeta.updatedAt).apply()
            
            _syncEvents.emit(SyncResult.Pull(isSuccess = true))
        } catch (e: Exception) {
            logError(e)
            _syncEvents.emit(SyncResult.Pull(isSuccess = false, error = e.message))
        }
    }

    private fun parseShard(json: String): Shard {
        val obj = JSONObject(json)
        val dataObj = obj.getJSONObject("data")
        val dataMap = mutableMapOf<String, Any>()
        dataObj.keys().forEach { key ->
            dataMap[key] = dataObj.get(key)
        }
        return Shard(obj.getInt("version"), dataMap)
    }

    private fun applyShard(context: Context, shard: Shard, isDataStore: Boolean) {
        val prefs = if (isDataStore) context.getSharedPreferences("rebuild_preference", Context.MODE_PRIVATE)
                    else PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit {
            shard.data.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                    else -> putString(key, value.toString())
                }
            }
        }
    }

    suspend fun getCloudMetadata(context: Context): SyncMetadata? = withContext(Dispatchers.IO) {
        val token = getAuthToken(context) ?: return@withContext null
        try {
            GoogleDriveApi.download(httpClient, token, META_FILE)?.let {
                mapper.readValue(it, SyncMetadata::class.java)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getLastSyncTime(context: Context): Long =
        context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC_TIME, 0L)
}

internal object GoogleDriveApi {
    private const val BASE = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
    private const val APP_DATA_SPACE = "appDataFolder"

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

    fun upload(client: OkHttpClient, token: String, filename: String, content: String) {
        val existingId = findFileId(client, token, filename)

        val boundary = "cs3_sync_boundary"
        val meta = if (existingId == null) {
            """{"name":"$filename","parents":["$APP_DATA_SPACE"]}"""
        } else {
            """{"name":"$filename"}"""
        }
        val body = "--$boundary\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                "$meta\r\n" +
                "--$boundary\r\n" +
                "Content-Type: application/json\r\n\r\n" +
                "$content\r\n" +
                "--$boundary--"

        val reqBody = body.toRequestBody("multipart/related; boundary=$boundary".toMediaTypeOrNull())

        val req = if (existingId == null) {
            Request.Builder()
                .url("$UPLOAD_BASE/files?uploadType=multipart")
                .addHeader("Authorization", "Bearer $token")
                .post(reqBody).build()
        } else {
            Request.Builder()
                .url("$UPLOAD_BASE/files/$existingId?uploadType=multipart")
                .addHeader("Authorization", "Bearer $token")
                .patch(reqBody).build()
        }

        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                val responseBody = response.body.string()
                val displayError = try {
                    val jsonObj = JSONObject(responseBody)
                    jsonObj.optJSONObject("error")?.optString("message") ?: responseBody
                } catch (_: Exception) {
                    responseBody
                }
                throw Exception("${response.code}: $displayError")
            }
        }
    }

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
