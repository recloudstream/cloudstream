package com.lagradost.cloudstream3.ui.result.compose

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.ResumeWatchingStatus
import com.lagradost.cloudstream3.ui.result.compose.components.CircleActionButton
import com.lagradost.cloudstream3.ui.result.compose.components.HeroPlayButton
import com.lagradost.cloudstream3.ui.result.compose.components.HeroTrailerButton
import com.lagradost.cloudstream3.ui.result.compose.components.MaturityRatingBadge
import com.lagradost.cloudstream3.ui.result.compose.components.MovieCardItem
import com.lagradost.cloudstream3.ui.result.compose.components.MovieCardSize
import com.lagradost.cloudstream3.ui.result.compose.components.MovieCardType
import com.lagradost.cloudstream3.ui.result.compose.components.MovieDetailsMovieCard
import com.lagradost.cloudstream3.ui.result.compose.components.MovieDetailsTokens
import com.lagradost.cloudstream3.ui.result.compose.components.SeasonDropdown
import com.lagradost.cloudstream3.ui.result.compose.components.VideoQualityBadge
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme
import com.lagradost.cloudstream3.ui.result.compose.theme.PrimaryBlack
import com.lagradost.cloudstream3.ui.result.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.result.compose.theme.TransparentBlack60
import com.lagradost.cloudstream3.ui.result.compose.theme.getRatingScoreColor
import com.lagradost.cloudstream3.ui.result.getWatchProgress

@Immutable
data class MovieTrailerData(
    val title: String,
    val runtime: String,
    val rawTrailer: Any? = null
)

