package com.lagradost.cloudstream3.ui.result.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme
import com.lagradost.cloudstream3.ui.result.compose.theme.PrimaryBlack
import com.lagradost.cloudstream3.ui.result.compose.theme.PrimaryWhite

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroPlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = stringResource(id = R.string.play_movie_button),
    progress: Float? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = MovieDetailsTheme.colors

    val scaleState = animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = MovieDetailsTokens.FastFocusAnimationSpec,
        label = "heroPlayBtnScale"
    )

    val background = if (isFocused) colors.primary else colors.primary.copy(alpha = 0.92f)
    val contentColor = colors.onPrimary
    val border = if (isFocused) BorderStroke(2.dp, colors.onBackground) else null

    Box(
        modifier = modifier
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .semantics {
                contentDescription = text
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(42.dp)
                .zIndex(if (isFocused) 10f else 0f)
                .graphicsLayer {
                    scaleX = scaleState.value
                    scaleY = scaleState.value
                }
                .clip(MovieDetailsTokens.ShapeCardSmall)
                .background(background)
                .then(if (border != null) Modifier.border(border, MovieDetailsTokens.ShapeCardSmall) else Modifier)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_play_arrow_24),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )

                Text(
                    text = text,
                    color = contentColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (progress != null && progress > 0.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomStart)
                        .background(PrimaryBlack.copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(PrimaryWhite)
                    )
                }
            }
        }
    }
}

@Composable
fun HeroTrailerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = MovieDetailsTheme.colors

    val scaleState = animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = MovieDetailsTokens.FastFocusAnimationSpec,
        label = "heroTrailerBtnScale"
    )

    val background = if (isFocused) colors.primary else colors.surfaceElevated.copy(alpha = 0.8f)
    val contentColor = if (isFocused) colors.onPrimary else colors.textPrimary
    val border = if (isFocused) BorderStroke(2.dp, colors.onBackground) else BorderStroke(1.dp, colors.border)

    Box(
        modifier = modifier
            .height(40.dp)
            .zIndex(if (isFocused) 10f else 0f)
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
            }
            .clip(MovieDetailsTokens.ShapeCardSmall)
            .background(background)
            .border(border, MovieDetailsTokens.ShapeCardSmall)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_baseline_play_arrow_24),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )

            Text(
                text = text,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun CircleActionButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = MovieDetailsTheme.colors
    val dimens = MovieDetailsTheme.dimens

    val scaleState = animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        animationSpec = MovieDetailsTokens.FastFocusAnimationSpec,
        label = "circleBtnScale"
    )

    val background = if (isFocused) colors.primary else colors.surfaceElevated.copy(alpha = 0.75f)
    val border = if (isFocused) BorderStroke(2.dp, colors.onBackground) else BorderStroke(1.dp, colors.border)
    val iconTint = if (isFocused) colors.onPrimary else colors.textPrimary

    Box(
        modifier = modifier
            .size(40.dp)
            .zIndex(if (isFocused) 10f else 0f)
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
            }
            .clip(CircleShape)
            .background(background)
            .border(border, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .semantics {
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(dimens.iconM)
        )
    }
}
