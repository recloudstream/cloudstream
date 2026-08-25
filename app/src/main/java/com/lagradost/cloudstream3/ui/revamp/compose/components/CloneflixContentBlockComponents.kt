package com.lagradost.cloudstream3.ui.revamp.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Blue100
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey10
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey100
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey400
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey600
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey700
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryBlack
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryRed
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite
import com.lagradost.cloudstream3.ui.revamp.compose.theme.TransparentWhite20

@Composable
fun CloneflixLandingHeader(
    onSignInClick: () -> Unit = {},
    selectedLanguage: String = "English",
    onLanguageSelected: (String) -> Unit = {},
    languageOptions: List<String> = listOf("English", "Español", "Français", "Deutsch", "日本語"),
    modifier: Modifier = Modifier
) {
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(horizontal = dimens.spacing2Xl, vertical = dimens.spacingL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "CLONEFLIX",
            style = typography.logoBebas,
            fontSize = 28.sp,
            color = PrimaryRed
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
        ) {
            CloneflixDropdown(
                options = languageOptions,
                selectedOption = selectedLanguage,
                onOptionSelected = onLanguageSelected,
                variant = CloneflixDropdownVariant.COMPACT,
                width = 120.dp
            )

            CloneflixButton(
                text = "Sign In",
                onClick = onSignInClick,
                variant = CloneflixButtonVariant.PRIMARY,
                size = CloneflixButtonSize.SMALL
            )
        }
    }
}

@Composable
fun CloneflixHomeHeader(
    selectedNavIndex: Int = 0,
    onNavItemSelected: (Int) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    navItems: List<String> = listOf("Home", "TV Shows", "Movies", "New & Popular", "My List", "Browse by Languages"),
    modifier: Modifier = Modifier
) {
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.95f))
            .padding(horizontal = dimens.spacing2Xl, vertical = dimens.spacingM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXl)
        ) {
            Text(
                text = "CLONEFLIX",
                style = typography.logoBebas,
                fontSize = 24.sp,
                color = PrimaryRed
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingL),
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEachIndexed { index, item ->
                    val isSelected = selectedNavIndex == index
                    val interactionSource = remember { MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()

                    Text(
                        text = item,
                        style = if (isSelected) typography.mediumBody else typography.regularSmallBody,
                        color = when {
                            isSelected -> PrimaryWhite
                            isFocused -> PrimaryWhite
                            else -> Grey10
                        },
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .focusable(interactionSource = interactionSource)
                            .clickable { onNavItemSelected(index) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
        ) {
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.cloneflix_ic_search),
                    contentDescription = "Search",
                    tint = PrimaryWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onNotificationsClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.cloneflix_ic_notification),
                    contentDescription = "Notifications",
                    tint = PrimaryWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onProfileClick() }
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(PrimaryRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.cloneflix_ic_person),
                        contentDescription = "Profile Avatar",
                        tint = PrimaryWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Icon(
                    painter = painterResource(id = R.drawable.cloneflix_ic_arrow_down),
                    contentDescription = "Profile Menu",
                    tint = PrimaryWhite,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun CloneflixFaqItem(
    question: String,
    answer: String,
    isOpen: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = CloneflixTheme.typography
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val rotationAngle by animateFloatAsState(
        targetValue = if (isOpen) 45f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "FaqIconRotation"
    )

    val itemBackgroundColor = when {
        isFocused -> Color(0xFF414141)
        isOpen -> Color(0xFF2D2D2D)
        else -> Color(0xFF2D2D2D)
    }

    val border = if (isFocused) BorderStroke(2.dp, PrimaryWhite) else null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 250))
    ) {
        Surface(
            onClick = onToggle,
            color = itemBackgroundColor,
            shape = RoundedCornerShape(2.dp),
            border = border,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .focusable(interactionSource = interactionSource)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = question,
                    style = typography.mediumTitle3,
                    fontSize = 20.sp,
                    color = PrimaryWhite,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    painter = painterResource(id = R.drawable.cloneflix_ic_plus),
                    contentDescription = if (isOpen) "Collapse answer" else "Expand answer",
                    tint = PrimaryWhite,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotationAngle)
                )
            }
        }

        AnimatedVisibility(visible = isOpen) {
            Column {
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    color = Color(0xFF2D2D2D),
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = answer,
                        style = typography.regularBody,
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
                        color = PrimaryWhite,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CloneflixFaqSection(
    modifier: Modifier = Modifier
) {
    val typography = CloneflixTheme.typography

    val faqList = remember {
        listOf(
            "What is Netflix?" to "Netflix is a streaming service that offers a wide variety of award-winning TV shows, movies, anime, documentaries, and more on thousands of internet-connected devices.\n\nYou can watch as much as you want, whenever you want – all for one low monthly price. There's always something new to discover and new TV shows and movies are added every week!",
            "How much does Netflix cost?" to "Watch Netflix on your smartphone, tablet, Smart TV, laptop, or streaming device, all for one fixed monthly fee. Plans range from standard with ads to premium. No extra costs, no contracts.",
            "Where can I watch?" to "Watch anywhere, anytime. Sign in with your Netflix account to watch instantly on the web at netflix.com from your personal computer or on any internet-connected device that offers the Netflix app, including smart TVs, smartphones, tablets, streaming media players and game consoles.\n\nYou can also download your favorite shows with the iOS or Android app. Use downloads to watch while you're on the go and without an internet connection.",
            "How do I cancel?" to "Netflix is flexible. There are no pesky contracts and no commitments. You can easily cancel your account online in two clicks. There are no cancellation fees – start or stop your account anytime.",
            "What can I watch on Netflix?" to "Netflix has an extensive library of feature films, documentaries, TV shows, anime, award-winning Netflix originals, and more. Watch as much as you want, anytime you want.",
            "Is Netflix good for kids?" to "The Netflix Kids experience is included in your membership to give parents control while kids enjoy family-friendly TV shows and movies in their own space.\n\nKids profiles come with PIN-protected parental controls that let you restrict the maturity rating of content kids can watch and block specific titles you don’t want kids to see."
        )
    }

    var openIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Frequently Asked Questions",
            style = typography.headerDisplay,
            fontSize = 32.sp,
            color = PrimaryWhite,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            faqList.forEachIndexed { index, (q, a) ->
                CloneflixFaqItem(
                    question = q,
                    answer = a,
                    isOpen = openIndex == index,
                    onToggle = {
                        openIndex = if (openIndex == index) -1 else index
                    }
                )
            }
        }
    }
}

