package com.lagradost.cloudstream3.syncproviders

open class SyncAPI : AuthAPI() {
    open var requireLibraryRefresh: Boolean = false
    open val mainUrl: String = "NONE"
    open val syncIdName: SyncIdName? = null

    open suspend fun updateStatus(
        auth: AuthData?,
        id: String,
        newStatus: AbstractSyncStatus
    ): Boolean = false

    open suspend fun status(auth: AuthData?, id: String): AbstractSyncStatus? = null

    open suspend fun load(auth: AuthData?, id: String): SyncResult? = null

    open suspend fun library(auth: AuthData?): LibraryMetadata? = null

    open class AbstractSyncStatus

    class SyncResult

    class LibraryMetadata
}

enum class SyncIdName {
    Anilist,
    MyAnimeList,
    Trakt,
    Imdb,
    Simkl,
    LocalList,
}
