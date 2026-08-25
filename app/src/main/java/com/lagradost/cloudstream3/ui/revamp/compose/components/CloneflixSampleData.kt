package com.lagradost.cloudstream3.ui.revamp.compose.components

object CloneflixSampleData {
    const val SAMPLE_TITLE_HOUSE_OF_NINJAS = "House of Ninjas"
    const val SAMPLE_MATCH_SCORE = "98% Match"
    const val SAMPLE_MATCH_NEW = "New"
    const val SAMPLE_MATURITY_RATING = "TV-MA"
    const val SAMPLE_DURATION_SEASONS = "3 Seasons"
    const val SAMPLE_QUALITY_HD = "HD"
    const val SAMPLE_QUALITY_4K = "4K Ultra HD"

    val SAMPLE_GENRES = listOf("Violent", "Dark", "Action")
    const val SAMPLE_SYNOPSIS = "Years after retiring from their formidable ninja lives, a dysfunctional family must return to shadowy missions to counteract a string of looming threats."

    val SAMPLE_MOVIE_ITEMS = listOf(
        CloneflixMovieCardItem(
            title = "House of Ninjas",
            badge = CloneflixBadgeType.RECENTLY_ADDED,
            showLogo = true,
            progress = 0.65f,
            runtime = "2h 18m"
        ),
        CloneflixMovieCardItem(
            title = "Stranger Things",
            badge = CloneflixBadgeType.TOP_10,
            showLogo = true,
            top10Rank = 1,
            runtime = "55m"
        ),
        CloneflixMovieCardItem(
            title = "The Witcher",
            badge = CloneflixBadgeType.NEW_SEASON,
            showLogo = true,
            progress = 0.20f,
            runtime = "1h 02m"
        ),
        CloneflixMovieCardItem(
            title = "Cyberpunk: Edgerunners",
            showLogo = true,
            progress = 0.85f,
            runtime = "24m"
        ),
        CloneflixMovieCardItem(
            title = "Squid Game",
            badge = CloneflixBadgeType.TOP_10,
            showLogo = true,
            top10Rank = 2,
            runtime = "1h 10m"
        )
    )
}
