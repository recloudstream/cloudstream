package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.AudioFile
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkPlayList
import com.lagradost.cloudstream3.utils.PlayListItem

data class ServerSettings(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val corsAllowedHosts: List<String> = listOf("*"),
    val useJsdelivr: Boolean = false,
)

data class AccountConfig(
    val id: String,
    val type: String,
    val name: String? = null,
    val data: Map<String, String> = emptyMap(),
)

data class RepositoryData(
    val id: String? = null,
    val name: String? = null,
    val url: String,
    val iconUrl: String? = null,
    val shortcode: String? = null,
    val enabled: Boolean = true,
)

data class PluginData(
    val internalName: String,
    val url: String? = null,
    val isOnline: Boolean = true,
    val filePath: String,
    val version: Int,
    val repositoryUrl: String? = null,
    val name: String? = null,
    val status: Int? = null,
    val apiVersion: Int? = null,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val tvTypes: List<String>? = null,
    val language: String? = null,
    val iconUrl: String? = null,
    val fileSize: Long? = null,
    val uploadedAt: Long? = null,
    val enabled: Boolean = true,
)

data class ServerConfig(
    val server: ServerSettings = ServerSettings(),
    val accounts: MutableList<AccountConfig> = mutableListOf(),
    val repositories: MutableList<RepositoryData> = mutableListOf(),
    val plugins: MutableList<PluginData> = mutableListOf(),
    val pluginSettings: MutableMap<String, MutableMap<String, MutableMap<String, Any?>>> = mutableMapOf(),
    val providerClasses: MutableList<String> = defaultProviderClasses(),
)

const val PLUGIN_VERSION_NOT_SET = Int.MIN_VALUE
const val PLUGIN_VERSION_ALWAYS_UPDATE = -1

data class Repository(
    val iconUrl: String? = null,
    val name: String,
    val description: String? = null,
    val manifestVersion: Int,
    val pluginLists: List<String>,
)

data class SitePlugin(
    val url: String,
    val status: Int,
    val version: Int,
    val apiVersion: Int,
    val name: String,
    val internalName: String,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val repositoryUrl: String? = null,
    val tvTypes: List<String>? = null,
    val language: String? = null,
    val iconUrl: String? = null,
    val fileSize: Long? = null,
)

data class ExtractorRequest(
    val url: String,
    val referer: String? = null,
)

data class LoadLinksRequest(
    val data: String,
    val isCasting: Boolean = false,
)

data class ProviderRegisterRequest(
    val className: String,
)

data class AccountUpsertRequest(
    val id: String? = null,
    val type: String,
    val name: String? = null,
    val data: Map<String, String> = emptyMap(),
)

data class RepositoryAddRequest(
    val url: String? = null,
    val shortcode: String? = null,
    val name: String? = null,
    val enabled: Boolean = true,
)

data class PluginInstallRequest(
    val repositoryUrl: String? = null,
    val internalName: String? = null,
)

data class PluginRemoveRequest(
    val repositoryUrl: String? = null,
    val internalName: String? = null,
    val filePath: String? = null,
)

data class PluginUploadResponse(
    val plugin: PluginData,
    val path: String,
)

data class ProviderInfo(
    val name: String,
    val mainUrl: String,
    val lang: String,
    val supportedTypes: List<String>,
    val hasMainPage: Boolean,
    val hasQuickSearch: Boolean,
    val sourcePlugin: String? = null,
)

data class ErrorResponse(
    val error: String,
)

data class RepositoryPluginsResponse(
    val repository: RepositoryData,
    val plugins: List<SitePluginWithVotes>,
)

data class SitePluginWithVotes(
    val plugin: SitePlugin,
    val votes: Int? = null,
)

data class ExtractorResponse(
    val success: Boolean,
    val links: List<ExtractorLinkDto>,
    val subtitles: List<SubtitleDto>,
    val error: String? = null,
)

data class ExtractorLinkDto(
    val source: String,
    val name: String,
    val url: String,
    val referer: String,
    val quality: Int,
    val type: String,
    val headers: Map<String, String>,
    val allHeaders: Map<String, String>,
    val userAgent: String?,
    val isM3u8: Boolean,
    val isDash: Boolean,
    val extractorData: String? = null,
    val audioTracks: List<AudioTrackDto> = emptyList(),
    val playlist: List<PlayListItemDto>? = null,
)

data class PlayListItemDto(
    val url: String,
    val durationUs: Long,
)

data class SubtitleDto(
    val lang: String,
    val url: String,
    val headers: Map<String, String>?,
    val langTag: String?,
)

data class AudioTrackDto(
    val url: String,
    val headers: Map<String, String>?,
)

fun ExtractorLink.toDto(): ExtractorLinkDto {
    val playlist = if (this is ExtractorLinkPlayList) {
        this.playlist.map { it.toDto() }
    } else {
        null
    }
    val allHeaders = getAllHeaders()
    val userAgent = headerValue(allHeaders, "User-Agent") ?: USER_AGENT
    return ExtractorLinkDto(
        source = source,
        name = name,
        url = url,
        referer = referer,
        quality = quality,
        type = type.name,
        headers = headers,
        allHeaders = allHeaders,
        userAgent = userAgent,
        isM3u8 = isM3u8,
        isDash = isDash,
        extractorData = extractorData,
        audioTracks = audioTracks.map { it.toDto() },
        playlist = playlist,
    )
}

fun SubtitleFile.toDto(): SubtitleDto = SubtitleDto(
    lang = lang,
    url = url,
    headers = headers,
    langTag = langTag,
)

fun MainAPI.toInfo(): ProviderInfo = ProviderInfo(
    name = name,
    mainUrl = mainUrl,
    lang = lang,
    supportedTypes = supportedTypes.map { it.name },
    hasMainPage = hasMainPage,
    hasQuickSearch = hasQuickSearch,
    sourcePlugin = sourcePlugin,
)

private fun PlayListItem.toDto(): PlayListItemDto = PlayListItemDto(
    url = url,
    durationUs = durationUs,
)

private fun AudioFile.toDto(): AudioTrackDto = AudioTrackDto(
    url = url,
    headers = headers,
)

private fun headerValue(headers: Map<String, String>, key: String): String? {
    return headers.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
}

private fun defaultProviderClasses(): MutableList<String> = mutableListOf(      
    "com.lagradost.cloudstream3.metaproviders.TmdbProvider",
    "com.lagradost.cloudstream3.metaproviders.TraktProvider",
)
