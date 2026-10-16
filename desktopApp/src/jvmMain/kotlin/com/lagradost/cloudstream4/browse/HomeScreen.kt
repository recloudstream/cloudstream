package com.lagradost.cloudstream4.browse

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
