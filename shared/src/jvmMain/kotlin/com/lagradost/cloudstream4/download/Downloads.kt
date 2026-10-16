package com.lagradost.cloudstream4.download

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream4.network.DesktopHttp
import com.lagradost.cloudstream4.player.DataStoreSubtitleSettings
import com.lagradost.cloudstream4.player.LinksViewModel
import com.lagradost.cloudstream4.player.SubtitleLanguages
import com.lagradost.cloudstream4.providers.loadLinksSafely
import com.lagradost.cloudstream4.settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class DownloadStatus { Queued, FindingLinks, Downloading, Paused, Failed, Done }

data class DownloadState(
    val status: DownloadStatus,
    val bytes: Long = 0,
    /** The full size, 0 while unknown */
    val total: Long = 0,
    /** Why it failed */
    val message: String? = null,
    /** Failed because the source picked is gone: the user picks one again */
    val pickAgain: Boolean = false,
) {
    val fraction: Float? get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
    val active: Boolean get() = status == DownloadStatus.Queued || status == DownloadStatus.FindingLinks || status == DownloadStatus.Downloading
}

/**
 * What to download: an episode, or a movie, and what finds its links. [link] is the source the user
 * picked, with the [subtitles] found beside it. Without, as when resuming, the links are found again
 * and the download carries on with the source picked before
 */
data class DownloadRequest(
    val title: DownloadedTitle,
    val episode: DownloadedEpisode,
    val source: DownloadSource,
    val link: ExtractorLink? = null,
    val subtitles: List<SubtitleFile> = emptyList(),
)

/** Finds the links and subtitles of a download, as the player does */
fun interface LinkFinder {
    suspend fun find(source: DownloadSource, onLink: (ExtractorLink) -> Unit, onSubtitle: (SubtitleFile) -> Unit)
}

/**
 * Downloads episodes and movies to play offline, a few at a time, each from the source the user
 * picked, as on Android: the best quality can be a file of many gigabytes. A download that stopped
 * carries on where it was when it is resumed: its links are found again, and it goes on with the same
 * source. Subtitles in the language picked for auto-select are saved beside the video, as on Android.
 */
