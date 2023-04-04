package com.lagradost.cloudstream3.syncproviders.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.FragmentActivity
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.auth.oauth2.TokenResponse
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.MemoryDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.BackupAPI
import com.lagradost.cloudstream3.syncproviders.InAppOAuth2API
import com.lagradost.cloudstream3.syncproviders.InAppOAuth2APIManager
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.BackupUtils
import com.lagradost.cloudstream3.utils.BackupUtils.getBackup
import com.lagradost.cloudstream3.utils.BackupUtils.restore
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStore
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import kotlinx.coroutines.Job
import java.io.InputStream
import java.util.*


// improvements and ideas
//  - add option to use proper oauth through google services one tap - would need google console project on behalf of cloudstream
//  - encrypt data on drive
//  - choose what should be synced
//  - having a button to run sync would be nice
class GoogleDriveApi(index: Int) :
    InAppOAuth2APIManager(index),
    BackupAPI<InAppOAuth2API.LoginData> {
    /////////////////////////////////////////
    /////////////////////////////////////////
    // Setup
    override val key = "gdrive"
    override val redirectUrl = "oauth/google-drive"

    override val idPrefix = "gdrive"
    override val name = "Google Drive"
    override val icon = R.drawable.ic_baseline_add_to_drive_24

    override val requiresFilename = true
    override val requiresSecret = true
    override val requiresClientId = true
    override val defaultFilenameValue = "cloudstreamapp-sync-file"
    override val defaultRedirectUrl =
        "https://chiff.github.io/cloudstream-sync/google-drive" // TODO: we should move this one to cs3 repo

    override var uploadJob: Job? = null

    var tempAuthFlow: AuthorizationCodeFlow? = null
    var lastBackupJson: String? = null

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
        runDownloader(true)

        tempAuthFlow = null
        return true
    }

    /////////////////////////////////////////
    /////////////////////////////////////////
    // InAppOAuth2APIManager implementation
    override suspend fun initialize() {
        if (loginInfo() == null) {
            return
        }

        ioSafe {
            runDownloader(true)
        }
    }

    override fun loginInfo(): AuthAPI.LoginInfo? {
        getCredentialsFromStore() ?: return null

        return AuthAPI.LoginInfo(
            name = "google-account-$accountIndex",
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
        return getValue<InAppOAuth2API.LoginData>(K.LOGIN_DATA)
    }

    /////////////////////////////////////////
    /////////////////////////////////////////
    // BackupAPI implementation
    override fun Context.mergeBackup(incomingData: String) {
        val currentData = getBackup()
        val newData = DataStore.mapper.readValue<BackupUtils.BackupFile>(incomingData)

        getRedundantKeys(currentData, newData).forEach {
            removeKey(it)
        }

        restore(
            newData,
            restoreSettings = true,
            restoreDataStore = true
        )
    }

    // 🤮
    private fun getRedundantKeys(
        old: BackupUtils.BackupFile,
        new: BackupUtils.BackupFile
    ): List<String> = mutableListOf(
        *getRedundant(old.settings._Bool, new.settings._Bool),
        *getRedundant(old.settings._Long, new.settings._Long),
        *getRedundant(old.settings._Float, new.settings._Float),
        *getRedundant(old.settings._Int, new.settings._Int),
        *getRedundant(old.settings._String, new.settings._String),
        *getRedundant(old.settings._StringSet, new.settings._StringSet),
        *getRedundant(old.datastore._Bool, new.datastore._Bool),
        *getRedundant(old.datastore._Long, new.datastore._Long),
        *getRedundant(old.datastore._Float, new.datastore._Float),
        *getRedundant(old.datastore._Int, new.datastore._Int),
        *getRedundant(old.datastore._String, new.datastore._String),
        *getRedundant(old.datastore._StringSet, new.datastore._StringSet),
    )

    private fun getRedundant(old: Map<String, *>?, new: Map<String, *>?): Array<String> =
        old.orEmpty().keys.subtract(new.orEmpty().keys).toTypedArray()

    override fun Context.createBackup(loginData: InAppOAuth2API.LoginData) {
        val drive = getDriveService() ?: return

        val fileName = loginData.fileName
        val syncFileId = loginData.syncFileId
        val ioFile = java.io.File(AcraApplication.context?.cacheDir, fileName)
        lastBackupJson = getBackup().toJson()
        ioFile.writeText(lastBackupJson!!)

        val fileMetadata = File()
        fileMetadata.name = fileName
        fileMetadata.mimeType = "application/json"
        val fileContent = FileContent("application/json", ioFile)

        val fileId = getOrCreateSyncFileId(drive, loginData)
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

    override fun downloadSyncData() {
        val ctx = AcraApplication.context ?: return
        val drive = getDriveService() ?: return
        val loginData = getLatestLoginData() ?: return

        val existingFileId = getOrCreateSyncFileId(drive, loginData)
        val existingFile = if (existingFileId != null) {
            try {
                drive.files().get(existingFileId)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        if (existingFile != null) {
            try {
                val inputStream: InputStream = existingFile.executeMediaAsInputStream()
                val content: String = inputStream.bufferedReader().use { it.readText() }
                Log.d("SYNC_API", "downloadSyncData merging")
                ctx.mergeBackup(content)
                return
            } catch (_: Exception) {
            }
        } else {
            Log.d("SYNC_API", "downloadSyncData file not exists")
            uploadSyncData()
        }
    }

    private fun getOrCreateSyncFileId(drive: Drive, loginData: InAppOAuth2API.LoginData): String? {
        val existingFileId: String? = loginData.syncFileId ?: drive
            .files()
            .list()
            .setQ("name='${loginData.fileName}' and trashed=false")
            .execute()
            .files
            ?.getOrNull(0)
            ?.id

        if (loginData.syncFileId == null) {
            if (existingFileId != null) {
                loginData.syncFileId = existingFileId
                storeValue(K.LOGIN_DATA, loginData)

                return existingFileId
            }

            return null
        }

        val verifyId = drive.files().get(existingFileId)
        return if (verifyId == null) {
            return null
        } else {
            existingFileId
        }
    }

    override fun uploadSyncData() {
        val ctx = AcraApplication.context ?: return
        val loginData = getLatestLoginData() ?: return
        Log.d("SYNC_API", "uploadSyncData createBackup")
        ctx.createBackup(loginData)
    }

    override fun shouldUpdate(changedKey: String, isSettings: Boolean): Boolean {
        val ctx = AcraApplication.context ?: return false

        // would be smarter to check properties, but its called once in UPLOAD_THROTTLE seconds
        val newBackup = ctx.getBackup().toJson()
        return lastBackupJson != newBackup
    }

    private fun getDriveService(): Drive? {
        val credential = getCredentialsFromStore() ?: return null

        return Drive.Builder(
            GAPI.HTTP_TRANSPORT,
            GAPI.JSON_FACTORY,
            credential
        )
            .setApplicationName("cloudstreamapp-drive-sync")
            .build()
    }

    /////////////////////////////////////////
    /////////////////////////////////////////
    // Internal
    private val continuousDownloader = BackupAPI.Scheduler<Unit>(
        BackupAPI.DOWNLOAD_THROTTLE.inWholeMilliseconds
    ) {
        if (uploadJob?.isActive == true) {
            uploadJob!!.invokeOnCompletion {
            Log.d("SYNC_API", "upload is running, reschedule download")
                runDownloader()
            }
        } else {
            Log.d("SYNC_API", "downloadSyncData will run")
            downloadSyncData()
            runDownloader()
        }
    }

    private fun runDownloader(runNow: Boolean = false) {
        if (runNow) {
            continuousDownloader.workNow()
        } else {
            continuousDownloader.work()

        }
    }

    private fun getCredentialsFromStore(): Credential? {
        val LOGIN_DATA = getLatestLoginData()
        val TOKEN = getValue<TokenResponse>(K.TOKEN)

        val credential = if (LOGIN_DATA != null && TOKEN != null) {
            GAPI.getCredentials(TOKEN, LOGIN_DATA)
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

    /////////////////////////////////////////
    /////////////////////////////////////////
    // Google API integration helper
    object GAPI {
        private const val DATA_STORE_ID = "gdrive_tokens"
        private val USED_SCOPES = listOf(DriveScopes.DRIVE_FILE)
        val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
        val JSON_FACTORY = GsonFactory.getDefaultInstance()

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
