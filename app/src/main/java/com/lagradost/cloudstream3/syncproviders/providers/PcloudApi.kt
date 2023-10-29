package com.lagradost.cloudstream3.syncproviders.providers

import android.content.Context
import android.util.Base64
import androidx.fragment.app.FragmentActivity
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.openBrowser
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.debugPrint
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.BackupAPI
import com.lagradost.cloudstream3.syncproviders.OAuth2API
import com.lagradost.cloudstream3.syncproviders.RemoteFile
import com.lagradost.cloudstream3.utils.AppUtils.splitQuery
import com.lagradost.nicehttp.NiceFile
import java.io.File
import java.net.URL
import java.security.SecureRandom

class PcloudApi(index: Int) : OAuth2API,
    BackupAPI<String>(index) {
    companion object {
        const val PCLOUD_TOKEN_KEY: String = "pcloud_token"
        const val PCLOUD_HOST_KEY: String = "pcloud_host"
        const val PCLOUD_USERNAME_KEY: String = "pcloud_username"
        const val PCLOUD_FILE_ID_KEY = "pcloud_file_id"
        const val FILENAME = "cloudstream-backup.json"

//        data class OAuthResponse(
//            @JsonProperty("access_token") val access_token: String,
//            @JsonProperty("token_type") val token_type: String,
//            @JsonProperty("uid") val uid: Int,
//
//            @JsonProperty("result") val result: Int,
//            @JsonProperty("error") val error: String?
//        )

        /** https://docs.pcloud.com/methods/file/uploadfile.html */
        data class FileUpload(
            @JsonProperty("result") val result: Int,
            // @JsonProperty("fileids") val fileids: List<Int>,
            @JsonProperty("metadata") val metadata: List<FileMetaData>,
        ) {
            data class FileMetaData(
                val fileid: Long,
            )
        }

        /** https://docs.pcloud.com/methods/streaming/getfilelink.html */
        data class FileLink(
            @JsonProperty("result") val result: Int,
            @JsonProperty("path") val path: String,
            @JsonProperty("hosts") val hosts: List<String>
        ) {
            fun getBestLink(): String? {
                val host = hosts.firstOrNull() ?: return null
                return "https://$host$path"
            }
        }

        data class UserInfo(
            @JsonProperty("email") val email: String,
            @JsonProperty("userid") val userid: String
        )
    }


    override val name = "pCloud"
    override val icon = R.drawable.ic_baseline_add_to_drive_24
    override val requiresLogin = true
    override val createAccountUrl = "https://my.pcloud.com/#page=login"
    override val idPrefix = "pcloud"

    override fun loginInfo(): AuthAPI.LoginInfo? {
        // Guarantee token
        if (getKey<String>(accountId, PCLOUD_TOKEN_KEY).isNullOrBlank()) return null

        val username = getKey<String>(accountId, PCLOUD_USERNAME_KEY) ?: return null
        return AuthAPI.LoginInfo(
            name = username,
            accountIndex = accountIndex
        )
    }

    override fun logOut() {
        removeAccountKeys()
    }

    override suspend fun initialize() {
        scheduleDownload(true)
    }

    val url = "https://pcloud.com/"
    override val key = "" // TODO FIX
    override val redirectUrl = "pcloud"

    override suspend fun handleRedirect(url: String): Boolean {
        // redirect_uri#access_token=XXXXX&token_type=bearer&uid=YYYYYY&state=ZZZZZZ&locationid=[1 or 2]&hostname=[api.pcloud.com or eapi.pcloud.com]
        val query = splitQuery(URL(url.replace(appString, "https").replace("#", "?")))

        if (query["state"] != state || state.isBlank()) {
            return false
        }
        state = ""

        val token = query["access_token"] ?: return false
        val hostname = query["hostname"] ?: return false

        val userInfo = app.get(
            "https://$hostname/userinfo",
            headers = mapOf("Authorization" to "Bearer $token")
        ).parsedSafe<UserInfo>() ?: return false

        switchToNewAccount()
        setKey(accountId, PCLOUD_TOKEN_KEY, token)
        setKey(accountId, PCLOUD_USERNAME_KEY, userInfo.email.substringBeforeLast("@"))
        setKey(accountId, PCLOUD_HOST_KEY, hostname)
        registerAccount()

        scheduleDownload(runNow = true, overwrite = true)
        return true
    }

    private fun getToken(): String? {
        return getKey(accountId, PCLOUD_TOKEN_KEY)
    }

    private val mainUrl: String
        get() = getKey<String>(accountId, PCLOUD_HOST_KEY)?.let { "https://$it" }
            ?: "https://api.pcloud.com"
    private val authHeaders: Map<String, String>
        get() = getToken()?.let { token -> mapOf("Authorization" to "Bearer $token") } ?: mapOf()

    private fun getFileId(): Long? = getKey(accountId, PCLOUD_FILE_ID_KEY)

    private var state = ""
    override fun authenticate(activity: FragmentActivity?) {
        val secureRandom = SecureRandom()
        val codeVerifierBytes = ByteArray(96) // base64 has 6bit per char; (8/6)*96 = 128
        secureRandom.nextBytes(codeVerifierBytes)
        state =
            Base64.encodeToString(codeVerifierBytes, Base64.DEFAULT).trimEnd('=').replace("+", "-")
                .replace("/", "_").replace("\n", "")
        val codeChallenge = state

        val request =
            "https://my.pcloud.com/oauth2/authorize?response_type=token&client_id=$key&state=$codeChallenge&redirect_uri=$appString://$redirectUrl"
        openBrowser(request, activity)
    }

    override suspend fun getLoginData(): String? {
        return getToken()
    }

    override suspend fun uploadFile(
        context: Context,
        backupJson: String,
        loginData: String
    ) {
        val ioFile = File(AcraApplication.context?.cacheDir, FILENAME)
        ioFile.writeText(backupJson)

        val uploadedFile = app.post(
            "$mainUrl/uploadfile",
            files = listOf(
                NiceFile(ioFile),
                NiceFile("nopartial", "1")
            ),
            headers = authHeaders
        ).parsedSafe<FileUpload>()

        debugPrint { "${this.name}: Uploaded file: $uploadedFile" }

        val fileId = uploadedFile?.metadata?.firstOrNull()?.fileid ?: return
        setKey(accountId, PCLOUD_FILE_ID_KEY, fileId)
    }

    override suspend fun getRemoteFile(
        context: Context,
        loginData: String
    ): RemoteFile {
        val fileId = getFileId() ?: return RemoteFile.NotFound()
        val fileLink = app.post(
            "$mainUrl/getfilelink", data = mapOf(
                "fileid" to fileId.toString()
            ),
            referer = "https://pcloud.com",
            headers = authHeaders
        ).parsedSafe<FileLink>()

        val url = fileLink?.getBestLink() ?: return RemoteFile.NotFound()
        return RemoteFile.Success(app.get(url).text)
    }
}