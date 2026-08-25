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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey100
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey850
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryBlack
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryRed
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentBlack90
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentWhite20

data class CloneflixEpisodeItemData(
    val episodeNumber: Int,
    val title: String,
    val durationText: String,
    val synopsis: String,
    val progress: Float = 0f,
    val isCurrent: Boolean = false
)

@Composable
fun CloneflixEpisodeCard(
    item: CloneflixEpisodeItemData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val dimens = CloneflixTheme.dimens
    val colors = CloneflixTheme.colors

    val scale by animateFloatAsState(
        targetValue = if (isFocused) CloneflixTokens.FOCUS_SCALE_FACTOR else 1f,
        label = "episodeCardScale"
    )

    val borderStroke = when {
        isFocused -> BorderStroke(dimens.borderFocus, PrimaryWhite)
        item.isCurrent -> BorderStroke(dimens.borderDefault, PrimaryRed)
        else -> BorderStroke(dimens.borderSubtle, colors.border)
    }

    Surface(
        modifier = modifier
            .width(CloneflixMovieCardDefaults.WidthTop10Medium)
            .scale(scale)
            .border(borderStroke, RoundedCornerShape(CloneflixTokens.RadiusCardMedium))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .semantics {
                role = Role.Button
                contentDescription = item.title
            },
        shape = RoundedCornerShape(CloneflixTokens.RadiusCardMedium),
        color = if (isFocused) Color(0xFF282828) else Color(0xEB1C1C1C)
    ) {
        Column(modifier = Modifier.padding(dimens.spacingS)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CloneflixMovieCardDefaults.HeightTop10Small)
                    .clip(RoundedCornerShape(CloneflixTokens.RadiusCard))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF333333), Color(0xFF141414))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.isCurrent) {
                    Box(
                        modifier = Modifier
                            .size(CloneflixMovieCardDefaults.PlayButtonOverlaySize)
                            .clip(CircleShape)
                            .background(PrimaryRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.cloneflix_ic_play),
                            contentDescription = stringResource(R.string.cloneflix_cd_play),
                            tint = PrimaryWhite,
                            modifier = Modifier.size(CloneflixMovieCardDefaults.PlayIconOverlaySize)
                        )
                    }
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.cloneflix_ic_play),
                        contentDescription = stringResource(R.string.cloneflix_cd_play),
                        tint = Grey200.copy(alpha = CloneflixTokens.ALPHA_MEDIUM),
                        modifier = Modifier.size(dimens.iconM)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(dimens.spacingXs)
                        .background(TransparentBlack90, RoundedCornerShape(CloneflixTokens.RadiusCard))
                        .padding(horizontal = dimens.spacingXs, vertical = dimens.spacingXxs)
                ) {
                    Text(
                        text = item.durationText,
                        style = CloneflixTheme.typography.regularCaption2,
                        color = PrimaryWhite
                    )
                }

                if (item.progress > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(dimens.progressHeight)
                            .background(Color(0x66FFFFFF))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(item.progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(PrimaryRed)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimens.spacingS))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.episodeNumber}. ${item.title}",
                    style = CloneflixTheme.typography.mediumBody,
                    color = if (item.isCurrent) PrimaryRed else PrimaryWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(dimens.spacingXs))

            Text(
                text = item.synopsis,
                style = CloneflixTheme.typography.regularCaption1,
                color = Grey200,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CloneflixEpisodesDrawer(
    seriesTitle: String,
    seasonName: String,
    episodes: List<CloneflixEpisodeItemData>,
    onEpisodeSelected: (CloneflixEpisodeItemData) -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val dimens = CloneflixTheme.dimens

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(dimens.borderDefault, dimens.dividerThickness.let { CloneflixTheme.colors.border }), RoundedCornerShape(topStart = dimens.radiusL, topEnd = dimens.radiusL)),
        shape = RoundedCornerShape(topStart = dimens.radiusL, topEnd = dimens.radiusL),
        color = TransparentBlack90
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dimens.spacingL)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spacing2Xl),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = seriesTitle,
                        style = CloneflixTheme.typography.mediumCaption1,
                        color = PrimaryRed
                    )
                    Text(
                        text = seasonName,
                        style = CloneflixTheme.typography.mediumTitle4,
                        color = PrimaryWhite
                    )
                }

                Box(
                    modifier = Modifier
                        .size(dimens.iconL)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable(onClick = onCloseClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.cloneflix_ic_close),
                        contentDescription = stringResource(R.string.cloneflix_cd_close),
                        tint = PrimaryWhite,
                        modifier = Modifier.size(dimens.iconS)
                    )
                }
            }

            Spacer(modifier = Modifier.height(dimens.spacingL))

            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = dimens.spacing2Xl),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingL),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(episodes) { _, episode ->
                    CloneflixEpisodeCard(
                        item = episode,
                        onClick = { onEpisodeSelected(episode) }
                    )
                }
            }
        }
    }
}

@Preview(name = "Episodes Drawer Preview", showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun EpisodesDrawerPreview() {
    CloneflixTheme {
        val sampleEpisodes = listOf(
            CloneflixEpisodeItemData(
                episodeNumber = 1,
                title = "Sanctuary",
                durationText = "54m",
                synopsis = "A troubled teenager with sumo ancestry enters the sacred world of sumo wrestling.",
                progress = 1.0f,
                isCurrent = false
            ),
            CloneflixEpisodeItemData(
                episodeNumber = 2,
                title = "The Rebel",
                durationText = "48m",
                synopsis = "Kiyoshi refuses to follow tradition and sparks outrage among seasoned stable masters.",
                progress = 0.45f,
                isCurrent = true
            ),
            CloneflixEpisodeItemData(
                episodeNumber = 3,
                title = "The Crucible",
                durationText = "51m",
                synopsis = "An intensive training camp tests the physical limits of every rookie wrestler.",
                progress = 0f,
                isCurrent = false
            )
        )

        CloneflixEpisodesDrawer(
            seriesTitle = "SANCTUARY",
            seasonName = "Season 1 Episodes",
            episodes = sampleEpisodes,
            onEpisodeSelected = {},
            onCloseClick = {}
        )
    }
}
