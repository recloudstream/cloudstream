package com.lagradost.cloudstream3.ui.revamp.compose.screens

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.getWatchProgress
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixBadgeType
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCircleActionButton
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixDropdown
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeroPlayButton
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeroTrailerButton
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixLogoVariant
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixLogoView
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMaturityRating
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieCard
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieCardItem
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieCardSize
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieCardType
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixSampleData
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixTokens
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixVideoQualityBadge
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.GreenAccent
import com.lagradost.cloudstream3.ui.revamp.compose.theme.getRatingScoreColor
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey100
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey50
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey600
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey700
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey750
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey800
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey850
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryBlack
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryRed
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentBlack60
import kotlinx.coroutines.launch

@Immutable
data class CloneflixTrailerData(
    val title: String,
    val runtime: String,
    val rawTrailer: Any? = null
)

@Immutable
data class CloneflixRecommendationRow(
    val rowIndex: Int,
    val items: List<CloneflixMovieCardItem>
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CloneflixMovieDetailsComposeScreen(
    modifier: Modifier = Modifier,
    title: String = CloneflixSampleData.SAMPLE_TITLE_HOUSE_OF_NINJAS,
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
    dynamicTrailers: List<CloneflixTrailerData>? = null,
    selectedSeasonIndex: Int = 0,
    isInWatchList: Boolean = false,
    hasTrailers: Boolean = false,
    onPlayClick: () -> Unit = {},
    onEpisodeClick: ((ResultEpisode) -> Unit)? = null,
    onEpisodeDownloadClick: ((ResultEpisode) -> Unit)? = null,
    onSeasonSelect: ((Int) -> Unit)? = null,
    onAddToListClick: () -> Unit = {},
    onLikeClick: () -> Unit = {},
    onTrailerClick: () -> Unit = {},
    onActorClick: ((String) -> Unit)? = null,
    onRecommendationClick: ((SearchResponse) -> Unit)? = null,
    onCloseClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val heroHeight = (screenHeight * 0.65f).coerceAtLeast(380.dp)

    val colors = CloneflixTheme.colors
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val playInteractionSource = remember { MutableInteractionSource() }
    val inMyListInteractionSource = remember { MutableInteractionSource() }
    val likeInteractionSource = remember { MutableInteractionSource() }
    val trailerInteractionSource = remember { MutableInteractionSource() }

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

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    val episodesToDisplay = dynamicEpisodes ?: emptyList()

    val castList = remember(dynamicActors, cast) {
        dynamicActors?.map { it.actor.name }?.filter { it.isNotBlank() }?.ifEmpty { cast } ?: cast
    }

    val recommendationCards = remember(dynamicRecommendations) {
        dynamicRecommendations?.map { rec ->
            CloneflixMovieCardItem(
                title = rec.name,
                type = CloneflixMovieCardType.POSTER,
                posterUrl = rec.posterUrl,
                showLogo = false,
                showBottomTitle = true
            )
        } ?: emptyList()
    }

    val chunkedRecommendations = remember(recommendationCards) {
        recommendationCards.chunked(6).mapIndexed { idx, list ->
            CloneflixRecommendationRow(idx, list)
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
                                .clickable(
                                    interactionSource = playInteractionSource,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = onPlayClick
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
                        CloneflixLogoView(
                            variant = CloneflixLogoVariant.WORDMARK_MEDIUM,
                            text = providerName
                        )
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
                                    color = PrimaryWhite,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(dimens.spacingL))

                            CloneflixHeroPlayButton(
                                onClick = onPlayClick,
                                text = "Play",
                                interactionSource = playInteractionSource,
                                enabled = false
                            )

                            Spacer(modifier = Modifier.height(dimens.spacingM))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
                            ) {
                                CloneflixCircleActionButton(
                                    icon = painterResource(
                                        id = if (inMyListState) R.drawable.cloneflix_ic_check else R.drawable.cloneflix_ic_plus
                                    ),
                                    contentDescription = if (inMyListState) "In My List" else "Add to My List",
                                    onClick = {
                                        inMyListState = !inMyListState
                                        onAddToListClick()
                                    },
                                    interactionSource = inMyListInteractionSource,
                                    enabled = false
                                )

                                CloneflixCircleActionButton(
                                    icon = painterResource(id = R.drawable.cloneflix_ic_thumb_up),
                                    contentDescription = "Like",
                                    onClick = onLikeClick,
                                    interactionSource = likeInteractionSource,
                                    enabled = false
                                )
                            }
                        }

                        if (hasTrailers) {
                            CloneflixHeroTrailerButton(
                                text = "Play Trailer",
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
                                        color = PrimaryWhite,
                                        style = typography.regularBody
                                    )
                                }

                                if (!seasonsCount.isNullOrBlank()) {
                                    Text(
                                        text = seasonsCount,
                                        color = PrimaryWhite,
                                        style = typography.regularBody
                                    )
                                }

                                if (!quality.isNullOrBlank()) {
                                    CloneflixVideoQualityBadge(quality = quality)
                                }
                            }
                        }

                        if (!maturityRating.isNullOrBlank() || !advisories.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(dimens.spacingS)
                            ) {
                                if (!maturityRating.isNullOrBlank()) {
                                    CloneflixMaturityRating(rating = maturityRating)
                                }
                                if (!advisories.isNullOrBlank()) {
                                    Text(
                                        text = advisories,
                                        color = Grey100,
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
                                        .clip(RoundedCornerShape(CloneflixTokens.RadiusCard))
                                        .background(PrimaryRed)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "TOP 10",
                                        style = typography.regularCaption2,
                                        color = PrimaryWhite,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Text(
                                    text = top10RankText,
                                    style = typography.mediumBody,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryWhite
                                )
                            }
                        }

                        if (synopsis.isNotBlank()) {
                            val synopsisInteractionSource = remember { MutableInteractionSource() }
                            val isSynopsisFocused by synopsisInteractionSource.collectIsFocusedAsState()

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(CloneflixTokens.RadiusCard))
                                    .background(if (isSynopsisFocused) Grey800.copy(alpha = 0.5f) else Color.Transparent)
                                    .then(
                                        if (isSynopsisFocused) {
                                            Modifier.border(
                                                BorderStroke(dimens.borderFocus, PrimaryWhite),
                                                RoundedCornerShape(CloneflixTokens.RadiusCard)
                                            )
                                        } else Modifier
                                    )
                                    .focusable(interactionSource = synopsisInteractionSource)
                                    .padding(dimens.spacingS)
                            ) {
                                Text(
                                    text = synopsis,
                                    style = typography.regularBody,
                                    color = if (isSynopsisFocused) PrimaryWhite else Grey50,
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
                                        text = "Cast",
                                        style = typography.regularCaption2,
                                        color = Grey200
                                    )
                                    Text(
                                        text = castList.joinToString(", "),
                                        style = typography.regularCaption1,
                                        color = PrimaryWhite,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (genres.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Genres",
                                        style = typography.regularCaption2,
                                        color = Grey200
                                    )
                                    Text(
                                        text = genres.joinToString(", "),
                                        style = typography.regularCaption1,
                                        color = PrimaryWhite
                                    )
                                }
                            }

                            if (moodTags.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "This show is",
                                        style = typography.regularCaption2,
                                        color = Grey200
                                    )
                                    Text(
                                        text = moodTags.joinToString(", "),
                                        style = typography.regularCaption1,
                                        color = PrimaryWhite
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
                            text = "Episodes",
                            style = typography.boldTitle2,
                            fontSize = 24.sp,
                            color = PrimaryWhite
                        )

                        if (seasonOptions.size > 1) {
                            CloneflixDropdown(
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
                    CloneflixDynamicEpisodeRow(
                        episode = ep,
                        onClick = onEpClick,
                        onDownloadClick = onEpDownload,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.spacing2Xl, vertical = 6.dp)
                    )
                }
            }

            if (chunkedRecommendations.isNotEmpty()) {
                item(key = "recommendations_header", contentType = "recommendations_header") {
                    Text(
                        text = "More Like This",
                        style = typography.boldTitle2,
                        fontSize = 22.sp,
                        color = PrimaryWhite,
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
                    CloneflixRecommendationRowView(
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
                            text = "Trailers & More",
                            style = typography.boldTitle2,
                            fontSize = 22.sp,
                            color = PrimaryWhite
                        )

                        Spacer(modifier = Modifier.height(dimens.spacingL))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dimens.spacingL)
                        ) {
                            trailersList.forEach { trailer ->
                                Box(modifier = Modifier.weight(1f)) {
                                    CloneflixTrailerItemCard(
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
                    val aboutBorder = if (isAboutFocused) BorderStroke(dimens.borderFocus, PrimaryWhite) else BorderStroke(1.dp, Grey600)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.spacing2Xl)
                            .padding(top = dimens.spacing3Xl, bottom = dimens.spacing3Xl)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(CloneflixTokens.RadiusCardMedium))
                                .background(if (isAboutFocused) Grey750 else Grey850)
                                .border(aboutBorder, RoundedCornerShape(CloneflixTokens.RadiusCardMedium))
                                .focusable(interactionSource = aboutInteractionSource)
                                .padding(dimens.spacing2Xl),
                            verticalArrangement = Arrangement.spacedBy(dimens.spacingM)
                        ) {
                            Text(
                                text = "About $title",
                                style = typography.boldTitle2,
                                fontSize = 20.sp,
                                color = PrimaryWhite
                            )

                            Spacer(modifier = Modifier.height(dimens.spacingXs))

                            if (!creator.isNullOrBlank()) {
                                CloneflixAboutMetadataRow(label = "Creator", value = creator)
                            }
                            if (castList.isNotEmpty()) {
                                CloneflixAboutMetadataRow(label = "Cast", value = castList.joinToString(", "))
                            }
                            if (writers.isNotEmpty()) {
                                CloneflixAboutMetadataRow(label = "Writers", value = writers.joinToString(", "))
                            }
                            if (genres.isNotEmpty()) {
                                CloneflixAboutMetadataRow(label = "Genres", value = genres.joinToString(", "))
                            }
                            if (moodTags.isNotEmpty()) {
                                CloneflixAboutMetadataRow(label = "This show is", value = moodTags.joinToString(", "))
                            }
                            if (!maturityRating.isNullOrBlank()) {
                                val ratingDescription = if (!advisories.isNullOrBlank()) {
                                    "$maturityRating Recommended for ages 16 and up. Contains $advisories."
                                } else {
                                    maturityRating
                                }
                                CloneflixAboutMetadataRow(
                                    label = "Maturity Rating",
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

@Composable
fun CloneflixDynamicEpisodeRow(
    episode: ResultEpisode,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens

    val scaleState = animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = CloneflixTokens.FastFocusAnimationSpec,
        label = "episodeScale"
    )

    val background = if (isFocused) Grey800 else Grey850
    val border = if (isFocused) BorderStroke(dimens.borderFocus, PrimaryWhite) else null
    val progress = remember(episode) { episode.getWatchProgress() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isFocused) 10f else 0f)
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
            }
            .clip(CloneflixTokens.ShapeCardMedium)
            .background(background)
            .then(if (border != null) Modifier.border(border, CloneflixTokens.ShapeCardMedium) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
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
                color = Grey200,
                modifier = Modifier.width(32.dp)
            )

            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(90.dp)
                    .clip(CloneflixTokens.ShapeCardSmall)
                    .background(Grey700)
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
                        painter = painterResource(id = R.drawable.cloneflix_ic_play),
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
                            .background(Grey800)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(PrimaryRed)
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
                        color = PrimaryWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (episode.runTime != null) "${episode.runTime}m" else "",
                        style = typography.regularCaption1,
                        color = Grey200
                    )
                }

                if (!episode.description.isNullOrBlank()) {
                    Text(
                        text = episode.description,
                        style = typography.regularCaption1,
                        color = Grey100,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }
            }

            CloneflixCircleActionButton(
                icon = painterResource(id = R.drawable.netflix_download),
                contentDescription = "Download Episode ${episode.episode}",
                onClick = onDownloadClick,
                modifier = Modifier.size(36.dp)
            )
        }
}

