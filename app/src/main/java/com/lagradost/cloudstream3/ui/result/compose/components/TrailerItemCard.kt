package com.lagradost.cloudstream3.ui.result.compose.components

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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.compose.MovieTrailerData
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme
import com.lagradost.cloudstream3.ui.result.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.result.compose.theme.TransparentBlack60

@Composable
fun TrailerItemCard(
    trailer: MovieTrailerData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val typography = MovieDetailsTheme.typography
    val dimens = MovieDetailsTheme.dimens
    val colors = MovieDetailsTheme.colors

    val scaleState = animateFloatAsState(
        targetValue = if (isFocused) MovieDetailsTokens.FOCUS_SCALE_FACTOR else 1f,
        animationSpec = MovieDetailsTokens.FastFocusAnimationSpec,
        label = "trailerScale"
    )

    val border = if (isFocused) BorderStroke(dimens.borderFocus, colors.primary) else BorderStroke(dimens.borderSubtle, colors.border)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isFocused) 10f else 0f)
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
            }
            .clip(MovieDetailsTokens.ShapeCardMedium)
            .background(colors.surfaceElevated)
            .border(border, MovieDetailsTokens.ShapeCardMedium)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(colors.surface)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(TransparentBlack60)
                    .border(BorderStroke(1.5.dp, PrimaryWhite), CircleShape)
                    .align(Alignment.Center)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_play_arrow_24),
                    contentDescription = null,
                    tint = PrimaryWhite,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.Center)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacingM),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = trailer.title,
                style = typography.mediumBody,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = trailer.runtime,
                style = typography.regularCaption2,
                color = colors.textSecondary
            )
        }
    }
}
