package com.lagradost.cloudstream4.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.arrow_downward
import com.lagradost.cloudstream4.generated.resources.no_results_found
import com.lagradost.cloudstream4.generated.resources.poster
import com.lagradost.cloudstream4.generated.resources.poster_horizontal
import com.lagradost.cloudstream4.theme.CloudStreamPreviewTheme
import com.mihon.material.padding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
@PreviewLightDark
fun Preview() {
    CloudStreamPreviewTheme {
        SearchResponseItem(
            response = ImmutableSearchResponse(
                name = "hello world",
                url = "",
                apiName = "TODO()",
                type = TvType.TvSeries,
                posterUrl = "https://cdn.marvel.com/content/2x/smbnd_online_1400x2100_reflection_03.webp",
                posterHeaders = persistentMapOf(),
                id = 0,
                quality = null,
                score = Score.from10(7.5f),
                year = 2002,
                dubStatus = persistentSetOf(),
                episodes = persistentMapOf(),
                language = null
            ), isHorizontal = false, action = {})
    }
}

const val HORIZONTAL_ASPECT_RATIO: Float = 1.0f / 0.63f
const val VERTICAL_ASPECT_RATIO: Float = 0.63f

@Composable
fun SearchResponseItem(
    response: ImmutableSearchResponse,
    action: (SearchResponseAction) -> Unit,
    isHorizontal: Boolean,
    modifier: Modifier = Modifier,
    innerModifier: Modifier = Modifier,
    showText: Boolean = true,
    clickable: Boolean = true,
    customText: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val imageRequest = response.imageRequest()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .combinedClickable(
                enabled = clickable,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    action(
                        SearchResponseAction(
                            response = response,
                            operation = SearchResponseOperation.Open
                        )
                    )
                }, onLongClick = {
                    SearchResponseAction(
                        response = response,
                        operation = SearchResponseOperation.Metadata
                    )
                })
            .rounded(),
    ) {
        Box(
            modifier = innerModifier
                .fillMaxWidth()
                .aspectRatio(
                    if (isHorizontal) {
                        HORIZONTAL_ASPECT_RATIO
                    } else {
                        VERTICAL_ASPECT_RATIO
                    }
                )
                .rounded()
                .indication(
                    interactionSource = interactionSource,
                    indication = ripple(color = Color.White)
                ).border(
                    width = 1.5.dp,
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedShape()
                ),
            contentAlignment = Alignment.BottomStart
        ) {
            AsyncImage(
                contentScale = ContentScale.Crop,
                model = imageRequest,
                contentDescription = response.name,
                error =
                    if (isHorizontal) {
                        painterResource(Res.drawable.poster_horizontal)
                    } else {
                        painterResource(Res.drawable.poster)
                    },
                modifier = Modifier.fillMaxSize()
            )
            /*if (response.watchPosition != null) {
                Column {
                    LinearProgressIndicator(
                        progress = {
                            (response.watchPosition.position / response.watchPosition.duration).toFloat()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    )
                    Spacer(Modifier.height(1.5.dp))
                }
            }*/

            response.score?.toStringNull(minScore = 0.1, maxScore = 10, decimals = 1)?.let { text ->
                Box(contentAlignment = Alignment.TopEnd, modifier = Modifier.fillMaxSize()) {
                    Text(
                        "$text ★",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(
                            MaterialTheme.padding.extraSmall
                        ).border(
                            width = 0.5.dp,
                            color = Color.White.copy(alpha = 0.1f),
                            shape = RoundedShape()
                        ).background(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedShape()
                        ).padding(
                            vertical = MaterialTheme.padding.extraSmall,
                            horizontal = MaterialTheme.padding.small
                        )
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(50.dp)
                .padding(horizontal = MaterialTheme.padding.extraSmall),
            contentAlignment = Alignment.Center
        ) {
            if (customText != null) {
                customText()
            } else if (showText) {
                Text(
                    text = response.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResponseDialog(
    dialog: ImmutableHomePageList,
    action: (SearchResponseAction) -> Unit,
    dismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        onDismissRequest = dismiss,
        modifier = Modifier.fillMaxSize(),
        dragHandle = { },
        shape = RectangleShape
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clickable(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                        }.invokeOnCompletion {
                            dismiss()
                        }
                    }
                )
                .padding(horizontal = 10.dp)
        ) {
            Text(
                text = dialog.name,
                style = MaterialTheme.typography.titleMedium
            )
            Icon(
                painter = painterResource(Res.drawable.arrow_downward),
                contentDescription = null,
            )
        }

        if (dialog.items.isEmpty()) {
            Text(
                style = MaterialTheme.typography.bodyLarge,
                text = stringResource(Res.string.no_results_found),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            SearchResponseGrid(
                items = dialog.items,
                searchAction = action,
            )
        }
    }
}

@OptIn(ExperimentalGridApi::class)
@Composable
fun SearchResponseGrid(
    items: ImmutableList<ImmutableSearchResponse>,
    modifier: Modifier = Modifier,
    listState: LazyGridState = rememberLazyGridState(),
    searchAction: (SearchResponseAction) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3), // GridCells.Fixed(if (isLandscape) 6 else 3),
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = items, key = { item ->
                item.uuid
            }) { response ->
            SearchResponseItem(
                response = response,
                action = searchAction,
                isHorizontal = false,
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .wrapContentHeight()
            )
        }
    }
}
