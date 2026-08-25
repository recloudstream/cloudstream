package com.lagradost.cloudstream3.ui.revamp.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey50
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryBlack
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryRed
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentBlack60
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentWhite20

enum class CloneflixPlayerOverlayState {
    DEFAULT,
    SOUND_CHANGE,
    SCROLL_PREVIEW,
    NEXT_PREVIEW,
    LIST_PREVIEW,
    SPEED_CHANGE,
    SUBTITLES_CHANGE
}

@Composable
fun CloneflixVideoPlayerPattern(
    title: String = "Sanctuary",
    episodeLabel: String = "E1 Episode 1",
    overlayState: CloneflixPlayerOverlayState = CloneflixPlayerOverlayState.DEFAULT,
    onOverlayStateChange: ((CloneflixPlayerOverlayState) -> Unit)? = null,
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(true) }
    var currentProgress by remember { mutableFloatStateOf(0.35f) }
    var volume by remember { mutableFloatStateOf(0.75f) }
    var isMuted by remember { mutableStateOf(false) }
    var selectedSpeed by remember { mutableStateOf(CloneflixPlaybackSpeed.SPEED_1_0X) }
    var selectedAudio by remember { mutableStateOf("Japanese [Original]") }
    var selectedSubtitle by remember { mutableStateOf("English (CC)") }
    var countdownSec by remember { mutableIntStateOf(9) }

    val sampleEpisodes = remember {
        listOf(
            CloneflixEpisodeItemData(1, "Sanctuary", "54m", "A troubled teenager with sumo ancestry enters the sacred world of sumo wrestling.", 1.0f, false),
            CloneflixEpisodeItemData(2, "The Rebel", "48m", "Kiyoshi refuses to follow tradition and sparks outrage among seasoned stable masters.", 0.35f, true),
            CloneflixEpisodeItemData(3, "The Crucible", "51m", "An intensive training camp tests the physical limits of every rookie wrestler.", 0f, false),
            CloneflixEpisodeItemData(4, "Blood on the Clay", "50m", "A tense tournament match pushes Kiyoshi to the brink against a fierce veteran.", 0f, false),
            CloneflixEpisodeItemData(5, "Honor Bound", "56m", "Family secrets emerge that threaten the future of the entire Ensho stable.", 0f, false)
        )
    }

    val audioTracks = remember {
        listOf("Japanese [Original]", "English", "Spanish (Latin America)", "French", "German")
    }

    val subtitleTracks = remember {
        listOf("Off", "English (CC)", "Japanese (CC)", "Spanish", "Simplified Chinese", "Traditional Chinese", "Arabic", "German", "French")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F0F0F))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF2A1115), Color(0xFF0D0D0E)),
                        radius = 900f
                    )
                )
        ) {
            Text(
                text = title.uppercase(),
                style = CloneflixTheme.typography.logoBebas,
                fontSize = 48.sp,
                color = Color(0x15FFFFFF),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xD9000000), Color.Transparent)
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xE6000000))
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TransparentWhite20)
                        .clickable(onClick = onBackClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.cloneflix_ic_arrow_left),
                        contentDescription = "Back",
                        tint = PrimaryWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = CloneflixTheme.typography.mediumHeadline2,
                        fontSize = 18.sp,
                        color = PrimaryWhite
                    )
                    Text(
                        text = "•",
                        style = CloneflixTheme.typography.mediumBody,
                        color = Grey200
                    )
                    Text(
                        text = episodeLabel,
                        style = CloneflixTheme.typography.regularBody,
                        color = Grey50
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .clickable {},
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.cloneflix_ic_flag),
                    contentDescription = "Report / Flag",
                    tint = PrimaryWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CloneflixPlayerControlButton(
                action = CloneflixPlayerControlAction.REPLAY_10,
                onClick = { currentProgress = (currentProgress - 0.05f).coerceAtLeast(0f) },
                size = 48.dp,
                iconSize = 24.dp
            )

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(PrimaryWhite)
                    .clickable { isPlaying = !isPlaying },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.cloneflix_ic_play),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = PrimaryBlack,
                    modifier = Modifier.size(28.dp)
                )
            }

            CloneflixPlayerControlButton(
                action = CloneflixPlayerControlAction.FORWARD_10,
                onClick = { currentProgress = (currentProgress + 0.05f).coerceAtMost(1f) },
                size = 48.dp,
                iconSize = 24.dp
            )
        }

        if (overlayState == CloneflixPlayerOverlayState.SCROLL_PREVIEW) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 140.dp, bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xEB1A1A1A),
                    border = BorderStroke(1.dp, Color(0x40FFFFFF))
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 130.dp, height = 75.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF442222), Color(0xFF1E1E1E))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Preview Frame",
                                style = CloneflixTheme.typography.regularCaption2,
                                color = Grey200
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "00:32:15",
                            style = CloneflixTheme.typography.mediumCaption1,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryWhite
                        )
                    }
                }
            }
        }

        if (overlayState == CloneflixPlayerOverlayState.SOUND_CHANGE) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 110.dp, bottom = 68.dp)
            ) {
                CloneflixVolumeControlPopup(
                    volume = volume,
                    onVolumeChange = { volume = it },
                    isMuted = isMuted,
                    onToggleMute = { isMuted = !isMuted }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CloneflixVideoProgressIndicator(
                progress = currentProgress,
                bufferedProgress = 0.65f,
                durationMs = 54 * 60 * 1000L,
                currentPositionMs = (currentProgress * 54 * 60 * 1000L).toLong(),
                onProgressChange = { currentProgress = it },
                size = CloneflixProgressSize.LARGE,
                showTimestamps = false
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CloneflixPlayerControlButton(
                        action = CloneflixPlayerControlAction.PLAY,
                        onClick = { isPlaying = !isPlaying },
                        size = 38.dp,
                        iconSize = 20.dp
                    )
                    CloneflixPlayerControlButton(
                        action = CloneflixPlayerControlAction.REPLAY_10,
                        onClick = { currentProgress = (currentProgress - 0.05f).coerceAtLeast(0f) },
                        size = 38.dp,
                        iconSize = 20.dp
                    )
                    CloneflixPlayerControlButton(
                        action = CloneflixPlayerControlAction.FORWARD_10,
                        onClick = { currentProgress = (currentProgress + 0.05f).coerceAtMost(1f) },
                        size = 38.dp,
                        iconSize = 20.dp
                    )
                    CloneflixPlayerControlButton(
                        action = if (isMuted) CloneflixPlayerControlAction.VOLUME_OFF else CloneflixPlayerControlAction.VOLUME_UP,
                        onClick = {
                            onOverlayStateChange?.invoke(
                                if (overlayState == CloneflixPlayerOverlayState.SOUND_CHANGE) CloneflixPlayerOverlayState.DEFAULT
                                else CloneflixPlayerOverlayState.SOUND_CHANGE
                            )
                        },
                        isSelected = overlayState == CloneflixPlayerOverlayState.SOUND_CHANGE,
                        size = 38.dp,
                        iconSize = 20.dp
                    )
                }

                Text(
                    text = "$title • $episodeLabel",
                    style = CloneflixTheme.typography.mediumBody,
                    fontSize = 13.sp,
                    color = Grey200,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CloneflixPlayerControlButton(
                        action = CloneflixPlayerControlAction.NEXT_EPISODE,
                        onClick = {
                            onOverlayStateChange?.invoke(
                                if (overlayState == CloneflixPlayerOverlayState.NEXT_PREVIEW) CloneflixPlayerOverlayState.DEFAULT
                                else CloneflixPlayerOverlayState.NEXT_PREVIEW
                            )
                        },
                        isSelected = overlayState == CloneflixPlayerOverlayState.NEXT_PREVIEW,
                        size = 38.dp,
                        iconSize = 20.dp
                    )
                    CloneflixPlayerControlButton(
                        action = CloneflixPlayerControlAction.EPISODES,
                        onClick = {
                            onOverlayStateChange?.invoke(
                                if (overlayState == CloneflixPlayerOverlayState.LIST_PREVIEW) CloneflixPlayerOverlayState.DEFAULT
                                else CloneflixPlayerOverlayState.LIST_PREVIEW
                            )
                        },
                        isSelected = overlayState == CloneflixPlayerOverlayState.LIST_PREVIEW,
                        size = 38.dp,
                        iconSize = 20.dp
                    )
                    CloneflixPlayerControlButton(
                        action = CloneflixPlayerControlAction.SUBTITLES,
                        onClick = {
                            onOverlayStateChange?.invoke(
                                if (overlayState == CloneflixPlayerOverlayState.SUBTITLES_CHANGE) CloneflixPlayerOverlayState.DEFAULT
                                else CloneflixPlayerOverlayState.SUBTITLES_CHANGE
                            )
                        },
                        isSelected = overlayState == CloneflixPlayerOverlayState.SUBTITLES_CHANGE,
                        size = 38.dp,
                        iconSize = 20.dp
                    )
                    CloneflixPlayerControlButton(
                        action = CloneflixPlayerControlAction.SPEED,
                        onClick = {
                            onOverlayStateChange?.invoke(
                                if (overlayState == CloneflixPlayerOverlayState.SPEED_CHANGE) CloneflixPlayerOverlayState.DEFAULT
                                else CloneflixPlayerOverlayState.SPEED_CHANGE
                            )
                        },
                        isSelected = overlayState == CloneflixPlayerOverlayState.SPEED_CHANGE,
                        size = 38.dp,
                        iconSize = 20.dp
                    )
                    CloneflixPlayerControlButton(
                        action = CloneflixPlayerControlAction.FULLSCREEN,
                        onClick = {},
                        size = 38.dp,
                        iconSize = 20.dp
                    )
                }
            }
        }

        if (overlayState == CloneflixPlayerOverlayState.NEXT_PREVIEW) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 80.dp)
            ) {
                CloneflixNextEpisodeCard(
                    episodeNumber = "Episode 2",
                    episodeTitle = "The Way of Shadows",
                    durationText = "48m",
                    synopsis = "A cryptic message from the past sends Haru on a clandestine reconnaissance mission.",
                    countdownSeconds = countdownSec,
                    totalCountdownSeconds = 10,
                    onPlayNextClick = {
                        onOverlayStateChange?.invoke(CloneflixPlayerOverlayState.DEFAULT)
                    },
                    onCancelClick = {
                        onOverlayStateChange?.invoke(CloneflixPlayerOverlayState.DEFAULT)
                    }
                )
            }
        }

        if (overlayState == CloneflixPlayerOverlayState.LIST_PREVIEW) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                CloneflixEpisodesDrawer(
                    seriesTitle = title.uppercase(),
                    seasonName = "Season 1 Episodes",
                    episodes = sampleEpisodes,
                    onEpisodeSelected = {
                        onOverlayStateChange?.invoke(CloneflixPlayerOverlayState.DEFAULT)
                    },
                    onCloseClick = {
                        onOverlayStateChange?.invoke(CloneflixPlayerOverlayState.DEFAULT)
                    }
                )
            }
        }

        if (overlayState == CloneflixPlayerOverlayState.SPEED_CHANGE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80000000))
                    .clickable { onOverlayStateChange?.invoke(CloneflixPlayerOverlayState.DEFAULT) },
                contentAlignment = Alignment.Center
            ) {
                CloneflixPlaybackSpeedSelector(
                    currentSpeed = selectedSpeed,
                    onSpeedSelected = {
                        selectedSpeed = it
                        onOverlayStateChange?.invoke(CloneflixPlayerOverlayState.DEFAULT)
                    },
                    onCloseClick = {
                        onOverlayStateChange?.invoke(CloneflixPlayerOverlayState.DEFAULT)
                    }
                )
            }
        }

        if (overlayState == CloneflixPlayerOverlayState.SUBTITLES_CHANGE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80000000))
                    .clickable { onOverlayStateChange?.invoke(CloneflixPlayerOverlayState.DEFAULT) },
                contentAlignment = Alignment.Center
            ) {
                CloneflixAudioSubtitlesSelector(
                    audioTracks = audioTracks,
                    selectedAudio = selectedAudio,
                    onAudioSelected = { selectedAudio = it },
                    subtitleTracks = subtitleTracks,
                    selectedSubtitle = selectedSubtitle,
                    onSubtitleSelected = { selectedSubtitle = it },
                    onCloseClick = {
                        onOverlayStateChange?.invoke(CloneflixPlayerOverlayState.DEFAULT)
                    }
                )
            }
        }
    }
}

@Preview(name = "Video Player Pattern Preview - Default", showBackground = true, backgroundColor = 0xFF141414, device = Devices.TV_720p)
@Composable
private fun VideoPlayerPatternDefaultPreview() {
    CloneflixTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141414))
                .padding(24.dp)
        ) {
            CloneflixVideoPlayerPattern(
                overlayState = CloneflixPlayerOverlayState.DEFAULT
            )
        }
    }
}
