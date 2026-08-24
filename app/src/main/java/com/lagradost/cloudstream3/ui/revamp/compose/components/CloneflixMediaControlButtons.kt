package com.lagradost.cloudstream3.ui.revamp.compose.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryBlack
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentBlack60
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentWhite20

enum class CloneflixPlayerControlAction {
    PLAY,
    PAUSE,
    REPLAY_10,
    FORWARD_10,
    VOLUME_UP,
    VOLUME_OFF,
    NEXT_EPISODE,
    SUBTITLES,
    SPEED,
    EPISODES,
    FULLSCREEN
}

@Composable
fun CloneflixPlayerControlButton(
    action: CloneflixPlayerControlAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = CloneflixPlayerDefaults.ControlButtonSize,
    iconSize: Dp = CloneflixPlayerDefaults.ControlIconSize,
    enabled: Boolean = true,
    isSelected: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val dimens = CloneflixTheme.dimens

    val scale by animateFloatAsState(
        targetValue = if (isFocused) CloneflixTokens.FOCUS_SCALE_FACTOR_LARGE else 1f,
        label = "playerBtnScale"
    )

    val iconRes = when (action) {
        CloneflixPlayerControlAction.PLAY -> R.drawable.cloneflix_ic_play
        CloneflixPlayerControlAction.PAUSE -> R.drawable.cloneflix_ic_play
        CloneflixPlayerControlAction.REPLAY_10 -> R.drawable.netflix_skip_back
        CloneflixPlayerControlAction.FORWARD_10 -> R.drawable.netflix_skip_forward
        CloneflixPlayerControlAction.VOLUME_UP -> R.drawable.ic_baseline_volume_up_24
        CloneflixPlayerControlAction.VOLUME_OFF -> R.drawable.ic_baseline_volume_mute_24
        CloneflixPlayerControlAction.NEXT_EPISODE -> R.drawable.ic_baseline_skip_next_24
        CloneflixPlayerControlAction.SUBTITLES -> R.drawable.ic_baseline_subtitles_24
        CloneflixPlayerControlAction.SPEED -> R.drawable.ic_baseline_speed_24
        CloneflixPlayerControlAction.EPISODES -> R.drawable.baseline_list_alt_24
        CloneflixPlayerControlAction.FULLSCREEN -> R.drawable.baseline_fullscreen_24
    }

    val contentDesc = when (action) {
        CloneflixPlayerControlAction.PLAY -> stringResource(R.string.cloneflix_cd_play)
        CloneflixPlayerControlAction.PAUSE -> stringResource(R.string.cloneflix_cd_pause)
        CloneflixPlayerControlAction.REPLAY_10 -> stringResource(R.string.cloneflix_cd_replay_10)
        CloneflixPlayerControlAction.FORWARD_10 -> stringResource(R.string.cloneflix_cd_forward_10)
        CloneflixPlayerControlAction.VOLUME_UP -> stringResource(R.string.cloneflix_cd_unmute)
        CloneflixPlayerControlAction.VOLUME_OFF -> stringResource(R.string.cloneflix_cd_mute)
        CloneflixPlayerControlAction.NEXT_EPISODE -> stringResource(R.string.cloneflix_cd_next_episode)
        CloneflixPlayerControlAction.SUBTITLES -> stringResource(R.string.cloneflix_cd_audio_subtitles)
        CloneflixPlayerControlAction.SPEED -> stringResource(R.string.cloneflix_cd_playback_speed)
        CloneflixPlayerControlAction.EPISODES -> stringResource(R.string.cloneflix_cd_episodes)
        CloneflixPlayerControlAction.FULLSCREEN -> stringResource(R.string.cloneflix_cd_unlock_screen)
    }

    val backgroundColor = when {
        isFocused -> TransparentWhite20
        isSelected -> TransparentWhite20
        else -> Color.Transparent
    }

    val borderStroke = if (isFocused) BorderStroke(dimens.borderFocus, PrimaryWhite) else null

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(if (borderStroke != null) Modifier.border(borderStroke, CircleShape) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .semantics {
                role = Role.Button
                contentDescription = contentDesc
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDesc,
            tint = if (enabled) PrimaryWhite else Grey200.copy(alpha = CloneflixTokens.ALPHA_MEDIUM),
            modifier = Modifier.size(iconSize)
        )
    }
}

enum class CloneflixCircleActionType {
    PLAY,
    ADD,
    ADDED,
    THUMB_UP,
    THUMB_DOWN,
    DOUBLE_THUMB_UP,
    MUTE,
    UNMUTE,
    ARROW_DOWN
}