@Composable
fun CloneflixTvPreviewCard(
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF141414),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, TransparentWhite20),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F0F0F))
                    .border(2.dp, Grey700, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF2A1010), Color(0xFF141414), Color(0xFF0A0A0A))
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "CLONEFLIX",
                            style = CloneflixTheme.typography.logoBebas,
                            fontSize = 36.sp,
                            color = PrimaryRed
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ultra HD 4K Streaming",
                            style = CloneflixTheme.typography.regularCaption1,
                            color = Grey10
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(6.dp)
                    .background(Grey600, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
            )
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(3.dp)
                    .background(Grey700, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
fun CloneflixMacPreviewCard(
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF141414),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, TransparentWhite20),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1F1F1F))
                    .border(1.5.dp, Grey700, RoundedCornerShape(6.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .background(Color(0xFF2B2B2B))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFFFF5F56), CircleShape))
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFFFFBD2E), CircleShape))
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFF27C93F), CircleShape))
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF1E1B4B), Color(0xFF111827), Color(0xFF0F172A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Stream on Web & Desktop",
                                style = CloneflixTheme.typography.boldTitle2,
                                fontSize = 18.sp,
                                color = PrimaryWhite
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Watch anywhere seamlessly",
                                style = CloneflixTheme.typography.regularCaption1,
                                color = Grey100
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .height(6.dp)
                    .background(Grey600, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            )
        }
    }
}

@Composable
fun CloneflixDownloadProgressCard(
    title: String = "Stranger Things",
    statusText: String = "Downloading...",
    modifier: Modifier = Modifier
) {
    Surface(
        color = PrimaryBlack,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, Color(0xFF404040)),
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFE50914), Color(0xFF1F1F1F))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ST",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = PrimaryWhite
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = CloneflixTheme.typography.mediumBody,
                    fontSize = 14.sp,
                    color = PrimaryWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = statusText,
                    style = CloneflixTheme.typography.regularCaption1,
                    fontSize = 12.sp,
                    color = Blue100
                )
            }

            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Blue100,
                    strokeWidth = 2.5.dp
                )
            }
        }
    }
}

