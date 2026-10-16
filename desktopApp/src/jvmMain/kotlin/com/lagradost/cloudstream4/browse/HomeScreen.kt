package com.lagradost.cloudstream4.browse

import androidx.compose.foundation.background
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.DpOffset
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream4.generated.resources.info
import com.lagradost.cloudstream4.providers.loadSafely
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.library.DataStoreWatchStore
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.autorenew_24px
import com.lagradost.cloudstream4.generated.resources.check
import com.lagradost.cloudstream4.generated.resources.chevron_right
import com.lagradost.cloudstream4.generated.resources.search_icon
import com.lagradost.cloudstream4.home.HomeAction
import com.lagradost.cloudstream4.home.HomeState
import com.lagradost.cloudstream4.home.HomeStatus
import com.lagradost.cloudstream4.home.HomeViewModel
import com.lagradost.cloudstream4.theme.WhiteButton
import org.jetbrains.compose.resources.painterResource

/** The home page of one provider, picked from a menu at the top */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    openExtensions: () -> Unit,
    openDetails: (apiName: String, url: String) -> Unit,
    /** Plays a Continue watching title where it was left */
    resume: (apiName: String, url: String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    // What the bar at the top blurs behind its glass
    val glass = rememberHazeState()
    // The provider's first title, large, as streaming apps open on one
    val featured = (state.status as? HomeStatus.Done)?.let { state.rows.firstOrNull()?.items?.firstOrNull() }
    val showBar = state.providers.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        // One list, so Continue watching scrolls away with the rows below it. The bar floats over the
        // banner; without one, the list starts below the bar
        LazyColumn(
            contentPadding = PaddingValues(top = if (showBar && featured == null) HomeBarClearance else 0.dp, bottom = DockClearance),
            modifier = Modifier.fillMaxSize().hazeSource(glass),
        ) {
            if (featured != null) item(key = "featured") { FeaturedBanner(featured, openDetails) }
            // Above the provider's rows, so it is there even when the provider fails to load
            item { ContinueWatchingRow(DataStoreWatchStore.instance, resume, Modifier.padding(top = 8.dp)) }

            // What fills the rest of the page while there are no rows
            fun LazyListScope.whole(content: @Composable () -> Unit) = item {
                Box(Modifier.fillParentMaxWidth().fillParentMaxHeight(0.8f), contentAlignment = Alignment.Center) { content() }
            }
            when (val status = state.status) {
                HomeStatus.NoProviders -> whole {
                    Message(
                        "No extensions with a home page",
                        "Install an extension to browse it here. If you already have, check the extension languages and media types under Settings > Providers.",
                    ) { WhiteButton(onClick = openExtensions) { Text("Open Extensions") } }
                }

                HomeStatus.Loading -> whole { CircularProgressIndicator() }

                is HomeStatus.Failed -> whole {
                    Message("${state.selected} could not be loaded", status.message) {
                        WhiteButton(onClick = { viewModel.onAction(HomeAction.Reload) }) { Text("Try again") }
                    }
                }

                HomeStatus.Done -> if (state.rows.isEmpty()) {
                    whole { Message("${state.selected} returned an empty home page") }
                } else {
                    items(state.rows) { row ->
                        PosterRow(
                            title = row.name,
                            items = row.items,
                            horizontal = row.horizontalImages,
                            modifier = Modifier.padding(top = 8.dp),
                            onItemClick = { openDetails(it.apiName, it.url) },
                        )
                    }
                }
            }
        }
        if (showBar) HomeBar(state, viewModel::onAction, glass, Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
    }
}

/** The room the bar takes at the top of the page, with the space around it */
private val HomeBarClearance = 84.dp

/**
 * A pill of frosted glass floating at the top, as the dock does at the bottom: the provider, which
 * opens the list of the others, and Reload, which turns while the page loads.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeBar(state: HomeState, onAction: (HomeAction) -> Unit, glass: HazeState, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    val loading = state.status == HomeStatus.Loading
    Row(
        modifier.glass(glass, RoundedCornerShape(percent = 50)).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            GlassButton(onClick = { menuOpen = true }, active = menuOpen, modifier = Modifier.widthIn(max = 360.dp)) {
                Text(
                    state.selected ?: "",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    painterResource(Res.drawable.chevron_right),
                    contentDescription = "Choose provider",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp).size(20.dp).rotate(if (menuOpen) 270f else 90f),
                )
            }
            ProviderMenu(
                expanded = menuOpen,
                providers = state.providers,
                selected = state.selected,
                onDismiss = { menuOpen = false },
                onSelect = {
                    menuOpen = false
                    onAction(HomeAction.SelectProvider(it))
                },
            )
        }
        // A hairline between the two
        Box(Modifier.size(1.dp, 22.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f)))
        TooltipArea(
            tooltip = { GlassTooltip("Reload") },
            delayMillis = 300,
            tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, alignment = Alignment.BottomCenter, offset = DpOffset(0.dp, 8.dp)),
        ) {
            val turn = rememberInfiniteTransition()
            val angle by turn.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)))
            GlassButton(onClick = { onAction(HomeAction.Reload) }, enabled = !loading, round = true) {
                Icon(
                    painterResource(Res.drawable.autorenew_24px),
                    contentDescription = "Reload",
                    modifier = Modifier.size(20.dp).rotate(if (loading) angle else 0f),
                )
            }
        }
    }
}

/**
 * The providers to pick from, in a rounded card of the theme's colours, the current one ticked. With
 * many, a box at the top narrows them down as you type.
 */
