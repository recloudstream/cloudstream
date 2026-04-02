package com.lagradost.cloudstream3.syncproviders.google

import android.app.PendingIntent
import android.content.Context
import androidx.core.content.edit
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.mvvm.logError
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

    private const val SYNC_PREFS = "cs3_sync_prefs"
    private const val KEY_LAST_SYNC_TIME = "last_sync_timestamp"
    private const val KEY_IS_ENABLED = "sync_enabled"
    private const val KEY_EMAIL = "connected_email"
    private const val DRIVE_SCOPE_URL = "https://www.googleapis.com/auth/drive.appdata"
    
    private const val META_FILE = "sync_meta.json"
    private const val SHARD_TRACKING = "shard_tracking.json"
    private const val SHARD_PROGRESS = "shard_progress.json"

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

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_ENABLED, false)

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
            .apply()
    }

    fun getConnectedEmail(context: Context): String? =
        context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_EMAIL, null)

    private suspend fun getAuthResult(context: Context): com.google.android.gms.auth.api.identity.AuthorizationResult? =
        withContext(Dispatchers.IO) {
            try {
                val authRequest = AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(Scope(DRIVE_SCOPE_URL)))
                    .build()
                Identity.getAuthorizationClient(context)
                    .authorize(authRequest)
                    .await()
            } catch (e: Exception) {
                null
            }
        }

    fun push(context: Context) = ioSafe {
        if (!isEnabled(context)) return@ioSafe
        
        val result = getAuthResult(context)
        val token = result?.accessToken
        
        if (token == null) {
            result?.pendingIntent?.let {
                _syncEvents.emit(SyncResult.NeedsAuth(it))
            } ?: _syncEvents.emit(SyncResult.Push(isSuccess = false, error = "No access token"))
            return@ioSafe
        }

        try {
            val trackingKeys = listOf(
                RESULT_WATCH_STATE_DATA,
                RESULT_FAVORITES_STATE_DATA,
                RESULT_SUBSCRIBED_STATE_DATA,
                RESULT_WATCH_STATE
            )
            
            val prefs = context.getSharedPrefs()
            val trackingData = mutableMapOf<String, String>()
            DataStore.run {
                trackingKeys.forEach { folder ->
                    context.getKeys("${DataStoreHelper.currentAccount}/$folder").forEach { key ->
                        prefs.getString(key, null)?.let {
                            trackingData[key] = it
                        }
                    }
                }
            }
            
            val progressData = mutableMapOf<String, String>()
            DataStore.run {
                context.getKeys("${DataStoreHelper.currentAccount}/$VIDEO_POS_DUR").forEach { key ->
                    prefs.getString(key, null)?.let {
                        progressData[key] = it
                    }
                }
            }

            if (trackingData.isNotEmpty()) {
                val shard = SyncShard(data = trackingData, metadata = trackingData.mapValues { System.currentTimeMillis() })
                GoogleDriveApi.upload(httpClient, token, SHARD_TRACKING, mapper.writeValueAsString(shard))
            }
            
            if (progressData.isNotEmpty()) {
                val shard = SyncShard(data = progressData, metadata = progressData.mapValues { System.currentTimeMillis() })
                GoogleDriveApi.upload(httpClient, token, SHARD_PROGRESS, mapper.writeValueAsString(shard))
            }

            val meta = SyncMetadata(updatedAt = System.currentTimeMillis())
            GoogleDriveApi.upload(httpClient, token, META_FILE, mapper.writeValueAsString(meta))

            context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis()).apply()
            
            _syncEvents.emit(SyncResult.Push(isSuccess = true))
        } catch (e: Exception) {
            logError(e)
            _syncEvents.emit(SyncResult.Push(isSuccess = false, error = e.message))
        }
    }

    fun pull(context: Context) = ioSafe {
        if (!isEnabled(context)) return@ioSafe
        
        val result = getAuthResult(context)
        val token = result?.accessToken
        
        if (token == null) {
            result?.pendingIntent?.let {
                _syncEvents.emit(SyncResult.NeedsAuth(it))
            } ?: _syncEvents.emit(SyncResult.Pull(isSuccess = false, error = "No access token"))
            return@ioSafe
        }

        try {
            val metaJson = GoogleDriveApi.download(httpClient, token, META_FILE)
            if (metaJson == null) {
                _syncEvents.emit(SyncResult.Pull(isSuccess = false, error = "No cloud backup found"))
                return@ioSafe
            }
            val remoteMeta = mapper.readValue(metaJson, SyncMetadata::class.java)
            
            val lastSync = context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_SYNC_TIME, 0L)
            
            if (remoteMeta.updatedAt <= lastSync) {
                _syncEvents.emit(SyncResult.Pull(isSuccess = true))
                return@ioSafe
            }

            val prefs = context.getSharedPrefs()
            
            GoogleDriveApi.download(httpClient, token, SHARD_TRACKING)?.let { json ->
                val shard = mapper.readValue(json, SyncShard::class.java)
                prefs.edit { 
                    shard.data.forEach { (key, value) -> putString(key, value) }
                }
            }

            GoogleDriveApi.download(httpClient, token, SHARD_PROGRESS)?.let { json ->
                val shard = mapper.readValue(json, SyncShard::class.java)
                prefs.edit { 
                    shard.data.forEach { (key, value) -> putString(key, value) }
                }
            }

            context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis()).apply()
            
            _syncEvents.emit(SyncResult.Pull(isSuccess = true))
        } catch (e: Exception) {
            logError(e)
            _syncEvents.emit(SyncResult.Pull(isSuccess = false, error = e.message))
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
