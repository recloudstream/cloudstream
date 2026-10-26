package com.lagradost.cloudstream3.syncproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi

/**
 * Account syncing (AniList, MAL, ...) is not available on desktop yet. These keep the same
 * binary signatures as the app so extensions that reference them still load; nobody is ever
 * logged in, which extensions already handle as "login required".
 */
class AccountManager {
    companion object {
        val aniListApi = AniListApi()
    }
}

data class AuthUser(
    @JsonProperty("name") val name: String?,
    @JsonProperty("id") val id: Int,
    @JsonProperty("profilePicture") val profilePicture: String? = null,
    @JsonProperty("profilePictureHeaders") val profilePictureHeaders: Map<String, String>? = null,
)

abstract class SyncAPI {
    open val name: String = "NONE"
    open val idPrefix: String = "NONE"
    open val mainUrl: String = "NONE"
    open var requireLibraryRefresh: Boolean = true
    open val syncIdName: SyncIdName? = null

    data class LibraryMetadata(
        val allLibraryLists: List<LibraryList>,
        val supportedListSorting: Set<Any>,
    )

    data class LibraryList(
        // UiText in the app, only read after a login, which cannot happen here
        val name: Any,
        val items: List<Any>,
    )
}

class SyncRepo(val api: SyncAPI) {
    val syncIdName = api.syncIdName
    val name get() = api.name
    val idPrefix get() = api.idPrefix

    fun authUser(): AuthUser? = null

    // Kotlin mangles functions returning Result, extensions call it by the app's mangled name
    @JvmName("library-IoAF18A")
    suspend fun library(): Any = Result.success<SyncAPI.LibraryMetadata?>(null)
}
