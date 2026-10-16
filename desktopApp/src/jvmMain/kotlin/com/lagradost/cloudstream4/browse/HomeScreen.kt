package com.lagradost.cloudstream4.browse

import androidx.compose.foundation.background
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
import androidx.compose.material3.TextButton
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
import com.lagradost.cloudstream4.generated.resources.arrow_downward
import com.lagradost.cloudstream4.home.HomeAction
import com.lagradost.cloudstream4.home.HomeState
import com.lagradost.cloudstream4.home.HomeStatus
import com.lagradost.cloudstream4.home.HomeViewModel
import com.lagradost.cloudstream4.theme.BlackButton
import com.lagradost.cloudstream4.theme.WhiteButton
import org.jetbrains.compose.resources.painterResource

/** The home page of one provider, picked from a menu at the top */
@Composable
fun HomeScreen(viewModel: HomeViewModel, openExtensions: () -> Unit, openDetails: (apiName: String, url: String) -> Unit) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        if (state.providers.isNotEmpty()) TopBar(state, viewModel::onAction)
        // One list, so Continue watching scrolls away with the rows below it
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
            // The provider's first title, large, as streaming apps open on one
            val featured = (state.status as? HomeStatus.Done)?.let { state.rows.firstOrNull()?.items?.firstOrNull() }
            if (featured != null) item(key = "featured") { FeaturedBanner(featured, openDetails) }
            // Above the provider's rows, so it is there even when the provider fails to load
            item { ContinueWatchingRow(DataStoreWatchStore.instance, openDetails, Modifier.padding(top = 8.dp)) }

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
    }
}

@Composable
private fun TopBar(state: HomeState, onAction: (HomeAction) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            TextButton(onClick = { menuOpen = true }) {
                Text(state.selected ?: "", style = MaterialTheme.typography.titleLarge)
                Icon(
                    painterResource(Res.drawable.arrow_downward),
                    contentDescription = "Choose provider",
                    modifier = Modifier.padding(start = 4.dp).size(20.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                state.providers.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            menuOpen = false
                            onAction(HomeAction.SelectProvider(name))
                        },
                    )
                }
            }
        }
        Box(Modifier.weight(1f))
        BlackButton(onClick = { onAction(HomeAction.Reload) }, enabled = state.status != HomeStatus.Loading) {
            Text("Reload")
        }
    }
}

/**
 * The provider's first title across the top of the page: its backdrop, faded into the page, with
 * its name, a few facts and the start of its story. The rows only have its poster, so the rest
 * loads from its page; the poster stands in until then.
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
            Text(
                details?.name ?: item.name,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val facts = listOfNotNull(
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

