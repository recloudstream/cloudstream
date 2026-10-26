package com.lagradost.cloudstream4.desktop.data

import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream4.theme.CloudStreamPrimaryColor
import com.lagradost.cloudstream4.theme.CloudStreamThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class RepositoryEntry(
    val name: String,
    val url: String,
    val iconUrl: String? = null,
)

data class InstalledPlugin(
    val internalName: String,
    val name: String,
    val repositoryUrl: String,
    val version: Int,
    val filePath: String,
    val iconUrl: String? = null,
    /** If the extension was converted from an Android-only .cs3 file */
    val convertedFromDex: Boolean = false,
    /** [com.lagradost.cloudstream4.desktop.plugins.DexConverter.VERSION] used for the conversion */
    val converterVersion: Int = 0,
)

/** Something the user saved or watched, enough to open the result page again. */
data class MediaEntry(
    val name: String,
    val url: String,
    val apiName: String,
    val posterUrl: String? = null,
    val type: TvType? = null,
    val posterHeaders: Map<String, String>? = null,
)

fun SearchResponse.toMediaEntry() = MediaEntry(name, url, apiName, posterUrl, type, posterHeaders)

data class WatchProgress(
    val media: MediaEntry,
    val episodeName: String? = null,
    val episodeData: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class Position(val positionMs: Long, val durationMs: Long) {
    val fraction: Float get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

enum class PlayerKind { Internal, External }

data class Settings(
    val theme: CloudStreamThemeMode = CloudStreamThemeMode.Dark,
    val primaryColor: CloudStreamPrimaryColor = CloudStreamPrimaryColor.NORMAL,
    val player: PlayerKind = PlayerKind.Internal,
    /** Full path or command of the external player, empty means auto detect VLC/mpv */
    val externalPlayer: String = "",
    /** Route raw.githubusercontent.com through jsDelivr, useful where GitHub is blocked */
    val jsdelivrProxy: Boolean = false,
    /** The provider shown on the home page */
    val homeProvider: String? = null,
    val seekSeconds: Int = 10,
    /** Empty means ~/Downloads/CloudStream */
    val downloadDirectory: String = "",
)

/**
 * Persistent app state for the desktop app. Each collection is exposed as a [StateFlow]
 * so the UI updates as soon as anything changes.
 */
object AppData {
    private val store = JsonFileStore(File(AppDirs.dataDir, "data.json"))

    private val _settings = MutableStateFlow(store.get("settings", Settings()))
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _repositories = MutableStateFlow(store.get("repositories", emptyList<RepositoryEntry>()))
    val repositories: StateFlow<List<RepositoryEntry>> = _repositories.asStateFlow()

    private val _plugins = MutableStateFlow(store.get("plugins", emptyList<InstalledPlugin>()))
    val plugins: StateFlow<List<InstalledPlugin>> = _plugins.asStateFlow()

    private val _bookmarks = MutableStateFlow(store.get("bookmarks", emptyList<MediaEntry>()))
    val bookmarks: StateFlow<List<MediaEntry>> = _bookmarks.asStateFlow()

    private val _history = MutableStateFlow(store.get("history", emptyList<WatchProgress>()))
    val history: StateFlow<List<WatchProgress>> = _history.asStateFlow()

    private fun <T> MutableStateFlow<T>.persist(key: String, transform: (T) -> T) {
        update(transform)
        store.set(key, value)
    }

    fun updateSettings(transform: (Settings) -> Settings) = _settings.persist("settings", transform)

    fun addRepository(repo: RepositoryEntry) = _repositories.persist("repositories") { list ->
        val index = list.indexOfFirst { it.url == repo.url }
        if (index == -1) list + repo else list.toMutableList().also { it[index] = repo }
    }

    fun removeRepository(url: String) = _repositories.persist("repositories") { list ->
        list.filterNot { it.url == url }
    }

    fun putPlugin(plugin: InstalledPlugin) = _plugins.persist("plugins") { list ->
        list.filterNot { it.filePath == plugin.filePath } + plugin
    }

    fun removePlugin(filePath: String) = _plugins.persist("plugins") { list ->
        list.filterNot { it.filePath == filePath }
    }

    fun isBookmarked(url: String) = _bookmarks.value.any { it.url == url }

    fun toggleBookmark(entry: MediaEntry) = _bookmarks.persist("bookmarks") { list ->
        if (list.any { it.url == entry.url }) list.filterNot { it.url == entry.url } else listOf(entry) + list
    }

    /** Playback position per episode/movie data string, used to resume and show watched bars */
    private val _positions = MutableStateFlow(store.get("positions", emptyMap<String, Position>()))
    val positions: StateFlow<Map<String, Position>> = _positions.asStateFlow()

    /** Keeps one history entry per show, the most recently watched episode, newest first */
    fun saveProgress(progress: WatchProgress) {
        _history.persist("history") { list ->
            listOf(progress) + list.filterNot { it.media.url == progress.media.url }.take(200)
        }
        val key = progress.episodeData ?: return
        _positions.persist("positions") { map ->
            // LinkedHashMap keeps insertion order, so the oldest entries are dropped first
            val next = LinkedHashMap(map)
            next.remove(key)
            next[key] = Position(progress.positionMs, progress.durationMs)
            if (next.size > 5000) next.keys.take(next.size - 5000).forEach { next.remove(it) }
            next
        }
    }

    fun removeHistory(url: String) = _history.persist("history") { list -> list.filterNot { it.media.url == url } }

    fun clearHistory() = _history.persist("history") { emptyList() }
}
