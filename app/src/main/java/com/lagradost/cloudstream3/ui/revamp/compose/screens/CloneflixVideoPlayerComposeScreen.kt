package com.lagradost.cloudstream3.ui.revamp.compose.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixAudioSubtitlesSelector
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCard
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCardElevation
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeader
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixNextEpisodeCard
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixPlaybackSpeed
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixPlaybackSpeedSelector
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixPlayerOverlayState
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixProgressSize
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixVideoPlayerPattern
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixVideoProgressIndicator
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixVolumeControlPopup
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryRed
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentWhite20

@Composable
fun CloneflixVideoPlayerComposeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val typography = CloneflixTheme.typography
    val colors = CloneflixTheme.colors
    val dimens = CloneflixTheme.dimens
    val scrollState = rememberScrollState()

    var interactiveProgress by remember { mutableFloatStateOf(0.42f) }
    var interactiveVolume by remember { mutableFloatStateOf(0.70f) }
    var isMuted by remember { mutableStateOf(false) }
    var demoSpeed by remember { mutableStateOf(CloneflixPlaybackSpeed.SPEED_1_0X) }
    var demoAudio by remember { mutableStateOf("Japanese [Original]") }
    var demoSubtitle by remember { mutableStateOf("English (CC)") }

    var selectedPatternState by remember { mutableStateOf(CloneflixPlayerOverlayState.DEFAULT) }

    val patternTabs = listOf(
        "Default" to CloneflixPlayerOverlayState.DEFAULT,
        "Sound Change" to CloneflixPlayerOverlayState.SOUND_CHANGE,
        "Scroll Preview" to CloneflixPlayerOverlayState.SCROLL_PREVIEW,
        "Next Preview" to CloneflixPlayerOverlayState.NEXT_PREVIEW,
        "List Preview" to CloneflixPlayerOverlayState.LIST_PREVIEW,
        "Speed Change" to CloneflixPlayerOverlayState.SPEED_CHANGE,
        "Subtitles Change" to CloneflixPlayerOverlayState.SUBTITLES_CHANGE
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(dimens.spacing2Xl)
    ) {
        // Section Header matching Figma
        CloneflixHeader(
            title = "Video Player",
            subtitle = "Video Progress Indicator • Volume Slider • Speed • Subtitles • Patterns",
            iconRes = R.drawable.cloneflix_ic_videoplayer
        )

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        // ==========================================
        // SECTION 1: COMPONENTS
        // ==========================================
        Text(
            text = "COMPONENTS",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        // 1. Video Progress Indicator
        CloneflixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CloneflixCardElevation.SURFACE
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingXl),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Video Progress Indicator (Beginning, Default, Hover)",
                    style = typography.sectionHeader,
                    color = colors.textSecondary
                )

                Text(
                    text = "Size = Large, Progress = Beginning (00:12 / 1:45:00)",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                CloneflixVideoProgressIndicator(
                    progress = 0.02f,
                    bufferedProgress = 0.15f,
                    durationMs = 105 * 60 * 1000L,
                    currentPositionMs = 12 * 1000L,
                    size = CloneflixProgressSize.LARGE,
                    onProgressChange = {}
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Size = Large, Progress = Middle Default (1:00:41 / 1:45:00)",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                CloneflixVideoProgressIndicator(
                    progress = 0.58f,
                    bufferedProgress = 0.78f,
                    durationMs = 105 * 60 * 1000L,
                    currentPositionMs = 3641 * 1000L,
                    size = CloneflixProgressSize.LARGE,
                    onProgressChange = {}
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Size = Large, Progress = Middle Hover (Glow Ring & Scrubber)",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                CloneflixVideoProgressIndicator(
                    progress = 0.58f,
                    bufferedProgress = 0.78f,
                    durationMs = 105 * 60 * 1000L,
                    currentPositionMs = 3641 * 1000L,
                    size = CloneflixProgressSize.LARGE,
                    isHoveredOrActive = true,
                    onProgressChange = {}
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Interactive Live Scrubber (Touch / D-Pad Seekable)",
                    style = typography.mediumBody,
                    color = colors.primary
                )
                CloneflixVideoProgressIndicator(
                    progress = interactiveProgress,
                    bufferedProgress = 0.85f,
                    durationMs = 60 * 60 * 1000L,
                    currentPositionMs = (interactiveProgress * 60 * 60 * 1000L).toLong(),
                    size = CloneflixProgressSize.LARGE,
                    onProgressChange = { interactiveProgress = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacingL))

        // 2. Volume Slider (High, Middle, Mute)
        CloneflixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CloneflixCardElevation.SURFACE
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingXl),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Volume Slider (High / Middle / Mute)",
                    style = typography.sectionHeader,
                    color = colors.textSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "High (85%)", style = typography.mediumCaption1, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        CloneflixVolumeControlPopup(
                            volume = 0.85f,
                            onVolumeChange = {},
                            isMuted = false,
                            onToggleMute = {}
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Middle (40%)", style = typography.mediumCaption1, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        CloneflixVolumeControlPopup(
                            volume = 0.40f,
                            onVolumeChange = {},
                            isMuted = false,
                            onToggleMute = {}
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Mute (0%)", style = typography.mediumCaption1, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        CloneflixVolumeControlPopup(
                            volume = 0f,
                            onVolumeChange = {},
                            isMuted = true,
                            onToggleMute = {}
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Interactive", style = typography.mediumCaption1, color = colors.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        CloneflixVolumeControlPopup(
                            volume = interactiveVolume,
                            onVolumeChange = { interactiveVolume = it },
                            isMuted = isMuted,
                            onToggleMute = { isMuted = !isMuted }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacingL))

        // 3. Next Episode Preview & Playback Speed
        CloneflixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CloneflixCardElevation.SURFACE
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingXl),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Next Episode Preview & Speed Selector",
                    style = typography.sectionHeader,
                    color = colors.textSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Next Episode Floating Card", style = typography.mediumCaption1, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        CloneflixNextEpisodeCard(
                            episodeNumber = "Episode 2",
                            episodeTitle = "The Way of Shadows",
                            durationText = "48m",
                            synopsis = "A cryptic message from the past sends Haru on a clandestine reconnaissance mission.",
                            countdownSeconds = 8,
                            totalCountdownSeconds = 10,
                            onPlayNextClick = { Toast.makeText(context, "Playing Next Episode", Toast.LENGTH_SHORT).show() },
                            onCancelClick = { Toast.makeText(context, "Dismissed Prompt", Toast.LENGTH_SHORT).show() }
                        )
                    }

                    Column(modifier = Modifier.weight(1.2f)) {
                        Text(text = "Playback Speed Indicator & Stepper", style = typography.mediumCaption1, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        CloneflixPlaybackSpeedSelector(
                            currentSpeed = demoSpeed,
                            onSpeedSelected = { demoSpeed = it }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacingL))

        // 4. Subtitles & Audio Selector
        CloneflixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CloneflixCardElevation.SURFACE
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingXl),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Subtitles & Audio Dialog",
                    style = typography.sectionHeader,
                    color = colors.textSecondary
                )

                val audioTracks = listOf("Japanese [Original]", "English", "Spanish (Latin America)", "French", "German")
                val subtitleTracks = listOf("Off", "English (CC)", "Japanese (CC)", "Spanish", "Simplified Chinese", "Traditional Chinese", "Arabic", "German", "French")

                CloneflixAudioSubtitlesSelector(
                    audioTracks = audioTracks,
                    selectedAudio = demoAudio,
                    onAudioSelected = { demoAudio = it },
                    subtitleTracks = subtitleTracks,
                    selectedSubtitle = demoSubtitle,
                    onSubtitleSelected = { demoSubtitle = it },
                    onCloseClick = { Toast.makeText(context, "Closed Dialog", Toast.LENGTH_SHORT).show() }
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        // ==========================================
        // SECTION 2: PATTERNS (FULL VIDEO PLAYER)
        // ==========================================
        Text(
            text = "PATTERNS",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        // Interactive pattern state switcher
        ScrollableTabRow(
            selectedTabIndex = patternTabs.indexOfFirst { it.second == selectedPatternState }.coerceAtLeast(0),
            containerColor = colors.surfaceElevated,
            contentColor = colors.textPrimary,
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                val curIndex = patternTabs.indexOfFirst { it.second == selectedPatternState }.coerceAtLeast(0)
                if (curIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[curIndex]),
                        color = colors.primary,
                        height = 3.dp
                    )
                }
            }
        ) {
            patternTabs.forEach { (label, state) ->
                val isSelected = selectedPatternState == state
                Tab(
                    selected = isSelected,
                    onClick = { selectedPatternState = state },
                    text = {
                        Text(
                            text = label,
                            style = if (isSelected) typography.mediumBody else typography.regularBody,
                            color = if (isSelected) colors.textPrimary else colors.textSecondary
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacingL))

        // Full Composed Video Player Pattern Card
        CloneflixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CloneflixCardElevation.SURFACE
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingXl),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Type = ${patternTabs.find { it.second == selectedPatternState }?.first ?: "Default"}",
                    style = typography.sectionHeader,
                    color = colors.textSecondary
                )

                CloneflixVideoPlayerPattern(
                    title = "Sanctuary",
                    episodeLabel = "E1 Episode 1",
                    overlayState = selectedPatternState,
                    onOverlayStateChange = { selectedPatternState = it },
                    onBackClick = { Toast.makeText(context, "Back Pressed", Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}

@Preview(name = "Video Player Screen Preview - TV", showBackground = true, backgroundColor = 0xFF141414, device = Devices.TV_720p)
@Preview(name = "Video Player Screen Preview - Phone", showBackground = true, backgroundColor = 0xFF141414, device = Devices.PHONE)
@Composable
private fun VideoPlayerScreenPreview() {
    CloneflixTheme {
        CloneflixVideoPlayerComposeScreen()
    }
}
