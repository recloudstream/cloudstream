package com.lagradost.cloudstream3.ui.revamp.compose.components

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey100
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey800
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryBlack
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryRed
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentBlack90
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentWhite20

@Composable
fun CloneflixNextEpisodeCard(
    episodeNumber: String,
    episodeTitle: String,
    durationText: String,
    synopsis: String,
    onPlayNextClick: () -> Unit,
    modifier: Modifier = Modifier,
    countdownSeconds: Int = 10,
    totalCountdownSeconds: Int = 10,
    onCancelClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        label = "nextEpisodeScale"
    )

    val borderStroke = if (isFocused) {
        BorderStroke(2.dp, PrimaryWhite)
    } else {
        BorderStroke(1.dp, Color(0x33FFFFFF))
    }

    val countdownProgress = (countdownSeconds.toFloat() / totalCountdownSeconds.toFloat()).coerceIn(0f, 1f)

    Surface(
        modifier = modifier
            .width(380.dp)
            .scale(scale)
            .border(borderStroke, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = TransparentBlack90
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { countdownProgress },
                            modifier = Modifier.size(26.dp),
                            color = PrimaryRed,
                            trackColor = Color(0x33FFFFFF),
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = "$countdownSeconds",
                            style = CloneflixTheme.typography.regularCaption2,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryWhite
                        )
                    }

                    Text(
                        text = "Next Episode in ${countdownSeconds}s",
                        style = CloneflixTheme.typography.mediumHeadline2,
                        fontSize = 16.sp,
                        color = PrimaryWhite
                    )
                }

                if (onCancelClick != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onCancelClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.cloneflix_ic_close),
                            contentDescription = "Dismiss",
                            tint = Grey200,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(78.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF2C2C2C), Color(0xFF161616))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0x99000000))
                            .border(1.dp, Color(0x66FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.cloneflix_ic_play),
                            contentDescription = "Play",
                            tint = PrimaryWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Color(0xB2000000), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = durationText,
                            style = CloneflixTheme.typography.regularCaption2,
                            fontSize = 10.sp,
                            color = PrimaryWhite
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = episodeNumber,
                        style = CloneflixTheme.typography.mediumCaption1,
                        color = PrimaryRed
                    )
                    Text(
                        text = episodeTitle,
                        style = CloneflixTheme.typography.mediumBody,
                        color = PrimaryWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = synopsis,
                        style = CloneflixTheme.typography.regularCaption1,
                        color = Grey200,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val playNextInteraction = remember { MutableInteractionSource() }
            val isPlayNextFocused by playNextInteraction.collectIsFocusedAsState()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isPlayNextFocused) Color(0xFFD00812) else PrimaryRed)
                    .then(
                        if (isPlayNextFocused) Modifier.border(2.dp, PrimaryWhite, RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .clickable(
                        interactionSource = playNextInteraction,
                        indication = null,
                        onClick = onPlayNextClick
                    )
                    .focusable(interactionSource = playNextInteraction)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Play Next Episode"
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.cloneflix_ic_play),
                    contentDescription = null,
                    tint = PrimaryWhite,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Play Next Episode",
                    style = CloneflixTheme.typography.mediumBody,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryWhite
                )
            }
        }
    }
}

@Preview(name = "Next Episode Card Preview", showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun NextEpisodeCardPreview() {
    CloneflixTheme {
        Box(
            modifier = Modifier
                .background(Color(0xFF141414))
                .padding(24.dp)
        ) {
            CloneflixNextEpisodeCard(
                episodeNumber = "Episode 2",
                episodeTitle = "The Way of Shadows",
                durationText = "48m",
                synopsis = "A cryptic message from the past sends Haru on a clandestine reconnaissance mission.",
                countdownSeconds = 8,
                totalCountdownSeconds = 10,
                onPlayNextClick = {},
                onCancelClick = {}
            )
        }
    }
}
