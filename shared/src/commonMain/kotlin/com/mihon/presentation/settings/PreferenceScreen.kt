package com.mihon.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import com.mihon.presentation.settings.widget.PreferenceGroupHeader
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Preference Screen composable which contains a list of [Preference] items
 * @param items [Preference] items which should be displayed on the preference screen. An item can be a single [PreferenceItem] or a group ([Preference.PreferenceGroup])
 * @param modifier [Modifier] to be applied to the preferenceScreen layout
 */
@Composable
fun PreferenceScreen(
    items: List<Preference>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state = rememberLazyListState()
    val highlightKey = SearchableSettings.highlightKey
    if (highlightKey != null) {
        LaunchedEffect(Unit) {
            val i = items.findHighlightedIndex(highlightKey)
            if (i >= 0) {
                delay(0.5.seconds)
                state.animateScrollToItem(i)
            }
            SearchableSettings.highlightKey = null
        }
    }

    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
    ) {
        items.fastForEachIndexed { i, preference ->
            when (preference) {
                // Create Preference Group
                is Preference.PreferenceGroup -> {
                    if (!preference.enabled) return@fastForEachIndexed

                    item {
                        Column {
                            PreferenceGroupHeader(title = preference.title)
                        }
                    }
                    val last = preference.preferenceItems.lastIndex
                    itemsIndexed(preference.preferenceItems) { j, item ->
                        PreferenceCard(first = j == 0, last = j == last) {
                            PreferenceItem(
                                item = item,
                                highlightKey = highlightKey,
                            )
                        }
                    }
                    item {
                        if (i < items.lastIndex) {
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }

                // Create Preference Item, in one stack of cards with the items around it outside groups
                is Preference.PreferenceItem<*, *> -> item {
                    if (i == 0) Spacer(modifier = Modifier.height(8.dp))
                    PreferenceCard(
                        first = items.getOrNull(i - 1) !is Preference.PreferenceItem<*, *>,
                        last = items.getOrNull(i + 1) !is Preference.PreferenceItem<*, *>,
                    ) {
                        PreferenceItem(
                            item = preference,
                            highlightKey = highlightKey,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A group's items as a stack of cards in the theme's box colour, a hair apart, rounded at the top of
 * the first and the bottom of the last, so the group reads as one block
 */
@Composable
private fun PreferenceCard(first: Boolean, last: Boolean, content: @Composable () -> Unit) {
    val outer = 12.dp
    val inner = 4.dp
    val shape = RoundedCornerShape(
        topStart = if (first) outer else inner,
        topEnd = if (first) outer else inner,
        bottomStart = if (last) outer else inner,
        bottomEnd = if (last) outer else inner,
    )
    Box(
        Modifier
            .padding(horizontal = PreferenceCardGutter)
            .padding(bottom = if (last) 0.dp else 2.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) { content() }
}

/** The space at the sides of the cards; the group titles line up with what is inside them */
internal val PreferenceCardGutter = 16.dp

private fun List<Preference>.findHighlightedIndex(highlightKey: String): Int {
    return flatMap {
        if (it is Preference.PreferenceGroup) {
            buildList<String?> {
                add(null) // Header
                addAll(it.preferenceItems.map { groupItem -> groupItem.title })
                add(null) // Spacer
            }
        } else {
            listOf(it.title)
        }
    }.indexOfFirst { it == highlightKey }
}
