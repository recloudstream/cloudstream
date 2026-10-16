package com.lagradost.cloudstream4.library

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream4.android.DesktopAndroid
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataStoreWatchStoreTest {
    private val dir = Files.createTempDirectory("cs-watch")

    init {
        DesktopAndroid.init(dir)
    }

    @AfterTest
    fun cleanup() {
        dir.toFile().deleteRecursively()
    }

    private val header = TitleHeader(42, "Catalog", "https://example.invalid/series", "A series", TvType.TvSeries, "https://example.invalid/p.jpg", year = 2024)

    @Test
    fun savesInTheAndroidAppsKeysAndReadsBack() {
        val store = DataStoreWatchStore(clock = { 1_000 })
        store.setBookmark(header, WatchType.PLANTOWATCH)
        store.setPosition(4243, 60_000, 1_200_000)
        store.setResume(ResumeEntry(header, 4243, 1, 1, 2_000))

        // A new store reads what the last one wrote, as after a restart
        val again = DataStoreWatchStore()
        assertEquals(WatchType.PLANTOWATCH, again.bookmark(42)!!.status)
        assertEquals(header, again.bookmark(42)!!.header)
        assertEquals(PlaybackPosition(60_000, 1_200_000), again.position(4243))
        // Continue watching keeps only what the Android app's header cache has
        assertEquals(listOf(ResumeEntry(header.copy(year = null), 4243, 1, 1, 2_000)), again.resumeEntries())

        // Writes go to the file in the background, wait for them
        val file = dir.resolve("shared_prefs/rebuild_preference.json")
        val keys = listOf("0/result_watch_state/42", "0/result_watch_state_data/42", "0/video_pos_dur/4243", "0/result_resume_watching_2/42", "download_header_cache/42")
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && !(Files.exists(file) && keys.all { it in file.readText() })) Thread.sleep(50)
        keys.forEach { assertTrue(it in file.readText(), "$it is saved under the Android app's key") }

        // The source last watched, desktop's own key
        assertNull(again.lastSource(42))
        again.setLastSource(42, SavedSource("Vidplay", 1080))
        assertEquals(SavedSource("Vidplay", 1080), DataStoreWatchStore().lastSource(42))

        again.setBookmark(header, WatchType.NONE)
        again.removeResume(42)
        assertNull(again.bookmark(42))
        assertTrue(again.resumeEntries().isEmpty())
    }
}