class Downloads(
    private val store: DownloadStore,
    private val scope: CoroutineScope,
    private val finder: LinkFinder,
    /** The downloads folder from the settings */
    private val root: () -> File,
    private val parallel: Int = 3,
    private val connections: () -> Int = { 3 },
    private val subtitleLanguage: () -> String? = { null },
) {
    private val _states = MutableStateFlow<Map<Int, DownloadState>>(emptyMap())

    /** Every download's state, by its episode id (a movie's is the title's id) */
    val states: StateFlow<Map<Int, DownloadState>> = _states.asStateFlow()

    private val jobs = ConcurrentHashMap<Int, Job>()
    private val slots = Semaphore(parallel.coerceIn(1, 10))

    init {
        // Downloads from before: done, or stopped halfway and ready to carry on
        val found = HashMap<Int, DownloadState>()
        for (parentId in store.titleIds()) for (episode in store.episodes(parentId)) {
            val file = store.file(episode.id) ?: continue
            val length = file.file.length()
            if (!file.file.isFile) continue
            found[episode.id] = if (isComplete(length, file.totalBytes)) DownloadState(DownloadStatus.Done, length, length)
            else DownloadState(DownloadStatus.Paused, length, file.totalBytes)
        }
        _states.value = found
    }

    private fun set(id: Int, state: DownloadState?) = _states.update { if (state == null) it - id else it + (id to state) }

    /** Changes a download's state, unless it was deleted meanwhile */
    private fun change(id: Int, change: DownloadState.() -> DownloadState) =
        _states.update { states -> states[id]?.let { states + (id to it.change()) } ?: states }

    fun state(id: Int): DownloadState? = states.value[id]

    /** Where new downloads go */
    val folder: File get() = root()

    /** Titles with a download, done or not, by name */
    fun titles(): List<DownloadedTitle> {
        val ids = states.value.keys
        return store.titleIds().mapNotNull { parentId ->
            store.title(parentId)?.takeIf { store.episodes(parentId).any { it.id in ids } }
        }.sortedBy { it.name.lowercase() }
    }

    /** A title's downloads, in season and episode order */
    fun episodes(parentId: Int): List<DownloadedEpisode> {
        val ids = states.value.keys
        return store.episodes(parentId).filter { it.id in ids }.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))
    }

    fun title(parentId: Int): DownloadedTitle? = store.title(parentId)

    /** The finished file of a download */
    fun file(id: Int): File? = store.file(id)?.file?.takeIf { state(id)?.status == DownloadStatus.Done && it.isFile }

    /** Subtitles saved beside a download: their language, as in the file name, and the file */
    fun subtitles(id: Int): List<Pair<String, File>> {
        val video = file(id) ?: return emptyList()
        val base = video.nameWithoutExtension
        return subtitleFiles(video).map { it.nameWithoutExtension.removePrefix(base).trim().ifEmpty { "Subtitles" } to it }.sortedBy { it.first }
    }

    /** "<video name> English.vtt" beside the video, but not "Episode 10 English.vtt" beside "Episode 1" */
    private fun subtitleFiles(video: File): List<File> {
        val base = video.nameWithoutExtension
        return video.parentFile?.listFiles().orEmpty().filter {
            it != video && it.isFile && it.extension.lowercase() in SUBTITLE_EXTENSIONS &&
                (it.nameWithoutExtension == base || it.nameWithoutExtension.startsWith("$base "))
        }
    }

    fun start(request: DownloadRequest) {
        val id = request.episode.id
        if (jobs[id]?.isActive == true || state(id)?.status == DownloadStatus.Done) return
        store.saveTitle(request.title)
        store.saveEpisode(request.episode, request.source)
        val before = state(id)
        // A new pick starts the file again, unless it is the same link
        val restart = request.link != null && store.file(id)?.linkHash?.let { it != request.link.url.hashCode() } == true
        set(id, DownloadState(DownloadStatus.Queued, if (restart) 0 else before?.bytes ?: 0, if (restart) 0 else before?.total ?: 0))
        jobs[id] = scope.launch {
            try {
                slots.withPermit { run(request) }
            } catch (e: CancellationException) {
                // Paused, or deleted, which has taken the state away already
                change(id) { copy(status = DownloadStatus.Paused) }
                throw e
            } finally {
                jobs.remove(id, coroutineContext[Job])
            }
        }
    }

    /** Carries on with a paused or failed download, finding its links again */
    fun resume(parentId: Int, id: Int) {
        val title = store.title(parentId) ?: return
        val episode = store.episode(parentId, id) ?: return
        val source = store.source(id) ?: return
        start(DownloadRequest(title, episode, source))
    }

    fun canResume(parentId: Int, id: Int) = store.source(id) != null && store.title(parentId) != null

    fun pause(id: Int) {
        jobs[id]?.cancel()
    }

    /** Stops the download and deletes its file and subtitles */
    fun delete(parentId: Int, id: Int) {
        val job = jobs.remove(id)
        val saved = store.file(id)
        val files = listOfNotNull(saved?.file) + subtitlesOf(id)
        set(id, null)
        store.remove(parentId, id)
        scope.launch {
            // The file is open until the download stops, and Windows cannot delete an open file
            job?.cancelAndJoin()
            withContext(Dispatchers.IO) {
                files.forEach { deleteFile(it) }
                saved?.let { removeEmptyFolders(it.folder, it.root) }
            }
        }
    }

    /**
     * Deletes a file, trying again for a few seconds while something holds it open, as the player or
     * a virus scan can on Windows. Its record is gone already, so a file left now would stay on the
     * disk for good: one that still cannot go is deleted when the app closes.
     */
    private suspend fun deleteFile(file: File) {
        repeat(DELETE_TRIES) {
            if (!file.exists() || file.delete()) return
            delay(DELETE_RETRY_MS)
        }
        println("WARN Downloads: could not delete $file, it goes when the app closes")
        file.deleteOnExit()
    }

    private fun subtitlesOf(id: Int): List<File> = store.file(id)?.file?.let(::subtitleFiles).orEmpty()

    private suspend fun run(request: DownloadRequest) {
        val id = request.episode.id
        val previous = store.file(id)
        val link: ExtractorLink
        val subtitles: List<SubtitleFile>
        if (request.link != null) {
            link = request.link
            subtitles = request.subtitles
        } else {
            change(id) { copy(status = DownloadStatus.FindingLinks, message = null, pickAgain = false) }
            val found = try {
                findAgain(request.source, previous)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                change(id) { copy(status = DownloadStatus.Failed, message = e.message ?: e.javaClass.simpleName) }
                return
            }
            if (found == null) {
                change(id) { copy(status = DownloadStatus.Failed, message = "The source picked before is gone, pick another", pickAgain = true) }
                return
            }
            link = found.first
            subtitles = found.second
        }

        val title = request.title
        val episode = request.episode
        val name = DownloadNames.fileName(title.name, episode.episode, episode.season, episode.name)
        val same = previous != null && previous.linkHash == link.url.hashCode() && previous.file.isFile
        var file = DownloadFile(
            totalBytes = if (same) previous!!.totalBytes else 0,
            root = previous?.root ?: root(),
            relativePath = previous?.relativePath ?: DownloadNames.folder(title.type, title.name),
            displayName = previous?.displayName ?: "$name.mp4",
            segmentsDone = if (same) previous!!.segmentsDone else null,
            segmentBytes = if (same) previous!!.segmentBytes else null,
            linkHash = link.url.hashCode(),
            linkName = link.name,
        )
        store.saveFile(id, file)
        val resume = when {
            !same -> null
            link.type == ExtractorLinkType.M3U8 -> previous!!.segmentsDone?.let { Downloader.Resume(it, previous.segmentBytes ?: 0) }
            else -> Downloader.Resume(0, 0)
        }
        change(id) { DownloadState(DownloadStatus.Downloading, if (same) file.file.length() else 0, file.totalBytes) }
        var saved = 0L
        try {
            Downloader.download(link, file.file, resume, connections()) { bytes, total, segments ->
                file = file.copy(totalBytes = maxOf(total, bytes), segmentsDone = segments ?: file.segmentsDone, segmentBytes = if (segments != null) bytes else file.segmentBytes)
                change(id) { DownloadState(DownloadStatus.Downloading, bytes, file.totalBytes) }
                // The store writes a whole file, so not on every update
                val now = System.nanoTime()
                if (now - saved > SAVE_EVERY_NS) {
                    saved = now
                    store.saveFile(id, file)
                }
            }
            val length = file.file.length()
            store.saveFile(id, file.copy(totalBytes = length))
            saveSubtitles(subtitles, file.copy(totalBytes = length))
            change(id) { DownloadState(DownloadStatus.Done, length, length) }
            return
        } catch (e: CancellationException) {
            store.saveFile(id, file)
            throw e
        } catch (e: Throwable) {
            store.saveFile(id, file)
            val error = e.message ?: e.javaClass.simpleName
            println("WARN Downloads: ${link.name} failed for ${title.name}: $error")
            // Another source may work
            change(id) { copy(status = DownloadStatus.Failed, message = error, pickAgain = true) }
        }
    }

    /**
     * The source a download started with, among the links found again: the same url, else the same
     * name, as a link's url often changes between visits. Null when it is gone
     */
    private suspend fun findAgain(source: DownloadSource, previous: DownloadFile?): Pair<ExtractorLink, List<SubtitleFile>>? {
        if (previous?.linkHash == null && previous?.linkName == null) return null
        val subtitles = ArrayList<SubtitleFile>()
        var byName: ExtractorLink? = null
        var same: ExtractorLink? = null
        coroutineScope {
            lateinit var finding: Job
            finding = launch {
                finder.find(
                    source,
                    onLink = { link ->
                        synchronized(subtitles) {
                            if (link.url.hashCode() == previous.linkHash) same = link
                            else if (byName == null && link.name == previous.linkName && downloadable(link)) byName = link
                        }
                        // The same link is found: no need to wait for the other extractors
                        if (same != null) finding.cancel()
                    },
                    onSubtitle = { sub -> synchronized(subtitles) { if (subtitles.none { it.url == sub.url }) subtitles += sub } },
                )
            }
        }
        val link = synchronized(subtitles) { same ?: byName } ?: return null
        return link to synchronized(subtitles) { subtitles.toList() }
    }

    /** Up to three subtitles in the language picked for auto-select, named after the video as on Android */
    private suspend fun saveSubtitles(subtitles: List<SubtitleFile>, file: DownloadFile) = withContext(Dispatchers.IO) {
        val tag = subtitleLanguage() ?: return@withContext
        val chosen = subtitles.filter { SubtitleLanguages.tagOf(it.lang) == tag }.take(3)
        val base = file.file.nameWithoutExtension
        chosen.forEachIndexed { index, sub ->
            runCatching {
                val extension = if (sub.url.contains(".srt")) "srt" else "vtt"
                val label = DownloadNames.sanitize(sub.lang).ifEmpty { "Subtitles" } + if (chosen.size > 1) " ${index + 1}" else ""
                val request = Request.Builder().url(sub.url).apply {
                    header("User-Agent", USER_AGENT)
                    sub.headers?.forEach { (key, value) -> header(key, value) }
                }.build()
                DesktopHttp.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    file.folder.resolve("$base $label.$extension").writeBytes(response.body.bytes())
                }
            }.onFailure { println("WARN Downloads: subtitle ${sub.lang} could not be saved: ${it.message}") }
        }
    }

    companion object {
        private val SUBTITLE_EXTENSIONS = setOf("vtt", "srt", "txt", "ass", "ttml", "sbv", "dfxp")
        private const val SAVE_EVERY_NS = 2_000_000_000L
        private const val DELETE_TRIES = 10
        private const val DELETE_RETRY_MS = 500L

        /**
         * Removes [folder] and the folders above it that a deleted download left empty, such as a
         * series' folder after its last episode, up to [root], the downloads folder, which stays
         */
        internal fun removeEmptyFolders(folder: File, root: File) {
            val stop = root.canonicalFile
            var current: File? = folder.canonicalFile
            while (current != null && current != stop && current.startsWith(stop) && current.list()?.isEmpty() == true) {
                if (!current.delete()) return
                current = current.parentFile
            }
        }

        /** Links that can be saved to a file: not DRM, not torrents or web pages */
        fun downloadable(link: ExtractorLink) = link !is DrmExtractorLink && Downloader.canDownload(link)

        /** Links to pick from, best quality first */
        fun forPicking(links: List<ExtractorLink>) = links.filter(::downloadable).distinctBy { it.url }.sortedByDescending { LinksViewModel.rank(it.quality) }

        /** Done when within 1 KB of the full size, as on Android */
        fun isComplete(bytes: Long, total: Long) = total > 0 && bytes > 1024 && bytes + 1024 >= total

        /** The folder picked in the settings, or Downloads\CloudStream in the user's folder */
        fun defaultRoot(): File = File(System.getProperty("user.home"), "Downloads").resolve("CloudStream")

        val instance: Downloads by lazy {
            Downloads(
                store = DownloadStore(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                finder = { source, onLink, onSubtitle ->
                    val api = APIHolder.getApiFromNameNull(source.apiName)
                        ?: throw IllegalStateException("${source.apiName} is not loaded")
                    api.loadLinksSafely(source.data, onSubtitle = onSubtitle, onLink = onLink)
                },
                root = { settings.general.downloadPath.get().takeIf { it.isNotBlank() }?.let(::File) ?: defaultRoot() },
                parallel = settings.general.parallelDownloads.get(),
                connections = { settings.general.concurrentConnections.get() },
                subtitleLanguage = { DataStoreSubtitleSettings.instance.autoSelect.value },
            )
        }
    }
}
