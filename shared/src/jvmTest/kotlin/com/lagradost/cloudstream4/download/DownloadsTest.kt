package com.lagradost.cloudstream4.download

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream4.android.DesktopAndroid
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadsTest {
    private val dir = Files.createTempDirectory("cs-downloads").toFile()

    init {
        DesktopAndroid.init(dir.toPath().resolve("home"))
    }

    private val video = ByteArray(300_000) { (it % 251).toByte() }
    private val segments = List(3) { n -> ByteArray(50_000) { (n * 7 + it % 13).toByte() } }
    private val ranges = Collections.synchronizedList(ArrayList<String?>())
    private val segmentHits = Collections.synchronizedList(ArrayList<Int>())

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/video.mp4") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            ranges += range
            val from = range?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
            exchange.responseHeaders.add("Content-Type", "video/mp4")
            if (from > 0) exchange.responseHeaders.add("Content-Range", "bytes $from-${video.size - 1}/${video.size}")
            exchange.send(if (from > 0) 206 else 200, video.copyOfRange(from, video.size))
        }
        createContext("/page.mp4") { it.responseHeaders.add("Content-Type", "text/html"); it.send(200, "<html>".toByteArray()) }
        createContext("/stream.m3u8") { exchange ->
            val playlist = buildString {
                appendLine("#EXTM3U")
                appendLine("#EXT-X-TARGETDURATION:10")
                segments.indices.forEach { appendLine("#EXTINF:10,"); appendLine("seg$it.ts") }
                appendLine("#EXT-X-ENDLIST")
            }
            exchange.send(200, playlist.toByteArray())
        }
        segments.indices.forEach { n ->
            createContext("/seg$n.ts") { segmentHits += n; it.send(200, segments[n]) }
        }
        createContext("/sub.vtt") { it.send(200, "WEBVTT\n\n00:00.000 --> 00:01.000\nHi\n".toByteArray()) }
        start()
    }
    private val base = "http://127.0.0.1:${server.address.port}"

    private fun HttpExchange.send(code: Int, body: ByteArray) {
        sendResponseHeaders(code, body.size.toLong())
        responseBody.use { it.write(body) }
    }

    @AfterTest
    fun cleanup() {
        server.stop(0)
        dir.deleteRecursively()
    }

    private fun link(path: String, type: ExtractorLinkType = ExtractorLinkType.VIDEO, name: String = "Test", quality: Int = 1080) = runBlocking {
        newExtractorLink("Test", name, "$base$path", type) { this.quality = quality }
    }

    @Test
    fun namesFilesAndFoldersAsAndroidDoes() {
        assertEquals("TVSeries/A show 2", DownloadNames.folder(TvType.TvSeries, "A show: 2?"))
        assertEquals("Movies", DownloadNames.folder(TvType.Movie, "A film"))
        assertEquals("Season 1 Episode 2 - Pilot", DownloadNames.fileName("A show", 2, 1, "Pilot"))
        assertEquals("Episode 2", DownloadNames.fileName("A show", 2, null, " "))
        assertEquals("A film", DownloadNames.fileName("A film.", null, null, null))
    }

    @Test
    fun aVideoCarriesOnWhereItStopped() = runBlocking {
        val file = File(dir, "out/video.mp4")
        file.parentFile.mkdirs()
        file.writeBytes(video.copyOf(100_000))
        Downloader.download(link("/video.mp4"), file, Downloader.Resume(0, 0), 3) { _, _, _ -> }
        assertEquals("bytes=100000-", ranges.single())
        assertContentEquals(video, file.readBytes())
    }

    @Test
    fun aWebPageIsNotSavedAsAVideo() {
        val file = File(dir, "page.mp4")
        val error = runCatching { runBlocking { Downloader.download(link("/page.mp4"), file, null, 3) { _, _, _ -> } } }.exceptionOrNull()
        assertNotNull(error)
    }

    @Test
    fun anHlsStreamIsJoinedAndResumesAfterTheLastSavedSegment() = runBlocking {
        val file = File(dir, "hls.mp4")
        var last: Triple<Long, Long, Int?>? = null
        Downloader.download(link("/stream.m3u8", ExtractorLinkType.M3U8), file, null, 2) { bytes, total, done -> last = Triple(bytes, total, done) }
        val joined = segments.reduce { a, b -> a + b }
        assertContentEquals(joined, file.readBytes())
        assertEquals(Triple(joined.size.toLong(), joined.size.toLong(), 3), last)

        // Stopped after segment 1 was saved, with part of segment 2 written after that
        file.writeBytes(segments[0] + segments[1].copyOf(10_000))
        segmentHits.clear()
        Downloader.download(link("/stream.m3u8", ExtractorLinkType.M3U8), file, Downloader.Resume(1, segments[0].size.toLong()), 2) { _, _, _ -> }
        assertContentEquals(joined, file.readBytes())
        assertEquals(listOf(1, 2), segmentHits.sorted())
    }

    @Test
    fun downloadsThePickedSourceAndKeepsTheAndroidKeys() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val root = File(dir, "Downloads")
        // Only for resuming: the picked source downloads without finding links again
        val finder = LinkFinder { _, _, _ -> error("not needed") }
        val downloads = Downloads(DownloadStore(), scope, finder, root = { root }, subtitleLanguage = { "en" })
        val title = DownloadedTitle(7, "Test", "$base/show", "A show", TvType.TvSeries, null)
        val episode = DownloadedEpisode(70, 7, "Pilot", 1, 1, null, null)
        downloads.start(
            DownloadRequest(title, episode, DownloadSource("Test", "data"), link("/video.mp4"), listOf(SubtitleFile("English", "$base/sub.vtt"))),
        )
        val done = withTimeout(20_000) { downloads.states.first { it[70]?.status == DownloadStatus.Done || it[70]?.status == DownloadStatus.Failed } }
        assertEquals(DownloadStatus.Done, done[70]?.status, done[70]?.message)

        val file = downloads.file(70)!!
        assertEquals(File(root, "TVSeries/A show/Season 1 Episode 1 - Pilot.mp4"), file)
        assertContentEquals(video, file.readBytes())
        assertEquals(listOf("English"), downloads.subtitles(70).map { it.first })
        assertEquals(listOf(title), downloads.titles())
        assertEquals(listOf(70), downloads.episodes(7).map { it.id })

        // A new start reads what is saved: the finished file is still done
        val again = Downloads(DownloadStore(), scope, finder, root = { root })
        assertEquals(DownloadStatus.Done, again.state(70)?.status)
        assertTrue(again.canResume(7, 70))

        val subtitle = File(root, "TVSeries/A show/Season 1 Episode 1 - Pilot English.vtt")
        assertTrue(subtitle.exists())
        again.delete(7, 70)
        // The files go in the background, the video first
        withTimeout(5_000) { while (file.exists() || subtitle.exists()) kotlinx.coroutines.delay(50) }
        assertNull(again.state(70))
        assertTrue(again.titles().isEmpty())
        // The series' folders it left empty go too, the downloads folder stays
        withTimeout(5_000) { while (File(root, "TVSeries").exists()) kotlinx.coroutines.delay(50) }
        assertTrue(root.isDirectory)
        scope.cancel()
    }

    @Test
    fun aHalfDownloadHeldOpenIsStillDeletedOnceItIsLetGo() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val root = File(dir, "Downloads")
        val store = DownloadStore()
        val title = DownloadedTitle(11, "Test", "$base/film3", "Held film", TvType.Movie, null)
        val movie = DownloadedEpisode(11, 11, null, null, null, null, null)
        store.saveTitle(title)
        store.saveEpisode(movie, DownloadSource("Test", "data"))
        val partial = DownloadFile(video.size.toLong(), root, "Movies", "Held film.mp4")
        partial.folder.mkdirs()
        partial.file.writeBytes(video.copyOf(100_000))
        File(root, "Movies/Other film.mp4").writeBytes(ByteArray(10))
        store.saveFile(11, partial)
        val downloads = Downloads(store, scope, LinkFinder { _, _, _ -> }, root = { root })
        assertEquals(DownloadStatus.Paused, downloads.state(11)?.status)

        // Something else has it open, as the player or a virus scan can: Windows refuses to delete it
        val held = java.io.FileInputStream(partial.file)
        downloads.delete(11, 11)
        assertNull(downloads.state(11))
        kotlinx.coroutines.delay(800)
        held.close()
        withTimeout(8_000) { while (partial.file.exists()) kotlinx.coroutines.delay(50) }
        // Another film is still in the folder, so it stays
        assertTrue(File(root, "Movies").isDirectory)
        scope.cancel()
    }

    @Test
    fun theSourcesToPickFromAreDownloadableOnesBestFirst() {
        val picked = Downloads.forPicking(
            listOf(
                link("/a.mp4", quality = 720),
                link("/t", ExtractorLinkType.MAGNET, quality = 2160),
                link("/b.m3u8", ExtractorLinkType.M3U8, quality = 1080),
                link("/a.mp4", quality = 720),
            ),
        )
        assertEquals(listOf("$base/b.m3u8", "$base/a.mp4"), picked.map { it.url })
    }

    @Test
    fun aPickedSourceThatFailsAsksForAnotherInsteadOfTakingOne() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val finder = LinkFinder { _, onLink, _ -> onLink(link("/video.mp4", quality = 2160)) }
        val downloads = Downloads(DownloadStore(), scope, finder, root = { File(dir, "Downloads") })
        val title = DownloadedTitle(8, "Test", "$base/film", "A film", TvType.Movie, null)
        val movie = DownloadedEpisode(8, 8, null, null, null, null, null)
        downloads.start(DownloadRequest(title, movie, DownloadSource("Test", "data"), link("/page.mp4")))
        val failed = withTimeout(10_000) { downloads.states.first { it[8]?.status == DownloadStatus.Failed } }[8]!!
        assertTrue(failed.pickAgain)
        assertNull(downloads.file(8))
        scope.cancel()
    }

    @Test
    fun resumingCarriesOnWithThePickedSourceFoundByItsName() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val root = File(dir, "Downloads")
        var mirror = true
        // The source's url changed since it was picked, and a better quality is found too
        val finder = LinkFinder { _, onLink, _ ->
            onLink(link("/stream.m3u8", ExtractorLinkType.M3U8, name = "Big", quality = 2160))
            if (mirror) onLink(link("/video.mp4?token=new", name = "Mirror A", quality = 720))
        }
        val store = DownloadStore()
        val title = DownloadedTitle(9, "Test", "$base/film2", "Another film", TvType.Movie, null)
        val movie = DownloadedEpisode(9, 9, null, null, null, null, null)
        val request = DownloadRequest(title, movie, DownloadSource("Test", "data"))
        store.saveTitle(title)
        store.saveEpisode(movie, request.source)
        val partial = DownloadFile(video.size.toLong(), root, "Movies", "Another film.mp4", linkHash = "$base/video.mp4?token=old".hashCode(), linkName = "Mirror A")
        partial.folder.mkdirs()
        partial.file.writeBytes(video.copyOf(100_000))
        store.saveFile(9, partial)

        val downloads = Downloads(store, scope, finder, root = { root })
        assertEquals(DownloadStatus.Paused, downloads.state(9)?.status)
        downloads.start(request)
        val done = withTimeout(20_000) { downloads.states.first { it[9]?.status == DownloadStatus.Done || it[9]?.status == DownloadStatus.Failed } }[9]!!
        assertEquals(DownloadStatus.Done, done.status, done.message)
        // Another url is another file, so it started again, and not with the bigger quality
        assertContentEquals(video, partial.file.readBytes())
        assertTrue(segmentHits.isEmpty())

        // Gone from the extension: the user picks another, nothing else is taken
        downloads.delete(9, 9)
        withTimeout(5_000) { while (partial.file.exists()) kotlinx.coroutines.delay(50) }
        store.saveTitle(title)
        store.saveEpisode(movie, request.source)
        // Deleting took the folder it left empty with it
        partial.folder.mkdirs()
        partial.file.writeBytes(video.copyOf(100_000))
        store.saveFile(9, partial)
        mirror = false
        val again = Downloads(store, scope, finder, root = { root })
        again.start(request)
        val gone = withTimeout(10_000) { again.states.first { it[9]?.status == DownloadStatus.Failed } }[9]!!
        assertTrue(gone.pickAgain)
        assertTrue(segmentHits.isEmpty())
        scope.cancel()
    }
}
