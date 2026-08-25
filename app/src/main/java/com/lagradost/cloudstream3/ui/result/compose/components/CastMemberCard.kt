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
import androidx.compose.foundation.shape.RoundedCornerShape
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
            .background(colors.surfaceElevated)
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
                    fontSize = if (size > 60.dp) 20.sp else 14.sp,
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
        modifier = modifier.size(92.dp),
        contentAlignment = Alignment.Center
    ) {
        if (hasVoiceActor && secondaryActor?.image != null) {
            CastAvatarCircle(
                actor = secondaryActor,
                size = 56.dp,
                borderColor = colors.primary.copy(alpha = 0.7f),
                borderWidth = 1.5.dp,
                modifier = Modifier.offset(x = 18.dp, y = 14.dp)
            )
        }

        CastAvatarCircle(
            actor = mainActor,
            size = 80.dp,
            borderColor = mainBorderColor,
            borderWidth = if (isFocused) 2.5.dp else 1.5.dp
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
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = mainName,
            style = typography.mediumSmallBody,
            fontWeight = FontWeight.Bold,
            color = if (isFocused) colors.textPrimary else colors.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
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
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = roleText,
                    style = typography.regularCaption2,
                    color = colors.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun resolveRoleText(actorData: ActorData): String? {
    if (!actorData.roleString.isNullOrBlank()) {
        return actorData.roleString
    }
    return when (actorData.role) {
        ActorRole.Main -> stringResource(id = R.string.actor_main)
        ActorRole.Supporting -> stringResource(id = R.string.actor_supporting)
        ActorRole.Background -> stringResource(id = R.string.actor_background)
        else -> null
    }
}

private fun resolveActiveActors(
    actorData: ActorData,
    isInverted: Boolean,
    hasVoiceActor: Boolean
): Pair<Actor?, Actor?> {
    return if (isInverted && hasVoiceActor) {
        actorData.voiceActor to actorData.actor
    } else {
        actorData.actor to actorData.voiceActor
    }
}

private fun handleCastLongClick(
    targetName: String,
    onActorClick: (String) -> Unit,
    onActorLongClick: ((String) -> Unit)?
) {
    if (targetName.isBlank()) return
    if (onActorLongClick != null) {
        onActorLongClick(targetName)
    } else {
        onActorClick(targetName)
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

    val (currentMain, currentSecondary) = resolveActiveActors(actorData, isInverted, hasVoiceActor)

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = MovieDetailsTheme.colors
    val dimens = MovieDetailsTheme.dimens

    val scaleState = animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = MovieDetailsTokens.FastFocusAnimationSpec,
        label = "castCardScale"
    )

    val border = if (isFocused) {
        BorderStroke(dimens.borderFocus, colors.primary)
    } else {
        BorderStroke(1.dp, colors.border.copy(alpha = 0.35f))
    }

    val roleText = resolveRoleText(actorData)
    val cardWidth = 130.dp

    Box(
        modifier = modifier
            .width(cardWidth)
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
                    handleCastLongClick(currentMain?.name ?: "", onActorClick, onActorLongClick)
                }
            )
            .focusable(interactionSource = interactionSource)
            .semantics {
                contentDescription = currentMain?.name ?: ""
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(if (isFocused) 10f else 0f)
                .graphicsLayer {
                    scaleX = scaleState.value
                    scaleY = scaleState.value
                }
                .clip(MovieDetailsTokens.ShapeCardMedium)
                .background(if (isFocused) colors.surfaceElevated else colors.surface)
                .border(border, MovieDetailsTokens.ShapeCardMedium)
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
}
