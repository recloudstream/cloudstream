package com.lagradost.cloudstream3.ui.result.compose

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
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.ui.result.compose.components.CircleActionButton
import com.lagradost.cloudstream3.ui.result.compose.components.DetailsLogoVariant
import com.lagradost.cloudstream3.ui.result.compose.components.DetailsLogoView
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
import com.lagradost.cloudstream3.ui.result.compose.theme.GreenAccent
import com.lagradost.cloudstream3.ui.result.compose.theme.Grey100
import com.lagradost.cloudstream3.ui.result.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.result.compose.theme.Grey50
import com.lagradost.cloudstream3.ui.result.compose.theme.Grey500
import com.lagradost.cloudstream3.ui.result.compose.theme.Grey600
import com.lagradost.cloudstream3.ui.result.compose.theme.Grey700
import com.lagradost.cloudstream3.ui.result.compose.theme.Grey750
import com.lagradost.cloudstream3.ui.result.compose.theme.Grey800
import com.lagradost.cloudstream3.ui.result.compose.theme.Grey850
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme
import com.lagradost.cloudstream3.ui.result.compose.theme.PrimaryBlack
import com.lagradost.cloudstream3.ui.result.compose.theme.PrimaryRed
import com.lagradost.cloudstream3.ui.result.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.result.compose.theme.TransparentBlack60
import com.lagradost.cloudstream3.ui.result.compose.theme.getRatingScoreColor
import com.lagradost.cloudstream3.ui.result.getWatchProgress
import kotlinx.coroutines.launch

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

    val colors = MovieDetailsTheme.colors
    val typography = MovieDetailsTheme.typography
    val dimens = MovieDetailsTheme.dimens
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

    val episodesToDisplay = dynamicEpisodes ?: emptyList()

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
            item(key = "hero_banner") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                        .clipToBounds()
                ) {
                    val imageUrl = backdropUrl ?: posterUrl
                    if (!imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = 0.85f }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        PrimaryBlack.copy(alpha = 0.3f),
                                        PrimaryBlack.copy(alpha = 0.8f),
                                        colors.background
                                    ),
                                    startY = 0f
                                )
                            )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        PrimaryBlack.copy(alpha = 0.7f),
                                        Color.Transparent
                                    ),
                                    startX = 0f,
                                    endX = 800f
                                )
                            )
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = dimens.spacing2Xl, end = dimens.spacing2Xl),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
                    ) {
                        CircleActionButton(
                            icon = painterResource(id = R.drawable.ic_baseline_close_24),
                            contentDescription = "Close",
                            onClick = onCloseClick
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(
                                start = dimens.spacing3Xl,
                                bottom = dimens.spacing2Xl,
                                end = dimens.spacing3Xl
                            )
                            .fillMaxWidth(0.85f)
                    ) {
                        if (!providerName.isNullOrBlank()) {
                            Text(
                                text = providerName.uppercase(),
                                style = typography.boldTitle2,
                                color = PrimaryRed,
                                letterSpacing = 2.sp,
                                modifier = Modifier.padding(bottom = dimens.spacingS)
                            )
                        }

                        DetailsLogoView(
                            logoUrl = logoUrl,
                            titleFallback = title,
                            variant = DetailsLogoVariant.FULL_COLOR,
                            height = 64.dp
                        )

                        Spacer(modifier = Modifier.height(dimens.spacingL))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(dimens.spacingM),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HeroPlayButton(
                                onClick = onPlayClick,
                                text = "Play",
                                interactionSource = playInteractionSource,
                                modifier = Modifier.focusRequester(playButtonFocusRequester)
                            )

                            if (hasTrailers) {
                                HeroTrailerButton(
                                    onClick = onTrailerClick,
                                    text = "Play Trailer",
                                    interactionSource = trailerInteractionSource
                                )
                            }

                            CircleActionButton(
                                icon = painterResource(
                                    id = if (inMyListState) R.drawable.ic_baseline_check_24 else R.drawable.ic_baseline_add_24
                                ),
                                contentDescription = if (inMyListState) "In My List" else "Add to My List",
                                onClick = {
                                    inMyListState = !inMyListState
                                    onAddToListClick()
                                },
                                interactionSource = inMyListInteractionSource
                            )

                            CircleActionButton(
                                icon = painterResource(id = R.drawable.ic_baseline_favorite_24),
                                contentDescription = "Favorite",
                                onClick = onLikeClick,
                                interactionSource = likeInteractionSource
                            )
                        }
                    }
                }
            }

            item(key = "meta_info_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.spacing3Xl)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimens.spacing4Xl)
                    ) {
                        Column(modifier = Modifier.weight(0.65f)) {
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
                                        color = Grey200,
                                        style = typography.regularBody
                                    )
                                }

                                if (!maturityRating.isNullOrBlank()) {
                                    MaturityRatingBadge(rating = maturityRating)
                                }

                                if (!seasonsCount.isNullOrBlank()) {
                                    Text(
                                        text = seasonsCount,
                                        color = Grey200,
                                        style = typography.regularBody
                                    )
                                }

                                if (!quality.isNullOrBlank()) {
                                    VideoQualityBadge(quality = quality)
                                }
                            }

                            if (!advisories.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(dimens.spacingS))
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(2.dp))
                                        .border(BorderStroke(1.dp, Grey700), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (!maturityRating.isNullOrBlank()) {
                                        Text(
                                            text = maturityRating,
                                            color = PrimaryWhite,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "•",
                                            color = Grey500,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Text(
                                        text = advisories,
                                        color = Grey200,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            if (!top10RankText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(dimens.spacingM))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingS)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(PrimaryRed),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "TOP\n10",
                                            color = PrimaryWhite,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black,
                                            lineHeight = 9.sp
                                        )
                                    }
                                    Text(
                                        text = top10RankText,
                                        color = PrimaryWhite,
                                        style = typography.boldTitle2,
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            if (synopsis.isNotBlank()) {
                                Spacer(modifier = Modifier.height(dimens.spacingL))
                                Text(
                                    text = synopsis,
                                    style = typography.regularBody,
                                    color = PrimaryWhite,
                                    lineHeight = 22.sp
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(0.35f),
                            verticalArrangement = Arrangement.spacedBy(dimens.spacingS)
                        ) {
                            if (castList.isNotEmpty()) {
                                Row {
                                    Text(
                                        text = "Cast: ",
                                        style = typography.regularSmallBody,
                                        color = Grey200
                                    )
                                    Text(
                                        text = castList.take(4).joinToString(", "),
                                        style = typography.regularSmallBody,
                                        color = PrimaryWhite,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (genres.isNotEmpty()) {
                                Row {
                                    Text(
                                        text = "Genres: ",
                                        style = typography.regularSmallBody,
                                        color = Grey200
                                    )
                                    Text(
                                        text = genres.joinToString(", "),
                                        style = typography.regularSmallBody,
                                        color = PrimaryWhite,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (moodTags.isNotEmpty()) {
                                Row {
                                    Text(
                                        text = "This show is: ",
                                        style = typography.regularSmallBody,
                                        color = Grey200
                                    )
                                    Text(
                                        text = moodTags.joinToString(", "),
                                        style = typography.regularSmallBody,
                                        color = PrimaryWhite,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (!creator.isNullOrBlank()) {
                                Row {
                                    Text(
                                        text = "Creator: ",
                                        style = typography.regularSmallBody,
                                        color = Grey200
                                    )
                                    Text(
                                        text = creator,
                                        style = typography.regularSmallBody,
                                        color = PrimaryWhite
                                    )
                                }
                            }

                            if (writers.isNotEmpty()) {
                                Row {
                                    Text(
                                        text = "Writers: ",
                                        style = typography.regularSmallBody,
                                        color = Grey200
                                    )
                                    Text(
                                        text = writers.joinToString(", "),
                                        style = typography.regularSmallBody,
                                        color = PrimaryWhite,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (episodesToDisplay.isNotEmpty() || seasonOptions.isNotEmpty()) {
                item(key = "episodes_section_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = dimens.spacing3Xl, end = dimens.spacing3Xl, top = dimens.spacing3Xl)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Episodes",
                                style = typography.boldTitle1,
                                fontSize = 24.sp,
                                color = PrimaryWhite
                            )

                            if (seasonOptions.size > 1) {
                                SeasonDropdown(
                                    options = seasonOptions,
                                    selectedOption = selectedSeasonText,
                                    onOptionSelected = { chosen ->
                                        selectedSeasonText = chosen
                                        val idx = seasonOptions.indexOf(chosen)
                                        if (idx >= 0) {
                                            onSeasonSelect?.invoke(idx)
                                        }
                                    },
                                    width = 160.dp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(dimens.spacingL))
                    }
                }

                itemsIndexed(
                    items = episodesToDisplay,
                    key = { _, ep -> "ep_${ep.id}_${ep.episode}" }
                ) { index, ep ->
                    EpisodeRowItem(
                        episode = ep,
                        index = index + 1,
                        onEpisodeClick = { onEpisodeClick?.invoke(ep) },
                        onDownloadClick = { onEpisodeDownloadClick?.invoke(ep) }
                    )
                }
            }

            if (trailersList.isNotEmpty()) {
                item(key = "trailers_section_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = dimens.spacing3Xl,
                                end = dimens.spacing3Xl,
                                top = dimens.spacing3Xl,
                                bottom = dimens.spacingL
                            )
                    ) {
                        Text(
                            text = "Trailers & More",
                            style = typography.boldTitle1,
                            fontSize = 24.sp,
                            color = PrimaryWhite
                        )
                    }
                }

                items(
                    items = trailersList,
                    key = { "trailer_${it.title}" }
                ) { trailer ->
                    TrailerRowItem(trailer = trailer)
                }
            }

            if (chunkedRecommendations.isNotEmpty()) {
                item(key = "recommendations_section_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = dimens.spacing3Xl,
                                end = dimens.spacing3Xl,
                                top = dimens.spacing3Xl,
                                bottom = dimens.spacingL
                            )
                    ) {
                        Text(
                            text = "More Like This",
                            style = typography.boldTitle1,
                            fontSize = 24.sp,
                            color = PrimaryWhite
                        )
                    }
                }

                items(
                    items = chunkedRecommendations,
                    key = { "rec_row_${it.rowIndex}" }
                ) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.spacing3Xl, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        row.items.forEach { cardItem ->
                            val rawRec = cardItem.rawItem as? SearchResponse
                                ?: dynamicRecommendations?.firstOrNull { it.name == cardItem.title }

                            MovieDetailsMovieCard(
                                title = cardItem.title,
                                type = MovieCardType.POSTER,
                                size = MovieCardSize.MEDIUM,
                                posterUrl = cardItem.posterUrl,
                                backdropUrl = cardItem.backdropUrl,
                                showBottomTitle = true,
                                onClick = {
                                    if (rawRec != null) {
                                        onRecommendationClick?.invoke(rawRec)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            item(key = "about_details_card") {
                val hasAboutData = title.isNotBlank() || castList.isNotEmpty() || genres.isNotEmpty() || moodTags.isNotEmpty()
                if (hasAboutData) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = dimens.spacing3Xl,
                                end = dimens.spacing3Xl,
                                top = dimens.spacing4Xl,
                                bottom = dimens.spacing4Xl
                            )
                    ) {
                        val aboutInteractionSource = remember { MutableInteractionSource() }
                        val isAboutFocused by aboutInteractionSource.collectIsFocusedAsState()

                        val border = if (isAboutFocused) {
                            BorderStroke(dimens.borderFocus, PrimaryWhite)
                        } else {
                            BorderStroke(dimens.borderSubtle, Grey750)
                        }

                        val scale by animateFloatAsState(
                            targetValue = if (isAboutFocused) 1.01f else 1f,
                            animationSpec = MovieDetailsTokens.FastFocusAnimationSpec,
                            label = "aboutCardScale"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(scale)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Grey850)
                                .border(border, RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = aboutInteractionSource,
                                    indication = null,
                                    onClick = {}
                                )
                                .focusable(interactionSource = aboutInteractionSource)
                                .padding(dimens.spacing2Xl)
                        ) {
                            Column {
                                Text(
                                    text = "About $title",
                                    style = typography.boldTitle2,
                                    fontSize = 20.sp,
                                    color = PrimaryWhite
                                )

                                Spacer(modifier = Modifier.height(dimens.spacingL))

                                if (!creator.isNullOrBlank()) {
                                    Text(
                                        text = "Creator: $creator",
                                        style = typography.regularSmallBody,
                                        color = Grey200,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }

                                if (castList.isNotEmpty()) {
                                    Row(modifier = Modifier.padding(bottom = 6.dp)) {
                                        Text(
                                            text = "Cast: ",
                                            style = typography.regularSmallBody,
                                            color = Grey200
                                        )
                                        Text(
                                            text = castList.joinToString(", "),
                                            style = typography.regularSmallBody,
                                            color = PrimaryWhite
                                        )
                                    }
                                }

                                if (genres.isNotEmpty()) {
                                    Row(modifier = Modifier.padding(bottom = 6.dp)) {
                                        Text(
                                            text = "Genres: ",
                                            style = typography.regularSmallBody,
                                            color = Grey200
                                        )
                                        Text(
                                            text = genres.joinToString(", "),
                                            style = typography.regularSmallBody,
                                            color = PrimaryWhite
                                        )
                                    }
                                }

                                if (moodTags.isNotEmpty()) {
                                    Row(modifier = Modifier.padding(bottom = 6.dp)) {
                                        Text(
                                            text = "This show is: ",
                                            style = typography.regularSmallBody,
                                            color = Grey200
                                        )
                                        Text(
                                            text = moodTags.joinToString(", "),
                                            style = typography.regularSmallBody,
                                            color = PrimaryWhite
                                        )
                                    }
                                }

                                if (!maturityRating.isNullOrBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = "Maturity Rating: ",
                                            style = typography.regularSmallBody,
                                            color = Grey200
                                        )
                                        MaturityRatingBadge(rating = maturityRating)
                                        if (!advisories.isNullOrBlank()) {
                                            Text(
                                                text = advisories,
                                                style = typography.regularSmallBody,
                                                color = Grey100
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRowItem(
    episode: ResultEpisode,
    index: Int,
    onEpisodeClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = MovieDetailsTheme.typography
    val dimens = MovieDetailsTheme.dimens

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.015f else 1f,
        animationSpec = MovieDetailsTokens.FastFocusAnimationSpec,
        label = "epRowScale"
    )

    val background = when {
        isFocused -> Grey750
        else -> Color.Transparent
    }

    val border = if (isFocused) BorderStroke(dimens.borderFocus, PrimaryWhite) else null

    val progress = remember(episode) {
        val posDur = DataStoreHelper.getViewPos(episode.id)
        if (posDur != null && posDur.duration > 0) {
            posDur.position.toFloat() / posDur.duration.toFloat()
        } else null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacing3Xl, vertical = 4.dp)
            .scale(scale)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .then(if (border != null) Modifier.border(border, RoundedCornerShape(6.dp)) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onEpisodeClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(dimens.spacingL),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${episode.episode}",
            style = typography.boldTitle1,
            fontSize = 22.sp,
            color = if (isFocused) PrimaryWhite else Grey200,
            modifier = Modifier.width(36.dp)
        )

        Box(
            modifier = Modifier
                .width(130.dp)
                .height(74.dp)
                .clip(MovieDetailsTokens.ShapeCardSmall)
                .background(Grey800)
        ) {
            val epPoster = episode.poster
            if (!epPoster.isNullOrBlank()) {
                AsyncImage(
                    model = epPoster,
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TransparentBlack60),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isFocused) PrimaryRed else PrimaryWhite.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_play_arrow_24),
                        contentDescription = "Play Episode",
                        tint = if (isFocused) PrimaryWhite else PrimaryBlack,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (progress != null && progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(Grey700)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(PrimaryRed)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(dimens.spacingL))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = episode.name ?: "Episode ${episode.episode}",
                    style = typography.mediumBody,
                    color = PrimaryWhite,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (episode.runTime != null && episode.runTime > 0) {
                    Text(
                        text = "${episode.runTime}m",
                        style = typography.regularCaption1,
                        color = Grey200,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            if (!episode.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = episode.description,
                    style = typography.regularSmallBody,
                    color = Grey100,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(dimens.spacingL))

        CircleActionButton(
            icon = painterResource(id = R.drawable.baseline_downloading_24),
            contentDescription = "Download Episode ${episode.episode}",
            onClick = onDownloadClick,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun TrailerRowItem(
    trailer: MovieTrailerData,
    modifier: Modifier = Modifier
) {
    val typography = MovieDetailsTheme.typography
    val dimens = MovieDetailsTheme.dimens

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.015f else 1f,
        animationSpec = MovieDetailsTokens.FastFocusAnimationSpec,
        label = "trailerRowScale"
    )

    val background = if (isFocused) Grey750 else Color.Transparent
    val border = if (isFocused) BorderStroke(dimens.borderFocus, PrimaryWhite) else null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacing3Xl, vertical = 4.dp)
            .scale(scale)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .then(if (border != null) Modifier.border(border, RoundedCornerShape(6.dp)) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = {}
            )
            .focusable(interactionSource = interactionSource)
            .padding(dimens.spacingL),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(130.dp)
                .height(74.dp)
                .clip(MovieDetailsTokens.ShapeCardSmall)
                .background(Grey800),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isFocused) PrimaryRed else PrimaryWhite.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_play_arrow_24),
                    contentDescription = "Play Trailer",
                    tint = if (isFocused) PrimaryWhite else PrimaryBlack,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(dimens.spacingL))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trailer.title,
                style = typography.mediumBody,
                color = PrimaryWhite,
                fontWeight = FontWeight.SemiBold
            )

            if (trailer.runtime.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = trailer.runtime,
                    style = typography.regularCaption1,
                    color = Grey200
                )
            }
        }
    }
}

@Preview(name = "Phone Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.PHONE)
@Preview(name = "TV Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.TV_720p)
@Composable
private fun MovieDetailsScreenPreview() {
    MovieDetailsTheme {
        MovieDetailsComposeScreen(
            title = "House of Ninjas",
            providerName = "CloudStream",
            matchScore = "98% Match",
            releaseYear = "2024",
            seasonsCount = "1 Season",
            quality = "UltraHD 4K",
            maturityRating = "TV-MA",
            advisories = "violence, language",
            top10RankText = "#1 in TV Shows Today",
            synopsis = "Years after retiring from their formidable ninja lives, a dysfunctional family must return to shadowy missions to counteract a string of looming threats."
        )
    }
}
