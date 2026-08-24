package com.lagradost.cloudstream3.ui.revamp.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryRed
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentBlack60

@Composable
fun CloneflixHeroActionPattern(
    onPlayClick: () -> Unit = {},
    onMoreInfoClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dimens = CloneflixTheme.dimens

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingL),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CloneflixButton(
            text = stringResource(R.string.cloneflix_btn_play),
            onClick = onPlayClick,
            variant = CloneflixButtonVariant.SECONDARY,
            size = CloneflixButtonSize.MEDIUM,
            icon = painterResource(id = R.drawable.cloneflix_ic_play)
        )

        CloneflixButton(
            text = stringResource(R.string.cloneflix_btn_more_info),
            onClick = onMoreInfoClick,
            variant = CloneflixButtonVariant.MORE_INFO,
            size = CloneflixButtonSize.MEDIUM,
            icon = painterResource(id = R.drawable.cloneflix_ic_info)
        )
    }
}

@Composable
fun CloneflixPreviewActionsPattern(
    isAdded: Boolean = false,
    onPlayClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    onThumbUpClick: () -> Unit = {},
    onExpandClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dimens = CloneflixTheme.dimens

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CloneflixCircleActionButton(
                type = CloneflixCircleActionType.PLAY,
                onClick = onPlayClick
            )
            CloneflixCircleActionButton(
                type = if (isAdded) CloneflixCircleActionType.ADDED else CloneflixCircleActionType.ADD,
                onClick = onAddClick
            )
            CloneflixCircleActionButton(
                type = CloneflixCircleActionType.THUMB_UP,
                onClick = onThumbUpClick
            )
        }

        CloneflixCircleActionButton(
            type = CloneflixCircleActionType.ARROW_DOWN,
            onClick = onExpandClick
        )
    }
}

@Composable
fun CloneflixHeroRatingPattern(
    isMuted: Boolean = true,
    rating: String = "TV-MA",
    onToggleMute: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dimens = CloneflixTheme.dimens

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingM),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CloneflixHeroSoundButton(
            isMuted = isMuted,
            onToggleMute = onToggleMute,
            size = CloneflixMovieCardDefaults.PlayButtonOverlaySize
        )

        CloneflixMaturityRating(
            rating = rating
        )
    }
}

@Composable
fun CloneflixVideoPlayerControlBar(
    title: String = "S1:E3 • The Shadow Protocol",
    currentTimeText: String = "14:20",
    totalTimeText: String = "45:00",
    progress: Float = 0.32f,
    isPlaying: Boolean = true,
    isMuted: Boolean = false,
    onPlayPauseToggle: () -> Unit = {},
    onSeekBack: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onToggleMute: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
    onSubtitlesClick: () -> Unit = {},
    onSpeedClick: () -> Unit = {},
    onEpisodesClick: () -> Unit = {},
    onFullscreenToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens
    var currentProgress by remember { mutableFloatStateOf(progress) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusM))
            .background(Color(0xDF141414))
            .padding(dimens.spacingL)
    ) {
        // Scrubber / Progress Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentTimeText,
                style = typography.regularCaption1,
                color = PrimaryWhite
            )

            Spacer(modifier = Modifier.width(dimens.spacingM))

            Slider(
                value = currentProgress,
                onValueChange = { currentProgress = it },
                colors = SliderDefaults.colors(
                    thumbColor = PrimaryRed,
                    activeTrackColor = PrimaryRed,
                    inactiveTrackColor = Color(0x66FFFFFF)
                ),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(dimens.spacingM))

            Text(
                text = totalTimeText,
                style = typography.regularCaption1,
                color = Grey200
            )
        }

        Spacer(modifier = Modifier.height(dimens.spacingM))

        // Control buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Group
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CloneflixPlayerControlButton(
                    action = if (isPlaying) CloneflixPlayerControlAction.PLAY else CloneflixPlayerControlAction.PAUSE,
                    onClick = onPlayPauseToggle,
                    size = CloneflixMovieCardDefaults.ActionButtonSize,
                    iconSize = dimens.iconM
                )
                CloneflixPlayerControlButton(
                    action = CloneflixPlayerControlAction.REPLAY_10,
                    onClick = onSeekBack,
                    size = CloneflixMovieCardDefaults.ActionButtonSize,
                    iconSize = dimens.iconM
                )
                CloneflixPlayerControlButton(
                    action = CloneflixPlayerControlAction.FORWARD_10,
                    onClick = onSeekForward,
                    size = CloneflixMovieCardDefaults.ActionButtonSize,
                    iconSize = dimens.iconM
                )
                CloneflixPlayerControlButton(
                    action = if (isMuted) CloneflixPlayerControlAction.VOLUME_OFF else CloneflixPlayerControlAction.VOLUME_UP,
                    onClick = onToggleMute,
                    size = CloneflixMovieCardDefaults.ActionButtonSize,
                    iconSize = dimens.iconM
                )
            }

            // Center Title
            Text(
                text = title,
                style = typography.mediumBody,
                fontWeight = FontWeight.Bold,
                color = PrimaryWhite
            )

            // Right Group
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CloneflixPlayerControlButton(
                    action = CloneflixPlayerControlAction.NEXT_EPISODE,
                    onClick = onNextEpisode,
                    size = CloneflixMovieCardDefaults.ActionButtonSize,
                    iconSize = dimens.iconM
                )
                CloneflixPlayerControlButton(
                    action = CloneflixPlayerControlAction.EPISODES,
                    onClick = onEpisodesClick,
                    size = CloneflixMovieCardDefaults.ActionButtonSize,
                    iconSize = dimens.iconM
                )
                CloneflixPlayerControlButton(
                    action = CloneflixPlayerControlAction.SUBTITLES,
                    onClick = onSubtitlesClick,
                    size = CloneflixMovieCardDefaults.ActionButtonSize,
                    iconSize = dimens.iconM
                )
                CloneflixPlayerControlButton(
                    action = CloneflixPlayerControlAction.SPEED,
                    onClick = onSpeedClick,
                    size = CloneflixMovieCardDefaults.ActionButtonSize,
                    iconSize = dimens.iconM
                )
                CloneflixPlayerControlButton(
                    action = CloneflixPlayerControlAction.FULLSCREEN,
                    onClick = onFullscreenToggle,
                    size = CloneflixMovieCardDefaults.ActionButtonSize,
                    iconSize = dimens.iconM
                )
            }
        }
    }
}

@Preview(name = "Button Patterns Preview", showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun ButtonPatternsPreview() {
    CloneflixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CloneflixHeroActionPattern()
            CloneflixPreviewActionsPattern()
            CloneflixHeroRatingPattern()
            CloneflixVideoPlayerControlBar()
        }
    }
}
