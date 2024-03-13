package com.lagradost.cloudstream3.syncproviders

import androidx.fragment.app.FragmentActivity
import com.fasterxml.jackson.annotation.JsonIgnore

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
    val infoUrl: String?


    // should launch intent to acquire token
    suspend fun getAuthorizationToken(activity: FragmentActivity, data: LoginData)

    // used to fill the UI if you want to edit any data about your login info
    fun getLatestLoginData(): LoginData?
}