package com.lagradost.cloudstream3.syncproviders

import androidx.fragment.app.FragmentActivity
import com.fasterxml.jackson.annotation.JsonIgnore
import com.lagradost.cloudstream3.AcraApplication

interface InAppOAuth2API : OAuth2API {
    data class LoginData(
        val secret: String,
        val clientId: String,
        val redirectUrl: String,
        val fileNameInput: String,
        var syncFileId: String?
    ) {
        @JsonIgnore
        val fileName = fileNameInput.replace(Regex("[^a-zA-Z0-9.\\-_]"), "") + ".json"
    }

    // this is for displaying the UI
    val requiresFilename: Boolean
    val requiresSecret: Boolean
    val requiresClientId: Boolean

    val defaultFilenameValue: String
    val defaultRedirectUrl: String


    // should launch intent to acquire token
    suspend fun getAuthorizationToken(activity: FragmentActivity, data: LoginData)

    // used to fill the UI if you want to edit any data about your login info
    fun getLatestLoginData(): LoginData?
}

abstract class InAppOAuth2APIManager(defIndex: Int) : AccountManager(defIndex), InAppOAuth2API {
    enum class K {
        LOGIN_DATA,
        TOKEN;

        val value: String = "data_oauth2_$name"
    }

    protected fun <T> storeValue(key: K, value: T) = AcraApplication.setKey(
        accountId, key.value, value
    )

    protected fun clearValue(key: K) = AcraApplication.removeKey(
        accountId, key.value
    )

    protected inline fun <reified T : Any> getValue(key: K) = AcraApplication.getKey<T>(
        accountId, key.value
    )

    override val requiresLogin = true
    override val createAccountUrl = null

    override fun logOut() {
        K.values().forEach { clearValue(it) }
        removeAccountKeys()
    }

}