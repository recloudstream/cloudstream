package com.lagradost.cloudstream3.syncproviders.providers

import androidx.fragment.app.FragmentActivity
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.AcraApplication.Companion.context
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPIManager
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.BackupUtils.restorePromptGithub
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute


class GithubApi(index: Int) : InAppAuthAPIManager(index){
    override val idPrefix = "Github"
    override val name = "Github"
    override val icon = R.drawable.ic_github_logo
    override val requiresPassword = true
    override val createAccountUrl = "https://github.com/settings/tokens/new?description=Cloudstream+Backup&scopes=gist"

    data class GithubOAuthEntity(
        var repoUrl: String,
        var token: String,
        var userName: String,
        var userAvatar: String,
        var gistUrl: String
    )
    companion object {
        const val GITHUB_USER_KEY: String = "github_user" // user data like profile
        var currentSession: GithubOAuthEntity? = null
    }
    private fun getAuthKey(): GithubOAuthEntity? {
        return getKey(accountId, GITHUB_USER_KEY)
    }


    data class gistsElements (
        @JsonProperty("git_pull_url") val gitUrl: String,
        @JsonProperty("url") val gistUrl:String,
        @JsonProperty("files") val files: Map<String, File>,
        @JsonProperty("owner") val owner: OwnerData
    )
    data class OwnerData(
        @JsonProperty("login") val userName: String,
        @JsonProperty("avatar_url") val userAvatar : String
    )
    data class File (
        @JsonProperty("content") val dataRaw: String?
    )

    private suspend fun initLogin(githubToken: String): Boolean{
        val response = app.get("https://api.github.com/gists",
            headers= mapOf(
                Pair("Accept" , "application/vnd.github+json"),
                Pair("Authorization", "token $githubToken"),
            )
        )

        if (!response.isSuccessful) { return false }

        val repo = tryParseJson<List<gistsElements>>(response.text)?.filter {
            it.files.keys.first() == "Cloudstream_Backup_data.txt"
        }

        if (repo?.isEmpty() == true){
            val gitresponse = app.post("https://api.github.com/gists",
                headers= mapOf(
                    Pair("Accept" , "application/vnd.github+json"),
                    Pair("Authorization", "token $githubToken"),
                ),
                requestBody = """{"description":"Cloudstream private backup gist","public":false,"files":{"Cloudstream_Backup_data.txt":{"content":"initialization"}}}""".toRequestBody(
                    RequestBodyTypes.JSON.toMediaTypeOrNull()))
            if (!gitresponse.isSuccessful) {return false}
            tryParseJson<gistsElements>(gitresponse.text).let {
                setKey(accountId, GITHUB_USER_KEY, GithubOAuthEntity(
                    token = githubToken,
                    repoUrl = it?.gitUrl?: run {
                        return false
                    },
                    userName = it.owner.userName,
                    userAvatar = it.owner.userAvatar,
                    gistUrl = it.gistUrl
                    ))
            }
            return true
        }
        else{
            repo?.first().let {
                setKey(accountId, GITHUB_USER_KEY, GithubOAuthEntity(
                    token = githubToken,
                    repoUrl = it?.gitUrl?: run {
                        return false
                    },
                    userName = it.owner.userName,
                    userAvatar = it.owner.userAvatar,
                    gistUrl = it.gistUrl
                    ))
                ioSafe  {
                    context?.restorePromptGithub()
                }


                return true
            }
        }

    }
    override suspend fun login(data: InAppAuthAPI.LoginData): Boolean {
        switchToNewAccount()
        val githubToken = data.password ?: throw IllegalArgumentException ("Requires Password")
        try {
            if (initLogin(githubToken)) {
                registerAccount()
                return true
            }
        } catch (e: Exception) {
            logError(e)
        }
        switchToOldAccount()
        return false
    }

    override fun getLatestLoginData(): InAppAuthAPI.LoginData? {
        val current = getAuthKey() ?: return null
        return InAppAuthAPI.LoginData(email = current.repoUrl, password = current.token, username = current.userName, server = current.gistUrl)
    }
    override suspend fun initialize() {
        currentSession = getAuthKey()
        val repoUrl = currentSession?.repoUrl ?: return
        val token = currentSession?.token ?: return
        setKey(repoUrl, token)
    }
    override fun logOut() {
        removeKey(accountId, GITHUB_USER_KEY)
        removeAccountKeys()
        currentSession = getAuthKey()
    }

    override fun loginInfo(): AuthAPI.LoginInfo? {
        return getAuthKey()?.let { user ->
             AuthAPI.LoginInfo(
                profilePicture = user.userAvatar,
                name = user.userName,
                accountIndex = accountIndex,
            )
        }
    }
}
