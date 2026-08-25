package com.lagradost.cloudstream3.ui.revamp.compose.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixBadgeType
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCard
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCardElevation
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixEpisodeItem
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixExpandedMoviePreview
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeader
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMaturityRating
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieBlockRow
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieCard
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieCardItem
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieCardSize
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieCardType
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixVideoQualityBadge
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey100
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey800
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey850
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite

@Composable
fun CloneflixMoviePreviewComposeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val typography = CloneflixTheme.typography
    val colors = CloneflixTheme.colors
    val dimens = CloneflixTheme.dimens
    val scrollState = rememberScrollState()

    var currentEpisode by remember { mutableIntStateOf(1) }

    fun showToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    val trendingMovies = remember {
        listOf(
            CloneflixMovieCardItem("House of Ninjas", showLogo = true, badge = CloneflixBadgeType.NEW_SEASON),
            CloneflixMovieCardItem("Rurouni Kenshin", showLogo = true, badge = CloneflixBadgeType.RECENTLY_ADDED),
            CloneflixMovieCardItem("Avatar: The Last Airbender", showLogo = true),
            CloneflixMovieCardItem("One Piece", showLogo = true, badge = CloneflixBadgeType.NEW_SEASON),
            CloneflixMovieCardItem("Stranger Things", showLogo = true),
            CloneflixMovieCardItem("Squid Game", showLogo = true, badge = CloneflixBadgeType.LEAVING_SOON)
        )
    }

    val continueWatchingMovies = remember {
        listOf(
            CloneflixMovieCardItem("House of Ninjas", type = CloneflixMovieCardType.CONTINUE_WATCHING, progress = 0.75f, showLogo = true),
            CloneflixMovieCardItem("The Witcher", type = CloneflixMovieCardType.CONTINUE_WATCHING, progress = 0.40f, showLogo = true),
            CloneflixMovieCardItem("Cyberpunk: Edgerunners", type = CloneflixMovieCardType.CONTINUE_WATCHING, progress = 0.90f, showLogo = true),
            CloneflixMovieCardItem("Arcane", type = CloneflixMovieCardType.CONTINUE_WATCHING, progress = 0.55f, showLogo = true)
        )
    }

    val top10Movies = remember {
        listOf(
            CloneflixMovieCardItem("House of Ninjas", type = CloneflixMovieCardType.TOP10, top10Rank = 1, showLogo = true, badge = CloneflixBadgeType.NEW_SEASON),
            CloneflixMovieCardItem("Avatar", type = CloneflixMovieCardType.TOP10, top10Rank = 2, showLogo = true),
            CloneflixMovieCardItem("Damsel", type = CloneflixMovieCardType.TOP10, top10Rank = 3, showLogo = true, badge = CloneflixBadgeType.RECENTLY_ADDED),
            CloneflixMovieCardItem("Spaceman", type = CloneflixMovieCardType.TOP10, top10Rank = 4, showLogo = true),
            CloneflixMovieCardItem("The Gentlemen", type = CloneflixMovieCardType.TOP10, top10Rank = 5, showLogo = true),
            CloneflixMovieCardItem("Code 8 Part II", type = CloneflixMovieCardType.TOP10, top10Rank = 6, showLogo = true),
            CloneflixMovieCardItem("Shogun", type = CloneflixMovieCardType.TOP10, top10Rank = 7, showLogo = true)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(dimens.spacing2Xl)
    ) {
        CloneflixHeader(
            title = "Movie Preview",
            subtitle = "Movie Cards • Samples • Carousels • Episode Lists • Detail Previews",
            iconRes = R.drawable.cloneflix_ic_play
        )

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "MOVIE CARDS (VARIANTS)",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingM))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingL),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingL)
            ) {
                Text(
                    text = "Standard & More Like This",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        CloneflixMovieCard(
                            title = "Default Medium",
                            type = CloneflixMovieCardType.DEFAULT,
                            size = CloneflixMovieCardSize.MEDIUM,
                            onClick = { showToast("Default Medium Clicked") }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Default (218x123dp)", style = typography.regularCaption2, color = Grey200)
                    }

                    Column {
                        CloneflixMovieCard(
                            title = "More Like This",
                            type = CloneflixMovieCardType.MORE_LIKE_THIS,
                            runtime = "2h 18m",
                            showLogo = true,
                            onClick = { showToast("More Like This Clicked") }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "More Like This (236x132dp)", style = typography.regularCaption2, color = Grey200)
                    }

                    Column {
                        CloneflixMovieCard(
                            title = "With Play Icon",
                            type = CloneflixMovieCardType.MORE_LIKE_THIS_WITH_PLAY,
                            runtime = "2h 18m",
                            showLogo = true,
                            onClick = { showToast("Play Clicked") }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "With Play Button", style = typography.regularCaption2, color = Grey200)
                    }
                }

                Spacer(modifier = Modifier.height(dimens.spacingS))

                Text(
                    text = "Episode, Player & Trailer Previews",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        CloneflixMovieCard(
                            title = "Episode Thumbnail",
                            type = CloneflixMovieCardType.EPISODE,
                            size = CloneflixMovieCardSize.SMALL,
                            onClick = { showToast("Episode Clicked") }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Episode (128x72dp)", style = typography.regularCaption2, color = Grey200)
                    }

                    Column {
                        CloneflixMovieCard(
                            title = "Player Timestamp",
                            type = CloneflixMovieCardType.PLAYER_PREVIEW,
                            timestamp = "51:29",
                            onClick = { showToast("Player Preview Clicked") }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Player Preview (51:29)", style = typography.regularCaption2, color = Grey200)
                    }

                    Column {
                        CloneflixMovieCard(
                            title = "Trailer",
                            subtitle = "Season 1 Trailer 1: House of Ninjas",
                            type = CloneflixMovieCardType.TRAILER,
                            onClick = { showToast("Trailer Clicked") }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Trailer Preview", style = typography.regularCaption2, color = Grey200)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))

        Text(
            text = "SAMPLES (HOME PAGE CARDS)",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingM))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingL),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingL)
            ) {
                Text(
                    text = "Badges & Branding Variations",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloneflixMovieCard(
                        title = "Default",
                        type = CloneflixMovieCardType.DEFAULT,
                        onClick = { showToast("Default Card") }
                    )

                    CloneflixMovieCard(
                        title = "With Logo",
                        type = CloneflixMovieCardType.DEFAULT,
                        showLogo = true,
                        onClick = { showToast("Card With Logo") }
                    )

                    CloneflixMovieCard(
                        title = "Recently Added",
                        type = CloneflixMovieCardType.DEFAULT,
                        showLogo = true,
                        badge = CloneflixBadgeType.RECENTLY_ADDED,
                        onClick = { showToast("Recently Added Card") }
                    )

                    CloneflixMovieCard(
                        title = "New Season",
                        type = CloneflixMovieCardType.DEFAULT,
                        showLogo = true,
                        badge = CloneflixBadgeType.NEW_SEASON,
                        onClick = { showToast("New Season Card") }
                    )

                    CloneflixMovieCard(
                        title = "Leaving Soon",
                        type = CloneflixMovieCardType.DEFAULT,
                        showLogo = true,
                        badge = CloneflixBadgeType.LEAVING_SOON,
                        onClick = { showToast("Leaving Soon Card") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))

        Text(
            text = "PATTERNS: MOVIE BLOCKS",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingM))

        CloneflixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CloneflixCardElevation.ELEVATED
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.spacingL),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingL)
            ) {
                CloneflixMovieBlockRow(
                    title = "Trending Now",
                    items = trendingMovies,
                    onItemClick = { showToast("Selected: ${it.title}") }
                )

                CloneflixMovieBlockRow(
                    title = "Continue Watching",
                    items = continueWatchingMovies,
                    onItemClick = { showToast("Resuming: ${it.title}") }
                )

                CloneflixMovieBlockRow(
                    title = "Top 10 TV Shows Today",
                    items = top10Movies,
                    onItemClick = { showToast("Top 10: #${it.top10Rank} ${it.title}") }
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))

        Text(
            text = "PATTERNS: LIST OF EPISODES",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingM))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingL),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CloneflixEpisodeItem(
                    episodeNumber = 1,
                    title = "The Offer",
                    duration = "55m",
                    synopsis = "While Haru Tawara develops a crush on a mysterious young woman at work, an unusual opportunity arises at his father's financially struggling brewery.",
                    isCurrent = currentEpisode == 1,
                    onClick = {
                        currentEpisode = 1
                        showToast("Playing Episode 1")
                    }
                )

                CloneflixEpisodeItem(
                    episodeNumber = 2,
                    title = "The Trail",
                    duration = "52m",
                    synopsis = "Haru accompanies Karen to investigate a whistleblower's apartment. Meanwhile, several other Tawaras are tempted to step out of their ordinary lives.",
                    isCurrent = currentEpisode == 2,
                    onClick = {
                        currentEpisode = 2
                        showToast("Playing Episode 2")
                    }
                )

                CloneflixEpisodeItem(
                    episodeNumber = 3,
                    title = "The Flower",
                    duration = "50m",
                    synopsis = "As Yoko investigates the Gentenkai group, Sohei receives an unexpected visitor. Haru uncovers a hidden truth about the recent incident.",
                    isCurrent = currentEpisode == 3,
                    onClick = {
                        currentEpisode = 3
                        showToast("Playing Episode 3")
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))

        Text(
            text = "PATTERNS: MOVIE PREVIEW (EXPANDED & DETAIL)",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingM))

        CloneflixExpandedMoviePreview(
            title = "House of Ninjas",
            matchScore = "New",
            maturityRating = "TV-MA",
            duration = "3 Seasons",
            quality = "HD",
            genres = listOf("Violent", "Dark", "Action"),
            synopsis = "Years after retiring from their formidable ninja lives, a dysfunctional family must return to shadowy missions to counteract a string of looming threats.",
            onPlayClick = { showToast("Play Clicked") },
            onAddClick = { showToast("Added to My List") },
            onThumbUpClick = { showToast("Liked") },
            onMuteClick = { showToast("Mute Toggled") }
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        Text(
            text = "More Like This Detail Card (Figma Type=Movie Preview Other)",
            style = typography.mediumBody,
            color = colors.textPrimary
        )

        Spacer(modifier = Modifier.height(dimens.spacingS))

        CloneflixMoreLikeThisDetailCard(
            title = "Rurouni Kenshin: The Beginning",
            runtime = "2h 18m",
            maturityRating = "TV-MA",
            quality = "HD",
            year = "2021",
            synopsis = "Before he was a protector, Kenshin was a fearsome assassin known as Battosai. But when he meets the gentle Tomoe Yukishiro, his story begins to change.",
            onAddClick = { showToast("Added to List") }
        )

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))
    }
}

