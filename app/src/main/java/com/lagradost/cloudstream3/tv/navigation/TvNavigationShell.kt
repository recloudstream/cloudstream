package com.lagradost.cloudstream3.tv.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import androidx.tv.material3.WideButton
import androidx.tv.material3.WideButtonDefaults
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.tv.TvProbeScreen
import com.lagradost.cloudstream3.tv.components.TvFocusScale
import com.lagradost.cloudstream3.tv.home.TvHomeScreen
import com.lagradost.cloudstream3.tv.home.rememberTvHomeFocusState
import com.lagradost.cloudstream3.tv.model.TvDestination

/**
 * Structural Compose TV shell: left nav (compact → expands on focus) + destination content.
 * Destinations switched via Compose state — no extra navigation library.
 */
@Composable
fun TvNavigationShell(
    modifier: Modifier = Modifier,
) {
    var destination by rememberSaveable { mutableStateOf(TvDestination.Home.name) }
    val selected = runCatching { TvDestination.valueOf(destination) }.getOrDefault(TvDestination.Home)
    val homeFocusState = rememberTvHomeFocusState()
    var showFocusProbe by rememberSaveable { mutableStateOf(false) }

    NavigationDrawer(
        modifier = modifier.fillMaxSize(),
        drawerContent = {
            val drawerOpen = it == DrawerValue.Open
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .fillMaxHeight()
                    .padding(12.dp)
                    .selectableGroup(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = if (drawerOpen) "CloudStream" else "CS",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
                )
                TvDestination.entries.forEach { dest ->
                    val iconRes = when (dest) {
                        TvDestination.Home -> R.drawable.home_icon_outline_24
                        TvDestination.Search -> R.drawable.search_icon
                        TvDestination.Watchlist -> R.drawable.ic_baseline_bookmark_border_24
                        TvDestination.Settings -> R.drawable.ic_outline_settings_24
                    }
                    NavigationDrawerItem(
                        selected = selected == dest,
                        onClick = {
                            destination = dest.name
                            if (dest != TvDestination.Settings) showFocusProbe = false
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = dest.label,
                            )
                        },
                    ) {
                        Text(dest.label)
                    }
                }
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 8.dp, top = 8.dp, end = 24.dp, bottom = 8.dp),
        ) {
            when (selected) {
                TvDestination.Home -> TvHomeScreen(focusState = homeFocusState)
                TvDestination.Search -> TvPlaceholderPane(
                    title = "Search",
                    body = "Phase 2 placeholder — search UI and providers land in a later phase.",
                )
                TvDestination.Watchlist -> TvPlaceholderPane(
                    title = "Watchlist",
                    body = "Phase 2 placeholder — no history / library wiring yet.",
                )
                TvDestination.Settings -> {
                    if (showFocusProbe) {
                        TvProbeScreen()
                    } else {
                        TvPlaceholderPane(
                            title = "Settings",
                            body = "Compose TV settings shell (mock). Release launcher unchanged.",
                            actionLabel = "Open focus probe (canary)",
                            onAction = { showFocusProbe = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvPlaceholderPane(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            WideButton(
                onClick = onAction,
                scale = WideButtonDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
            ) {
                Text(actionLabel)
            }
        }
    }
}
