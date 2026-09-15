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
import androidx.compose.runtime.remember
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
import com.lagradost.cloudstream3.tv.model.TvContentRef
import com.lagradost.cloudstream3.tv.model.TvDetailsAction
import com.lagradost.cloudstream3.tv.model.TvDetailsContent
import com.lagradost.cloudstream3.tv.model.TvDetailsUiState

/**
 * Cinematic Details screen. Loads via [TvDetailsViewModel] / [com.lagradost.cloudstream3.tv.data.TvDetailsRepository].
 * Watch Now is a stub callback only — no player.
 */
@Composable
fun TvDetailsScreen(
    ref: TvContentRef,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onWatchNow: (TvDetailsContent) -> Unit = {},
    viewModel: TvDetailsViewModel = viewModel(
        key = "tv-details:${ref.apiName}|${ref.url}",
    ),
) {
    val uiState by viewModel.state.collectAsState()

    LaunchedEffect(ref) {
        viewModel.onWatchNowStub = {
            val content = (viewModel.state.value as? TvDetailsUiState.Content)?.details
            if (content != null) onWatchNow(content)
        }
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
            details = state.details,
            onWatchNow = { viewModel.onAction(TvDetailsAction.WatchNow) },
            onBack = onBack,
            modifier = modifier,
        )
        is TvDetailsUiState.Error -> TvDetailsErrorPane(
            message = state.message,
            titleHint = state.titleHint ?: ref.title.takeIf { it.isNotBlank() },
            onRetry = { viewModel.onAction(TvDetailsAction.Retry) },
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun TvDetailsContentPane(
    details: TvDetailsContent,
    onWatchNow: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val watchFocus = remember { FocusRequester() }
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

    LaunchedEffect(details.url) {
        runCatching { watchFocus.requestFocus() }
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
                    .widthIn(max = 720.dp),
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
                        maxLines = 8,
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
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onWatchNow,
                        enabled = !details.comingSoon,
                        modifier = Modifier.focusRequester(watchFocus),
                        scale = ButtonDefaults.scale(focusedScale = TvFocusScale.HeroButtonFocused),
                        glow = ButtonDefaults.glow(
                            focusedGlow = Glow(
                                elevationColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                elevation = 12.dp,
                            ),
                        ),
                    ) {
                        Text(if (details.comingSoon) "Coming Soon" else "Watch Now")
                    }
                    Button(
                        onClick = onBack,
                        scale = ButtonDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
                    ) {
                        Text("Back")
                    }
                }
                Text(
                    text = "Watch Now is a stub in Phase 4 — no player.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
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
            text = titleHint ?: "Couldn't load details",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = message,
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