@Composable
fun CloneflixTrailerItemCard(
    trailer: CloneflixTrailerData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens

    val scaleState = animateFloatAsState(
        targetValue = if (isFocused) CloneflixTokens.FOCUS_SCALE_FACTOR else 1f,
        animationSpec = CloneflixTokens.FastFocusAnimationSpec,
        label = "trailerScale"
    )

    val border = if (isFocused) BorderStroke(dimens.borderFocus, PrimaryWhite) else BorderStroke(dimens.borderSubtle, Grey700)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isFocused) 10f else 0f)
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
            }
            .clip(CloneflixTokens.ShapeCardMedium)
            .background(Grey850)
            .border(border, CloneflixTokens.ShapeCardMedium)
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
                .background(Grey700)
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
                    painter = painterResource(id = R.drawable.cloneflix_ic_play),
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
                color = PrimaryWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = trailer.runtime,
                style = typography.regularCaption2,
                color = Grey200
            )
        }
    }
}

@Composable
fun CloneflixAboutMetadataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val typography = CloneflixTheme.typography

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            style = typography.regularCaption1,
            color = Grey200,
            modifier = Modifier.width(130.dp)
        )
        Text(
            text = value,
            style = typography.regularCaption1,
            color = PrimaryWhite,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun CloneflixRecommendationRowView(
    row: CloneflixRecommendationRow,
    onCardClick: (CloneflixMovieCardItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = CloneflixTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacing2Xl, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
    ) {
        row.items.forEach { cardItem ->
            Box(modifier = Modifier.weight(1f)) {
                CloneflixMovieCard(
                    title = cardItem.title,
                    type = CloneflixMovieCardType.POSTER,
                    size = CloneflixMovieCardSize.MEDIUM,
                    badge = cardItem.badge,
                    runtime = cardItem.runtime,
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
private fun CloneflixMovieDetailsComposeScreenPreview() {
    CloneflixTheme {
        CloneflixMovieDetailsComposeScreen(
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
