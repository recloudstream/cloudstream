package com.lagradost.cloudstream3.ui.revamp.compose.screens

import android.widget.Toast
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixAvatarGraphic
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixAvatarModel
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixCard
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixHeader
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixLargeAvatar
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixOfficialAvatars
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixProfileMenuDropdown
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixSmallAvatar
import com.lagradost.cloudstream3.ui.revamp.compose.components.CloneflixUserProfilesRow
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey200
import com.lagradost.cloudstream3.ui.revamp.compose.theme.Grey800
import com.lagradost.cloudstream3.ui.revamp.compose.theme.PrimaryWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloneflixAvatarsComposeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val typography = CloneflixTheme.typography
    val colors = CloneflixTheme.colors
    val dimens = CloneflixTheme.dimens
    val scrollState = rememberScrollState()

    var selectedProfileName by remember { mutableStateOf("Jennifer") }
    var menuExpanded by remember { mutableStateOf(false) }
    var activeOfficialAvatar by remember { mutableStateOf(CloneflixOfficialAvatars.Red) }

    val sampleProfiles = remember {
        listOf(
            "Jennifer" to CloneflixOfficialAvatars.Red,
            "Bill" to CloneflixOfficialAvatars.FluffyBlue,
            "Alise" to CloneflixOfficialAvatars.Green,
            "James" to CloneflixOfficialAvatars.Purple
        )
    }

    val dropdownProfiles = remember {
        listOf(
            "Jennifer" to CloneflixOfficialAvatars.Red,
            "Max" to CloneflixOfficialAvatars.Blue,
            "Beyoncé" to CloneflixOfficialAvatars.Pink
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
            title = "User Profile Avatar",
            subtitle = "Small & Large Variants • Who's Watching Sample • Profile Menu • Official Avatars",
            iconRes = R.drawable.cloneflix_ic_account
        )

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "SMALL AVATAR (32DP)",
            style = typography.sectionHeader,
            color = colors.primary
        )
        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(dimens.spacingL)) {
                Text(
                    text = "Header & Compact Navigation Avatars",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(dimens.spacingXs))
                Text(
                    text = "Type=UserProfilePicture (Standalone) and Type=UserProfileMenu (With Dropdown Arrow).",
                    style = typography.regularCaption1,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(dimens.spacingL))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CloneflixSmallAvatar(
                            avatar = CloneflixOfficialAvatars.Purple,
                            showMenuArrow = false,
                            onClick = {
                                Toast.makeText(context, "Small Avatar Clicked", Toast.LENGTH_SHORT).show()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "UserProfilePicture",
                            style = typography.regularCaption1,
                            color = colors.textSecondary
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CloneflixSmallAvatar(
                            avatar = CloneflixOfficialAvatars.Red,
                            showMenuArrow = true,
                            isExpanded = menuExpanded,
                            onClick = { menuExpanded = !menuExpanded }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "UserProfileMenu",
                            style = typography.regularCaption1,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "LARGE AVATAR & FOCUS STATES (144DP)",
            style = typography.sectionHeader,
            color = colors.primary
        )
        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(dimens.spacingL)) {
                Text(
                    text = "Profile Avatar & Add Profile (Default vs Hover/Focused)",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(dimens.spacingXs))
                Text(
                    text = "Google TV / Android TV D-Pad focus traversal with white border and scale effect.",
                    style = typography.regularCaption1,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(dimens.spacingL))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CloneflixLargeAvatar(
                            name = "James",
                            avatar = CloneflixOfficialAvatars.Purple,
                            isHoverState = false,
                            onClick = { Toast.makeText(context, "Selected James (Default)", Toast.LENGTH_SHORT).show() }
                        )
                        Text(text = "Default", style = typography.regularCaption1, color = colors.textSecondary)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CloneflixLargeAvatar(
                            name = "James",
                            avatar = CloneflixOfficialAvatars.Purple,
                            isHoverState = true,
                            onClick = { Toast.makeText(context, "Selected James (Hover/Focus)", Toast.LENGTH_SHORT).show() }
                        )
                        Text(text = "Hover / Focus", style = typography.regularCaption1, color = colors.primary)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CloneflixLargeAvatar(
                            name = "Add Profile",
                            avatar = null,
                            isAddProfile = true,
                            isHoverState = false,
                            onClick = { Toast.makeText(context, "Add Profile (Default)", Toast.LENGTH_SHORT).show() }
                        )
                        Text(text = "Default", style = typography.regularCaption1, color = colors.textSecondary)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CloneflixLargeAvatar(
                            name = "Add Profile",
                            avatar = null,
                            isAddProfile = true,
                            isHoverState = true,
                            onClick = { Toast.makeText(context, "Add Profile (Hover/Focus)", Toast.LENGTH_SHORT).show() }
                        )
                        Text(text = "Hover / Focus", style = typography.regularCaption1, color = colors.primary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "SAMPLE: WHO'S WATCHING? (PROFILE SELECTOR)",
            style = typography.sectionHeader,
            color = colors.primary
        )
        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.spacing2Xl, horizontal = dimens.spacingL)
            ) {
                Text(
                    text = "Who's Watching?",
                    style = typography.boldTitle1,
                    color = PrimaryWhite,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(dimens.spacingXs))
                Text(
                    text = "Active Profile: $selectedProfileName",
                    style = typography.mediumSmallBody,
                    color = colors.primary
                )
                Spacer(modifier = Modifier.height(dimens.spacing2Xl))

                CloneflixUserProfilesRow(
                    profiles = sampleProfiles,
                    selectedProfileName = selectedProfileName,
                    onProfileClick = { name, _ ->
                        selectedProfileName = name
                        Toast.makeText(context, "Switched to profile $name", Toast.LENGTH_SHORT).show()
                    },
                    onAddProfileClick = {
                        Toast.makeText(context, "Add new profile clicked", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "SAMPLE: PROFILE MENU (HEADER POPOVER)",
            style = typography.sectionHeader,
            color = colors.primary
        )
        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(dimens.spacingL)) {
                Text(
                    text = "Interactive Profile Dropdown Menu",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(dimens.spacingXs))
                Text(
                    text = "Top-right navigation popover with profile switching, management actions, and sign out.",
                    style = typography.regularCaption1,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(dimens.spacingL))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.spacingL),
                    contentAlignment = Alignment.CenterStart
                ) {
                    CloneflixProfileMenuDropdown(
                        currentProfiles = dropdownProfiles,
                        onProfileSelected = { name ->
                            Toast.makeText(context, "Selected $name", Toast.LENGTH_SHORT).show()
                        },
                        onManageProfilesClick = {
                            Toast.makeText(context, "Manage Profiles", Toast.LENGTH_SHORT).show()
                        },
                        onTransferProfilesClick = {
                            Toast.makeText(context, "Transfer Profiles", Toast.LENGTH_SHORT).show()
                        },
                        onAccountClick = {
                            Toast.makeText(context, "Account Settings", Toast.LENGTH_SHORT).show()
                        },
                        onHelpCenterClick = {
                            Toast.makeText(context, "Help Center", Toast.LENGTH_SHORT).show()
                        },
                        onSignOutClick = {
                            Toast.makeText(context, "Sign out of Cloneflix", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing2Xl))

        Text(
            text = "OFFICIAL AVATARS CATALOG",
            style = typography.sectionHeader,
            color = colors.primary
        )
        Spacer(modifier = Modifier.height(dimens.spacingL))

        CloneflixCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(dimens.spacingL)) {
                Text(
                    text = "Main / Popular (16 Official Avatars)",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(dimens.spacingXs))
                Text(
                    text = "Selected Avatar: ${activeOfficialAvatar.name}",
                    style = typography.regularCaption1,
                    color = colors.primary
                )
                Spacer(modifier = Modifier.height(dimens.spacingL))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CloneflixOfficialAvatars.Popular.forEach { avatar ->
                        AvatarGridItem(
                            avatar = avatar,
                            isSelected = avatar.id == activeOfficialAvatar.id,
                            onClick = { activeOfficialAvatar = avatar }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(dimens.spacing2Xl))

                Text(
                    text = "All Others (Extended Catalog 01..36)",
                    style = typography.mediumBody,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(dimens.spacingXs))
                Text(
                    text = "Algorithmic avatar variations for user profile customization.",
                    style = typography.regularCaption1,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(dimens.spacingL))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CloneflixOfficialAvatars.getAllOthers(36).forEach { avatar ->
                        AvatarGridItem(
                            avatar = avatar,
                            isSelected = avatar.id == activeOfficialAvatar.id,
                            size = 64.dp,
                            onClick = { activeOfficialAvatar = avatar }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarGridItem(
    avatar: CloneflixAvatarModel,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 80.dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused || isSelected) 1.08f else 1.0f,
        label = "GridAvatarScale"
    )

    val borderStroke = when {
        isFocused || isSelected -> androidx.compose.foundation.BorderStroke(2.5.dp, PrimaryWhite)
        else -> androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(2.dp)
            .semantics {
                role = Role.Button
                contentDescription = "Avatar ${avatar.name}${if (isSelected) ", selected" else ""}"
            }
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
                .border(borderStroke, RoundedCornerShape(6.dp))
        ) {
            CloneflixAvatarGraphic(avatar = avatar)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = avatar.name,
            style = CloneflixTheme.typography.regularCaption2,
            color = if (isFocused || isSelected) PrimaryWhite else Grey200,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Preview(name = "Avatars Screen Phone Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.PHONE)
@Preview(name = "Avatars Screen TV Preview", showBackground = true, backgroundColor = 0xFF141414, device = Devices.TV_720p)
@Composable
private fun CloneflixAvatarsScreenPreview() {
    CloneflixTheme {
        CloneflixAvatarsComposeScreen()
    }
}