@Composable
fun CloneflixMoreLikeThisDetailCard(
    title: String,
    runtime: String,
    maturityRating: String,
    quality: String,
    year: String,
    synopsis: String,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = CloneflixTheme.colors
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens

    Box(
        modifier = modifier
            .width(260.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Grey850)
            .border(BorderStroke(0.5.dp, colors.border), RoundedCornerShape(6.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CloneflixMovieCard(
                title = title,
                type = CloneflixMovieCardType.MORE_LIKE_THIS,
                runtime = runtime,
                showLogo = true,
                modifier = Modifier.fillMaxWidth()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingM)
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
                        CloneflixMaturityRating(rating = maturityRating)
                        CloneflixVideoQualityBadge(quality = quality)
                        Text(
                            text = year,
                            style = typography.regularCaption1,
                            color = Grey100
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Grey800)
                            .border(BorderStroke(1.dp, PrimaryWhite.copy(alpha = 0.6f)), CircleShape)
                            .clickable(onClick = onAddClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.cloneflix_ic_plus),
                            contentDescription = "Add",
                            tint = PrimaryWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = synopsis,
                    style = typography.regularSmallBody,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = Grey200,
                    maxLines = 3
                )
            }
        }
    }
}

@Preview(name = "Phone Movie Preview Screen", showBackground = true, backgroundColor = 0xFF141414, device = Devices.PHONE)
@Preview(
    name = "TV Movie Preview Screen",
    showBackground = true,
    backgroundColor = 0xFF141414,
    device = "spec:width=1920dp,height=1080dp,dpi=320,orientation=landscape"
)
@Composable
private fun CloneflixMoviePreviewScreenPreview() {
    CloneflixTheme {
        CloneflixMoviePreviewComposeScreen()
    }
}
