package com.lagradost.cloudstream4.browse

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.chevron_left
import com.lagradost.cloudstream4.generated.resources.chevron_right
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.lagradost.cloudstream4.network.DesktopHttp
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.serviceLoaderEnabled
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream4.AppDirs
import com.lagradost.cloudstream4.theme.BlackButton
import okio.Path.Companion.toOkioPath

/**
 * Loads posters with OkHttp like the Android app does. OkHttp tries every address a host has,
 * which the JDK client used by Coil's default network module does not.
 */
fun desktopImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
    .crossfade(200)
    // A fixed size: a share of the heap limit would grow with it, and decoded posters live outside the
    // heap. 96 MB holds a few hundred posters at the size they are shown
    .memoryCache { MemoryCache.Builder().maxSizeBytes(96L * 1024 * 1024).build() }
    .diskCache {
        DiskCache.Builder()
            .directory(AppDirs.cache.resolve("images").toOkioPath())
            .maxSizeBytes(512L * 1024 * 1024)
            .build()
    }
    // Only this fetcher, so the Ktor one that the shared module brings in is never picked instead
    .serviceLoaderEnabled(false)
    // The app's connections and DNS, without its HTTP cache: posters have the disk cache above
    .components { add(OkHttpNetworkFetcherFactory(callFactory = { DesktopHttp.uncached })) }
    .build()

private val verticalWidth = 140.dp
private val horizontalWidth = 240.dp

/** An image from a provider, which may need its own headers to serve it */
@Composable
fun RemoteImage(url: String?, headers: Map<String, String>?, contentDescription: String?, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    val context = LocalPlatformContext.current
    val request = remember(url, headers) {
        ImageRequest.Builder(context)
            .data(url)
            .httpHeaders(NetworkHeaders.Builder().apply {
                set("User-Agent", USER_AGENT)
                headers?.forEach { (key, value) -> set(key, value) }
            }.build())
            .build()
    }
    Box(modifier.clip(RoundedCornerShape(corner)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The space at the sides of the pages' rows */
val PageGutter = 32.dp

/** Grows a little under the mouse, as cards do in streaming apps, over its neighbours */
@Composable
fun Modifier.hoverZoom(interaction: MutableInteractionSource, enabled: Boolean = true): Modifier {
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered && enabled) 1.06f else 1f, tween(150))
    return zIndex(if (hovered) 1f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            shadowElevation = (scale - 1f) * 200f
            shape = RoundedCornerShape(8.dp)
        }
        .hoverable(interaction)
}

/** A poster with its title underneath */
@Composable
fun PosterCard(item: SearchResponse, horizontal: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .width(if (horizontal) horizontalWidth else verticalWidth)
            .hoverZoom(interaction, enabled = onClick != null)
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick) else Modifier)
    ) {
        RemoteImage(
            item.posterUrl,
            item.posterHeaders,
            contentDescription = item.name,
            modifier = Modifier.fillMaxWidth().aspectRatio(if (horizontal) 16f / 9f else 2f / 3f),
        )
        Text(
            item.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** A row's title, the same on every page */
@Composable
fun RowTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), modifier = modifier)
}

/**
 * A titled, sideways-scrolling row of posters. Under the mouse, arrows at its ends scroll it by
 * most of its width, as the mouse cannot swipe.
 *
 * @param trailing shown after the last poster, for example a "More" button
 */
@Composable
fun PosterRow(
    title: String,
    items: List<SearchResponse>,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    loading: Boolean = false,
    onItemClick: ((SearchResponse) -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = PageGutter, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RowTitle(title)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        ScrollingRow {
            // No keys, providers sometimes list the same item twice and duplicate keys crash the row
            items(items) { item -> PosterCard(item, horizontal, onClick = onItemClick?.let { { it(item) } }) }
            if (trailing != null) item { trailing() }
        }
    }
}

/** A sideways row with the arrows of [PosterRow], room around its cards to grow under the mouse */
@Composable
fun ScrollingRow(content: LazyListScope.() -> Unit) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var width by remember { mutableStateOf(0) }
    Box(Modifier.fillMaxWidth().hoverable(hover).onSizeChanged { width = it.width }) {
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = PageGutter, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        Row(Modifier.matchParentSize()) {
            if (hovered && state.canScrollBackward) RowArrow(Res.drawable.chevron_left, "Scroll back") {
                scope.launch { state.animateScrollBy(-width * 0.8f) }
            }
            Spacer(Modifier.weight(1f))
            if (hovered && state.canScrollForward) RowArrow(Res.drawable.chevron_right, "Scroll on") {
                scope.launch { state.animateScrollBy(width * 0.8f) }
            }
        }
    }
}

@Composable
private fun RowArrow(icon: DrawableResource, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .width(48.dp)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = Color.White, modifier = Modifier.size(36.dp))
    }
}

/** The end of a row that has more pages */
@Composable
fun MoreButton(loading: Boolean, horizontal: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.width(if (horizontal) horizontalWidth else verticalWidth).aspectRatio(if (horizontal) 16f / 9f else 2f / 3f),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(24.dp))
        else BlackButton(onClick = onClick) { Text("More") }
    }
}

/** A centred message for empty, loading and error states */
@Composable
fun Message(title: String, body: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        body?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        action?.let {
            Spacer(Modifier.size(16.dp))
            it()
        }
    }
}
