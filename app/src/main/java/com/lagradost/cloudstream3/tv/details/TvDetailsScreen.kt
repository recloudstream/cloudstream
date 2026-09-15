package com.lagradost.cloudstream3.tv.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.tv.components.TvFocusScale
import com.lagradost.cloudstream3.tv.model.TvAvailabilityClassifier
import com.lagradost.cloudstream3.tv.model.TvContentRef
import com.lagradost.cloudstream3.tv.model.TvDetailsAction
import com.lagradost.cloudstream3.tv.model.TvDetailsContent
import com.lagradost.cloudstream3.tv.model.TvDetailsUiState
import com.lagradost.cloudstream3.tv.model.TvPlaybackRequest
import com.lagradost.cloudstream3.tv.model.isSeriesOrAnime
import com.lagradost.cloudstream3.tv.model.watchNowDisabledReason

/**
 * Cinematic Details screen. Loads via [TvDetailsViewModel] / TvDetailsRepository.
 * Movies: Watch Now → Activity → TvPlaybackBridge.
 * Series/Anime: season/episode selector → Play Episode → same bridge path.
 * Live/Torrent: explicit unsupported. Mock never plays.
 */
@Composable
fun TvDetailsScreen(
    ref: TvContentRef,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPlaybackRequest: (TvPlaybackRequest) -> Unit = {},
    viewModel: TvDetailsViewModel = viewModel(
        key = "tv-details:${ref.apiName}|${ref.url}",
    ),
) {
    val uiState by viewModel.state.collectAsState()

    LaunchedEffect(ref) {
        viewModel.onPlaybackRequest = onPlaybackRequest
        viewModel.bind(ref)
    }

    BackHandler(onBack = onBack)

    when (val state = uiState) {
        is TvDetailsUiState.Loading -> TvDetailsLoadingPane(
            titleHint = state.titleHint ?: ref.title.takeIf { it.isNotBlank() },
            onBack = onBack,
            modifier = modifier,
        )
        is TvDetailsUiState.Content -> TvDetailsContentPane(
            content = state,
            onAction = viewModel::onAction,
            onBack = onBack,
            modifier = modifier,
        )
        is TvDetailsUiState.Error -> {
            val status = TvAvailabilityClassifier.fromDetailsFailure(state.message)
            TvDetailsErrorPane(
                availabilityTitle = status.title,
                message = state.message,
                titleHint = state.titleHint ?: ref.title.takeIf { it.isNotBlank() },
                onRetry = { viewModel.onAction(TvDetailsAction.Retry) },
                onBack = onBack,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun TvDetailsContentPane(
    content: TvDetailsUiState.Content,
    onAction: (TvDetailsAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val details = content.details
    val context = LocalContext.current
    val primaryFocus = remember { FocusRequester() }
    val placeholder = ColorPainter(Color(0xFF1A1A1E))
    val imageUrl = details.backdropUrl?.takeIf { it.isNotBlank() }
        ?: details.posterUrl?.takeIf { it.isNotBlank() }
    val backdropRequest = ImageRequest.Builder(context)
        .data(imageUrl)
        .size(1280, 720)
        .crossfade(true)
        .httpHeaders(detailsHeaders(details))
        .build()
    val posterRequest = ImageRequest.Builder(context)
        .data(details.posterUrl?.takeIf { it.isNotBlank() })
        .size(400, 600)
        .crossfade(true)
        .httpHeaders(detailsHeaders(details))
        .build()

    var restoreEpisodeFocus by remember { mutableStateOf(false) }

    LaunchedEffect(details.url, details.variantLabel) {
        // Movies / unsupported: focus primary CTA. Series/Anime: focus Play Episode.
        runCatching { primaryFocus.requestFocus() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = backdropRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = placeholder,
            error = placeholder,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.92f),
                        0.55f to Color.Black.copy(alpha = 0.72f),
                        1f to Color.Black.copy(alpha = 0.45f),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.5f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 36.dp, end = 36.dp, top = 28.dp, bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Surface(
                modifier = Modifier
                    .width(180.dp)
                    .aspectRatio(2f / 3f),
                shape = RoundedCornerShape(12.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = Color(0xFF2A2A2E),
                ),
            ) {
                AsyncImage(
                    model = posterRequest,
                    contentDescription = details.title,
                    contentScale = ContentScale.Crop,
                    placeholder = placeholder,
                    error = placeholder,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .widthIn(max = 780.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = buildHeaderLabel(details),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = details.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildMetadataLine(details),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (details.genres.isNotEmpty()) {
                    Text(
                        text = details.genres.joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (details.synopsis.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = details.synopsis,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (details.actors.isNotEmpty()) {
                    Text(
                        text = "Cast: " + details.actors.take(8).joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val disabledReason = details.watchNowDisabledReason()
                    val playEnabled = disabledReason == null && when {
                        details.variantLabel == "Movie" -> true
                        details.isSeriesOrAnime() -> content.selectedEpisode?.isPlayable == true
                        else -> false
                    }
                    val ctaLabel = when {
                        details.comingSoon -> "Coming Soon"
                        details.isSeriesOrAnime() -> "Play Episode"
                        else -> "Watch Now"
                    }
                    Button(
                        onClick = {
                            if (details.isSeriesOrAnime()) {
                                onAction(TvDetailsAction.PlaySelectedEpisode)
                            } else {
                                onAction(TvDetailsAction.WatchNow)
                            }
                        },
                        enabled = playEnabled,
                        modifier = Modifier.focusRequester(primaryFocus),
                        scale = ButtonDefaults.scale(focusedScale = TvFocusScale.HeroButtonFocused),
                        glow = ButtonDefaults.glow(
                            focusedGlow = Glow(
                                elevationColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                elevation = 12.dp,
                            ),
                        ),
                    ) {
                        Text(ctaLabel)
                    }
                    Button(
                        onClick = onBack,
                        scale = ButtonDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
                    ) {
                        Text("Back")
                    }
                }
                Text(
                    text = details.watchNowDisabledReason()
                        ?: when {
                            details.isSeriesOrAnime() -> {
                                val ep = content.selectedEpisode
                                if (ep != null) {
                                    "Playing ${ep.titleLine} via existing GeneratorPlayer."
                                } else {
                                    "Select an episode, then Play Episode."
                                }
                            }
                            else -> "Watch Now starts existing CloudStream playback (GeneratorPlayer)."
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )

                if (details.hasEpisodeSelector) {
                    Spacer(Modifier.height(8.dp))
                    TvEpisodeSelector(
                        content = content,
                        onAction = onAction,
                        restoreEpisodeFocus = restoreEpisodeFocus,
                        onRestoreConsumed = { restoreEpisodeFocus = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvDetailsLoadingPane(
    titleHint: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { backFocus.requestFocus() }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = titleHint ?: "Loading details…",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Fetching via APIRepository.load…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onBack,
            modifier = Modifier.focusRequester(backFocus),
            scale = ButtonDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun TvDetailsErrorPane(
    availabilityTitle: String,
    message: String,
    titleHint: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(message) {
        runCatching { retryFocus.requestFocus() }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = availabilityTitle,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (!titleHint.isNullOrBlank()) {
            Text(
                text = titleHint,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = message + "\n\nRetry or Back. No silent swap to other titles.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onRetry,
                modifier = Modifier.focusRequester(retryFocus),
                scale = ButtonDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
            ) {
                Text("Retry")
            }
            Button(
                onClick = onBack,
                scale = ButtonDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
            ) {
                Text("Back")
            }
        }
    }
}

private fun detailsHeaders(details: TvDetailsContent): NetworkHeaders =
    NetworkHeaders.Builder().also { headers ->
        headers["User-Agent"] = USER_AGENT
        details.posterHeaders?.forEach { (k, v) -> headers[k] = v }
    }.build()

private fun buildHeaderLabel(details: TvDetailsContent): String {
    val parts = buildList {
        add(details.variantLabel.uppercase())
        details.typeLabel?.let { add(it) }
        details.apiName.takeIf { it.isNotBlank() }?.let { add(it) }
        if (details.comingSoon) add("COMING SOON")
    }
    return parts.joinToString(" · ")
}

private fun buildMetadataLine(details: TvDetailsContent): String {
    val parts = buildList {
        details.year?.let { add(it.toString()) }
        details.rating?.let { add("★ $it") }
        details.runtime?.let { add(it) }
        details.contentRating?.let { add(it) }
        details.showStatus?.let { add(it) }
        details.episodeCount?.let { add("$it episodes") }
    }
    return parts.joinToString("  •  ")
}