@Composable
fun CloneflixMobilePreviewCard(
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF141414),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, TransparentWhite20),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 280.dp)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1E1E1E))
                .border(2.dp, Grey700, RoundedCornerShape(24.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460))
                        )
                    )
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(70.dp)
                        .height(14.dp)
                        .background(Color.Black, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                )

                CloneflixDownloadProgressCard(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
fun CloneflixKidsPreviewCard(
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF141414),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, TransparentWhite20),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFFFB703), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🐯", fontSize = 22.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFFFB8500), CircleShape)
                            .border(2.dp, PrimaryWhite, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🚀", fontSize = 26.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF219EBC), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🦄", fontSize = 22.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Kids Profile Experience",
                    style = CloneflixTheme.typography.boldTitle2,
                    fontSize = 18.sp,
                    color = PrimaryWhite
                )

                Text(
                    text = "Family-friendly entertainment & parental controls",
                    style = CloneflixTheme.typography.regularCaption1,
                    color = PrimaryWhite.copy(alpha = 0.9f)
                )
            }
        }
    }
}

enum class CloneflixBlockLayout {
    HEADLINE_IMAGE,
    IMAGE_HEADLINE
}

@Composable
fun CloneflixFeatureBlock(
    title: String,
    description: String,
    layout: CloneflixBlockLayout = CloneflixBlockLayout.HEADLINE_IMAGE,
    previewContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens

    Surface(
        color = PrimaryBlack,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dimens.spacing2Xl)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spacingL),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val textContent: @Composable (Modifier) -> Unit = { mod ->
                    Column(
                        modifier = mod.padding(dimens.spacingL),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            style = typography.headerDisplay,
                            fontSize = 32.sp,
                            lineHeight = 38.sp,
                            color = PrimaryWhite
                        )
                        Spacer(modifier = Modifier.height(dimens.spacingM))
                        Text(
                            text = description,
                            style = typography.regularBody,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                            color = Grey10
                        )
                    }
                }

                val previewSlot: @Composable (Modifier) -> Unit = { mod ->
                    Box(
                        modifier = mod.padding(dimens.spacingL),
                        contentAlignment = Alignment.Center
                    ) {
                        previewContent()
                    }
                }

                when (layout) {
                    CloneflixBlockLayout.HEADLINE_IMAGE -> {
                        textContent(Modifier.weight(1f))
                        previewSlot(Modifier.weight(1f))
                    }
                    CloneflixBlockLayout.IMAGE_HEADLINE -> {
                        previewSlot(Modifier.weight(1f))
                        textContent(Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimens.spacingL))
            CloneflixDivider()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloneflixLandingFooter(
    onPhoneClick: () -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    selectedLanguage: String = "English",
    onLanguageSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens

    val links = listOf(
        "FAQ", "Help Center", "Account", "Media Center",
        "Investor Relations", "Jobs", "Ways to Watch", "Terms of Use",
        "Privacy", "Cookie Preferences", "Corporate Information", "Contact Us",
        "Speed Test", "Legal Notices", "Only on Netflix", "Ad Choices"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PrimaryBlack)
            .padding(horizontal = dimens.spacing2Xl, vertical = 48.dp)
    ) {
        Text(
            text = "Questions? Call 1-844-505-2993",
            style = typography.regularBody,
            color = Grey200,
            modifier = Modifier
                .clickable { onPhoneClick() }
                .padding(bottom = dimens.spacingXl)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 4,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            links.forEach { link ->
                Text(
                    text = link,
                    style = typography.regularCaption1,
                    fontSize = 13.sp,
                    color = Grey200,
                    modifier = Modifier
                        .widthIn(min = 140.dp, max = 220.dp)
                        .clickable { onLinkClick(link) }
                        .padding(vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        CloneflixDropdown(
            options = listOf("English", "Español", "Français", "Deutsch"),
            selectedOption = selectedLanguage,
            onOptionSelected = onLanguageSelected,
            variant = CloneflixDropdownVariant.COMPACT,
            width = 130.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Netflix United States",
            style = typography.regularCaption2,
            color = Grey400
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloneflixAuthFooter(
    onPhoneClick: () -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    selectedLanguage: String = "English",
    onLanguageSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens

    val links = listOf(
        "FAQ", "Help Center", "Terms of Use",
        "Privacy", "Cookie Preferences", "Corporate Information"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PrimaryBlack.copy(alpha = 0.9f))
            .padding(horizontal = dimens.spacing2Xl, vertical = 32.dp)
    ) {
        Text(
            text = "Questions? Call 1-844-505-2993",
            style = typography.regularBody,
            color = Grey200,
            modifier = Modifier
                .clickable { onPhoneClick() }
                .padding(bottom = dimens.spacingL)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 4,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            links.forEach { link ->
                Text(
                    text = link,
                    style = typography.regularCaption1,
                    fontSize = 13.sp,
                    color = Grey200,
                    modifier = Modifier
                        .widthIn(min = 140.dp, max = 220.dp)
                        .clickable { onLinkClick(link) }
                        .padding(vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        CloneflixDropdown(
            options = listOf("English", "Español", "Français"),
            selectedOption = selectedLanguage,
            onOptionSelected = onLanguageSelected,
            variant = CloneflixDropdownVariant.COMPACT,
            width = 120.dp
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloneflixHomeFooter(
    onSocialClick: (String) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    onServiceCodeClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val typography = CloneflixTheme.typography
    val dimens = CloneflixTheme.dimens

    val links = listOf(
        "Audio Description", "Help Center", "Gift Cards", "Media Center",
        "Investor Relations", "Jobs", "Netflix Shop", "Terms of Use",
        "Privacy", "Legal Notices", "Cookie Preferences", "Corporate Information",
        "Contact Us", "Do Not Sell or Share My Personal Information", "Ad Choices"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PrimaryBlack)
            .padding(horizontal = dimens.spacing2Xl, vertical = 40.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            val socialIcons = listOf(
                "Facebook" to R.drawable.cloneflix_ic_social_fb,
                "Instagram" to R.drawable.cloneflix_ic_social_ig,
                "Twitter" to R.drawable.cloneflix_ic_social_x,
                "YouTube" to R.drawable.cloneflix_ic_social_yt
            )

            socialIcons.forEach { (platform, iconRes) ->
                IconButton(
                    onClick = { onSocialClick(platform) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = platform,
                        tint = PrimaryWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 4,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            links.forEach { link ->
                Text(
                    text = link,
                    style = typography.regularCaption1,
                    fontSize = 13.sp,
                    color = Grey200,
                    modifier = Modifier
                        .widthIn(min = 140.dp, max = 220.dp)
                        .clickable { onLinkClick(link) }
                        .padding(vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            onClick = onServiceCodeClick,
            shape = RoundedCornerShape(2.dp),
            border = BorderStroke(1.dp, Grey200),
            color = Color.Transparent,
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            Text(
                text = "Service Code",
                style = typography.regularCaption1,
                fontSize = 13.sp,
                color = Grey200,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Text(
            text = "© 1997 - 2024 Netflix, Inc.",
            style = typography.regularCaption2,
            fontSize = 11.sp,
            color = Grey400
        )
    }
}

@Preview(name = "Landing Header Preview", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LandingHeaderPreview() {
    CloneflixTheme {
        CloneflixLandingHeader()
    }
}

@Preview(name = "Home Header Preview", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun HomeHeaderPreview() {
    CloneflixTheme {
        CloneflixHomeHeader()
    }
}

@Preview(name = "FAQ Section Preview", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FaqSectionPreview() {
    CloneflixTheme {
        CloneflixFaqSection(modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "Feature Block Preview", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FeatureBlockPreview() {
    CloneflixTheme {
        CloneflixFeatureBlock(
            title = "Enjoy on your TV",
            description = "Watch on Smart TVs, Playstation, Xbox, Chromecast, Apple TV, Blu-ray players, and more.",
            layout = CloneflixBlockLayout.HEADLINE_IMAGE,
            previewContent = {
                CloneflixTvPreviewCard(modifier = Modifier.fillMaxWidth(0.9f))
            }
        )
    }
}

@Preview(name = "Home Footer Preview", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun HomeFooterPreview() {
    CloneflixTheme {
        CloneflixHomeFooter()
    }
}
