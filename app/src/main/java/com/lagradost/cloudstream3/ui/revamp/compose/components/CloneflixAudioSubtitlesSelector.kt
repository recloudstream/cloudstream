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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey450
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryBlack
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryRed
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentBlack90
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentWhite20

@Composable
fun CloneflixTrackOptionItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        label = "trackItemScale"
    )

    val backgroundColor = when {
        isFocused -> TransparentWhite20
        isSelected -> Color(0x1AE50914)
        else -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .then(
                if (isFocused) Modifier.border(1.5.dp, PrimaryWhite, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics {
                role = Role.RadioButton
                contentDescription = "$title, ${if (isSelected) "selected" else "not selected"}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    painter = painterResource(id = R.drawable.cloneflix_ic_check),
                    contentDescription = null,
                    tint = PrimaryWhite,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Text(
            text = title,
            style = CloneflixTheme.typography.regularBody,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) PrimaryWhite else Grey200,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CloneflixAudioSubtitlesSelector(
    audioTracks: List<String>,
    selectedAudio: String,
    onAudioSelected: (String) -> Unit,
    subtitleTracks: List<String>,
    selectedSubtitle: String,
    onSubtitleSelected: (String) -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val audioListState = rememberLazyListState()
    val subtitleListState = rememberLazyListState()

    Surface(
        modifier = modifier
            .width(640.dp)
            .border(BorderStroke(1.dp, Color(0x33FFFFFF)), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = TransparentBlack90
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Audio & Subtitles",
                    style = CloneflixTheme.typography.mediumHeadline2,
                    fontSize = 20.sp,
                    color = PrimaryWhite
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable(onClick = onCloseClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.cloneflix_ic_close),
                        contentDescription = "Close",
                        tint = PrimaryWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = "AUDIO",
                        style = CloneflixTheme.typography.mediumHeadline2,
                        fontSize = 14.sp,
                        color = Grey100,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        state = audioListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(audioTracks) { track ->
                            CloneflixTrackOptionItem(
                                title = track,
                                isSelected = track == selectedAudio,
                                onClick = { onAudioSelected(track) }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color(0x26FFFFFF))
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = "SUBTITLES",
                        style = CloneflixTheme.typography.mediumHeadline2,
                        fontSize = 14.sp,
                        color = Grey100,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        state = subtitleListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(subtitleTracks) { track ->
                            CloneflixTrackOptionItem(
                                title = track,
                                isSelected = track == selectedSubtitle,
                                onClick = { onSubtitleSelected(track) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Audio Subtitles Dialog Preview", showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun AudioSubtitlesDialogPreview() {
    CloneflixTheme {
        val audioTracks = listOf(
            "Japanese [Original]",
            "English",
            "Spanish (Latin America)",
            "French",
            "German"
        )
        val subtitleTracks = listOf(
            "Off",
            "English (CC)",
            "Japanese (CC)",
            "Spanish",
            "Simplified Chinese",
            "Traditional Chinese",
            "Arabic",
            "French",
            "German"
        )

        Box(
            modifier = Modifier
                .background(Color(0xFF141414))
                .padding(24.dp)
        ) {
            CloneflixAudioSubtitlesSelector(
                audioTracks = audioTracks,
                selectedAudio = "Japanese [Original]",
                onAudioSelected = {},
                subtitleTracks = subtitleTracks,
                selectedSubtitle = "English (CC)",
                onSubtitleSelected = {},
                onCloseClick = {}
            )
        }
    }
}
