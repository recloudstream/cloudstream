package com.lagradost.cloudstream3.ui.result.compose

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.ResumeWatchingStatus
import com.lagradost.cloudstream3.ui.result.compose.components.EpisodeRowItem
import com.lagradost.cloudstream3.ui.result.compose.components.MovieCardItem
import com.lagradost.cloudstream3.ui.result.compose.components.MovieCardType
import com.lagradost.cloudstream3.ui.result.compose.model.MovieRecommendationRow
import com.lagradost.cloudstream3.ui.result.compose.model.MovieTrailerData
import com.lagradost.cloudstream3.ui.result.compose.model.getPlayButtonText
import com.lagradost.cloudstream3.ui.result.compose.model.resolveAiringSchedule
import com.lagradost.cloudstream3.ui.result.compose.sections.AboutSection
import com.lagradost.cloudstream3.ui.result.compose.sections.CastAndCrewSection
import com.lagradost.cloudstream3.ui.result.compose.sections.EpisodesHeaderSection
import com.lagradost.cloudstream3.ui.result.compose.sections.HeroBannerSection
import com.lagradost.cloudstream3.ui.result.compose.sections.MovieInfoSynopsisSection
import com.lagradost.cloudstream3.ui.result.compose.sections.RecommendationRowView
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieDetailsComposeScreen(
    modifier: Modifier = Modifier,
    title: String = "",
    providerName: String? = null,
    backdropUrl: String? = null,
    posterUrl: String? = null,
    logoUrl: String? = null,
    matchScore: String? = null,
    releaseYear: String? = null,
    seasonsCount: String? = null,
    quality: String? = null,
    maturityRating: String? = null,
    advisories: String? = null,
    top10RankText: String? = null,
    synopsis: String = "",
    cast: List<String> = emptyList(),
    genres: List<String> = emptyList(),
    moodTags: List<String> = emptyList(),
    creator: String? = null,
    writers: List<String> = emptyList(),
    dynamicEpisodes: List<ResultEpisode>? = null,
    dynamicRecommendations: List<SearchResponse>? = null,
    dynamicActors: List<ActorData>? = null,
    dynamicSeasons: List<String>? = null,
    dynamicTrailers: List<MovieTrailerData>? = null,
    resumeStatus: ResumeWatchingStatus? = null,
    isMovie: Boolean = true,
    selectedSeasonIndex: Int = 0,
    dynamicDubs: List<String>? = null,
    selectedDubIndex: Int = 0,
    onDubSelect: ((Int) -> Unit)? = null,
    dynamicRanges: List<String>? = null,
    selectedRangeIndex: Int = 0,
    onRangeSelect: ((Int) -> Unit)? = null,
    onPlayLongClick: (() -> Unit)? = null,
    onEpisodeLongClick: ((ResultEpisode) -> Unit)? = null,
    statusText: String? = null,
    isOngoing: Boolean = false,
    nextAiringUnixTime: Long? = null,
    nextAiringEpisode: String? = null,
    nextAiringDate: String? = null,
    isInWatchList: Boolean = false,
    isFavorite: Boolean = false,
    hasTrailers: Boolean = false,
    onPlayClick: () -> Unit = {},
    onEpisodeClick: ((ResultEpisode) -> Unit)? = null,
    onSeasonSelect: ((Int) -> Unit)? = null,
    onAddToListClick: () -> Unit = {},
    onLikeClick: () -> Unit = {},
    onTrailerClick: () -> Unit = {},
    onSearchClick: (() -> Unit)? = null,
    onActorClick: ((String) -> Unit)? = null,
    onRecommendationClick: ((SearchResponse) -> Unit)? = null,
    onCloseClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val heroHeight = (screenHeight * 0.65f).coerceAtLeast(380.dp)

    val colors = MovieDetailsTheme.colors
    val lazyListState = rememberLazyListState()

    val playInteractionSource = remember { MutableInteractionSource() }
    val inMyListInteractionSource = remember { MutableInteractionSource() }
    val likeInteractionSource = remember { MutableInteractionSource() }
    val trailerInteractionSource = remember { MutableInteractionSource() }
    val searchInteractionSource = remember { MutableInteractionSource() }

    val playButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        lazyListState.scrollToItem(0)
        playButtonFocusRequester.requestFocus()
    }

    val seasonOptions = remember(dynamicSeasons) {
        dynamicSeasons ?: emptyList()
    }

    var selectedSeasonText by remember(selectedSeasonIndex, seasonOptions) {
        mutableStateOf(seasonOptions.getOrElse(selectedSeasonIndex) { seasonOptions.firstOrNull() ?: "" })
    }

    LaunchedEffect(selectedSeasonIndex, seasonOptions) {
        if (seasonOptions.isNotEmpty()) {
            selectedSeasonText = seasonOptions.getOrElse(selectedSeasonIndex) { seasonOptions.first() }
        }
    }

    val dubOptions = remember(dynamicDubs) {
        dynamicDubs ?: emptyList()
    }

    var selectedDubText by remember(selectedDubIndex, dubOptions) {
        mutableStateOf(dubOptions.getOrElse(selectedDubIndex) { dubOptions.firstOrNull() ?: "" })
    }

    LaunchedEffect(selectedDubIndex, dubOptions) {
        if (dubOptions.isNotEmpty()) {
            selectedDubText = dubOptions.getOrElse(selectedDubIndex) { dubOptions.first() }
        }
    }

    val rangeOptions = remember(dynamicRanges) {
        dynamicRanges ?: emptyList()
    }

    var selectedRangeText by remember(selectedRangeIndex, rangeOptions) {
        mutableStateOf(rangeOptions.getOrElse(selectedRangeIndex) { rangeOptions.firstOrNull() ?: "" })
    }

    LaunchedEffect(selectedRangeIndex, rangeOptions) {
        if (rangeOptions.isNotEmpty()) {
            selectedRangeText = rangeOptions.getOrElse(selectedRangeIndex) { rangeOptions.first() }
        }
    }

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    val episodesToDisplay = dynamicEpisodes ?: emptyList()

    val airingSchedule = remember(
        statusText,
        isOngoing,
        nextAiringUnixTime,
        nextAiringEpisode,
        nextAiringDate,
        episodesToDisplay,
        context
    ) {
        resolveAiringSchedule(
            context = context,
            statusText = statusText,
            isOngoing = isOngoing,
            nextAiringUnixTime = nextAiringUnixTime,
            nextAiringEpisode = nextAiringEpisode,
            nextAiringDate = nextAiringDate,
            episodes = episodesToDisplay
        )
    }

    val playButtonText = remember(resumeStatus, episodesToDisplay, isMovie, context) {
        getPlayButtonText(context, resumeStatus, episodesToDisplay, isMovie)
    }

    val resumeProgressFraction = remember(resumeStatus) {
        val prog = resumeStatus?.progress
        if (prog != null && prog.maxProgress > 0) {
            prog.progress.toFloat() / prog.maxProgress.toFloat()
        } else {
            null
        }
    }

    val castList = remember(dynamicActors, cast) {
        dynamicActors?.map { it.actor.name }?.filter { it.isNotBlank() }?.ifEmpty { cast } ?: cast
    }

    val recommendationCards = remember(dynamicRecommendations) {
        dynamicRecommendations?.map { rec ->
            MovieCardItem(
                title = rec.name,
                type = MovieCardType.POSTER,
                posterUrl = rec.posterUrl,
                showLogo = false,
                showBottomTitle = true
            )
        } ?: emptyList()
    }

    val chunkedRecommendations = remember(recommendationCards) {
        recommendationCards.chunked(6).mapIndexed { idx, list ->
            MovieRecommendationRow(idx, list)
        }
    }

    val tvBringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float
            ): Float {
                val parentFraction = 0.30f
                val targetOffset = parentFraction * containerSize
                return offset - targetOffset
            }
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides tvBringIntoViewSpec) {
        LazyColumn(
            state = lazyListState,
            modifier = modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            item(key = "hero_banner", contentType = "hero_banner") {
                HeroBannerSection(
                    title = title,
                    providerName = providerName,
                    backdropUrl = backdropUrl,
                    logoUrl = logoUrl,
                    heroHeight = heroHeight,
                    playButtonText = playButtonText,
                    resumeProgressFraction = resumeProgressFraction,
                    isInWatchList = isInWatchList,
                    isFavorite = isFavorite,
                    hasTrailers = hasTrailers,
                    playButtonFocusRequester = playButtonFocusRequester,
                    playInteractionSource = playInteractionSource,
                    inMyListInteractionSource = inMyListInteractionSource,
                    likeInteractionSource = likeInteractionSource,
                    trailerInteractionSource = trailerInteractionSource,
                    searchInteractionSource = searchInteractionSource,
                    onPlayClick = onPlayClick,
                    onPlayLongClick = onPlayLongClick,
                    onAddToListClick = onAddToListClick,
                    onLikeClick = onLikeClick,
                    onTrailerClick = onTrailerClick,
                    onSearchClick = onSearchClick
                )
            }

            item(key = "movie_info_synopsis", contentType = "movie_info") {
                MovieInfoSynopsisSection(
                    matchScore = matchScore,
                    releaseYear = releaseYear,
                    seasonsCount = seasonsCount,
                    quality = quality,
                    maturityRating = maturityRating,
                    advisories = advisories,
                    top10RankText = top10RankText,
                    synopsis = synopsis,
                    castList = castList,
                    genres = genres,
                    moodTags = moodTags,
                    airingSchedule = airingSchedule
                )
            }

            if (episodesToDisplay.isNotEmpty()) {
                item(key = "episodes_header", contentType = "episodes_header") {
                    EpisodesHeaderSection(
                        seasonOptions = seasonOptions,
                        selectedSeasonText = selectedSeasonText,
                        onSeasonSelect = onSeasonSelect,
                        onSeasonTextChange = { selectedSeasonText = it },
                        dubOptions = dubOptions,
                        selectedDubText = selectedDubText,
                        onDubSelect = onDubSelect,
                        onDubTextChange = { selectedDubText = it },
                        rangeOptions = rangeOptions,
                        selectedRangeText = selectedRangeText,
                        onRangeSelect = onRangeSelect,
                        onRangeTextChange = { selectedRangeText = it },
                        airingSchedule = airingSchedule
                    )
                }

                items(
                    items = episodesToDisplay,
                    key = { ep -> "episode_${ep.id}" },
                    contentType = { "episode_row" }
                ) { ep ->
                    val onEpClick = remember(ep) {
                        {
                            if (onEpisodeClick != null) onEpisodeClick(ep)
                            else showToast("Playing ${ep.headerName}: ${ep.name}")
                        }
                    }
                    val onEpLongClick = remember(ep) {
                        if (onEpisodeLongClick != null) {
                            { onEpisodeLongClick(ep) }
                        } else null
                    }
                    EpisodeRowItem(
                        episode = ep,
                        onClick = onEpClick,
                        onLongClick = onEpLongClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MovieDetailsTheme.dimens.spacing2Xl, vertical = 6.dp)
                    )
                }
            }

            if (chunkedRecommendations.isNotEmpty()) {
                item(key = "recommendations_header", contentType = "recommendations_header") {
                    Text(
                        text = stringResource(id = R.string.more_like_this),
                        style = MovieDetailsTheme.typography.boldTitle2,
                        fontSize = 22.sp,
                        color = colors.textPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MovieDetailsTheme.dimens.spacing2Xl)
                            .padding(top = MovieDetailsTheme.dimens.spacing3Xl, bottom = MovieDetailsTheme.dimens.spacingL)
                    )
                }

                items(
                    items = chunkedRecommendations,
                    key = { row -> "rec_row_${row.rowIndex}" },
                    contentType = { "recommendation_row" }
                ) { row ->
                    RecommendationRowView(
                        row = row,
                        onCardClick = { cardItem ->
                            val rec = dynamicRecommendations?.find { it.name == cardItem.title }
                            if (rec != null && onRecommendationClick != null) {
                                onRecommendationClick(rec)
                            } else {
                                showToast("Opened recommendation: ${cardItem.title}")
                            }
                        }
                    )
                }
            }

            if (!dynamicActors.isNullOrEmpty()) {
                item(key = "cast_and_crew_section", contentType = "cast_section") {
                    CastAndCrewSection(
                        actors = dynamicActors,
                        onActorClick = onActorClick,
                        onActorLongClick = onActorClick
                    )
                }
            }

            val hasAboutContent = !creator.isNullOrBlank() || castList.isNotEmpty() ||
                    writers.isNotEmpty() || genres.isNotEmpty() || moodTags.isNotEmpty() ||
                    !maturityRating.isNullOrBlank()

            if (hasAboutContent) {
                item(key = "about_section", contentType = "about_section") {
                    AboutSection(
                        title = title,
                        creator = creator,
                        castList = castList,
                        writers = writers,
                        genres = genres,
                        moodTags = moodTags,
                        maturityRating = maturityRating,
                        advisories = advisories
                    )
                }
            }
        }
    }
}

