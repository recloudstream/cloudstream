package com.mihon.presentation.settings

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.no_results_found
import com.mihon.presentation.secondaryItemAlpha
import com.mihon.presentation.settings.widget.TextPreferenceWidget
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

private fun getLocalizedBreadcrumb(path: String, node: String?, isLtr: Boolean): String {
    return if (node == null) {
        path
    } else {
        if (isLtr) {
            // This locale reads left to right.
            "$path > $node"
        } else {
            // This locale reads right to left.
            "$node < $path"
        }
    }
}

data class SettingsData<T>(
    val title: String,
    val contents: List<Preference>,
    val navigation: T,
)

data class SearchResultItem<T>(
    val title: String,
    val breadcrumbs: String,
    val highlightKey: String,
    val navigation: T,
)

@Composable
fun <T> SettingSearchResults(
    searchKey: String,
    onItemClick: (SearchResultItem<T>) -> Unit,
    items: ImmutableList<SettingsData<T>>,
    nestedScrollConnection: NestedScrollConnection,
    empty: @Composable () -> Unit
) {
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    val result by produceState<List<SearchResultItem<T>>?>(initialValue = null, searchKey) {
        if (searchKey.isEmpty()) {
            value = null
            return@produceState
        }
        value = items.asSequence()
            .flatMap { settingsData ->
                settingsData.contents.asSequence()
                    // Only search from enabled prefs and one with valid title
                    .filter { it.enabled && it.title.isNotBlank() }
                    // Flatten items contained inside *enabled* PreferenceGroup
                    .flatMap { p ->
                        when (p) {
                            is Preference.PreferenceGroup -> {
                                if (p.enabled) {
                                    p.preferenceItems.asSequence()
                                        .filter { it.enabled && it.title.isNotBlank() }
                                        .map { p.title to it }
                                } else {
                                    emptySequence()
                                }
                            }

                            is Preference.PreferenceItem<*, *> -> sequenceOf(null to p)
                        }
                    }
                    // Don't show info preference
                    .filterNot { it.second is Preference.PreferenceItem.InfoPreference }
                    // Filter by search query
                    .filter { (_, p) ->
                        val inTitle = p.title.contains(searchKey, true)
                        val inSummary = p.subtitle?.contains(searchKey, true) ?: false
                        inTitle || inSummary
                    }
                    // Map result data
                    .map { (categoryTitle, p) ->
                        SearchResultItem(
                            navigation = settingsData.navigation,
                            title = p.title,
                            breadcrumbs = getLocalizedBreadcrumb(
                                path = settingsData.title,
                                node = categoryTitle,
                                isLtr = isLtr,
                            ),
                            highlightKey = p.title,
                        )
                    }
            }
            .take(10) // Just take top 10 result for quicker result
            .toList()
    }

    Crossfade(
        targetState = result,
        label = "results",
    ) {
        when {
            it == null -> {
                empty()
            }

            it.isEmpty() -> {
                Text(
                    text = stringResource(Res.string.no_results_found),
                    modifier = Modifier.secondaryItemAlpha().fillMaxWidth().padding(20.dp),
                    textAlign = TextAlign.Center
                )
            }

            else -> {
                /*LazyColumn(
                    state = listState,
                    contentPadding = contentPadding,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(
                        items = it,
                        key = { i -> i.hashCode() },
                    ) { item ->*/
                Column(
                    modifier = Modifier
                        .nestedScroll(nestedScrollConnection)
                ) {
                    it.forEach { item ->
                        TextPreferenceWidget(
                            title = item.title,
                            subtitle = item.breadcrumbs,
                            onPreferenceClick = {
                                onItemClick(item)
                            }
                        )

                        /*Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(item) }
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                        ) {
                            Text(
                                text = item.title,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                fontWeight = FontWeight.Normal,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = item.breadcrumbs,
                                modifier = Modifier.paddingFromBaseline(top = 16.dp),
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }*/
                    }
                }
            }
        }
    }
}
