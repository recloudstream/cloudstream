package com.lagradost.cloudstream3.syncproviders.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.FragmentActivity
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.auth.oauth2.TokenResponse
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.MemoryDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.BackupAPI
import com.lagradost.cloudstream3.syncproviders.InAppOAuth2API
import com.lagradost.cloudstream3.syncproviders.RemoteFile
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.ioWorkSafe
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.util.Date


/**
 * ## Improvements and ideas
 *
 * | State    | Priority | Description
 * |---------:|:--------:|---------------------------------------------------------------------
 * | Someday  | 5        | Choose what should be synced and recheck `invalidKeys` in createBackupScheduler
 * | Someday  | 3        | Add option to use proper OAuth through Google Services One Tap
 * | Someday  | 5        | Encrypt data on Drive (low priority)
 * | Someday  | 4        | Make local sync
 * | Someday  | 4        | Make sync button more interactive
 * | Solved   | 4        | Add button to manually trigger sync
 * | Solved   | 1        | Racing conditions when multiple devices in use
 * | Solved   | 2        | Restoring backup should update view models
 * | Solved   | 1        | Check if data was really changed when calling backupscheduler.work then
 * |          |          | dont update sync meta if not needed
 * | Solved   | 4        | Implement backup before user quits application
 * | Solved   | 1        | Do not write sync meta when user is not syncing data
 * | Solved   | 1        | Fix sync/restore bugs
 * | Solved   | 1        | When scheduler has queued upload job (but is not working in backupApi
 * |          |          | yet) we should postpone download and prioritize upload
 * | Solved   | 3        | Move "https://chiff.github.io/cloudstream-sync/google-drive"
 */
