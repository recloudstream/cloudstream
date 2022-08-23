package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPIManager
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
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
    override val createAccountUrl = "https://github.com/settings/tokens/new"

    data class GithubOAuthEntity(
        var repoUrl: String,
        var token: String
    )
    companion object {
        const val GITHUB_USER_KEY: String = "github_user" // user data like profile
        var currentSession: GithubOAuthEntity? = null
    }
    private fun getAuthKey(): GithubOAuthEntity? {
        return getKey(accountId, GITHUB_USER_KEY)
    }
    private class repodata (
        @JsonProperty("full_name") val repoUrl: String
    )


    private suspend fun initLogin(githubToken: String): Boolean{
        val response = app.post("https://api.github.com/user/repos",
            headers= mapOf(
                Pair("Accept" , "application/vnd.github+json"),
                Pair("Authorization", "token $githubToken"),
            ),
            requestBody = """{"name":"sync data for Cloudstream", "description": "Private repo for cloudstream Account", "private": true}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()))

        if (response.isSuccessful) {
            val repoUrl = tryParseJson<repodata>(response.text).let {
                setKey(accountId, GITHUB_USER_KEY, GithubOAuthEntity(
                    token = githubToken,
                    repoUrl = it?.repoUrl?: run {
                        return false
                    }))
                it.repoUrl
            }
            val tmpDir = createTempDir()
            val git = Git.cloneRepository()
                .setURI("https://github.com/$repoUrl.git")
                .setDirectory(tmpDir)
                .setTimeout(30)
                .setCredentialsProvider(
                    UsernamePasswordCredentialsProvider(githubToken, "")
                )
                .call()
            createTempFile("backup", "txt", tmpDir)
            git.add()
                .addFilepattern(".")
                .call()
            git.commit()
                .setAll(true)
                .setMessage("Update backup")
                .call()
            git.remoteAdd()
                .setName("origin")
                .setUri(URIish("https://github.com/$repoUrl.git"))
                .call()
            git.push()
                .setRemote("https://github.com/$repoUrl.git")
                .setTimeout(30)
                .setCredentialsProvider(
                    UsernamePasswordCredentialsProvider(githubToken, "")
                )
                .call();
            return true
        }
        return false
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
            switchToOldAccount()
        }
        switchToOldAccount()
        return false
    }

    override fun getLatestLoginData(): InAppAuthAPI.LoginData? {
        val current = getAuthKey() ?: return null
        return InAppAuthAPI.LoginData(username = current.repoUrl, password = current.token)
    }
    override suspend fun initialize() {
        currentSession = getAuthKey() ?: return // just in case the following fails
        setKey(currentSession!!.repoUrl, currentSession!!.token)
    }
    override fun logOut() {
        AcraApplication.removeKey(accountId, GITHUB_USER_KEY)
        removeAccountKeys()
        currentSession = getAuthKey()
    }

    override fun loginInfo(): AuthAPI.LoginInfo? {
        getAuthKey()?.let { user ->
            return AuthAPI.LoginInfo(
                profilePicture = null,
                name = user.repoUrl,
                accountIndex = accountIndex,
            )
        }
        return null
    }
}