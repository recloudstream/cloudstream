package com.lagradost.cloudstream3.tv.model

/**
 * Explicit demo catalog for Phase 3 mock fallback / Continue Watching.
 * Never presented as a silent API success.
 */
object TvMockCatalog {
    private const val P = "https://picsum.photos/seed"

    val hero: TvMediaItem = TvMediaItem(
        id = "hero-nebula",
        title = "Nebula Drift",
        subtitle = "Original · Sci-Fi",
        posterUrl = "$P/nebula-poster/400/600",
        backdropUrl = "$P/nebula-backdrop/1280/720",
        year = 2026,
        rating = "8.4",
        runtime = "2h 14m",
        genres = listOf("Sci-Fi", "Adventure", "Drama"),
        synopsis = "A salvage crew chasing a dying star discovers a signal that rewrites " +
            "everything they know about home. Explicit Compose TV demo hero.",
        isMock = true,
    )

    val continueWatching: TvContentRail = TvContentRail(
        id = TvRailIds.CONTINUE,
        title = "Continue Watching",
        isMock = true,
        items = listOf(
            item("cw1", "Harbor Lights", "S2 E4 · 42m left", "harbor", progress = 0.62f, year = 2025),
            item("cw2", "Glass Kingdom", "1h 05m left", "glass", progress = 0.35f, year = 2024),
            item("cw3", "Midnight Courier", "S1 E7 · 18m left", "courier", progress = 0.81f, year = 2026),
            item("cw4", "Iron Orchard", "E3 · 51m left", "orchard", progress = 0.22f, year = 2023),
            item("cw5", "Silent Cascade", "S3 E1 · 1h left", "cascade", progress = 0.08f, year = 2025),
        ),
    )

    val trending: TvContentRail = TvContentRail(
        id = TvRailIds.TRENDING,
        title = "Trending",
        isMock = true,
        items = listOf(
            item("tr1", "Crimson Atlas", "Thriller", "crimson", year = 2026, rating = "8.1"),
            item("tr2", "Polar Echo", "Mystery", "polar", year = 2025, rating = "7.9"),
            item("tr3", "Velvet Circuit", "Action", "velvet", year = 2026, rating = "8.6"),
            item("tr4", "Ashen Choir", "Horror", "ashen", year = 2024, rating = "7.4"),
            item("tr5", "Lumen Protocol", "Sci-Fi", "lumen", year = 2026, rating = "8.9"),
            item("tr6", "Desert Frequency", "Drama", "desert", year = 2025, rating = "7.7"),
        ),
    )

    val movies: TvContentRail = TvContentRail(
        id = TvRailIds.MOVIES,
        title = "Movies",
        isMock = true,
        items = listOf(
            item("mv1", "Last Ember", "Feature", "ember", year = 2022, runtime = "1h 58m"),
            item("mv2", "Paper Storm", "Feature", "paper", year = 2023, runtime = "2h 05m"),
            item("mv3", "Copper Sky", "Feature", "copper", year = 2021, runtime = "1h 44m"),
            item("mv4", "Night Archive", "Feature", "archive", year = 2024, runtime = "2h 18m"),
            item("mv5", "River of Static", "Feature", "river", year = 2025, runtime = "1h 51m"),
        ),
    )

    val anime: TvContentRail = TvContentRail(
        id = TvRailIds.ANIME,
        title = "Anime",
        isMock = true,
        items = listOf(
            item("an1", "Starforged Academy", "TV · 24 ep", "starforge", year = 2026),
            item("an2", "Kitsune Circuit", "TV · 12 ep", "kitsune", year = 2025),
            item("an3", "Orbital Sakura", "Movie", "sakura", year = 2024),
            item("an4", "Blade of Mist", "TV · 13 ep", "blade", year = 2023),
            item("an5", "Chrono Harbor", "OVA", "chrono", year = 2026),
            item("an6", "Neon Shrine", "TV · 26 ep", "neon", year = 2025),
        ),
    )

    val fullFallback: TvHomeCatalog = TvHomeCatalog(
        hero = hero,
        rails = listOf(continueWatching, trending, movies, anime),
        providerName = null,
        usingMockFallback = true,
    )

    private fun item(
        id: String,
        title: String,
        subtitle: String,
        seed: String,
        year: Int? = null,
        rating: String? = null,
        runtime: String? = null,
        progress: Float? = null,
    ) = TvMediaItem(
        id = id,
        title = title,
        subtitle = subtitle,
        posterUrl = "$P/$seed-poster/400/600",
        backdropUrl = "$P/$seed-backdrop/800/450",
        year = year,
        rating = rating,
        runtime = runtime,
        progressFraction = progress,
        isMock = true,
    )
}