class GoogleDriveApi(index: Int) :
    BackupAPI<InAppOAuth2API.LoginData>(index), InAppOAuth2API {
    override val key = "gdrive"
    override val redirectUrl = "oauth/google-drive"

    override val idPrefix = "gdrive"
    override val name = "Google Drive"
    override val icon = R.drawable.ic_baseline_add_to_drive_24

    override val requiresLogin = true
    override val createAccountUrl = null
    override val requiresFilename = true
    override val requiresSecret = true
    override val requiresClientId = true
    override val defaultFilenameValue = "cloudstreamapp-sync-file"
    override val defaultRedirectUrl =
        "https://recloudstream.github.io/cloudstream-sync/google-drive"
    override val infoUrl = "https://recloudstream.github.io/cloudstream-sync/google-drive/help.html"

    private var tempAuthFlow: AuthorizationCodeFlow? = null

    companion object {
        const val GOOGLE_ACCOUNT_INFO_KEY = "google_account_info_key"
    }

    private fun <T> storeValue(key: K, value: T) = setKey(
        accountId, key.value, value
    )

    private fun clearValue(key: K) = removeKey(accountId, key.value)

    private inline fun <reified T : Any> getValue(key: K) = getKey<T>(
        accountId, key.value
    )

    enum class K {
        LOGIN_DATA,
        IS_READY,
        TOKEN,
        ;

        val value: String = "data_oauth2_$name"
    }

    /////////////////////////////////////////
    /////////////////////////////////////////
    // OAuth2API implementation
    override fun authenticate(activity: FragmentActivity?) {
        // this was made for direct authentication for OAuth2
        throw IllegalStateException("Authenticate should not be called")
    }

    override suspend fun handleRedirect(url: String): Boolean {
        val flow = tempAuthFlow
        val data = getValue<InAppOAuth2API.LoginData>(K.LOGIN_DATA)

        if (flow == null || data == null) {
            return false
        }

        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code")

        val googleTokenResponse = try {
            flow.newTokenRequest(code)
                .setRedirectUri(data.redirectUrl)
                .execute()
        } catch (e: Exception) {
            switchToOldAccount()
            return false
        }

        flow.createAndStoreCredential(
            googleTokenResponse,
            data.clientId
        )

        storeValue(K.TOKEN, googleTokenResponse)
        storeValue(K.IS_READY, true)

        // First launch overwrites
        scheduleDownload(runNow = true, overwrite = true)

        tempAuthFlow = null
        return true
    }

    override suspend fun initialize() {
        ioSafe {
            scheduleDownload(true)
        }
    }

    private suspend fun fetchUserInfo(driveService: Drive): GoogleUser? {
        return ioWorkSafe {
            val user = driveService.about()
                .get()
                .apply {
                    this.fields = "user"
                }
                .execute()
                .user
            GoogleUser(user.displayName, user.photoLink)
        }
    }

    private suspend fun getUserInfo(driveService: Drive): GoogleUser? {
        return getKey(accountId, GOOGLE_ACCOUNT_INFO_KEY)
            ?: fetchUserInfo(driveService).also { user ->
                setKey(accountId, GOOGLE_ACCOUNT_INFO_KEY, user)
            }
    }

    data class GoogleUser(
        val displayName: String,
        val photoLink: String?,
    )

    private fun getBlankUser(): GoogleUser {
        return GoogleUser(
            "google-account-$accountIndex",
            null,
        )
    }

    override fun loginInfo(): AuthAPI.LoginInfo? {
        val driveService = getLatestLoginData()?.let { getDriveService(it) } ?: return null
        val userInfo = runBlocking {
            getUserInfo(driveService)
        } ?: getBlankUser()

        return AuthAPI.LoginInfo(
            name = userInfo.displayName,
            profilePicture = userInfo.photoLink,
            accountIndex = accountIndex
        )
    }

    /////////////////////////////////////////
    /////////////////////////////////////////
    // InAppOAuth2API implementation
    override suspend fun getAuthorizationToken(
        activity: FragmentActivity,
        data: InAppOAuth2API.LoginData
    ) {
        val credential = loginInfo()
        if (credential != null) {
            switchToNewAccount()
        }

        storeValue(K.IS_READY, false)
        storeValue(K.LOGIN_DATA, data)

        val authFlow = GAPI.createAuthFlow(data.clientId, data.secret)
        this.tempAuthFlow = authFlow

        try {
            registerAccount()

            val url = authFlow.newAuthorizationUrl().setRedirectUri(data.redirectUrl).build()
            val customTabIntent = CustomTabsIntent.Builder().setShowTitle(true).build()
            customTabIntent.launchUrl(activity, Uri.parse(url))
        } catch (e: Exception) {
            switchToOldAccount()
            CommonActivity.showToast(
                activity,
                activity.getString(R.string.authenticated_user_fail).format(name)
            )
        }
    }

    override fun getLatestLoginData(): InAppOAuth2API.LoginData? {
        return getValue(K.LOGIN_DATA)
    }

    override suspend fun getLoginData(): InAppOAuth2API.LoginData? {
        return getLatestLoginData()
    }

    /////////////////////////////////////////
    /////////////////////////////////////////
    // BackupAPI implementation
    override suspend fun isReady(): Boolean {
        val loginData = getLatestLoginData()
        return getValue<Boolean>(K.IS_READY) == true &&
                loginInfo() != null &&
                loginData != null &&
                getDriveService(loginData) != null &&
                AcraApplication.context != null
    }

    override suspend fun getRemoteFile(
        context: Context,
        loginData: InAppOAuth2API.LoginData
    ): RemoteFile {
        val drive =
            getDriveService(loginData) ?: return RemoteFile.Error("Cannot get drive service")

        val existingFileId = getOrFindExistingSyncFileId(drive, loginData)
        val existingFile = if (existingFileId != null) {
            try {
                drive.files().get(existingFileId)
            } catch (e: Exception) {
                Log.e(LOG_KEY, "Could not find file for id $existingFileId", e)
                null
            }
        } else {
            null
        }

        if (existingFile != null) {
            try {
                val inputStream: InputStream = existingFile.executeMediaAsInputStream()
                val content: String = inputStream.bufferedReader().use { it.readText() }
                Log.d(LOG_KEY, "downloadSyncData merging")
                return RemoteFile.Success(content)
            } catch (e: Exception) {
                Log.e(LOG_KEY, "download failed", e)
            }
        }

        // if failed
        Log.d(LOG_KEY, "downloadSyncData file not exists")
        return RemoteFile.NotFound()
    }

    override suspend fun uploadFile(
        context: Context,
        backupJson: String,
        loginData: InAppOAuth2API.LoginData
    ) {
        val drive = getDriveService(loginData) ?: return

        val fileName = loginData.fileName
        val syncFileId = loginData.syncFileId
        val ioFile = java.io.File(AcraApplication.context?.cacheDir, fileName)
        ioFile.writeText(backupJson)

        val fileMetadata = File()
        fileMetadata.name = fileName
        fileMetadata.mimeType = "application/json"
        val fileContent = FileContent("application/json", ioFile)

        val fileId = getOrFindExistingSyncFileId(drive, loginData)
        if (fileId != null) {
            try {
                val file = drive.files()
                    .update(fileId, fileMetadata, fileContent)
                    .setKeepRevisionForever(false)
                    .execute()
                loginData.syncFileId = file.id
            } catch (_: Exception) {
                val file = drive.files().create(fileMetadata, fileContent).execute()
                loginData.syncFileId = file.id
            }
        } else {
            val file = drive.files().create(fileMetadata, fileContent).execute()
            loginData.syncFileId = file.id
        }

        // in case we had to create new file
        if (syncFileId != loginData.syncFileId) {
            storeValue(K.LOGIN_DATA, loginData)
        }
    }

    private fun getOrFindExistingSyncFileId(
        drive: Drive,
        loginData: InAppOAuth2API.LoginData
    ): String? {
        if (loginData.syncFileId != null) {
            try {
                val verified = drive.files().get(loginData.syncFileId).execute()
                return verified.id
            } catch (_: Exception) {
            }
        }

        val existingFileId: String? = drive
            .files()
            .list()
            .setQ("name='${loginData.fileName}' and trashed=false")
            .execute()
            .files
            ?.getOrNull(0)
            ?.id

        if (existingFileId != null) {
            loginData.syncFileId = existingFileId
            storeValue(K.LOGIN_DATA, loginData)

            return existingFileId
        }

        return null
    }

    private fun getDriveService(loginData: InAppOAuth2API.LoginData): Drive? {
        val credential = getCredentialsFromStore(loginData) ?: return null

        return Drive.Builder(
            GAPI.HTTP_TRANSPORT,
            GAPI.JSON_FACTORY,
            credential
        )
            .setApplicationName("cloudstreamapp-drive-sync")
            .build()
    }


    private fun getCredentialsFromStore(loginData: InAppOAuth2API.LoginData): Credential? {
        val token = getValue<TokenResponse>(K.TOKEN)

        val credential = if (token != null) {
            GAPI.getCredentials(token, loginData)
        } else {
            return null
        }

        if (credential.expirationTimeMilliseconds < Date().time) {
            val success = credential.refreshToken()

            if (!success) {
                logOut()
                return null
            }
        }

        return credential
    }

    override fun logOut() {
        K.values().forEach { clearValue(it) }
        removeAccountKeys()
    }

    /////////////////////////////////////////
    /////////////////////////////////////////
    // Google API integration helper
    object GAPI {
        private const val DATA_STORE_ID = "gdrive_tokens"
        private val USED_SCOPES = listOf(DriveScopes.DRIVE_FILE)
        val HTTP_TRANSPORT: NetHttpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val JSON_FACTORY: GsonFactory = GsonFactory.getDefaultInstance()

        fun createAuthFlow(clientId: String, clientSecret: String): GoogleAuthorizationCodeFlow =
            GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT,
                JSON_FACTORY,
                clientId,
                clientSecret,
                USED_SCOPES
            )
                .setCredentialDataStore(MemoryDataStoreFactory().getDataStore(DATA_STORE_ID))
                .setApprovalPrompt("force")
                .setAccessType("offline")
                .build()

        fun getCredentials(
            tokenResponse: TokenResponse,
            loginData: InAppOAuth2API.LoginData,
        ): Credential = createAuthFlow(
            loginData.clientId,
            loginData.secret
        ).loadCredential(loginData.clientId) ?: createAuthFlow(
            loginData.clientId,
            loginData.secret
        ).createAndStoreCredential(
            tokenResponse,
            loginData.clientId
        )
    }
}