@Composable
private fun ProviderMenu(
    expanded: Boolean,
    providers: List<String>,
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember(expanded) { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, 10.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = colors.surfaceContainer,
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, colors.onBackground.copy(alpha = 0.08f)),
        modifier = Modifier.widthIn(min = 260.dp).heightIn(max = 480.dp),
    ) {
        if (providers.size > 8) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onBackground),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .focusRequester(focus),
                decorationBox = { field ->
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(Res.drawable.search_icon), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Box(Modifier.padding(start = 10.dp)) {
                            if (query.isEmpty()) Text("Find a provider", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            field()
                        }
                    }
                },
            )
        }
        val shown = providers.filter { it.contains(query.trim(), ignoreCase = true) }
        if (shown.isEmpty()) Text(
            "No provider called \"${query.trim()}\"",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        shown.forEach { name ->
            val current = name == selected
            DropdownMenuItem(
                text = {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (current) colors.primary else colors.onBackground,
                    )
                },
                trailingIcon = if (current) {
                    { Icon(painterResource(Res.drawable.check), contentDescription = "Current", tint = colors.primary, modifier = Modifier.size(18.dp)) }
                } else null,
                onClick = { onSelect(name) },
                modifier = Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(10.dp)),
            )
        }
    }
}

/**
 * The provider's first title across the top of the page: its backdrop, faded into the page, with
 * its name, a few facts and the start of its story. The rows only have its poster, so the rest
 * loads from its page; the poster stands in until then. What the extension gives for it is used as
 * the Android app's home preview does: the wide background picture, and the title's logo in place
 * of its name, which comes back if the logo does not load.
 */
@Composable
private fun LazyItemScope.FeaturedBanner(item: SearchResponse, openDetails: (apiName: String, url: String) -> Unit) {
    val details by produceState<LoadResponse?>(null, item.apiName, item.url) {
        val api = APIHolder.getApiFromNameNull(item.apiName) ?: return@produceState
        value = (api.loadSafely(item.url) as? Resource.Success)?.value
    }
    val background = MaterialTheme.colorScheme.background
    Box(
        Modifier
            .fillParentMaxWidth()
            .heightIn(min = 340.dp, max = 600.dp)
            .fillParentMaxHeight(0.62f)
            .padding(bottom = 8.dp),
    ) {
        RemoteImage(
            details?.backgroundPosterUrl ?: details?.posterUrl ?: item.posterUrl,
            details?.posterHeaders ?: item.posterHeaders,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            corner = 0.dp,
        )
        // Faded into the page below and to the left, where the text is
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to background)))
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to background.copy(alpha = 0.92f), 0.55f to Color.Transparent)))
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth(0.5f).padding(start = PageGutter, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val name = details?.name ?: item.name
            var logoFailed by remember(details?.logoUrl) { mutableStateOf(false) }
            val logo = details?.logoUrl?.takeIf { it.isNotBlank() && !logoFailed }
            if (logo != null) AsyncImage(
                model = logo,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                onError = { logoFailed = true },
                modifier = Modifier.fillMaxWidth(0.8f).height(120.dp),
            ) else Text(
                name,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val facts = listOfNotNull(
                details?.score?.let { "★ ${"%.1f".format(it.toFloat(10))}" },
                details?.contentRating?.takeIf { it.isNotBlank() },
                details?.year?.toString(),
                details?.duration?.let { "$it min" },
                details?.tags?.take(3)?.joinToString(", ")?.ifEmpty { null },
            )
            if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            details?.plot?.let { Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WhiteButton(onClick = { openDetails(item.apiName, item.url) }) {
                    Icon(painterResource(Res.drawable.info), contentDescription = null)
                    Text("More info", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

