package com.lagradost.cloudstream4.desktop.downloads

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.sortUrls
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.M3u8Helper2
import com.lagradost.cloudstream4.desktop.data.AppData
import com.lagradost.cloudstream4.desktop.data.JsonFileStore
import com.lagradost.cloudstream4.desktop.data.AppDirs
import com.lagradost.cloudstream4.desktop.data.MediaEntry
import com.lagradost.cloudstream4.desktop.ui.Playable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

enum class DownloadStatus { Queued, Downloading, Paused, Completed, Failed }

data class DownloadItem(
    /** The episode/movie data string, unique per downloadable item */
    val id: String,
    val media: MediaEntry,
    val title: String,
    val season: Int? = null,
    val episode: Int? = null,
    val filePath: String,
    val status: DownloadStatus = DownloadStatus.Queued,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    /** Number of HLS segments written, used to resume */
    val segmentsDone: Int = 0,
    val segmentsTotal: Int = 0,
    val subtitlePaths: List<String> = emptyList(),
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val progress: Float
        get() = when {
            status == DownloadStatus.Completed -> 1f
            segmentsTotal > 0 -> segmentsDone.toFloat() / segmentsTotal
            totalBytes > 0 -> downloadedBytes.toFloat() / totalBytes
            else -> 0f
        }

    val file: File get() = File(filePath)
}

/**
 * Downloads movies and episodes for offline viewing. Plain video links are downloaded directly
 * (resumable with Range requests) and HLS streams are fetched segment by segment into a .ts file.
 */
object DownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = JsonFileStore(File(AppDirs.dataDir, "downloads.json"))
    private val jobs = ConcurrentHashMap<String, Job>()
    private val parallel = Semaphore(2)

    private val _downloads = MutableStateFlow(
        store.get("items", emptyList<DownloadItem>()).map {
            // Anything that was running when the app closed is resumable
            if (it.status == DownloadStatus.Downloading || it.status == DownloadStatus.Queued) it.copy(status = DownloadStatus.Paused) else it
        }
    )
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    val defaultDirectory: File
        get() = File(System.getProperty("user.home"), "Downloads/CloudStream")

    val directory: File
        get() = AppData.settings.value.downloadDirectory.takeIf { it.isNotBlank() }?.let(::File) ?: defaultDirectory

    fun forEpisode(data: String): DownloadItem? = _downloads.value.firstOrNull { it.id == data }

    private fun persist() = store.set("items", _downloads.value)

    private fun update(id: String, persist: Boolean = false, transform: (DownloadItem) -> DownloadItem) {
        _downloads.update { list -> list.map { if (it.id == id) transform(it) else it } }
        if (persist) persist()
    }

    private fun sanitize(name: String) = name.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "").trim().take(120).ifBlank { "download" }

    /** Queues a download, links are loaded from the extension unless [link] is given */
    fun enqueue(media: MediaEntry, playable: Playable, link: ExtractorLink? = null) {
        val existing = forEpisode(playable.data)
        if (existing != null) {
            if (existing.status != DownloadStatus.Completed) resume(existing.id, link)
            return
        }
        val folder = File(directory, sanitize(media.name))
        val item = DownloadItem(
            id = playable.data,
            media = media,
            title = playable.title,
            season = playable.season,
            episode = playable.episode,
            // The extension is picked once the link type is known
            filePath = File(folder, sanitize(playable.title.removePrefix(media.name).removePrefix(" · ").ifBlank { media.name })).path,
        )
        _downloads.update { listOf(item) + it }
        persist()
        start(item.id, link)
    }

    fun resume(id: String, link: ExtractorLink? = null) {
        if (jobs[id]?.isActive == true) return
        update(id, persist = true) { it.copy(status = DownloadStatus.Queued, error = null) }
        start(id, link)
    }

    fun pause(id: String) {
        jobs.remove(id)?.cancel()
        update(id, persist = true) { it.copy(status = DownloadStatus.Paused) }
    }

    fun delete(id: String) {
        jobs.remove(id)?.cancel()
        val item = forEpisode(id) ?: return
        item.file.delete()
        File(item.filePath + ".part").delete()
        item.subtitlePaths.forEach { File(it).delete() }
        item.file.parentFile?.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
        _downloads.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    private fun start(id: String, link: ExtractorLink?) {
        jobs[id] = scope.launch {
            parallel.withPermit {
                try {
                    run(id, link)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    logError(t)
                    update(id, persist = true) { it.copy(status = DownloadStatus.Failed, error = t.message ?: t.javaClass.simpleName) }
                } finally {
                    jobs.remove(id)
                }
            }
        }
    }

    private suspend fun loadLinks(item: DownloadItem): Pair<List<ExtractorLink>, List<SubtitleFile>> {
        val api = APIHolder.getApiFromNameNull(item.media.apiName)
            ?: throw IllegalStateException("The extension ${item.media.apiName} is not loaded")
        val links = mutableSetOf<ExtractorLink>()
        val subtitles = mutableListOf<SubtitleFile>()
        try {
            withTimeout(api.loadLinksTimeoutMs ?: 120_000L) {
                api.loadLinks(item.id, false, { synchronized(subtitles) { subtitles += it } }) { link ->
                    if (link !is DrmExtractorLink && (link.type == ExtractorLinkType.VIDEO || link.type == ExtractorLinkType.M3U8)) {
                        synchronized(links) { links += link }
                    }
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException && t !is kotlinx.coroutines.TimeoutCancellationException) throw t
            logError(t)
        }
        return sortUrls(links) to subtitles
    }

    private suspend fun run(id: String, preferred: ExtractorLink?) {
        update(id) { it.copy(status = DownloadStatus.Downloading) }
        val item = forEpisode(id) ?: return
        val (links, subtitles) = if (preferred != null) listOf(preferred) to emptyList() else loadLinks(item)
        if (links.isEmpty()) throw IllegalStateException("No downloadable links found")

        var lastError: Throwable? = null
        for (link in links) {
            coroutineContext.ensureActive()
            try {
                val extension = if (link.type == ExtractorLinkType.M3U8) "ts" else
                    link.url.substringBefore('?').substringAfterLast('.', "mp4").lowercase().takeIf { it in setOf("mp4", "mkv", "webm", "avi", "mov") } ?: "mp4"
                val base = item.filePath.substringBeforeLast(".${item.file.extension}").takeIf { item.file.extension in setOf("ts", "mp4", "mkv", "webm", "avi", "mov") }
                    ?: item.filePath
                val target = File("$base.$extension")
                update(id) { it.copy(filePath = target.path) }
                target.parentFile.mkdirs()

                if (link.type == ExtractorLinkType.M3U8) downloadHls(id, link, target) else downloadFile(id, link, target)
                val subtitleFiles = downloadSubtitles(target, subtitles)
                update(id, persist = true) {
                    it.copy(status = DownloadStatus.Completed, subtitlePaths = subtitleFiles.map { f -> f.path }, error = null)
                }
                return
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                logError(t)
                lastError = t
                // A different link means a different file, start over
                update(id) { it.copy(downloadedBytes = 0, totalBytes = 0, segmentsDone = 0, segmentsTotal = 0) }
            }
        }
        throw lastError ?: IllegalStateException("Download failed")
    }

    private fun headersFor(link: ExtractorLink): Map<String, String> = buildMap {
        put("User-Agent", USER_AGENT)
        if (link.referer.isNotBlank()) put("Referer", link.referer)
        putAll(link.headers)
    }

    private suspend fun downloadFile(id: String, link: ExtractorLink, target: File) {
        val part = File(target.path + ".part")
        val offset = if (part.exists()) part.length() else 0L
        val request = Request.Builder().url(link.url).apply {
            headersFor(link).forEach { (k, v) -> header(k, v) }
            if (offset > 0) header("Range", "bytes=$offset-")
        }.build()

        app.baseClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            val resumed = response.code == 206
            val length = response.body.contentLength()
            val total = if (length > 0) length + (if (resumed) offset else 0) else 0
            var written = if (resumed) offset else 0L
            update(id) { it.copy(totalBytes = total, downloadedBytes = written) }

            FileOutputStream(part, resumed).use { out ->
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    var lastUpdate = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 300) {
                            lastUpdate = now
                            update(id) { it.copy(downloadedBytes = written) }
                        }
                    }
                }
            }
            update(id) { it.copy(downloadedBytes = written, totalBytes = maxOf(total, written)) }
        }
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
    }

    private suspend fun downloadHls(id: String, link: ExtractorLink, target: File) {
        val data = M3u8Helper2.hslLazy(
            M3u8Helper.M3u8Stream(link.url, link.quality, headersFor(link)),
            selectBest = true,
            requireAudio = true,
        )
        val part = File(target.path + ".part")
        val start = forEpisode(id)?.segmentsDone?.takeIf { part.exists() && it < data.size } ?: 0
        update(id) { it.copy(segmentsTotal = data.size, segmentsDone = start) }

        var written = if (start > 0) part.length() else 0L
        FileOutputStream(part, start > 0).use { out ->
            for (index in start until data.size) {
                coroutineContext.ensureActive()
                val bytes = data.resolveLinkSafe(index) ?: throw IllegalStateException("Failed to download segment $index")
                out.write(bytes)
                out.flush()
                written += bytes.size
                update(id, persist = index % 25 == 0) { it.copy(segmentsDone = index + 1, downloadedBytes = written) }
            }
        }
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
    }

    /** Saves subtitles next to the video with the same name, so VLC and other players pick them up */
    private suspend fun downloadSubtitles(video: File, subtitles: List<SubtitleFile>): List<File> =
        subtitles.distinctBy { it.lang }.take(10).mapNotNull { subtitle ->
            try {
                val text = app.get(subtitle.url, headers = subtitle.headers.orEmpty()).text
                val extension = when {
                    text.trimStart().startsWith("WEBVTT") -> "vtt"
                    text.contains("[Script Info]") -> "ass"
                    else -> "srt"
                }
                File(video.parentFile, "${video.nameWithoutExtension}.${sanitize(subtitle.lang)}.$extension").apply { writeText(text) }
            } catch (t: Throwable) {
                logError(t)
                null
            }
        }
}
