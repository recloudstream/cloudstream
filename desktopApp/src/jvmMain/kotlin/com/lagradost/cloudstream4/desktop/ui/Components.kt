package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream4.compose.WhiteButton
import com.lagradost.cloudstream4.desktop.data.MediaEntry
import com.lagradost.cloudstream4.desktop.data.toMediaEntry

@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    headers: Map<String, String>? = null,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val context = LocalPlatformContext.current
    val request = remember(url, headers) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .apply {
                if (!headers.isNullOrEmpty()) {
                    httpHeaders(NetworkHeaders.Builder().apply { headers.forEach { (k, v) -> set(k, v) } }.build())
                }
            }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
fun PosterCard(
    title: String,
    posterUrl: String?,
    headers: Map<String, String>?,
    modifier: Modifier = Modifier,
    width: Dp = 140.dp,
    horizontal: Boolean = false,
    subtitle: String? = null,
    progress: Float? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        modifier = modifier
            .width(if (horizontal) width * 1.6f else width)
            .clip(RoundedCornerShape(10.dp))
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (horizontal) 16f / 9f else 2f / 3f)
                .clip(RoundedCornerShape(10.dp))
        ) {
            RemoteImage(posterUrl, Modifier.fillMaxSize(), headers, contentDescription = title)
            if (hovered) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)))
            }
            if (progress != null && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                    drawStopIndicator = {},
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (hovered) FontWeight.Bold else FontWeight.Normal,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SearchResponseCard(item: SearchResponse, horizontal: Boolean = false, width: Dp = 140.dp) {
    val navigator = LocalNavigator.current
    PosterCard(
        title = item.name,
        posterUrl = item.posterUrl,
        headers = item.posterHeaders,
        horizontal = horizontal,
        width = width,
        subtitle = listOfNotNull(item.type?.name, item.quality?.name).joinToString(" • ").ifBlank { null },
        onClick = { navigator.push(Route.Result(item.toMediaEntry())) },
    )
}

@Composable
fun MediaEntryCard(entry: MediaEntry, subtitle: String? = null, progress: Float? = null) {
    val navigator = LocalNavigator.current
    PosterCard(
        title = entry.name,
        posterUrl = entry.posterUrl,
        headers = entry.posterHeaders,
        subtitle = subtitle ?: entry.apiName,
        progress = progress,
        onClick = { navigator.push(Route.Result(entry)) },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SearchResponseRow(title: String, items: List<SearchResponse>, horizontal: Boolean = false) {
    Column {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items, key = { it.url + it.apiName }) { SearchResponseCard(it, horizontal) }
        }
    }
}

@Composable
fun LoadingBox(text: String? = null, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            if (text != null) {
                Spacer(Modifier.height(12.dp))
                Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MessageBox(
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
    action: Pair<String, () -> Unit>? = null,
) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (message != null) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (action != null) {
                Spacer(Modifier.height(4.dp))
                WhiteButton(action.first, onClick = action.second)
            }
        }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

val BottomFade = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))

/** Desktop text fields do not reliably fire IME actions, so handle Enter directly */
fun Modifier.onEnter(action: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
        action()
        true
    } else false
}
