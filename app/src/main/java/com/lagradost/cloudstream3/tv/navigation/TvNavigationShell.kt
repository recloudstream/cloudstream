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
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.tv.TvProbeScreen
import com.lagradost.cloudstream3.tv.details.TvDetailsScreen
import com.lagradost.cloudstream3.tv.home.TvHomeScreen
import com.lagradost.cloudstream3.tv.home.rememberTvHomeFocusState
import com.lagradost.cloudstream3.tv.model.TvContentRef
import com.lagradost.cloudstream3.tv.model.TvDestination
import com.lagradost.cloudstream3.tv.model.TvPlaybackRequest
import com.lagradost.cloudstream3.tv.model.TvResumeHint
import com.lagradost.cloudstream3.tv.search.TvSearchScreen
import com.lagradost.cloudstream3.tv.settings.TvSettingsScreen
import com.lagradost.cloudstream3.tv.search.rememberTvSearchFocusState
import com.lagradost.cloudstream3.tv.watchlist.TvWatchlistScreen
import com.lagradost.cloudstream3.tv.watchlist.rememberTvWatchlistFocusState

/**
 * Structural Compose TV shell: left nav + destination content.
 * Phase 11: Settings over existing AppSettings; Phase 10 playback pipeline unchanged.
 * Watchlist → same TvDetailsScreen. Mock never becomes real TvPlaybackRequest.
 */
@Composable
fun TvNavigationShell(
    modifier: Modifier = Modifier,
    onPlaybackRequest: (TvPlaybackRequest) -> Unit = {},
) {
    var destination by rememberSaveable { mutableStateOf(TvDestination.Home.name) }
    val selected = runCatching { TvDestination.valueOf(destination) }.getOrDefault(TvDestination.Home)
    val homeFocusState = rememberTvHomeFocusState()
    val searchFocusState = rememberTvSearchFocusState()
    val watchlistFocusState = rememberTvWatchlistFocusState()
    var showFocusProbe by rememberSaveable { mutableStateOf(false) }

    // Compact saveable identity — never store SearchResponse / LoadResponse here.
    var detailsUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsApiName by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsTitle by rememberSaveable { mutableStateOf<String?>(null) }
    // Phase 9 CW restore hints encoded as strings for rememberSaveable.
    var detailsResumeSeason by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsResumeEpisode by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsResumeEpisodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsResumeParentId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsResumeTypeLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsHasResumeHint by rememberSaveable { mutableStateOf(false) }

    val detailsRef = run {
        val url = detailsUrl
        val api = detailsApiName
        if (!url.isNullOrBlank() && !api.isNullOrBlank()) {
            val hint = if (detailsHasResumeHint) {
                TvResumeHint(
                    season = detailsResumeSeason?.toIntOrNull(),
                    episode = detailsResumeEpisode?.toIntOrNull(),
                    episodeId = detailsResumeEpisodeId?.toIntOrNull(),
                    parentId = detailsResumeParentId?.toIntOrNull(),
                    typeLabel = detailsResumeTypeLabel,
                )
            } else {
                null
            }
            TvContentRef(
                url = url,
                apiName = api,
                title = detailsTitle.orEmpty(),
                resumeHint = hint,
            )
        } else {
            null
        }
    }

    fun openDetails(ref: TvContentRef) {
        detailsUrl = ref.url
        detailsApiName = ref.apiName
        detailsTitle = ref.title
        val hint = ref.resumeHint
        detailsHasResumeHint = hint != null
        detailsResumeSeason = hint?.season?.toString()
        detailsResumeEpisode = hint?.episode?.toString()
        detailsResumeEpisodeId = hint?.episodeId?.toString()
        detailsResumeParentId = hint?.parentId?.toString()
        detailsResumeTypeLabel = hint?.typeLabel
    }

    fun closeDetails() {
        detailsUrl = null
        detailsApiName = null
        detailsTitle = null
        detailsHasResumeHint = false
        detailsResumeSeason = null
        detailsResumeEpisode = null
        detailsResumeEpisodeId = null
        detailsResumeParentId = null
        detailsResumeTypeLabel = null
    }

    fun guardedPlayback(request: TvPlaybackRequest) {
        if (request.isMock) return // Mock never becomes real playback.
        onPlaybackRequest(request)
    }

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
                        selected = selected == dest && detailsRef == null,
                        onClick = {
                            closeDetails()
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
            when {
                detailsRef != null -> {
                    TvDetailsScreen(
                        ref = detailsRef,
                        onBack = { closeDetails() },
                        onPlaybackRequest = ::guardedPlayback,
                    )
                }
                selected == TvDestination.Home -> {
                    TvHomeScreen(
                        focusState = homeFocusState,
                        onOpenDetails = { openDetails(it) },
                        onPlaybackRequest = ::guardedPlayback,
                    )
                }
                selected == TvDestination.Search -> {
                    TvSearchScreen(
                        focusState = searchFocusState,
                        onOpenDetails = { openDetails(it) },
                    )
                }
                selected == TvDestination.Watchlist -> {
                    TvWatchlistScreen(
                        focusState = watchlistFocusState,
                        onOpenDetails = { openDetails(it) },
                    )
                }
                selected == TvDestination.Settings -> {
                    if (showFocusProbe) {
                        TvProbeScreen()
                    } else {
                        TvSettingsScreen(
                            onOpenFocusProbe = { showFocusProbe = true },
                        )
                    }
                }
            }
        }
    }
}
