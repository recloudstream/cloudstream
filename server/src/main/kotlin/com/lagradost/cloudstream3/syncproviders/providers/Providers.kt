package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName

class AniListApi : SyncAPI() {
    override val name: String = "AniList"
    override val idPrefix: String = "ANILIST"
    override val syncIdName: SyncIdName = SyncIdName.Anilist
}

class MALApi : SyncAPI() {
    override val name: String = "MyAnimeList"
    override val idPrefix: String = "MAL"
    override val syncIdName: SyncIdName = SyncIdName.MyAnimeList
}

class SimklApi : SyncAPI() {
    override val name: String = "Simkl"
    override val idPrefix: String = "SIMKL"
    override val syncIdName: SyncIdName = SyncIdName.Simkl
}

class LocalList : SyncAPI() {
    override val name: String = "LocalList"
    override val idPrefix: String = "LOCAL"
    override val syncIdName: SyncIdName = SyncIdName.LocalList
}

class OpenSubtitlesApi : SubtitleAPI() {
    override val name: String = "OpenSubtitles"
    override val idPrefix: String = "OPENSUBTITLES"
}

class Addic7ed : SubtitleAPI() {
    override val name: String = "Addic7ed"
    override val idPrefix: String = "ADDIC7ED"
}

class SubDlApi : SubtitleAPI() {
    override val name: String = "SubDl"
    override val idPrefix: String = "SUBDL"
}

class SubSourceApi : SubtitleAPI() {
    override val name: String = "SubSource"
    override val idPrefix: String = "SUBSOURCE"
}