@Immutable
data class MovieRecommendationRow(
    val rowIndex: Int,
    val items: List<MovieCardItem>
)

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
    isInWatchList: Boolean = false,
    hasTrailers: Boolean = false,
    onPlayClick: () -> Unit = {},
    onEpisodeClick: ((ResultEpisode) -> Unit)? = null,
    onEpisodeDownloadClick: ((ResultEpisode) -> Unit)? = null,
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
    val typography = MovieDetailsTheme.typography
    val dimens = MovieDetailsTheme.dimens
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

    var inMyListState by remember(isInWatchList) { mutableStateOf(isInWatchList) }

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

    val playButtonText = remember(resumeStatus, episodesToDisplay, isMovie, context) {
        if (resumeStatus != null) {
            val resumeEp = resumeStatus.result
            val prefix = if (resumeStatus.progress != null) {
                context.getString(R.string.resume)
            } else {
                context.getString(R.string.play_movie_button)
            }
            if (resumeStatus.isMovie) {
                prefix
            } else {
                val sShort = context.getString(R.string.season_short)
                val eShort = context.getString(R.string.episode_short)
                val s = resumeEp.season
                val e = resumeEp.episode
                val epCode = if (s != null && s > 0 && e > 0) {
                    "$sShort$s:$eShort$e"
                } else if (e > 0) {
                    "$eShort$e"
                } else {
                    ""
                }
                if (epCode.isNotBlank()) {
                    "$prefix $epCode"
                } else {
                    prefix
                }
            }
        } else {
            val firstEp = episodesToDisplay.firstOrNull()
            if (firstEp != null && !isMovie) {
                val sShort = context.getString(R.string.season_short)
                val eShort = context.getString(R.string.episode_short)
                val s = firstEp.season
                val e = firstEp.episode
                val epCode = if (s != null && s > 0 && e > 0) {
                    "$sShort$s:$eShort$e"
                } else if (e > 0) {
                    "$eShort$e"
                } else {
                    ""
                }
                val playStr = context.getString(R.string.play_movie_button)
                if (epCode.isNotBlank()) {
                    "$playStr $epCode"
                } else {
                    playStr
                }
            } else {
                context.getString(R.string.play_movie_button)
            }
        }
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

    val trailersList = dynamicTrailers ?: emptyList()

    val tvBringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float
            ): Float {
                val parentFraction = 0.30f
                val targetOffset = (parentFraction * containerSize)
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                        .clipToBounds()
                        .background(colors.background)
                ) {
                    if (!backdropUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = backdropUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        colors.background.copy(alpha = 0.5f),
                                        colors.background.copy(alpha = 0.92f),
                                        colors.background
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                            .padding(horizontal = dimens.spacing2Xl, vertical = dimens.spacing2Xl)
                            .zIndex(50f),
                        verticalArrangement = Arrangement.spacedBy(dimens.spacingM)
                    ) {
                        Box(
                            modifier = Modifier
                                .height(42.dp)
                                .width(110.dp)
                                .focusRequester(playButtonFocusRequester)
                                .focusable(interactionSource = playInteractionSource)
                                .combinedClickable(
                                    interactionSource = playInteractionSource,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = onPlayClick,
                                    onLongClick = onPlayLongClick
                                )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .focusable(interactionSource = inMyListInteractionSource)
                                        .clickable(
                                            interactionSource = inMyListInteractionSource,
                                            indication = null,
                                            role = Role.Button
                                        ) {
                                            inMyListState = !inMyListState
                                            onAddToListClick()
                                        }
                                )

                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .focusable(interactionSource = likeInteractionSource)
                                        .clickable(
                                            interactionSource = likeInteractionSource,
                                            indication = null,
                                            role = Role.Button,
                                            onClick = onLikeClick
                                        )
                                )

                                if (onSearchClick != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .focusable(interactionSource = searchInteractionSource)
                                            .clickable(
                                                interactionSource = searchInteractionSource,
                                                indication = null,
                                                role = Role.Button,
                                                onClick = onSearchClick
                                            )
                                    )
                                }
                            }

                            if (hasTrailers) {
                                Box(
                                    modifier = Modifier
                                        .height(40.dp)
                                        .width(120.dp)
                                        .focusable(interactionSource = trailerInteractionSource)
                                        .clickable(
                                            interactionSource = trailerInteractionSource,
                                            indication = null,
                                            role = Role.Button,
                                            onClick = onTrailerClick
                                        )
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(dimens.spacing2Xl)
                    ) {
                        if (!providerName.isNullOrBlank()) {
                            Text(
                                text = providerName.uppercase(),
                                style = typography.boldTitle2.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.8).sp
                                ),
                                color = colors.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .padding(horizontal = dimens.spacing2Xl, vertical = dimens.spacingL),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            if (!logoUrl.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .height(68.dp)
                                        .width(240.dp)
                                ) {
                                    AsyncImage(
                                        model = logoUrl,
                                        contentDescription = title,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Text(
                                    text = title,
                                    style = typography.boldTitle1,
                                    fontSize = 32.sp,
                                    color = colors.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(dimens.spacingL))

                            HeroPlayButton(
                                onClick = onPlayClick,
                                onLongClick = onPlayLongClick,
                                text = playButtonText,
                                progress = resumeProgressFraction,
                                interactionSource = playInteractionSource,
                                enabled = false
                            )

                            Spacer(modifier = Modifier.height(dimens.spacingM))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
                            ) {
                                CircleActionButton(
                                    icon = painterResource(
                                        id = if (inMyListState) R.drawable.ic_baseline_check_24 else R.drawable.ic_baseline_add_24
                                    ),
                                    contentDescription = if (inMyListState) {
                                        stringResource(id = R.string.in_my_list)
                                    } else {
                                        stringResource(id = R.string.add_to_my_list)
                                    },
                                    onClick = {
                                        inMyListState = !inMyListState
                                        onAddToListClick()
                                    },
                                    interactionSource = inMyListInteractionSource,
                                    enabled = false
                                )

                                CircleActionButton(
                                    icon = painterResource(id = R.drawable.ic_baseline_favorite_24),
                                    contentDescription = stringResource(id = R.string.favorite),
                                    onClick = onLikeClick,
                                    interactionSource = likeInteractionSource,
                                    enabled = false
                                )

                                if (onSearchClick != null) {
                                    CircleActionButton(
                                        icon = painterResource(id = R.drawable.search_icon),
                                        contentDescription = stringResource(id = R.string.title_search),
                                        onClick = onSearchClick,
                                        interactionSource = searchInteractionSource,
                                        enabled = false
                                    )
                                }
                            }
                        }

                        if (hasTrailers) {
                            HeroTrailerButton(
                                text = stringResource(id = R.string.play_trailer),
                                onClick = onTrailerClick,
                                interactionSource = trailerInteractionSource,
                                enabled = false
                            )
                        }
                    }
                }
            }

            item(key = "movie_info_synopsis", contentType = "movie_info") {
                val hasRightColumn = castList.isNotEmpty() || genres.isNotEmpty() || moodTags.isNotEmpty()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.spacing2Xl, vertical = dimens.spacingL),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacing2Xl)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(if (hasRightColumn) 0.65f else 1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(dimens.spacingM)
                    ) {
                        val hasLine1 = !matchScore.isNullOrBlank() || !releaseYear.isNullOrBlank() ||
                                !seasonsCount.isNullOrBlank() || !quality.isNullOrBlank()

                        if (hasLine1) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
                            ) {
                                if (!matchScore.isNullOrBlank()) {
                                    Text(
                                        text = matchScore,
                                        color = getRatingScoreColor(matchScore),
                                        style = typography.mediumBody,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (!releaseYear.isNullOrBlank()) {
                                    Text(
                                        text = releaseYear,
                                        color = colors.textPrimary,
                                        style = typography.regularBody
                                    )
                                }

                                if (!seasonsCount.isNullOrBlank()) {
                                    Text(
                                        text = seasonsCount,
                                        color = colors.textPrimary,
                                        style = typography.regularBody
                                    )
                                }

                                if (!quality.isNullOrBlank()) {
                                    VideoQualityBadge(quality = quality)
                                }
                            }
                        }

                        if (!maturityRating.isNullOrBlank() || !advisories.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(dimens.spacingS)
                            ) {
                                if (!maturityRating.isNullOrBlank()) {
                                    MaturityRatingBadge(rating = maturityRating)
                                }
                                if (!advisories.isNullOrBlank()) {
                                    Text(
                                        text = advisories,
                                        color = colors.textMuted,
                                        style = typography.regularCaption1
                                    )
                                }
                            }
                        }

                        if (!top10RankText.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(dimens.spacingS),
                                modifier = Modifier.padding(vertical = dimens.spacingXs)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(MovieDetailsTokens.ShapeCardSmall)
                                        .background(colors.primary)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.top_10_badge_text),
                                        style = typography.regularCaption2,
                                        color = colors.onPrimary,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Text(
                                    text = top10RankText,
                                    style = typography.mediumBody,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                        }

                        if (synopsis.isNotBlank()) {
                            val synopsisInteractionSource = remember { MutableInteractionSource() }
                            val isSynopsisFocused by synopsisInteractionSource.collectIsFocusedAsState()

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MovieDetailsTokens.ShapeCardSmall)
                                    .background(if (isSynopsisFocused) colors.surfaceElevated.copy(alpha = 0.5f) else Color.Transparent)
                                    .then(
                                        if (isSynopsisFocused) {
                                            Modifier.border(
                                                BorderStroke(dimens.borderFocus, colors.primary),
                                                MovieDetailsTokens.ShapeCardSmall
                                            )
                                        } else Modifier
                                    )
                                    .focusable(interactionSource = synopsisInteractionSource)
                                    .padding(dimens.spacingS)
                            ) {
                                Text(
                                    text = synopsis,
                                    style = typography.regularBody,
                                    color = if (isSynopsisFocused) colors.textPrimary else colors.textSecondary,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }

                    if (hasRightColumn) {
                        Column(
                            modifier = Modifier
                                .weight(0.35f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(dimens.spacingM)
                        ) {
                            if (castList.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = stringResource(id = R.string.cast_label),
                                        style = typography.regularCaption2,
                                        color = colors.textSecondary
                                    )
                                    Text(
                                        text = castList.joinToString(", "),
                                        style = typography.regularCaption1,
                                        color = colors.textPrimary,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (genres.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = stringResource(id = R.string.genres_label),
                                        style = typography.regularCaption2,
                                        color = colors.textSecondary
                                    )
                                    Text(
                                        text = genres.joinToString(", "),
                                        style = typography.regularCaption1,
                                        color = colors.textPrimary
                                    )
                                }
                            }

                            if (moodTags.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = stringResource(id = R.string.mood_tags_label),
                                        style = typography.regularCaption2,
                                        color = colors.textSecondary
                                    )
                                    Text(
                                        text = moodTags.joinToString(", "),
                                        style = typography.regularCaption1,
                                        color = colors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (episodesToDisplay.isNotEmpty()) {
                item(key = "episodes_header", contentType = "episodes_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.spacing2Xl)
                            .padding(top = dimens.spacing2Xl, bottom = dimens.spacingL),
                        horizontalArrangement = Arrangement.spacedBy(dimens.spacingL),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.episodes),
                            style = typography.boldTitle2,
                            fontSize = 24.sp,
                            color = colors.textPrimary
                        )

                        if (seasonOptions.size > 1) {
                            SeasonDropdown(
                                options = seasonOptions,
                                selectedOption = selectedSeasonText,
                                onOptionSelected = { seasonStr ->
                                    selectedSeasonText = seasonStr
                                    val idx = seasonOptions.indexOf(seasonStr)
                                    if (idx >= 0) onSeasonSelect?.invoke(idx)
                                },
                                width = 180.dp
                            )
                        }

                        if (dubOptions.size > 1) {
                            SeasonDropdown(
                                options = dubOptions,
                                selectedOption = selectedDubText,
                                onOptionSelected = { dubStr ->
                                    selectedDubText = dubStr
                                    val idx = dubOptions.indexOf(dubStr)
                                    if (idx >= 0) onDubSelect?.invoke(idx)
                                },
                                width = 140.dp
                            )
                        }

                        if (rangeOptions.size > 1) {
                            SeasonDropdown(
                                options = rangeOptions,
                                selectedOption = selectedRangeText,
                                onOptionSelected = { rangeStr ->
                                    selectedRangeText = rangeStr
                                    val idx = rangeOptions.indexOf(rangeStr)
                                    if (idx >= 0) onRangeSelect?.invoke(idx)
                                },
                                width = 140.dp
                            )
                        }
                    }
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
                    val onEpDownload = remember(ep) {
                        {
                            if (onEpisodeDownloadClick != null) onEpisodeDownloadClick(ep)
                            else showToast("Downloading Episode ${ep.episode}")
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
                        onDownloadClick = onEpDownload,
                        onLongClick = onEpLongClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.spacing2Xl, vertical = 6.dp)
                    )
                }
            }

            if (chunkedRecommendations.isNotEmpty()) {
                item(key = "recommendations_header", contentType = "recommendations_header") {
                    Text(
                        text = stringResource(id = R.string.more_like_this),
                        style = typography.boldTitle2,
                        fontSize = 22.sp,
                        color = colors.textPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.spacing2Xl)
                            .padding(top = dimens.spacing3Xl, bottom = dimens.spacingL)
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

            if (trailersList.isNotEmpty()) {
                item(key = "trailers_section", contentType = "trailers_section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.spacing2Xl)
                            .padding(top = dimens.spacing3Xl, bottom = dimens.spacingL)
                    ) {
                        Text(
                            text = stringResource(id = R.string.trailers_and_more),
                            style = typography.boldTitle2,
                            fontSize = 22.sp,
                            color = colors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(dimens.spacingL))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dimens.spacingL)
                        ) {
                            trailersList.forEach { trailer ->
                                Box(modifier = Modifier.weight(1f)) {
                                    TrailerItemCard(
                                        trailer = trailer,
                                        onClick = {
                                            if (hasTrailers) onTrailerClick()
                                            else showToast("Playing ${trailer.title}")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val hasAboutContent = !creator.isNullOrBlank() || castList.isNotEmpty() ||
                    writers.isNotEmpty() || genres.isNotEmpty() || moodTags.isNotEmpty() ||
                    !maturityRating.isNullOrBlank()

            if (hasAboutContent) {
                item(key = "about_section", contentType = "about_section") {
                    val aboutInteractionSource = remember { MutableInteractionSource() }
                    val isAboutFocused by aboutInteractionSource.collectIsFocusedAsState()
                    val aboutBorder = if (isAboutFocused) BorderStroke(dimens.borderFocus, colors.primary) else BorderStroke(1.dp, colors.border)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.spacing2Xl)
                            .padding(top = dimens.spacing3Xl, bottom = dimens.spacing3Xl)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MovieDetailsTokens.ShapeCardMedium)
                                .background(if (isAboutFocused) colors.surfaceElevated else colors.surface)
                                .border(aboutBorder, MovieDetailsTokens.ShapeCardMedium)
                                .focusable(interactionSource = aboutInteractionSource)
                                .padding(dimens.spacing2Xl),
                            verticalArrangement = Arrangement.spacedBy(dimens.spacingM)
                        ) {
                            Text(
                                text = stringResource(id = R.string.about_title_format, title),
                                style = typography.boldTitle2,
                                fontSize = 20.sp,
                                color = colors.textPrimary
                            )

                            Spacer(modifier = Modifier.height(dimens.spacingXs))

                            if (!creator.isNullOrBlank()) {
                                AboutMetadataRow(label = stringResource(id = R.string.creator_label), value = creator)
                            }
                            if (castList.isNotEmpty()) {
                                AboutMetadataRow(label = stringResource(id = R.string.cast_label), value = castList.joinToString(", "))
                            }
                            if (writers.isNotEmpty()) {
                                AboutMetadataRow(label = stringResource(id = R.string.writers_label), value = writers.joinToString(", "))
                            }
                            if (genres.isNotEmpty()) {
                                AboutMetadataRow(label = stringResource(id = R.string.genres_label), value = genres.joinToString(", "))
                            }
                            if (moodTags.isNotEmpty()) {
                                AboutMetadataRow(label = stringResource(id = R.string.mood_tags_label), value = moodTags.joinToString(", "))
                            }
                            if (!maturityRating.isNullOrBlank()) {
                                val ratingDescription = if (!advisories.isNullOrBlank()) {
                                    "$maturityRating ($advisories)"
                                } else {
                                    maturityRating
                                }
                                AboutMetadataRow(
                                    label = stringResource(id = R.string.maturity_rating_label),
                                    value = ratingDescription
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeRowItem(
    episode: ResultEpisode,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val typography = MovieDetailsTheme.typography
    val dimens = MovieDetailsTheme.dimens
    val colors = MovieDetailsTheme.colors

    val scaleState = animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = MovieDetailsTokens.FastFocusAnimationSpec,
        label = "episodeScale"
    )

    val background = if (isFocused) colors.surfaceElevated else colors.surface
    val border = if (isFocused) BorderStroke(dimens.borderFocus, colors.primary) else null
    val progress = remember(episode) { episode.getWatchProgress() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isFocused) 10f else 0f)
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
            }
            .clip(MovieDetailsTokens.ShapeCardMedium)
            .background(background)
            .then(if (border != null) Modifier.border(border, MovieDetailsTokens.ShapeCardMedium) else Modifier)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(dimens.spacingL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingL)
    ) {
        Text(
            text = episode.episode.toString(),
            style = typography.boldTitle1,
            fontSize = 24.sp,
            color = colors.textSecondary,
            modifier = Modifier.width(32.dp)
        )

        Box(
            modifier = Modifier
                .width(160.dp)
                .height(90.dp)
                .clip(MovieDetailsTokens.ShapeCardSmall)
                .background(colors.surface)
        ) {
            if (!episode.poster.isNullOrBlank()) {
                AsyncImage(
                    model = episode.poster,
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(TransparentBlack60)
                    .border(BorderStroke(1.dp, PrimaryWhite), CircleShape)
                    .align(Alignment.Center)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_play_arrow_24),
                    contentDescription = null,
                    tint = PrimaryWhite,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.Center)
                )
            }

            if (progress > 0.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomStart)
                        .background(colors.border)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(colors.primary)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${episode.episode}. ${episode.name ?: episode.headerName}",
                    style = typography.mediumBody,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (episode.runTime != null) "${episode.runTime}m" else "",
                    style = typography.regularCaption1,
                    color = colors.textSecondary
                )
            }

            if (!episode.description.isNullOrBlank()) {
                Text(
                    text = episode.description,
                    style = typography.regularCaption1,
                    color = colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }
        }

        CircleActionButton(
            icon = painterResource(id = R.drawable.baseline_downloading_24),
            contentDescription = stringResource(id = R.string.download_episode_format, episode.episode),
            onClick = onDownloadClick,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
fun TrailerItemCard(
    trailer: MovieTrailerData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val typography = MovieDetailsTheme.typography
    val dimens = MovieDetailsTheme.dimens
    val colors = MovieDetailsTheme.colors

    val scaleState = animateFloatAsState(
        targetValue = if (isFocused) MovieDetailsTokens.FOCUS_SCALE_FACTOR else 1f,
        animationSpec = MovieDetailsTokens.FastFocusAnimationSpec,
        label = "trailerScale"
    )

    val border = if (isFocused) BorderStroke(dimens.borderFocus, colors.primary) else BorderStroke(dimens.borderSubtle, colors.border)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isFocused) 10f else 0f)
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
            }
            .clip(MovieDetailsTokens.ShapeCardMedium)
            .background(colors.surfaceElevated)
            .border(border, MovieDetailsTokens.ShapeCardMedium)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(colors.surface)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(TransparentBlack60)
                    .border(BorderStroke(1.5.dp, PrimaryWhite), CircleShape)
                    .align(Alignment.Center)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_play_arrow_24),
                    contentDescription = null,
                    tint = PrimaryWhite,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.Center)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacingM),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = trailer.title,
                style = typography.mediumBody,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = trailer.runtime,
                style = typography.regularCaption2,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
fun AboutMetadataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val typography = MovieDetailsTheme.typography
    val colors = MovieDetailsTheme.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (label.endsWith(":")) label else "$label:",
            style = typography.regularCaption1,
            color = colors.textSecondary,
            modifier = Modifier.width(130.dp)
        )
        Text(
            text = value,
            style = typography.regularCaption1,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun RecommendationRowView(
    row: MovieRecommendationRow,
    onCardClick: (MovieCardItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = MovieDetailsTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacing2Xl, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
    ) {
        row.items.forEach { cardItem ->
            Box(modifier = Modifier.weight(1f)) {
                MovieDetailsMovieCard(
                    title = cardItem.title,
                    type = MovieCardType.POSTER,
                    size = MovieCardSize.MEDIUM,
                    badge = cardItem.badge,
                    posterUrl = cardItem.posterUrl,
                    showLogo = false,
                    showBottomTitle = true,
                    onClick = { onCardClick(cardItem) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (row.items.size < 6) {
            repeat(6 - row.items.size) {
                Spacer(modifier = Modifier.weight(1f))
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
            title = "HOUSE OF NINJAS",
            providerName = "SUPERSTREAM",
            matchScore = "98% Match",
            releaseYear = "2024",
            seasonsCount = "1 Season",
            quality = "4K Ultra HD",
            maturityRating = "TV-MA",
            advisories = "smoking, violence, language",
            top10RankText = "#2 in TV Shows Today",
            synopsis = "Years after retiring from their formidable ninja lives, a dysfunctional family must return to shadowy missions to counteract a string of looming threats.",
            cast = listOf("Kento Kaku", "Yosuke Eguchi", "Tae Kimura", "Kengo Kora"),
            genres = listOf("TV Dramas", "Action", "Japanese", "Suspenseful"),
            moodTags = listOf("Dark", "Suspenseful", "Exciting"),
            creator = "Dave Boyle",
            writers = listOf("Dave Boyle", "Masahiro Yamaura", "Kota Oishi"),
            isInWatchList = false,
            hasTrailers = true
        )
    }
}
