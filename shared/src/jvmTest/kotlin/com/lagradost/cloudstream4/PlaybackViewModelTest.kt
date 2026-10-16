package com.lagradost.cloudstream4

import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.addSeasonNames
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTorrentLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream4.detail.DetailAction
import com.lagradost.cloudstream4.detail.DetailStatus
import com.lagradost.cloudstream4.detail.DetailViewModel
import com.lagradost.cloudstream4.detail.PlayRequest
import com.lagradost.cloudstream4.library.InMemoryWatchStore
import com.lagradost.cloudstream4.library.SavedSource
import com.lagradost.cloudstream4.library.WatchType
import com.lagradost.cloudstream4.library.saveProgress
import com.lagradost.cloudstream4.player.HumanChecks
import com.lagradost.cloudstream4.player.LinksAction
import com.lagradost.cloudstream4.player.LinksViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Serves a movie, a series, an anime and a torrent, and links for each, from memory */
private class FakeCatalog : MainAPI() {
    override var name = "Catalog"
    override var mainUrl = "https://example.invalid"

    /** loadLinks waits for this before sending the links listed after [slowAfter] */
    var gate: CompletableDeferred<Unit>? = null
    var slowAfter = Int.MAX_VALUE
    var links: List<Pair<String, Int>> = emptyList()
    var torrents = 0
    /** Runs as links load, like an extractor whose site asks for a check */
    var duringLinks: (suspend () -> Unit)? = null

    private fun ep(data: String, season: Int?, number: Int, title: String? = null) =
        newEpisode(data, initializer = { this.season = season; this.episode = number; this.name = title }, fix = false)

    override suspend fun load(url: String): LoadResponse = when (url.substringAfterLast('/')) {
        "movie" -> newMovieLoadResponse("A movie", url, TvType.Movie, "movie-data") { plot = "Plot"; tags = listOf("Drama", " ") }
        "series" -> newTvSeriesLoadResponse(
            "A series", url, TvType.TvSeries,
            listOf(ep("s2e1", 2, 1), ep("s1e1", 1, 1, "Pilot"), ep("s1e2", 1, 2), ep("extra", null, 1)),
        ) { addSeasonNames(listOf(SeasonData(1, "The beginning"), SeasonData(2, displaySeason = 20))) }

        "anime" -> newAnimeLoadResponse("An anime", url, TvType.Anime) {
            addEpisodes(DubStatus.Subbed, listOf(ep("sub1", null, 1), ep("sub2", null, 2)))
            addEpisodes(DubStatus.Dubbed, listOf(ep("dub1", null, 1)))
        }

        "torrent" -> newTorrentLoadResponse("A torrent", url, magnet = "magnet:?xt=x")
        else -> throw IllegalStateException("not found")
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        duringLinks?.invoke()
        subtitleCallback(newSubtitleFile("English", "https://example.invalid/en.vtt"))
        subtitleCallback(newSubtitleFile("English", "https://example.invalid/en.vtt"))
        repeat(torrents) { callback(newExtractorLink("T", "Torrent", "magnet:?xt=$it", ExtractorLinkType.MAGNET)) }
        links.forEachIndexed { index, (name, quality) ->
            if (index == slowAfter) gate?.await()
            callback(newExtractorLink(name, name, "https://example.invalid/$name.m3u8", ExtractorLinkType.M3U8) { this.quality = quality })
        }
        return links.isNotEmpty()
    }
}

/** Sites that ask for a check, and the ones the user passed */
private class FakeChecks : HumanChecks {
    val sites = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val passed = mutableListOf<String>()
    override val asked = sites