@Composable
fun CloneflixCircleActionButton(
    type: CloneflixCircleActionType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = CloneflixMovieCardDefaults.ActionButtonSize,
    iconSize: Dp = CloneflixTheme.dimens.iconM,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val dimens = CloneflixTheme.dimens

    val scale by animateFloatAsState(
        targetValue = if (isFocused) CloneflixTokens.FOCUS_SCALE_FACTOR_LARGE else 1f,
        label = "circleBtnScale"
    )

    val isPlay = type == CloneflixCircleActionType.PLAY

    val containerColor = when {
        isPlay -> PrimaryWhite
        isFocused -> TransparentWhite20
        else -> TransparentBlack60
    }

    val contentColor = when {
        isPlay -> PrimaryBlack
        else -> PrimaryWhite
    }

    val borderStroke = when {
        isPlay -> if (isFocused) BorderStroke(dimens.borderFocus, PrimaryBlack) else null
        isFocused -> BorderStroke(dimens.borderFocus, PrimaryWhite)
        else -> BorderStroke(dimens.borderDefault, Grey200.copy(alpha = CloneflixTokens.ALPHA_HIGH))
    }

    val iconRes = when (type) {
        CloneflixCircleActionType.PLAY -> R.drawable.cloneflix_ic_play
        CloneflixCircleActionType.ADD -> R.drawable.cloneflix_ic_plus
        CloneflixCircleActionType.ADDED -> R.drawable.cloneflix_ic_check
        CloneflixCircleActionType.THUMB_UP -> R.drawable.cloneflix_ic_thumb_up
        CloneflixCircleActionType.THUMB_DOWN -> R.drawable.cloneflix_ic_thumb_down
        CloneflixCircleActionType.DOUBLE_THUMB_UP -> R.drawable.cloneflix_ic_double_thumb_up
        CloneflixCircleActionType.MUTE -> R.drawable.ic_baseline_volume_mute_24
        CloneflixCircleActionType.UNMUTE -> R.drawable.ic_baseline_volume_up_24
        CloneflixCircleActionType.ARROW_DOWN -> R.drawable.cloneflix_ic_arrow_down
    }

    val desc = when (type) {
        CloneflixCircleActionType.PLAY -> stringResource(R.string.cloneflix_cd_play)
        CloneflixCircleActionType.ADD -> stringResource(R.string.cloneflix_cd_add_to_list)
        CloneflixCircleActionType.ADDED -> stringResource(R.string.cloneflix_cd_remove_from_list)
        CloneflixCircleActionType.THUMB_UP -> stringResource(R.string.cloneflix_cd_like)
        CloneflixCircleActionType.THUMB_DOWN -> stringResource(R.string.cloneflix_cd_dislike)
        CloneflixCircleActionType.DOUBLE_THUMB_UP -> stringResource(R.string.cloneflix_cd_double_like)
        CloneflixCircleActionType.MUTE -> stringResource(R.string.cloneflix_cd_mute)
        CloneflixCircleActionType.UNMUTE -> stringResource(R.string.cloneflix_cd_unmute)
        CloneflixCircleActionType.ARROW_DOWN -> stringResource(R.string.cloneflix_cd_episodes)
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(containerColor)
            .then(if (borderStroke != null) Modifier.border(borderStroke, CircleShape) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .semantics {
                role = Role.Button
                contentDescription = desc
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = desc,
            tint = contentColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun CloneflixThumbsReactionPill(
    onDislikeClick: () -> Unit,
    onLikeClick: () -> Unit,
    onLoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = CloneflixTheme.dimens
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CloneflixTokens.RadiusPill),
        color = Color(0xEB232323),
        border = BorderStroke(dimens.borderDefault, Grey200.copy(alpha = CloneflixTokens.ALPHA_MEDIUM))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.spacingM, vertical = dimens.spacingS),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CloneflixCircleActionButton(
                type = CloneflixCircleActionType.THUMB_DOWN,
                onClick = onDislikeClick,
                size = CloneflixMovieCardDefaults.PlayButtonOverlaySize,
                iconSize = dimens.iconS
            )
            CloneflixCircleActionButton(
                type = CloneflixCircleActionType.THUMB_UP,
                onClick = onLikeClick,
                size = CloneflixMovieCardDefaults.PlayButtonOverlaySize,
                iconSize = dimens.iconS
            )
            CloneflixCircleActionButton(
                type = CloneflixCircleActionType.DOUBLE_THUMB_UP,
                onClick = onLoveClick,
                size = CloneflixMovieCardDefaults.PlayButtonOverlaySize,
                iconSize = dimens.iconS
            )
        }
    }
}

@Composable
fun CloneflixHeroSoundButton(
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = CloneflixMovieCardDefaults.PlayButtonOverlaySize
) {
    val dimens = CloneflixTheme.dimens
    CloneflixCircleActionButton(
        type = if (isMuted) CloneflixCircleActionType.MUTE else CloneflixCircleActionType.UNMUTE,
        onClick = onToggleMute,
        size = size,
        iconSize = dimens.iconS,
        modifier = modifier
    )
}

@Composable
fun CloneflixHeroReplayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = CloneflixMovieCardDefaults.PlayButtonOverlaySize
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val dimens = CloneflixTheme.dimens

    val scale by animateFloatAsState(
        targetValue = if (isFocused) CloneflixTokens.FOCUS_SCALE_FACTOR_LARGE else 1f,
        label = "replayBtnScale"
    )

    val border = if (isFocused) {
        BorderStroke(dimens.borderFocus, PrimaryWhite)
    } else {
        BorderStroke(dimens.borderDefault, Grey200.copy(alpha = CloneflixTokens.ALPHA_HIGH))
    }

    val replayDesc = stringResource(R.string.cloneflix_cd_replay_10)

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(TransparentBlack60)
            .border(border, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .semantics {
                role = Role.Button
                contentDescription = replayDesc
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.cloneflix_ic_replay_arrow),
            contentDescription = replayDesc,
            tint = PrimaryWhite,
            modifier = Modifier.size(dimens.iconS)
        )
    }
}

@Preview(name = "Media Control Buttons Preview", showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun MediaControlButtonsPreview() {
    CloneflixTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CloneflixCircleActionButton(type = CloneflixCircleActionType.PLAY, onClick = {})
            CloneflixCircleActionButton(type = CloneflixCircleActionType.ADD, onClick = {})
            CloneflixCircleActionButton(type = CloneflixCircleActionType.THUMB_UP, onClick = {})
            CloneflixThumbsReactionPill(onDislikeClick = {}, onLikeClick = {}, onLoveClick = {})
        }
    }
}
