package com.lagradost.cloudstream3.ui.revamp.compose.screens

import android.widget.ImageView
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.viewinterop.AndroidView
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.getWatchProgress
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixBadgeType
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCircleActionButton
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixDropdown
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeroPlayButton
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
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import kotlinx.coroutines.launch

/**
 * Data representation for trailer / extra clips in Figma Node 121:4925.
 */
data class CloneflixTrailerData(
    val title: String,
    val runtime: String,
    val rawTrailer: Any? = null
)

/**
 * Movie Details Page / Details Only Screen based on Figma Node 121:4892.
 * Fully dynamic and optimized for Google TV / Android TV D-Pad traversal and Mobile touch.
 * Only renders sections and elements where real data is available; hides dummy/unsupported UI.
 */
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
    val scrollState = rememberScrollState()

    val playButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        scrollState.scrollTo(0)
        playButtonFocusRequester.requestFocus()
    }

    var inMyListState by remember(isInWatchList) { mutableStateOf(isInWatchList) }

    val seasonOptions = remember(dynamicSeasons) {
        dynamicSeasons ?: emptyList()
    }

    var selectedSeasonText by remember(selectedSeasonIndex, seasonOptions) {
        mutableStateOf(seasonOptions.getOrElse(selectedSeasonIndex) { seasonOptions.firstOrNull() ?: "" })
    }

    // Keep selectedSeasonText in sync when selectedSeasonIndex prop updates from ViewModel
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

    val trailersList = dynamicTrailers ?: emptyList()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
    ) {
        // ==========================================
        // 1. HERO BANNER SECTION (Figma 121:4893 - 60% Screen Height Crop)
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .clipToBounds()
                .background(colors.background)
        ) {
            // Live Backdrop Image filling full banner width & height
            if (!backdropUrl.isNullOrBlank()) {
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            adjustViewBounds = false
                            clipToOutline = true
                        }
                    },
                    update = { imageView ->
                        imageView.loadImage(backdropUrl)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                )
            }

            // Bottom Hero Gradient Overlay (fades smoothly down into the page background)
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

            // Top-Right: Logo + Close Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingS),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(dimens.spacing2Xl)
            ) {
                CloneflixLogoView(
                    variant = CloneflixLogoVariant.WORDMARK_MEDIUM,
                    text = providerName
                )
                CloneflixCircleActionButton(
                    icon = painterResource(id = R.drawable.cloneflix_ic_close),
                    contentDescription = "Close",
                    onClick = {
                        onCloseClick()
                    }
                )
            }

            // Title, Wordmark, and Action Buttons aligned at the bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = dimens.spacing2Xl, vertical = dimens.spacingL)
            ) {
                // Movie Title Logo or Title Text
                if (!logoUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .height(68.dp)
                            .width(240.dp)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                ImageView(ctx).apply {
                                    scaleType = ImageView.ScaleType.FIT_START
                                }
                            },
                            update = { imageView ->
                                imageView.loadImage(logoUrl)
                            },
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

                // Primary Play Button (Separate dedicated section)
                CloneflixHeroPlayButton(
                    onClick = onPlayClick,
                    text = "Play",
                    modifier = Modifier
                        .focusRequester(playButtonFocusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused && scrollState.value > 0) {
                                // Keep Hero banner fully visible when play button is focused
                            }
                        }
                )

                Spacer(modifier = Modifier.height(dimens.spacingM))

                // Secondary Action Buttons Row (Add to List, Like, Trailer) below Play button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
                ) {
                    // Add to My List (+)
                    CloneflixCircleActionButton(
                        icon = painterResource(
                            id = if (inMyListState) R.drawable.cloneflix_ic_check else R.drawable.cloneflix_ic_plus
                        ),
                        contentDescription = if (inMyListState) "In My List" else "Add to My List",
                        onClick = {
                            inMyListState = !inMyListState
                            onAddToListClick()
                        }
                    )

                    // Thumbs Up / Like
                    CloneflixCircleActionButton(
                        icon = painterResource(id = R.drawable.cloneflix_ic_thumb_up),
                        contentDescription = "Like",
                        onClick = onLikeClick
                    )

                    // Trailer Toggle (shown only if trailer links available)
                    if (hasTrailers) {
                        CloneflixCircleActionButton(
                            icon = painterResource(id = R.drawable.cloneflix_ic_play),
                            contentDescription = "Trailer",
                            onClick = onTrailerClick
                        )
                    }
                }
            }
        }

        // ==========================================
        // CONTENT SECTIONS WRAPPER WITH SAFE MARGINS
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spacing2Xl, vertical = dimens.spacingL)
        ) {
            // ==========================================
            // 2. MOVIE INFO & METADATA SECTION (Figma 121:4894)
            // ==========================================
            val hasRightColumn = castList.isNotEmpty() || genres.isNotEmpty() || moodTags.isNotEmpty()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing2Xl)
            ) {
                // Left Column: Badges, Top 10, Synopsis
                Column(
                    modifier = Modifier
                        .weight(if (hasRightColumn) 0.65f else 1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingM)
                ) {
                    // Line 1: Match Score, Year, Duration, Quality (rendered only if available)
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

                    // Line 2: Rating & Content Advisories (rendered only if available)
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

                    // Line 3: Top 10 Rank Badge (rendered only if available)
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

                    // Line 4: Synopsis
                    if (synopsis.isNotBlank()) {
                        Text(
                            text = synopsis,
                            style = typography.regularBody,
                            color = Grey50,
                            lineHeight = 22.sp
                        )
                    }
                }

                // Right Column: Cast, Genres, Mood Tags (rendered only if metadata exists)
                if (hasRightColumn) {
                    Column(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(dimens.spacingM)
                    ) {
                        // Cast Row
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

                        // Genres Row
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

                        // This show is (Moods)
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

            if (episodesToDisplay.isNotEmpty() || recommendationCards.isNotEmpty() || trailersList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(dimens.spacing3Xl))
            }

            // ==========================================
            // 3. EPISODES & SEASONS SECTION (Figma 121:4895)
            // ==========================================
            if (episodesToDisplay.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Episodes",
                        style = typography.boldTitle2,
                        fontSize = 24.sp,
                        color = PrimaryWhite
                    )

                    // Season Selection Dropdown
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

                Spacer(modifier = Modifier.height(dimens.spacingL))

                // Episodes List
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingL)
                ) {
                    episodesToDisplay.forEach { ep ->
                        CloneflixDynamicEpisodeRow(
                            episode = ep,
                            onClick = {
                                if (onEpisodeClick != null) onEpisodeClick(ep)
                                else showToast("Playing ${ep.headerName}: ${ep.name}")
                            },
                            onDownloadClick = {
                                if (onEpisodeDownloadClick != null) onEpisodeDownloadClick(ep)
                                else showToast("Downloading Episode ${ep.episode}")
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(dimens.spacing3Xl))
            }

            // ==========================================
            // 4. MORE LIKE THIS SECTION (Figma 121:4909 - Poster Layout)
            // ==========================================
            if (recommendationCards.isNotEmpty()) {
                Text(
                    text = "More Like This",
                    style = typography.boldTitle2,
                    fontSize = 22.sp,
                    color = PrimaryWhite
                )

                Spacer(modifier = Modifier.height(dimens.spacingL))

                // Responsive Poster Grid (6 columns per row for TV immersion)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingL)
                ) {
                    val chunkedRecommendations = recommendationCards.chunked(6)
                    chunkedRecommendations.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
                        ) {
                            rowItems.forEach { cardItem ->
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
                                        onClick = {
                                            val rec = dynamicRecommendations?.find { it.name == cardItem.title }
                                            if (rec != null && onRecommendationClick != null) {
                                                onRecommendationClick(rec)
                                            } else {
                                                showToast("Opened recommendation: ${cardItem.title}")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            if (rowItems.size < 6) {
                                repeat(6 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(dimens.spacing3Xl))
            }

            // ==========================================
            // 5. TRAILERS & MORE SECTION (Figma 121:4925)
            // ==========================================
            if (trailersList.isNotEmpty()) {
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

                Spacer(modifier = Modifier.height(dimens.spacing3Xl))
            }

            // ==========================================
            // 6. ABOUT SECTION (Figma 121:4932 - Focusable Container for D-Pad)
            // ==========================================
            val hasAboutContent = !creator.isNullOrBlank() || castList.isNotEmpty() ||
                    writers.isNotEmpty() || genres.isNotEmpty() || moodTags.isNotEmpty() ||
                    !maturityRating.isNullOrBlank()

            if (hasAboutContent) {
                val aboutInteractionSource = remember { MutableInteractionSource() }
                val isAboutFocused by aboutInteractionSource.collectIsFocusedAsState()
                val aboutBorder = if (isAboutFocused) BorderStroke(dimens.borderFocus, PrimaryWhite) else BorderStroke(1.dp, Grey600)

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

                Spacer(modifier = Modifier.height(dimens.spacing3Xl))
            }
        }
    }
}

/**
 * Dynamic Episode item row inside the Details Page (Figma Node 121:4895).
 */
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

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        label = "episodeScale"
    )

    val background = if (isFocused) Grey800 else Grey850
    val border = if (isFocused) BorderStroke(dimens.borderFocus, PrimaryWhite) else null
    val progress = remember(episode) { episode.getWatchProgress() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(CloneflixTokens.RadiusCardMedium))
            .background(background)
            .then(if (border != null) Modifier.border(border, RoundedCornerShape(CloneflixTokens.RadiusCardMedium)) else Modifier)
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
        // Episode Number Index
        Text(
            text = episode.episode.toString(),
            style = typography.boldTitle1,
            fontSize = 24.sp,
            color = Grey200,
            modifier = Modifier.width(32.dp)
        )

        // 16:9 Episode Thumbnail with Play Overlay & Progress Bar
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(CloneflixTokens.RadiusCard))
                .background(Grey700)
        ) {
            if (!episode.poster.isNullOrBlank()) {
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                    },
                    update = { imageView ->
                        imageView.loadImage(episode.poster)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Play Icon Overlay
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

            // Watched Progress Bar (if watched partially)
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

        // Title, Duration, and Description
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

        // Download Action Button
        CloneflixCircleActionButton(
            icon = painterResource(id = R.drawable.netflix_download),
            contentDescription = "Download Episode ${episode.episode}",
            onClick = onDownloadClick,
            modifier = Modifier.size(36.dp)
        )
    }
}

/**
 * Trailer item card in Trailers & More (Figma Node 121:4925).
 */
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

    val scale by animateFloatAsState(
        targetValue = if (isFocused) CloneflixTokens.FOCUS_SCALE_FACTOR else 1f,
        label = "trailerScale"
    )

    val border = if (isFocused) BorderStroke(dimens.borderFocus, PrimaryWhite) else BorderStroke(dimens.borderSubtle, Grey700)

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(CloneflixTokens.RadiusCardMedium))
            .background(Grey850)
            .border(border, RoundedCornerShape(CloneflixTokens.RadiusCardMedium))
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
            // Play overlay
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

/**
 * Metadata row inside the About section (Figma Node 121:4932).
 */
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

@Preview(
    name = "TV 1080p Landscape",
    device = "spec:width=1920dp,height=1080dp,dpi=320,orientation=landscape",
    showBackground = true,
    backgroundColor = 0xFF141414
)
@Preview(
    name = "TV 720p Landscape",
    device = "spec:width=1280dp,height=720dp,dpi=213,orientation=landscape",
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
