package com.lagradost.cloudstream3.ui.revamp.compose.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixBadgeType
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCard
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCardElevation
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixContentBadge
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeader
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixLogoVariant
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixLogoView
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMaturityRating
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieInfoBlock
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieInfoOverview
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixMovieInfoRow
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixTop10Badge
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixTop10RankBanner
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixTop10Size
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixVideoQualityBadge
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey300T40
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite

data class CloneflixIconItem(
    val name: String,
    val resId: Int
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloneflixIconsLabelsComposeScreen(
    onItemClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val typography = CloneflixTheme.typography
    val colors = CloneflixTheme.colors
    val dimens = CloneflixTheme.dimens
    val scrollState = rememberScrollState()

    val copyAction: (String) -> Unit = onItemClick ?: { textToCopy ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Cloneflix Asset", textToCopy)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied: $textToCopy", Toast.LENGTH_SHORT).show()
    }

    val iconList = remember {
        listOf(
            CloneflixIconItem("Info", R.drawable.cloneflix_ic_info),
            CloneflixIconItem("Notification", R.drawable.cloneflix_ic_notification),
            CloneflixIconItem("Search", R.drawable.cloneflix_ic_search),
            CloneflixIconItem("Play", R.drawable.cloneflix_ic_play),
            CloneflixIconItem("ArrowRight", R.drawable.cloneflix_ic_arrow_right),
            CloneflixIconItem("ArrowLeft", R.drawable.cloneflix_ic_arrow_left),
            CloneflixIconItem("ArrowUp", R.drawable.cloneflix_ic_arrow_up),
            CloneflixIconItem("ArrowDown", R.drawable.cloneflix_ic_arrow_down),
            CloneflixIconItem("ThumbUp", R.drawable.cloneflix_ic_thumb_up),
            CloneflixIconItem("ThumbDown", R.drawable.cloneflix_ic_thumb_down),
            CloneflixIconItem("PlusWide", R.drawable.cloneflix_ic_plus),
            CloneflixIconItem("Cross", R.drawable.cloneflix_ic_close),
            CloneflixIconItem("Flag", R.drawable.cloneflix_ic_flag),
            CloneflixIconItem("ArrowRightNarrow", R.drawable.cloneflix_ic_arrow_right_narrow),
            CloneflixIconItem("CircleError", R.drawable.cloneflix_ic_error_circle),
            CloneflixIconItem("Question", R.drawable.cloneflix_ic_help),
            CloneflixIconItem("Pensil", R.drawable.cloneflix_ic_edit),
            CloneflixIconItem("Person", R.drawable.cloneflix_ic_person),
            CloneflixIconItem("Account", R.drawable.cloneflix_ic_account),
            CloneflixIconItem("AudioDesc", R.drawable.cloneflix_ic_ad),
            CloneflixIconItem("Facebook", R.drawable.cloneflix_ic_social_fb),
            CloneflixIconItem("Twitter/X", R.drawable.cloneflix_ic_social_x),
            CloneflixIconItem("Instagram", R.drawable.cloneflix_ic_social_ig),
            CloneflixIconItem("YouTube", R.drawable.cloneflix_ic_social_yt)
        )
    }

    val maturityRatings = remember {
        listOf("TV-Y", "TV-Y7", "G", "TV-G", "PG", "TV-PG", "PG-13", "TV-14", "R", "TV-MA", "NC-17")
    }

    val videoQualities = remember {
        listOf("HD", "4K", "HDR", "UltraHD 4K", "Dolby Vision")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(dimens.spacing2Xl)
    ) {
        CloneflixHeader(
            title = "Icons & Labels",
            subtitle = "Design System 2024 • Website & TV Reference",
            iconRes = R.drawable.cloneflix_ic_labels
        )

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "ICONS (24)",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingM))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingM),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingM)
        ) {
            iconList.forEach { iconItem ->
                CloneflixIconCard(
                    iconItem = iconItem,
                    onClick = { copyAction(iconItem.name) }
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))

        Text(
            text = "MATURITY RATINGS",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingM))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingL),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                maturityRatings.forEach { rating ->
                    CloneflixMaturityRating(
                        rating = rating,
                        onClick = { copyAction(rating) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))

        Text(
            text = "VIDEO QUALITY",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingM))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingL),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                videoQualities.forEach { quality ->
                    CloneflixVideoQualityBadge(
                        quality = quality,
                        onClick = { copyAction(quality) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))

        Text(
            text = "LABELS & BADGES",
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
                    text = "Top 10 Badges",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CloneflixTop10Badge(size = CloneflixTop10Size.LARGE)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Large (32dp)", style = typography.regularCaption2, color = colors.textSecondary)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CloneflixTop10Badge(size = CloneflixTop10Size.MEDIUM)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Medium (28dp)", style = typography.regularCaption2, color = colors.textSecondary)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CloneflixTop10Badge(size = CloneflixTop10Size.SMALL)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Small (24dp)", style = typography.regularCaption2, color = colors.textSecondary)
                    }
                }

                CloneflixTop10RankBanner(
                    rankText = "#2 in TV Shows Today",
                    size = CloneflixTop10Size.LARGE,
                    onClick = { copyAction("#2 in TV Shows Today") }
                )

                CloneflixTop10RankBanner(
                    rankText = "#1 in Movies Today",
                    size = CloneflixTop10Size.MEDIUM,
                    onClick = { copyAction("#1 in Movies Today") }
                )

                Spacer(modifier = Modifier.height(dimens.spacingXs))

                Text(
                    text = "Content Status Pills",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CloneflixContentBadge(type = CloneflixBadgeType.RECENTLY_ADDED)
                    CloneflixContentBadge(type = CloneflixBadgeType.NEW_SEASON)
                    CloneflixContentBadge(type = CloneflixBadgeType.LEAVING_SOON)
                }
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))

        Text(
            text = "LOGO VARIANTS",
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
                    text = "Wordmark",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    CloneflixLogoView(variant = CloneflixLogoVariant.WORDMARK_MEDIUM)
                    CloneflixLogoView(variant = CloneflixLogoVariant.WORDMARK_SMALL)
                }

                Spacer(modifier = Modifier.height(dimens.spacingXs))

                Text(
                    text = "Lettermark (Icon / Emblem)",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CloneflixLogoView(variant = CloneflixLogoVariant.LETTERMARK_LARGE)
                    CloneflixLogoView(variant = CloneflixLogoVariant.LETTERMARK_MEDIUM)
                    CloneflixLogoView(variant = CloneflixLogoVariant.LETTERMARK_SMALL)
                }
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))

        Text(
            text = "PATTERNS & PREVIEWS",
            style = typography.sectionHeader,
            color = colors.primary
        )

        Spacer(modifier = Modifier.height(dimens.spacingM))

        Text(
            text = "Inline Metadata Pattern (Figma Section 12)",
            style = typography.mediumBody,
            color = colors.textPrimary
        )

        Spacer(modifier = Modifier.height(dimens.spacingS))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.padding(dimens.spacingL)) {
                CloneflixMovieInfoRow(
                    matchText = "New",
                    maturityRating = "TV-MA",
                    duration = "3 Seasons",
                    quality = "HD",
                    hasAudioDescription = true
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacingL))

        Text(
            text = "Movie Info Block Pattern (Figma Section 13)",
            style = typography.mediumBody,
            color = colors.textPrimary
        )

        Spacer(modifier = Modifier.height(dimens.spacingS))

        CloneflixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CloneflixCardElevation.ELEVATED
        ) {
            Box(modifier = Modifier.padding(dimens.spacingL)) {
                CloneflixMovieInfoBlock(
                    matchScore = "98% Match",
                    releaseYear = "2024",
                    duration = "3 Seasons",
                    maturityRating = "TV-MA",
                    advisories = "smoking, violence",
                    quality = "HD",
                    top10RankText = "#2 in TV Shows Today",
                    hasAudioDescription = true
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacingL))

        Text(
            text = "Full Movie Info Overview (Figma Section 14)",
            style = typography.mediumBody,
            color = colors.textPrimary
        )

        Spacer(modifier = Modifier.height(dimens.spacingS))

        CloneflixMovieInfoOverview(
            title = "House of Ninjas",
            synopsis = "Years after retiring from their formidable ninja lives, a dysfunctional family must return to shadowy missions to counteract a string of looming threats.",
            cast = "Kento Kaku, Yosuke Eguchi, Tae Kimura, more",
            genres = "TV Dramas, Japanese, TV Thrillers",
            moodTags = "Dark, Suspenseful, Exciting",
            matchScore = "New",
            releaseYear = "2024",
            duration = "3 Seasons",
            maturityRating = "TV-MA",
            advisories = "smoking, violence",
            quality = "HD",
            top10RankText = "#2 in TV Shows Today",
            onClick = { copyAction("House of Ninjas Overview") }
        )

        Spacer(modifier = Modifier.height(dimens.spacing3Xl))
    }
}

@Composable
private fun CloneflixIconCard(
    iconItem: CloneflixIconItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = CloneflixTheme.colors
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val activeBorder = if (isFocused) {
        BorderStroke(2.dp, PrimaryWhite)
    } else {
        BorderStroke(1.dp, colors.border)
    }

    Column(
        modifier = modifier
            .width(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Grey300T40 else colors.surface)
            .border(activeBorder, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(vertical = dimens.spacingM, horizontal = dimens.spacingS),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = iconItem.resId),
            contentDescription = iconItem.name,
            tint = if (isFocused) colors.primary else PrimaryWhite,
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.height(dimens.spacingS))

        Text(
            text = iconItem.name,
            style = typography.regularCaption2,
            color = if (isFocused) PrimaryWhite else Grey200,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Preview(name = "Phone Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.PHONE)
@Preview(name = "TV Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.TV_720p)
@Composable
private fun CloneflixIconsLabelsPreview() {
    CloneflixTheme {
        CloneflixIconsLabelsComposeScreen()
    }
}
