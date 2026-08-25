package com.lagradost.cloudstream3.ui.result.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ActorRole
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme

@Composable
private fun CastAvatarCircle(
    actor: Actor?,
    size: Dp,
    borderColor: Color,
    borderWidth: Dp,
    modifier: Modifier = Modifier
) {
    val colors = MovieDetailsTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.surface)
            .border(BorderStroke(borderWidth, borderColor), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!actor?.image.isNullOrBlank()) {
            AsyncImage(
                model = actor?.image,
                contentDescription = actor?.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (actor?.name ?: "?").take(2).uppercase(),
                    color = colors.textSecondary,
                    fontSize = if (size > 50.dp) 18.sp else 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CastDualAvatar(
    mainActor: Actor?,
    secondaryActor: Actor?,
    hasVoiceActor: Boolean,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MovieDetailsTheme.colors
    val mainBorderColor = if (isFocused) colors.primary else colors.border

    Box(
        modifier = modifier.size(72.dp),
        contentAlignment = Alignment.Center
    ) {
        if (hasVoiceActor && secondaryActor?.image != null) {
            CastAvatarCircle(
                actor = secondaryActor,
                size = 48.dp,
                borderColor = colors.primary.copy(alpha = 0.6f),
                borderWidth = 1.5.dp,
                modifier = Modifier.offset(x = 14.dp, y = 10.dp)
            )
        }

        CastAvatarCircle(
            actor = mainActor,
            size = 64.dp,
            borderColor = mainBorderColor,
            borderWidth = 2.dp
        )
    }
}

@Composable
private fun CastMemberInfo(
    mainName: String,
    secondaryName: String?,
    roleText: String?,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MovieDetailsTheme.colors
    val typography = MovieDetailsTheme.typography

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = mainName,
            style = typography.mediumSmallBody,
            fontWeight = FontWeight.Bold,
            color = if (isFocused) colors.textPrimary else colors.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (!secondaryName.isNullOrBlank()) {
            Text(
                text = secondaryName,
                style = typography.regularCaption2,
                color = colors.textMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!roleText.isNullOrBlank()) {
            Text(
                text = roleText,
                style = typography.regularCaption2,
                color = colors.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CastMemberCard(
    actorData: ActorData,
    onActorClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onActorLongClick: ((String) -> Unit)? = null
) {
    var isInverted by remember { mutableStateOf(false) }
    val voiceActor = actorData.voiceActor
    val hasVoiceActor = voiceActor != null && voiceActor.name.isNotBlank()

    val currentMain = if (isInverted && hasVoiceActor) voiceActor else actorData.actor
    val currentSecondary = if (isInverted && hasVoiceActor) actorData.actor else voiceActor

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = MovieDetailsTheme.colors
    val dimens = MovieDetailsTheme.dimens

    val scaleState = animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = MovieDetailsTokens.FastFocusAnimationSpec,
        label = "castCardScale"
    )

    val border = if (isFocused) {
        BorderStroke(dimens.borderFocus, colors.primary)
    } else {
        BorderStroke(1.dp, colors.border.copy(alpha = 0.3f))
    }

    val roleText = when {
        !actorData.roleString.isNullOrBlank() -> actorData.roleString
        actorData.role == ActorRole.Main -> stringResource(id = R.string.actor_main)
        actorData.role == ActorRole.Supporting -> stringResource(id = R.string.actor_supporting)
        actorData.role == ActorRole.Background -> stringResource(id = R.string.actor_background)
        else -> null
    }

    Column(
        modifier = modifier
            .width(110.dp)
            .zIndex(if (isFocused) 10f else 0f)
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
            }
            .clip(MovieDetailsTokens.ShapeCardMedium)
            .background(if (isFocused) colors.surfaceElevated else colors.surface.copy(alpha = 0.5f))
            .border(border, MovieDetailsTokens.ShapeCardMedium)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = {
                    if (hasVoiceActor) {
                        isInverted = !isInverted
                    } else {
                        onActorClick(currentMain?.name ?: "")
                    }
                },
                onLongClick = {
                    val targetName = currentMain?.name ?: ""
                    if (targetName.isNotBlank()) {
                        if (onActorLongClick != null) {
                            onActorLongClick(targetName)
                        } else {
                            onActorClick(targetName)
                        }
                    }
                }
            )
            .focusable(interactionSource = interactionSource)
            .padding(vertical = 10.dp, horizontal = 6.dp)
            .semantics {
                contentDescription = currentMain?.name ?: ""
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CastDualAvatar(
            mainActor = currentMain,
            secondaryActor = currentSecondary,
            hasVoiceActor = hasVoiceActor,
            isFocused = isFocused
        )

        CastMemberInfo(
            mainName = currentMain?.name ?: "",
            secondaryName = if (hasVoiceActor) currentSecondary?.name else null,
            roleText = roleText,
            isFocused = isFocused
        )
    }
}
