package com.lagradost.cloudstream3.ui.home

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TorrentSearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCacheTest {
    private val mapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @Test
    fun testMovieSearchResponseAllFields() {
        @Suppress("DEPRECATION_ERROR")
        val movie = MovieSearchResponse(
            name = "Interstellar & Space: The Odyssey (2014) [4K HDR] \"Special\"",
            url = "https://provider.com/watch/interstellar?source=main&lang=en#play",
            apiName = "MegaMovieProvider",
            type = TvType.Movie,
            posterUrl = "https://cdn.provider.com/posters/interstellar.jpg",
            year = 2014,
            id = 998877,
            quality = SearchQuality.FourK,
            score = Score.from10(8.9),
            posterHeaders = mapOf(
                "User-Agent" to "Cloudstream/4.0",
                "Referer" to "https://provider.com/",
                "Authorization" to "Bearer test_token_123"
            )
        )

        val cached = HomeCache.CachedSearchResponse.fromSearchResponse(movie)
        assertEquals(movie.name, cached.name)
        assertEquals(movie.url, cached.url)
        assertEquals("MegaMovieProvider", cached.apiName)
        assertEquals(TvType.Movie, cached.type)
        assertEquals(movie.posterUrl, cached.posterUrl)
        assertEquals(2014, cached.year)
        assertEquals(998877, cached.id)
        assertEquals(SearchQuality.FourK, cached.quality)
        assertEquals(8.9, cached.scoreDouble ?: 0.0, 0.05)
        assertEquals("https://provider.com/", cached.posterHeaders?.get("Referer"))

        val restored = cached.toSearchResponse()
        assertTrue(restored is MovieSearchResponse)
        val restoredMovie = restored as MovieSearchResponse
        assertEquals(movie.name, restoredMovie.name)
        assertEquals(movie.url, restoredMovie.url)
        assertEquals(2014, restoredMovie.year)
        assertEquals(SearchQuality.FourK, restoredMovie.quality)
        assertEquals(8.9, restoredMovie.score?.toDouble(10) ?: 0.0, 0.05)
        assertEquals("Bearer test_token_123", restoredMovie.posterHeaders?.get("Authorization"))
    }

    @Test
    fun testTvSeriesAndAsianDramaTypes() {
        @Suppress("DEPRECATION_ERROR")
        val tvSeries = TvSeriesSearchResponse(
            name = "Breaking Bad",
            url = "https://provider.com/series/breaking-bad",
            apiName = "TvProvider",
            type = TvType.TvSeries,
            posterUrl = "https://provider.com/bb.jpg",
            year = 2008,
            episodes = 62,
            id = 101,
            quality = SearchQuality.HD,
            score = Score.from10(9.5)
        )

        val cachedTv = HomeCache.CachedSearchResponse.fromSearchResponse(tvSeries)
        val restoredTv = cachedTv.toSearchResponse()
        assertTrue(restoredTv is TvSeriesSearchResponse)
        assertEquals(62, (restoredTv as TvSeriesSearchResponse).episodes)

        @Suppress("DEPRECATION_ERROR")
        val drama = TvSeriesSearchResponse(
            name = "Crash Landing on You",
            url = "https://dramaprovider.com/cloy",
            apiName = "DramaProvider",
            type = TvType.AsianDrama,
            posterUrl = "https://dramaprovider.com/cloy.jpg",
            year = 2019,
            episodes = 16,
            score = Score.from10(9.1)
        )

        val cachedDrama = HomeCache.CachedSearchResponse.fromSearchResponse(drama)
        assertEquals(TvType.AsianDrama, cachedDrama.type)
        val restoredDrama = cachedDrama.toSearchResponse()
        assertTrue(restoredDrama is TvSeriesSearchResponse)
        assertEquals(16, (restoredDrama as TvSeriesSearchResponse).episodes)
        assertEquals(TvType.AsianDrama, restoredDrama.type)
    }

    @Test
    fun testAnimeWithAllDubStatusVariations() {
        @Suppress("DEPRECATION_ERROR")
        val subAndDubAnime = AnimeSearchResponse(
            name = "Demon Slayer",
            url = "https://anime.com/demon-slayer",
            apiName = "AnimeProvider",
            type = TvType.Anime,
            posterUrl = "https://anime.com/ds.jpg",
            year = 2019,
            dubStatus = mutableSetOf(DubStatus.Subbed, DubStatus.Dubbed),
            otherName = "Kimetsu no Yaiba",
            episodes = mutableMapOf(DubStatus.Subbed to 26, DubStatus.Dubbed to 26),
            id = 555,
            quality = SearchQuality.HD,
            score = Score.from10(8.7)
        )

        val cached = HomeCache.CachedSearchResponse.fromSearchResponse(subAndDubAnime)
        val restored = cached.toSearchResponse() as AnimeSearchResponse
        assertEquals("Kimetsu no Yaiba", restored.otherName)
        assertEquals(2, restored.dubStatus?.size)
        assertTrue(restored.dubStatus?.contains(DubStatus.Subbed) == true)
        assertTrue(restored.dubStatus?.contains(DubStatus.Dubbed) == true)
        assertEquals(26, restored.episodes[DubStatus.Subbed])
        assertEquals(26, restored.episodes[DubStatus.Dubbed])

        @Suppress("DEPRECATION_ERROR")
        val ovaAnime = AnimeSearchResponse(
            name = "Fate/Stay Night OVA",
            url = "https://anime.com/fate-ova",
            apiName = "AnimeProvider",
            type = TvType.OVA,
            posterUrl = "https://anime.com/fate.jpg",
            dubStatus = mutableSetOf(DubStatus.Subbed),
            episodes = mutableMapOf(DubStatus.Subbed to 1)
        )

        val cachedOva = HomeCache.CachedSearchResponse.fromSearchResponse(ovaAnime)
        val restoredOva = cachedOva.toSearchResponse() as AnimeSearchResponse
        assertEquals(TvType.OVA, restoredOva.type)
        assertEquals(1, restoredOva.episodes[DubStatus.Subbed])

        @Suppress("DEPRECATION_ERROR")
        val movieAnime = AnimeSearchResponse(
            name = "Your Name",
            url = "https://anime.com/your-name",
            apiName = "AnimeProvider",
            type = TvType.AnimeMovie,
            posterUrl = "https://anime.com/your-name.jpg",
            year = 2016
        )
        val cachedAnimeMovie = HomeCache.CachedSearchResponse.fromSearchResponse(movieAnime)
        val restoredAnimeMovie = cachedAnimeMovie.toSearchResponse() as AnimeSearchResponse
        assertEquals(TvType.AnimeMovie, restoredAnimeMovie.type)
        assertEquals(2016, restoredAnimeMovie.year)
    }

    @Test
    fun testLiveStreamWithAndWithoutLanguage() {
        @Suppress("DEPRECATION_ERROR")
        val liveWithLang = LiveSearchResponse(
            name = "NHK World Japan",
            url = "https://live.tv/nhk",
            apiName = "IptvProvider",
            type = TvType.Live,
            posterUrl = "https://live.tv/nhk.png",
            lang = "ja",
            id = 1
        )
        val cachedWithLang = HomeCache.CachedSearchResponse.fromSearchResponse(liveWithLang)
        val restoredWithLang = cachedWithLang.toSearchResponse() as LiveSearchResponse
        assertEquals(TvType.Live, restoredWithLang.type)
        assertEquals("ja", restoredWithLang.lang)

        @Suppress("DEPRECATION_ERROR")
        val liveNoLang = LiveSearchResponse(
            name = "Global Sports HD",
            url = "https://live.tv/sports",
            apiName = "IptvProvider",
            type = TvType.Live,
            posterUrl = null,
            lang = null
        )
        val cachedNoLang = HomeCache.CachedSearchResponse.fromSearchResponse(liveNoLang)
        val restoredNoLang = cachedNoLang.toSearchResponse() as LiveSearchResponse
        assertEquals(TvType.Live, restoredNoLang.type)
        assertNull(restoredNoLang.lang)
        assertNull(restoredNoLang.posterUrl)
    }

    @Test
    fun testTorrentAndQualityEnums() {
        val qualities = listOf(
            SearchQuality.FourK,
            SearchQuality.UHD,
            SearchQuality.BlueRay,
            SearchQuality.HD,
            SearchQuality.HQ,
            SearchQuality.SD,
            SearchQuality.Cam,
            SearchQuality.CamRip,
            SearchQuality.Telecine,
            SearchQuality.Telesync,
            SearchQuality.DVD,
            SearchQuality.WorkPrint,
            SearchQuality.WebRip,
            SearchQuality.HDR,
            SearchQuality.SDR
        )

        for (q in qualities) {
            @Suppress("DEPRECATION_ERROR")
            val torrent = TorrentSearchResponse(
                name = "Sample Torrent $q",
                url = "magnet:?xt=urn:btih:sample_$q",
                apiName = "TorrentProvider",
                type = TvType.Torrent,
                posterUrl = "https://torrent.org/poster.jpg",
                quality = q
            )
            val cached = HomeCache.CachedSearchResponse.fromSearchResponse(torrent)
            assertEquals(q, cached.quality)
            val restored = cached.toSearchResponse() as TorrentSearchResponse
            assertEquals(q, restored.quality)
        }
    }

    @Test
    fun testRatingScoreBoundaryValuesAndFormats() {
        val testScores = listOf(
            0.0,
            1.5,
            5.0,
            7.65,
            9.99,
            10.0
        )

        for (scoreVal in testScores) {
            @Suppress("DEPRECATION_ERROR")
            val item = MovieSearchResponse(
                name = "Score Test $scoreVal",
                url = "https://test.com/$scoreVal",
                apiName = "RatingProvider",
                type = TvType.Movie,
                score = Score.from10(scoreVal)
            )
            val cached = HomeCache.CachedSearchResponse.fromSearchResponse(item)
            assertEquals(scoreVal, cached.scoreDouble ?: -1.0, 0.05)

            val restored = cached.toSearchResponse()
            val restoredScore = restored.score?.toDouble(10) ?: -1.0
            assertEquals(scoreVal, restoredScore, 0.05)
        }

        // Test with null score (providers that don't supply score)
        @Suppress("DEPRECATION_ERROR")
        val noScoreItem = MovieSearchResponse(
            name = "No Score Movie",
            url = "https://test.com/no-score",
            apiName = "RatingProvider",
            type = TvType.Movie,
            score = null
        )
        val cachedNoScore = HomeCache.CachedSearchResponse.fromSearchResponse(noScoreItem)
        assertNull(cachedNoScore.scoreDouble)
        val restoredNoScore = cachedNoScore.toSearchResponse()
        assertNull(restoredNoScore.score)
    }

    @Test
    fun testOtherTvTypesAndNullTypeFallback() {
        val otherTypes = listOf(
            TvType.Cartoon,
            TvType.Documentary,
            TvType.Music,
            TvType.AudioBook,
            TvType.Podcast,
            TvType.CustomMedia,
            TvType.Others,
            TvType.NSFW
        )

        for (t in otherTypes) {
            @Suppress("DEPRECATION_ERROR")
            val item = MovieSearchResponse(
                name = "Test $t",
                url = "https://test.com/$t",
                apiName = "OtherProvider",
                type = t
            )
            val cached = HomeCache.CachedSearchResponse.fromSearchResponse(item)
            assertEquals(t, cached.type)
            val restored = cached.toSearchResponse()
            assertEquals(t, restored.type)
        }

        // When TvType is null, defaults safely to MovieSearchResponse
        val nullTypeCached = HomeCache.CachedSearchResponse(
            name = "Null Type Item",
            url = "https://test.com/null-type",
            apiName = "DefaultProvider",
            type = null
        )
        val restored = nullTypeCached.toSearchResponse()
        assertTrue(restored is MovieSearchResponse)
        assertEquals(TvType.Movie, restored.type)
    }

    @Test
    fun testMultiSectionHomePageWithHorizontalAndVerticalLists() {
        @Suppress("DEPRECATION_ERROR")
        val bannerMovie1 = MovieSearchResponse(
            name = "Hero Movie 1",
            url = "https://provider.com/hero1",
            apiName = "MultiProvider",
            type = TvType.Movie,
            posterUrl = "https://provider.com/hero1_landscape.jpg",
            score = Score.from10(8.5)
        )
        @Suppress("DEPRECATION_ERROR")
        val bannerMovie2 = MovieSearchResponse(
            name = "Hero Movie 2",
            url = "https://provider.com/hero2",
            apiName = "MultiProvider",
            type = TvType.Movie,
            posterUrl = "https://provider.com/hero2_landscape.jpg",
            score = Score.from10(9.0)
        )

        @Suppress("DEPRECATION_ERROR")
        val rowItem1 = TvSeriesSearchResponse(
            name = "Popular Series 1",
            url = "https://provider.com/series1",
            apiName = "MultiProvider",
            type = TvType.TvSeries,
            posterUrl = "https://provider.com/series1.jpg",
            episodes = 24,
            year = 2023
        )

        @Suppress("DEPRECATION_ERROR")
        val bannerRow = HomePageList(
            name = "Spotlight Banner",
            list = listOf(bannerMovie1, bannerMovie2),
            isHorizontalImages = true
        )

        @Suppress("DEPRECATION_ERROR")
        val seriesRow = HomePageList(
            name = "Binge-Worthy Series",
            list = listOf(rowItem1),
            isHorizontalImages = false
        )

        @Suppress("DEPRECATION_ERROR")
        val emptyRow = HomePageList(
            name = "Coming Soon",
            list = emptyList(),
            isHorizontalImages = false
        )

        @Suppress("DEPRECATION_ERROR")
        val page1 = HomePageResponse(
            items = listOf(bannerRow, seriesRow, emptyRow),
            hasNext = true
        )

        @Suppress("DEPRECATION_ERROR")
        val page2 = HomePageResponse(
            items = emptyList(),
            hasNext = false
        )

        val cachedHomeData = HomeCache.CachedHomeData(
            unixTime = 1750000000L,
            responses = listOf(
                HomeCache.CachedHomePageResponse.fromHomePageResponse(page1),
                HomeCache.CachedHomePageResponse.fromHomePageResponse(page2)
            )
        )

        // Serialize to JSON
        val json = mapper.writeValueAsString(cachedHomeData)
        assertNotNull(json)

        // Deserialize back
        val deserialized = mapper.readValue(json, HomeCache.CachedHomeData::class.java)
        assertEquals(1750000000L, deserialized.unixTime)
        assertEquals(2, deserialized.responses.size)

        val restoredPage1 = deserialized.responses[0].toHomePageResponse()
        assertTrue(restoredPage1.hasNext)
        assertEquals(3, restoredPage1.items.size)

        // Validate banner row
        val restoredBanner = restoredPage1.items[0]
        assertEquals("Spotlight Banner", restoredBanner.name)
        assertTrue(restoredBanner.isHorizontalImages)
        assertEquals(2, restoredBanner.list.size)
        assertEquals("Hero Movie 1", restoredBanner.list[0].name)
        assertEquals(8.5, restoredBanner.list[0].score?.toDouble(10) ?: 0.0, 0.05)

        // Validate series row
        val restoredSeries = restoredPage1.items[1]
        assertEquals("Binge-Worthy Series", restoredSeries.name)
        assertFalse(restoredSeries.isHorizontalImages)
        assertEquals(1, restoredSeries.list.size)
        val seriesItem = restoredSeries.list[0] as TvSeriesSearchResponse
        assertEquals("Popular Series 1", seriesItem.name)
        assertEquals(24, seriesItem.episodes)
        assertEquals(2023, seriesItem.year)

        // Validate empty row
        val restoredEmpty = restoredPage1.items[2]
        assertEquals("Coming Soon", restoredEmpty.name)
        assertTrue(restoredEmpty.list.isEmpty())

        // Validate page 2
        val restoredPage2 = deserialized.responses[1].toHomePageResponse()
        assertFalse(restoredPage2.hasNext)
        assertTrue(restoredPage2.items.isEmpty())
    }

    @Test
    fun testJsonFaultToleranceWithFutureAndArbitraryFields() {
        val complexUnknownJson = """
            {
                "unixTime": 1712345678,
                "schemaVersion": "2.5.0",
                "customConfig": {
                    "enableFeatureX": true,
                    "retryCount": 5
                },
                "responses": [
                    {
                        "hasNext": true,
                        "extraSectionId": "section_99",
                        "items": [
                            {
                                "name": "StreamPlay Top 10",
                                "isHorizontalImages": false,
                                "badge": "NEW",
                                "tags": ["Action", "Sci-Fi"],
                                "list": [
                                    {
                                        "name": "StreamPlay Exclusive 1",
                                        "url": "https://streamplay.to/movie/100",
                                        "apiName": "StreamPlay",
                                        "type": "Movie",
                                        "posterUrl": "https://streamplay.to/posters/100.webp",
                                        "posterHeaders": {
                                            "X-Auth-Token": "secret_key",
                                            "X-Custom-Header": "custom_val"
                                        },
                                        "id": 10001,
                                        "quality": "FourK",
                                        "scoreDouble": 9.35,
                                        "year": 2024,
                                        "randomPluginMetadata": "ignored_value",
                                        "extraNestedObject": { "key": 42 }
                                    },
                                    {
                                        "name": "StreamPlay Drama Series",
                                        "url": "https://streamplay.to/series/200",
                                        "apiName": "StreamPlay",
                                        "type": "AsianDrama",
                                        "posterUrl": "https://streamplay.to/posters/200.webp",
                                        "tvEpisodes": 16,
                                        "year": 2024,
                                        "scoreDouble": 8.8
                                    },
                                    {
                                        "name": "StreamPlay Anime",
                                        "url": "https://streamplay.to/anime/300",
                                        "apiName": "StreamPlay",
                                        "type": "Anime",
                                        "otherName": "Kimetsu 2024",
                                        "dubStatus": ["Subbed", "Dubbed"],
                                        "animeEpisodes": { "Subbed": 12, "Dubbed": 12 },
                                        "scoreDouble": 9.0
                                    }
                                ]
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val deserialized = mapper.readValue(complexUnknownJson, HomeCache.CachedHomeData::class.java)
        assertEquals(1712345678L, deserialized.unixTime)
        assertEquals(1, deserialized.responses.size)

        val pageResponse = deserialized.responses[0].toHomePageResponse()
        assertTrue(pageResponse.hasNext)
        assertEquals(1, pageResponse.items.size)

        val items = pageResponse.items[0].list
        assertEquals(3, items.size)

        // Item 1: Movie
        assertTrue(items[0] is MovieSearchResponse)
        val movie = items[0] as MovieSearchResponse
        assertEquals("StreamPlay Exclusive 1", movie.name)
        assertEquals(SearchQuality.FourK, movie.quality)
        assertEquals(9.35, movie.score?.toDouble(10) ?: 0.0, 0.05)
        assertEquals("secret_key", movie.posterHeaders?.get("X-Auth-Token"))

        // Item 2: AsianDrama
        assertTrue(items[1] is TvSeriesSearchResponse)
        val drama = items[1] as TvSeriesSearchResponse
        assertEquals("StreamPlay Drama Series", drama.name)
        assertEquals(16, drama.episodes)
        assertEquals(TvType.AsianDrama, drama.type)

        // Item 3: Anime
        assertTrue(items[2] is AnimeSearchResponse)
        val anime = items[2] as AnimeSearchResponse
        assertEquals("StreamPlay Anime", anime.name)
        assertEquals("Kimetsu 2024", anime.otherName)
        assertEquals(12, anime.episodes[DubStatus.Subbed])
    }
}