    override suspend fun pass(host: String): Boolean {
        passed += host
        return true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModelTest {
    private val api = FakeCatalog()

    // Not a test dispatcher: the links view model waits in real time before picking a link
    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun <T> await(flow: StateFlow<T>, predicate: (T) -> Boolean): T =
        runBlocking { withTimeout(5_000) { flow.first(predicate) } }

    @Test
    fun movieGetsAPlayRequestAndBlankTagsAreDropped() {
        val vm = DetailViewModel(api, "https://example.invalid/movie")
        val state = await(vm.state) { it.status == DetailStatus.Done }
        assertEquals("movie-data", state.movieData)
        assertEquals(listOf("Drama"), state.response!!.tags)
        assertTrue(state.seasons.isEmpty())
        assertEquals(PlayRequest("Catalog", "A movie", null, "movie-data"), vm.playRequest()!!.copy(tracking = null))
    }

    @Test
    fun seriesEpisodesAreGroupedBySeasonWithTheirNames() {
        val vm = DetailViewModel(api, "https://example.invalid/series")
        val state = await(vm.state) { it.status == DetailStatus.Done }
        assertEquals(listOf("The beginning", "Season 20", "Other"), state.seasons.map { it.label })
        assertEquals(listOf("s1e1", "s1e2"), state.seasons[0].episodes.map { it.data })

        val pilot = state.seasons[0].episodes[0]
        assertEquals(PlayRequest("Catalog", "A series", "S1 E1 · Pilot", "s1e1"), vm.playRequest(pilot)!!.copy(tracking = null))

        vm.onAction(DetailAction.SelectSeason(1))
        assertEquals(1, vm.state.value.selectedSeason)
        vm.onAction(DetailAction.SelectSeason(99))
        assertEquals(2, vm.state.value.selectedSeason)
    }

    @Test
    fun animeEpisodesCanBeSwitchedBetweenSubbedAndDubbed() {
        val vm = DetailViewModel(api, "https://example.invalid/anime")
        val state = await(vm.state) { it.status == DetailStatus.Done }
        assertEquals(listOf(DubStatus.Subbed, DubStatus.Dubbed), state.dubs)
        assertEquals(listOf("sub1", "sub2"), state.seasons.single().episodes.map { it.data })
        assertEquals("Episodes", state.seasons.single().label)

        vm.onAction(DetailAction.SelectDub(DubStatus.Dubbed))
        assertEquals(listOf("dub1"), vm.state.value.seasons.single().episodes.map { it.data })
        assertEquals("E1", DetailViewModel.episodeLabel(vm.state.value.seasons.single().episodes.single()))
    }

    @Test
    fun torrentsAreMarkedUnsupportedAndFailuresCanBeRetried() {
        val torrent = await(DetailViewModel(api, "https://example.invalid/torrent").state) { it.status == DetailStatus.Done }
        assertTrue(torrent.unsupported != null)

        val missing = DetailViewModel(api, "https://example.invalid/missing")
        assertIs<DetailStatus.Failed>(await(missing.state) { it.status is DetailStatus.Failed }.status)
        missing.onAction(DetailAction.Retry)
        assertIs<DetailStatus.Failed>(await(missing.state) { it.status is DetailStatus.Failed }.status)
    }

    private fun links(vararg links: Pair<String, Int>, autoPickMs: Long = 50): LinksViewModel {
        api.links = links.toList()
        return LinksViewModel(api, PlayRequest("Catalog", "A movie", null, "movie-data"), autoPickMs.milliseconds)
    }

    @Test
    fun theBestLinkIsPickedAndTorrentsAreSkipped() {
        api.torrents = 2
        val vm = links("low" to Qualities.P360.value, "unknown" to Qualities.Unknown.value, "hd" to Qualities.P1080.value, "sd" to Qualities.P720.value)
        val state = await(vm.state) { !it.loading }

        assertEquals(listOf("hd", "sd", "unknown", "low"), state.links.map { it.name })
        assertEquals("hd", state.selectedLink?.name)
        assertEquals(2, state.skipped)
        assertEquals(1, state.subtitles.size)
        assertEquals("hd · 1080p", LinksViewModel.label(state.links[0]))
    }

    @Test
    fun whereTorrentsCanPlayTheyComeAfterEveryOtherLink() {
        api.torrents = 2
        api.links = listOf("low" to Qualities.P360.value)
        val vm = LinksViewModel(api, PlayRequest("Catalog", "A movie", null, "movie-data"), 50.milliseconds, torrents = true)
        val state = await(vm.state) { !it.loading }

        assertEquals(listOf("low", "Torrent", "Torrent"), state.links.map { it.name })
        assertEquals("low", state.selectedLink?.name)
        assertEquals(0, state.skipped)

        val title = await(DetailViewModel(api, "https://example.invalid/torrent", torrents = true).state) { it.status == DetailStatus.Done }
        assertNull(title.unsupported)
        assertEquals("magnet:?xt=x", title.movieData)
        // The title's magnet plays even when the extension gives no link for it
        api.torrents = 0
        api.links = emptyList()
        val magnet = LinksViewModel(api, PlayRequest("Catalog", "A torrent", null, "magnet:?xt=x"), 50.milliseconds, torrents = true)
        assertEquals("magnet:?xt=x", await(magnet.state) { !it.loading }.selectedLink?.url)
    }

    @Test
    fun aFailedLinkMovesOnToTheNextUntilNoneAreLeft() {
        val vm = links("a" to 1080, "b" to 720)
        await(vm.state) { !it.loading }

        vm.onAction(LinksAction.Failed("https://example.invalid/a.m3u8", "403"))
        assertEquals("b", vm.state.value.selectedLink?.name)
        // A late failure report for a link no longer playing changes nothing
        vm.onAction(LinksAction.Failed("https://example.invalid/a.m3u8", "403"))
        assertEquals("b", vm.state.value.selectedLink?.name)

        vm.onAction(LinksAction.Failed("https://example.invalid/b.m3u8", "timeout"))
        assertNull(vm.state.value.selected)
        assertTrue(vm.state.value.exhausted)

        vm.onAction(LinksAction.Select("https://example.invalid/a.m3u8"))
        assertEquals("a", vm.state.value.selectedLink?.name)
    }

    private fun preferring(source: SavedSource, vararg links: Pair<String, Int>): LinksViewModel {
        api.links = links.toList()
        // Long enough that only the saved source can be picked before loading ends
        return LinksViewModel(api, PlayRequest("Catalog", "A movie", null, "movie-data", preferredSource = source), 10.seconds)
    }

    @Test
    fun theSourceLastWatchedPlaysAsSoonAsItComes() {
        api.gate = CompletableDeferred()
        api.slowAfter = 2
        val vm = preferring(SavedSource("sd", 720), "hd" to 1080, "sd" to 720, "late" to 2160)

        // Picked while links are still coming, over a better one
        val early = await(vm.state) { it.selected != null }
        assertTrue(early.loading)
        assertEquals("sd", early.selectedLink?.name)
        api.gate!!.complete(Unit)
        assertEquals("sd", await(vm.state) { !it.loading }.selectedLink?.name)

        // When it fails, the best of the rest plays
        vm.onAction(LinksAction.Failed("https://example.invalid/sd.m3u8", "403"))
        assertEquals("late", vm.state.value.selectedLink?.name)
    }

    @Test
    fun theSourceLastWatchedComesFirstInAnotherQuality() {
        val vm = preferring(SavedSource("sd", 480), "hd" to 1080, "sd" to 720)
        assertEquals("sd", await(vm.state) { !it.loading }.selectedLink?.name)
    }

    @Test
    fun aSlowExtractorDoesNotHoldBackPlayback() {
        api.gate = CompletableDeferred()
        api.slowAfter = 1
        val vm = links("fast" to 480, "slow" to 1080)

        // The fast link plays while the slow extractor is still working
        val early = await(vm.state) { it.selected != null }
        assertTrue(early.loading)
        assertEquals("fast", early.selectedLink?.name)

        api.gate!!.complete(Unit)
        val done = await(vm.state) { !it.loading }
        assertEquals(listOf("slow", "fast"), done.links.map { it.name })
        // The better link arriving later does not interrupt what is playing
        assertEquals("fast", done.selectedLink?.name)
    }

    @Test
    fun sitesThatWantACheckComeLastAndAreOnlyPassedWhenAsked() {
        val checks = FakeChecks()
        api.links = listOf("a" to 1080)
        api.duringLinks = { checks.sites.emit("guarded.example") }
        val vm = LinksViewModel(api, PlayRequest("Catalog", "A movie", null, "movie-data"), 50.milliseconds, checks = checks)

        // The other links play first, and nothing is asked of the user
        val first = await(vm.state) { !it.loading }
        assertEquals("a", first.selectedLink?.name)
        assertEquals(listOf("guarded.example"), first.checkSites)
        assertTrue(checks.passed.isEmpty())
        vm.onAction(LinksAction.Failed("https://example.invalid/a.m3u8", "403"))
        assertTrue(vm.state.value.exhausted)

        // Once nothing else plays, the user passes the check, and the site's links come
        api.duringLinks = null
        api.links = listOf("a" to 1080, "guarded" to 720)
        vm.onAction(LinksAction.PassChecks)
        val after = await(vm.state) { !it.loading && it.links.size == 2 }
        assertEquals(listOf("guarded.example"), checks.passed)
        assertTrue(after.checkSites.isEmpty())
        // The link that failed before is not tried again
        assertEquals("guarded", after.selectedLink?.name)
    }

    @Test
    fun skipPlaysTheBestFoundSoFarAndStopsFindingLinks() {
        api.gate = CompletableDeferred()
        api.slowAfter = 2
        // A long wait before picking, so only the skip plays a link
        val vm = links("sd" to 720, "hd" to 1080, "late" to 2160, autoPickMs = 60_000)
        await(vm.state) { it.links.size == 2 }
        assertNull(vm.state.value.selected)

        vm.onAction(LinksAction.Skip)
        val skipped = vm.state.value
        assertEquals(false, skipped.loading)
        assertEquals("hd", skipped.selectedLink?.name)
        // The extractor still running is left out
        api.gate!!.complete(Unit)
        Thread.sleep(200)
        assertEquals(listOf("hd", "sd"), vm.state.value.links.map { it.name })
    }

    @Test
    fun noLinksMeansExhausted() {
        val vm = links()
        val state = await(vm.state) { !it.loading }
        assertNull(state.selected)
        assertTrue(state.exhausted)
    }

    @Test
    fun episodeIdsAreTheAndroidAppsIds() {
        val vm = DetailViewModel(api, "https://example.invalid/series")
        val state = await(vm.state) { it.status == DetailStatus.Done }
        // The url without the provider's address and slashes, hashed, like ResultViewModel2.getId
        val titleId = "series".hashCode()
        assertEquals(titleId, state.titleId)
        // Series episodes sort by season then number: extra, S1 E1, S1 E2, S2 E1
        val pilot = state.seasons[0].episodes[0]
        assertEquals(titleId + 100_000 + 1 + 1, state.episodeIds[pilot])

        val anime = await(DetailViewModel(api, "https://example.invalid/anime").state) { it.status == DetailStatus.Done }
        val dubbed = anime.episodeIds.entries.single { it.key.data == "dub1" }.value
        assertEquals("anime".hashCode() + 1 + 1_000_000, dubbed)
    }

    @Test
    fun libraryAndResumeFollowWhatWasWatched() {
        val store = InMemoryWatchStore()
        val vm = DetailViewModel(api, "https://example.invalid/series", store)
        val state = await(vm.state) { it.status == DetailStatus.Done }
        val (pilot, second) = state.seasons[0].episodes

        vm.onAction(DetailAction.SetBookmark(WatchType.WATCHING))
        assertEquals(WatchType.WATCHING, vm.state.value.bookmark)
        assertEquals("A series", store.bookmark(state.titleId!!)!!.header.name)

        // Halfway through the pilot: Resume picks it up there
        val tracking = vm.playRequest(pilot)!!.tracking!!
        assertEquals(state.episodeIds[second], tracking.next!!.id)
        store.saveProgress(tracking, positionMs = 600_000, durationMs = 1_200_000)
        await(vm.state) { it.resume?.episode == pilot }
        assertEquals(600_000, vm.state.value.resume!!.positionMs)
        assertEquals(0.5f, vm.state.value.progressOf(pilot)!!.fraction)
        assertEquals(600_000, vm.playRequest(pilot)!!.tracking!!.startPositionMs)
        // Continue watching plays the same, from the source last watched
        store.setLastSource(state.titleId!!, SavedSource("hd", 1080))
        assertEquals(vm.playRequest(pilot), vm.resumeRequest())
        assertEquals(SavedSource("hd", 1080), vm.resumeRequest()!!.preferredSource)

        // Nearly finished: Continue watching moves on to the next episode, which starts at the beginning
        store.saveProgress(tracking, positionMs = 1_150_000, durationMs = 1_200_000)
        await(vm.state) { it.resume?.episode == second }
        assertTrue(vm.state.value.progressOf(pilot)!!.watched)
        assertEquals(0, vm.playRequest(pilot)!!.tracking!!.startPositionMs, "A finished episode starts again from the beginning")

        // Finishing the last episode takes the title out of Continue watching
        val extra = vm.playRequest(state.seasons[2].episodes.single())!!.tracking!!
        assertNull(extra.next)
        store.saveProgress(extra, positionMs = 1_190_000, durationMs = 1_200_000)
        assertNull(store.resume(state.titleId!!))

        vm.onAction(DetailAction.SetBookmark(WatchType.NONE))
        assertNull(store.bookmark(state.titleId!!))
    }

    @Test
    fun shortVideosAreNotRemembered() {
        val store = InMemoryWatchStore()
        val vm = DetailViewModel(api, "https://example.invalid/movie", store)
        await(vm.state) { it.status == DetailStatus.Done }
        store.saveProgress(vm.playRequest()!!.tracking!!, positionMs = 10_000, durationMs = 20_000)
        assertTrue(store.resumeEntries().isEmpty())
    }
}