@Preview(
    name = "TV 1080p Landscape",
    device = "spec:width=1920dp,height=1080dp,dpi=320,orientation=landscape",
    showBackground = true,
    backgroundColor = 0xFF141414
)
@Composable
private fun MovieDetailsComposeScreenPreview() {
    MovieDetailsTheme {
        MovieDetailsComposeScreen(
            title = "Stranger Things",
            providerName = "Netflix",
            backdropUrl = null,
            matchScore = "98% Match",
            releaseYear = "2024",
            seasonsCount = "4 Seasons",
            quality = "4K ULTRA HD",
            maturityRating = "16+",
            advisories = "fear, language, violence",
            top10RankText = "#1 in TV Shows Today",
            statusText = "Ongoing",
            isOngoing = true,
            nextAiringEpisode = "Episode 5",
            nextAiringDate = "2d 14h",
            synopsis = "When a young boy vanishes, a small town uncovers a mystery involving secret experiments, terrifying supernatural forces and one strange little girl.",
            cast = listOf("Winona Ryder", "David Harbour", "Millie Bobby Brown", "Finn Wolfhard"),
            genres = listOf("Sci-Fi", "Horror", "Drama"),
            moodTags = listOf("Ominous", "Nostalgic", "Suspenseful"),
            creator = "The Duffer Brothers"
        )
    }
}
