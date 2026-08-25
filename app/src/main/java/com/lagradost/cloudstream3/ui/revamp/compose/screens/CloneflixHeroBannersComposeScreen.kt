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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCard
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCardElevation
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeader
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeroBanner
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeroBannerType
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHomePageHeroPattern
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieInfoOverview
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixTitlePreview
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixTitlePreviewSize
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme

@Composable
fun CloneflixHeroBannersComposeScreen(
    onActionClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val typography = CloneflixTheme.typography
    val colors = CloneflixTheme.colors
    val dimens = CloneflixTheme.dimens
    val scrollState = rememberScrollState()

    val notifyAction: (String) -> Unit = onActionClick ?: { actionName ->
        Toast.makeText(context, actionName, Toast.LENGTH_SHORT).show()
    }

    var isHomeAudioMuted by remember { mutableStateOf(false) }
    var isModalAudioMuted by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(dimens.spacing2Xl)
    ) {
        CloneflixHeader(
            title = "Hero Banners",
            subtitle = "Title Preview • Hero Banners • Page Patterns",
            iconRes = R.drawable.cloneflix_ic_play
        )

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "TITLE PREVIEW SIZES",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CloneflixCardElevation.SURFACE
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingXl),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Size = Large (518 × 406)",
                    style = typography.sectionHeader,
                    color = colors.textSecondary
                )
                CloneflixTitlePreview(
                    size = CloneflixTitlePreviewSize.LARGE,
                    title = "HOUSE OF NINJAS",
                    top10RankText = "#2 in TV Shows Today",
                    synopsis = "Years after retiring from their formidable ninja lives, a dysfunctional family must return to shadowy missions to counteract a string of looming threats.",
                    onPlayClick = { notifyAction("Play: House of Ninjas") },
                    onMoreInfoClick = { notifyAction("More Info: House of Ninjas") }
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CloneflixCardElevation.SURFACE
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingXl),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Size = Medium (518 × 360)",
                    style = typography.sectionHeader,
                    color = colors.textSecondary
                )
                CloneflixTitlePreview(
                    size = CloneflixTitlePreviewSize.MEDIUM,
                    title = "HOUSE OF NINJAS",
                    synopsis = "Years after retiring from their formidable ninja lives, a dysfunctional family must return to shadowy missions to counteract a string of looming threats.",
                    onPlayClick = { notifyAction("Play: House of Ninjas") },
                    onMoreInfoClick = { notifyAction("More Info: House of Ninjas") }
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CloneflixCardElevation.SURFACE
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingXl),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Size = Small Variants (Small1, Small2, Small3)",
                    style = typography.sectionHeader,
                    color = colors.textSecondary
                )

                Text(
                    text = "Small 1: Logo + Action Buttons",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                CloneflixTitlePreview(
                    size = CloneflixTitlePreviewSize.SMALL1,
                    title = "HOUSE OF NINJAS",
                    onPlayClick = { notifyAction("Play: House of Ninjas") },
                    onMoreInfoClick = { notifyAction("More Info: House of Ninjas") }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Small 2: Title Banner Box (518 × 207)",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                CloneflixTitlePreview(
                    size = CloneflixTitlePreviewSize.SMALL2,
                    title = "HOUSE OF NINJAS"
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Small 3: Compact Title Box (340 × 136)",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                CloneflixTitlePreview(
                    size = CloneflixTitlePreviewSize.SMALL3,
                    title = "HOUSE OF NINJAS"
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "HERO BANNER VARIANTS",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CloneflixCardElevation.SURFACE
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingXl),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Type = HomePage (Full Backdrop Scrims, Title Preview, Rating, Audio Toggle)",
                    style = typography.sectionHeader,
                    color = colors.textSecondary
                )

                CloneflixHeroBanner(
                    type = CloneflixHeroBannerType.HOME_PAGE,
                    title = "HOUSE OF NINJAS",
                    top10RankText = "#2 in TV Shows Today",
                    synopsis = "Years after retiring from their formidable ninja lives, a dysfunctional family must return to shadowy missions to counteract a string of looming threats.",
                    maturityRating = "16+",
                    onPlayClick = { notifyAction("Playing House of Ninjas") },
                    onMoreInfoClick = { notifyAction("Opening House of Ninjas details") },
                    onAudioToggle = {
                        isHomeAudioMuted = !isHomeAudioMuted
                        notifyAction(if (isHomeAudioMuted) "Audio Muted" else "Audio Unmuted")
                    },
                    isMuted = isHomeAudioMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CloneflixCardElevation.SURFACE
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingXl),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Type = MoviePreview (Modal Header with Close, Title Logo, Play, +, Like, Volume)",
                    style = typography.sectionHeader,
                    color = colors.textSecondary
                )

                CloneflixHeroBanner(
                    type = CloneflixHeroBannerType.MOVIE_PREVIEW,
                    title = "HOUSE OF NINJAS",
                    onPlayClick = { notifyAction("Playing Movie Preview") },
                    onCloseClick = { notifyAction("Closed Movie Preview Modal") },
                    onAddToListClick = { notifyAction("Added House of Ninjas to My List") },
                    onThumbsUpClick = { notifyAction("Liked House of Ninjas") },
                    onAudioToggle = {
                        isModalAudioMuted = !isModalAudioMuted
                        notifyAction(if (isModalAudioMuted) "Preview Audio Muted" else "Preview Audio Unmuted")
                    },
                    isMuted = isModalAudioMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CloneflixCardElevation.SURFACE
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingXl),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Type = LandingPage (Vignette, Header, Central Headline, CTA Button)",
                    style = typography.sectionHeader,
                    color = colors.textSecondary
                )

                CloneflixHeroBanner(
                    type = CloneflixHeroBannerType.LANDING_PAGE,
                    onPlayClick = { notifyAction("Get Started / Sign In clicked") }
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CloneflixCardElevation.SURFACE
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingXl),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Type = AuthenticationPage (Vignette, Center Sign-In Box)",
                    style = typography.sectionHeader,
                    color = colors.textSecondary
                )

                CloneflixHeroBanner(
                    type = CloneflixHeroBannerType.AUTHENTICATION_PAGE,
                    onPlayClick = { notifyAction("Sign In clicked") }
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "COMPOSED PATTERNS (HERO BANNER+)",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CloneflixCardElevation.SURFACE
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingXl),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Pattern = HomePage (Top Navigation Bar + Hero Banner + Trending Shelf Row)",
                    style = typography.sectionHeader,
                    color = colors.textSecondary
                )

                CloneflixHomePageHeroPattern(
                    onPlayClick = { notifyAction("Home Pattern: Play Video") },
                    onMoreInfoClick = { notifyAction("Home Pattern: More Info") },
                    onSearchClick = { notifyAction("Home Pattern: Open Search") },
                    onNotificationClick = { notifyAction("Home Pattern: Open Notifications") },
                    onProfileClick = { notifyAction("Home Pattern: Open Profile Menu") },
                    onCardClick = { movie -> notifyAction("Selected Trending: $movie") }
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CloneflixCardElevation.SURFACE
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingXl),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Pattern = MoviePreview (Header Hero + Integrated Details Overview)",
                    style = typography.sectionHeader,
                    color = colors.textSecondary
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceElevated)
                ) {
                    CloneflixHeroBanner(
                        type = CloneflixHeroBannerType.MOVIE_PREVIEW,
                        title = "HOUSE OF NINJAS",
                        onPlayClick = { notifyAction("Modal: Play Video") },
                        onCloseClick = { notifyAction("Modal: Closed") },
                        onAddToListClick = { notifyAction("Modal: Added to My List") },
                        onThumbsUpClick = { notifyAction("Modal: Liked") },
                        onAudioToggle = {
                            isModalAudioMuted = !isModalAudioMuted
                            notifyAction(if (isModalAudioMuted) "Audio Muted" else "Audio Unmuted")
                        },
                        isMuted = isModalAudioMuted
                    )

                    CloneflixMovieInfoOverview(
                        title = "House of Ninjas",
                        synopsis = "Years after retiring from their formidable ninja lives, a dysfunctional family must return to shadowy missions to counteract a string of looming threats.",
                        cast = "Kento Kaku, Yosuke Eguchi, Tae Kimura, more",
                        genres = "TV Dramas, Japanese, TV Thrillers",
                        moodTags = "Dark, Suspenseful, Exciting",
                        matchScore = "98% Match",
                        releaseYear = "2024",
                        duration = "1 Season",
                        maturityRating = "TV-MA",
                        advisories = "smoking, violence",
                        quality = "HD",
                        top10RankText = "#2 in TV Shows Today",
                        onClick = { notifyAction("Overview clicked") },
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Preview(
    name = "Hero Banners Screen Preview",
    showBackground = true,
    backgroundColor = 0xFF141414,
    device = "spec:width=1920dp,height=1080dp,dpi=320,orientation=landscape"
)
@Composable
private fun HeroBannersScreenPreview() {
    CloneflixTheme {
        CloneflixHeroBannersComposeScreen()
    }
}
