package com.lagradost.cloudstream3.ui.revamp.compose.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixButton
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixButtonSize
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixButtonVariant
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCard
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCheckbox
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCircleActionButton
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCircleActionType
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixDivider
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeader
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeroActionPattern
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeroRatingPattern
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeroReplayButton
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeroSoundButton
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixPlayerControlAction
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixPlayerControlButton
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixPreviewActionsPattern
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixThumbsReactionPill
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixVideoPlayerControlBar
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey100
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite

@Composable
fun CloneflixButtonsComposeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val typography = CloneflixTheme.typography
    val colors = CloneflixTheme.colors
    val dimens = CloneflixTheme.dimens
    val scrollState = rememberScrollState()

    var rememberMeChecked by remember { mutableStateOf(true) }
    var isAddedToList by remember { mutableStateOf(false) }
    var isMutedHero by remember { mutableStateOf(true) }
    var isPlayerPlaying by remember { mutableStateOf(true) }
    var isPlayerMuted by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(dimens.spacing2Xl)
    ) {
        CloneflixHeader(
            title = "Buttons",
            subtitle = "Action Buttons • Media Control Buttons • Compositions & Patterns",
            iconRes = R.drawable.cloneflix_ic_play
        )

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "ACTION BUTTONS",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingL),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingL)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Sign In (Primary Red • Large & Small)",
                        style = typography.mediumBody,
                        color = PrimaryWhite
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CloneflixButton(
                            text = "Sign In",
                            onClick = { Toast.makeText(context, "Sign In Large Clicked", Toast.LENGTH_SHORT).show() },
                            variant = CloneflixButtonVariant.PRIMARY,
                            size = CloneflixButtonSize.LARGE
                        )
                        CloneflixButton(
                            text = "Sign In",
                            onClick = { Toast.makeText(context, "Sign In Small Clicked", Toast.LENGTH_SHORT).show() },
                            variant = CloneflixButtonVariant.PRIMARY,
                            size = CloneflixButtonSize.SMALL
                        )
                    }
                }

                CloneflixDivider()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Secondary / Dark Action",
                        style = typography.mediumBody,
                        color = PrimaryWhite
                    )
                    CloneflixButton(
                        text = "Use a Sign-In Code",
                        onClick = { Toast.makeText(context, "Use a Sign-In Code Clicked", Toast.LENGTH_SHORT).show() },
                        variant = CloneflixButtonVariant.DARK_SECONDARY,
                        size = CloneflixButtonSize.MEDIUM
                    )
                }

                CloneflixDivider()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Hero CTA Actions (Play & More Info)",
                        style = typography.mediumBody,
                        color = PrimaryWhite
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CloneflixButton(
                            text = "Play",
                            onClick = { Toast.makeText(context, "Play Clicked", Toast.LENGTH_SHORT).show() },
                            variant = CloneflixButtonVariant.SECONDARY,
                            size = CloneflixButtonSize.MEDIUM,
                            icon = painterResource(id = R.drawable.cloneflix_ic_play)
                        )
                        CloneflixButton(
                            text = "More Info",
                            onClick = { Toast.makeText(context, "More Info Clicked", Toast.LENGTH_SHORT).show() },
                            variant = CloneflixButtonVariant.MORE_INFO,
                            size = CloneflixButtonSize.MEDIUM,
                            icon = painterResource(id = R.drawable.cloneflix_ic_info)
                        )
                    }
                }

                CloneflixDivider()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Get Started Action (Onboarding)",
                        style = typography.mediumBody,
                        color = PrimaryWhite
                    )
                    CloneflixButton(
                        text = "Get Started",
                        onClick = { Toast.makeText(context, "Get Started Clicked", Toast.LENGTH_SHORT).show() },
                        variant = CloneflixButtonVariant.PRIMARY,
                        size = CloneflixButtonSize.LARGE,
                        icon = painterResource(id = R.drawable.cloneflix_ic_chevron_right),
                        modifier = Modifier.height(56.dp)
                    )
                }

                CloneflixDivider()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Manage Profiles (Outlined Border)",
                        style = typography.mediumBody,
                        color = PrimaryWhite
                    )
                    CloneflixButton(
                        text = "Manage Profiles",
                        onClick = { Toast.makeText(context, "Manage Profiles Clicked", Toast.LENGTH_SHORT).show() },
                        variant = CloneflixButtonVariant.OUTLINE,
                        size = CloneflixButtonSize.MEDIUM
                    )
                }

                CloneflixDivider()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Checkbox Button (Remember Me)",
                        style = typography.mediumBody,
                        color = PrimaryWhite
                    )
                    CloneflixCheckbox(
                        checked = rememberMeChecked,
                        onCheckedChange = { rememberMeChecked = it },
                        label = "Remember me"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))

        Text(
            text = "MEDIA CONTROL BUTTONS",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingL),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingL)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Video Player Controls (48dp / D-pad focusable)",
                        style = typography.mediumBody,
                        color = PrimaryWhite
                    )
                    Text(
                        text = "Play, 10s Rewind, 10s Forward, Sound, Mute, Next Ep, Subtitles, Speed, Episodes, Fullscreen",
                        style = typography.regularCaption1,
                        color = Grey200
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CloneflixPlayerControlButton(
                            action = CloneflixPlayerControlAction.PLAY,
                            onClick = { Toast.makeText(context, "Play", Toast.LENGTH_SHORT).show() }
                        )
                        CloneflixPlayerControlButton(
                            action = CloneflixPlayerControlAction.REPLAY_10,
                            onClick = { Toast.makeText(context, "10s Back", Toast.LENGTH_SHORT).show() }
                        )
                        CloneflixPlayerControlButton(
                            action = CloneflixPlayerControlAction.FORWARD_10,
                            onClick = { Toast.makeText(context, "10s Forward", Toast.LENGTH_SHORT).show() }
                        )
                        CloneflixPlayerControlButton(
                            action = CloneflixPlayerControlAction.VOLUME_UP,
                            onClick = { Toast.makeText(context, "Volume Up", Toast.LENGTH_SHORT).show() }
                        )
                        CloneflixPlayerControlButton(
                            action = CloneflixPlayerControlAction.VOLUME_OFF,
                            onClick = { Toast.makeText(context, "Mute", Toast.LENGTH_SHORT).show() }
                        )
                        CloneflixPlayerControlButton(
                            action = CloneflixPlayerControlAction.NEXT_EPISODE,
                            onClick = { Toast.makeText(context, "Next Episode", Toast.LENGTH_SHORT).show() }
                        )
                        CloneflixPlayerControlButton(
                            action = CloneflixPlayerControlAction.SUBTITLES,
                            onClick = { Toast.makeText(context, "Subtitles", Toast.LENGTH_SHORT).show() }
                        )
                        CloneflixPlayerControlButton(
                            action = CloneflixPlayerControlAction.SPEED,
                            onClick = { Toast.makeText(context, "Speed", Toast.LENGTH_SHORT).show() }
                        )
                        CloneflixPlayerControlButton(
                            action = CloneflixPlayerControlAction.EPISODES,
                            onClick = { Toast.makeText(context, "Episodes", Toast.LENGTH_SHORT).show() }
                        )
                        CloneflixPlayerControlButton(
                            action = CloneflixPlayerControlAction.FULLSCREEN,
                            onClick = { Toast.makeText(context, "Fullscreen", Toast.LENGTH_SHORT).show() }
                        )
                    }
                }

                CloneflixDivider()

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Movie Preview Action Buttons (40dp)",
                        style = typography.mediumBody,
                        color = PrimaryWhite
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CloneflixCircleActionButton(
                            type = CloneflixCircleActionType.PLAY,
                            onClick = { Toast.makeText(context, "Play Circle", Toast.LENGTH_SHORT).show() }
                        )
                        CloneflixCircleActionButton(
                            type = if (isAddedToList) CloneflixCircleActionType.ADDED else CloneflixCircleActionType.ADD,
                            onClick = {
                                isAddedToList = !isAddedToList
                                Toast.makeText(context, if (isAddedToList) "Added to list" else "Removed from list", Toast.LENGTH_SHORT).show()
                            }
                        )
                        CloneflixCircleActionButton(
                            type = CloneflixCircleActionType.THUMB_UP,
                            onClick = { Toast.makeText(context, "Liked", Toast.LENGTH_SHORT).show() }
                        )
                        CloneflixCircleActionButton(
                            type = CloneflixCircleActionType.ARROW_DOWN,
                            onClick = { Toast.makeText(context, "Details", Toast.LENGTH_SHORT).show() }
                        )
                    }
                }

                CloneflixDivider()

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Thumbs Multi-Reaction Pill (Dislike • Like • Love)",
                        style = typography.mediumBody,
                        color = PrimaryWhite
                    )
                    CloneflixThumbsReactionPill(
                        onDislikeClick = { Toast.makeText(context, "Dislike Clicked", Toast.LENGTH_SHORT).show() },
                        onLikeClick = { Toast.makeText(context, "Like Clicked", Toast.LENGTH_SHORT).show() },
                        onLoveClick = { Toast.makeText(context, "Love Clicked", Toast.LENGTH_SHORT).show() }
                    )
                }

                CloneflixDivider()

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Hero Banner Video Controls (35dp Sound Toggle & Replay)",
                        style = typography.mediumBody,
                        color = PrimaryWhite
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CloneflixHeroSoundButton(
                            isMuted = isMutedHero,
                            onToggleMute = {
                                isMutedHero = !isMutedHero
                                Toast.makeText(context, if (isMutedHero) "Audio Muted" else "Audio Playing", Toast.LENGTH_SHORT).show()
                            }
                        )
                        CloneflixHeroReplayButton(
                            onClick = { Toast.makeText(context, "Replay Video", Toast.LENGTH_SHORT).show() }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))

        Text(
            text = "PATTERNS & COMPOSITIONS",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingL),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingL)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pattern: Hero CTAs (Play + More Info)",
                        style = typography.mediumBody,
                        color = PrimaryWhite
                    )
                    CloneflixHeroActionPattern(
                        onPlayClick = { Toast.makeText(context, "Hero Play", Toast.LENGTH_SHORT).show() },
                        onMoreInfoClick = { Toast.makeText(context, "Hero More Info", Toast.LENGTH_SHORT).show() }
                    )
                }

                CloneflixDivider()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pattern: Movie Card Quick Actions",
                        style = typography.mediumBody,
                        color = PrimaryWhite
                    )
                    CloneflixPreviewActionsPattern(
                        isAdded = isAddedToList,
                        onPlayClick = { Toast.makeText(context, "Play Preview", Toast.LENGTH_SHORT).show() },
                        onAddClick = { isAddedToList = !isAddedToList },
                        onThumbUpClick = { Toast.makeText(context, "Thumb Up", Toast.LENGTH_SHORT).show() },
                        onExpandClick = { Toast.makeText(context, "Expand Details", Toast.LENGTH_SHORT).show() }
                    )
                }

                CloneflixDivider()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pattern: Hero Banner Rating + Sound",
                        style = typography.mediumBody,
                        color = PrimaryWhite
                    )
                    CloneflixHeroRatingPattern(
                        isMuted = isMutedHero,
                        rating = "TV-MA",
                        onToggleMute = { isMutedHero = !isMutedHero }
                    )
                }

                CloneflixDivider()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pattern: Video Player Bottom Control Bar",
                        style = typography.mediumBody,
                        color = PrimaryWhite
                    )
                    CloneflixVideoPlayerControlBar(
                        isPlaying = isPlayerPlaying,
                        isMuted = isPlayerMuted,
                        onPlayPauseToggle = { isPlayerPlaying = !isPlayerPlaying },
                        onToggleMute = { isPlayerMuted = !isPlayerMuted },
                        onSeekBack = { Toast.makeText(context, "-10s", Toast.LENGTH_SHORT).show() },
                        onSeekForward = { Toast.makeText(context, "+10s", Toast.LENGTH_SHORT).show() },
                        onNextEpisode = { Toast.makeText(context, "Next Episode", Toast.LENGTH_SHORT).show() },
                        onEpisodesClick = { Toast.makeText(context, "Episode List Drawer", Toast.LENGTH_SHORT).show() },
                        onSubtitlesClick = { Toast.makeText(context, "Subtitles Dialog", Toast.LENGTH_SHORT).show() },
                        onSpeedClick = { Toast.makeText(context, "Speed Selector", Toast.LENGTH_SHORT).show() },
                        onFullscreenToggle = { Toast.makeText(context, "Fullscreen Toggle", Toast.LENGTH_SHORT).show() }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))
    }
}

@Preview(name = "Buttons Phone Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.PHONE)
@Preview(name = "Buttons TV Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.TV_720p)
@Composable
private fun CloneflixButtonsScreenPreview() {
    CloneflixTheme {
        CloneflixButtonsComposeScreen()
    }
}
