package com.lagradost.cloudstream4.ui.screens.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.category_accounts
import com.lagradost.cloudstream4.generated.resources.category_accounts_subtitle
import com.lagradost.cloudstream4.generated.resources.category_extensions
import com.lagradost.cloudstream4.generated.resources.category_extensions_subtitle
import com.lagradost.cloudstream4.generated.resources.category_general
import com.lagradost.cloudstream4.generated.resources.category_general_subtitle
import com.lagradost.cloudstream4.generated.resources.category_layout
import com.lagradost.cloudstream4.generated.resources.category_layout_subtitle
import com.lagradost.cloudstream4.generated.resources.category_player
import com.lagradost.cloudstream4.generated.resources.category_player_subtitle
import com.lagradost.cloudstream4.generated.resources.category_providers
import com.lagradost.cloudstream4.generated.resources.category_providers_subtitle
import com.lagradost.cloudstream4.generated.resources.category_updates
import com.lagradost.cloudstream4.generated.resources.category_updates_subtitle
import com.lagradost.cloudstream4.ui.components.ProfileImage
import com.lagradost.cloudstream4.ui.components.ProfilePicture
import com.lagradost.cloudstream4.ui.components.cloudStreamRipple
import com.lagradost.cloudstream4.ui.components.settings.SettingsItem
import com.lagradost.cloudstream4.ui.icons.account_circle
import com.lagradost.cloudstream4.ui.icons.extension
import com.lagradost.cloudstream4.ui.icons.mobile_arrow_down
import com.lagradost.cloudstream4.ui.icons.palette
import com.lagradost.cloudstream4.ui.icons.play_circle
import com.lagradost.cloudstream4.ui.icons.storage
import com.lagradost.cloudstream4.ui.icons.tune
import com.lagradost.cloudstream4.ui.theme.CloudStreamTheme
import com.lagradost.cloudstream4.utils.DeviceLayout
import org.jetbrains.compose.resources.stringResource

@Immutable
data class SettingsProfileState(
    val name: String,
    val profilePictureUrl: String? = null,
    val profileImage: ProfileImage = ProfileImage.DARK_BLUE,
)

@Immutable
data class SettingsVersionState(
    val appVersion: String,
    val commitHash: String,
    val buildDate: String,
)

enum class SettingsCategory {
    GENERAL,
    PLAYER,
    PROVIDERS,
    LAYOUT,
    UPDATES,
    ACCOUNTS,
    EXTENSIONS,
}

@Composable
fun SettingsCategory.label(): String = stringResource(
    when (this) {
        SettingsCategory.GENERAL -> Res.string.category_general
        SettingsCategory.PLAYER -> Res.string.category_player
        SettingsCategory.PROVIDERS -> Res.string.category_providers
        SettingsCategory.LAYOUT -> Res.string.category_layout
        SettingsCategory.UPDATES -> Res.string.category_updates
        SettingsCategory.ACCOUNTS -> Res.string.category_accounts
        SettingsCategory.EXTENSIONS -> Res.string.category_extensions
    }
)

@Composable
fun SettingsCategory.subtitle(): String = stringResource(
    when (this) {
        SettingsCategory.GENERAL -> Res.string.category_general_subtitle
        SettingsCategory.PLAYER -> Res.string.category_player_subtitle
        SettingsCategory.PROVIDERS -> Res.string.category_providers_subtitle
        SettingsCategory.LAYOUT -> Res.string.category_layout_subtitle
        SettingsCategory.UPDATES -> Res.string.category_updates_subtitle
        SettingsCategory.ACCOUNTS -> Res.string.category_accounts_subtitle
        SettingsCategory.EXTENSIONS -> Res.string.category_extensions_subtitle
    }
)

private fun SettingsCategory.icon(): ImageVector = when (this) {
    SettingsCategory.GENERAL -> tune
    SettingsCategory.PLAYER -> play_circle
    SettingsCategory.PROVIDERS -> storage
    SettingsCategory.LAYOUT -> palette
    SettingsCategory.UPDATES -> mobile_arrow_down
    SettingsCategory.ACCOUNTS -> account_circle
    SettingsCategory.EXTENSIONS -> extension
}

@Composable
fun SettingsScreen(
    profile: SettingsProfileState,
    version: SettingsVersionState,
    onNavigate: (SettingsCategory) -> Unit,
    onVersionLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = CloudStreamTheme.colors
    val isTV by remember { DeviceLayout.isLayoutState(DeviceLayout.TV) }
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (isTV) firstItemFocusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .then(
                if (isTV) {
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsProfileHeader(profile)

            SettingsCategory.entries.forEachIndexed { index, category ->
                SettingsItem(
                    title = category.label(),
                    subtitle = category.subtitle(),
                    icon = category.icon(),
                    focusRequester = if (index == 0) firstItemFocusRequester else null,
                    onClick = { onNavigate(category) },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsVersionFooter(version = version, onLongClick = onVersionLongClick)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsProfileHeader(profile: SettingsProfileState) {
    val colors = CloudStreamTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfilePicture(
            profileImage = profile.profileImage,
            profilePictureUrl = profile.profilePictureUrl,
            size = 50.dp,
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = profile.name,
            color = colors.onBackground,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsVersionFooter(version: SettingsVersionState, onLongClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onLongClick = onLongClick,
                onClick = {},
            )
            .cloudStreamRipple(interactionSource)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VersionChip(version.appVersion)
        VersionDot()
        VersionChip(version.commitHash)
        VersionDot()
        VersionChip(version.buildDate)
    }
}

@Composable
private fun VersionChip(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        color = CloudStreamTheme.colors.onBackground.copy(alpha = 0.6f),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun VersionDot() {
    Text(
        text = "•",
        color = CloudStreamTheme.colors.onBackground.copy(alpha = 0.6f),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF111111) // Dark background
@Preview(name = "Light", showBackground = true, backgroundColor = 0xFFFFFFFF) // Light background
@Composable
private fun SettingsScreenPreview() {
    CloudStreamTheme {
        SettingsScreen(
            profile = SettingsProfileState(
                name = "Default",
                profileImage = ProfileImage.DARK_BLUE,
            ),
            version = SettingsVersionState(
                appVersion = "1.0.0-PRE",
                commitHash = "abc1234",
                buildDate = "Jan 1, 2026 12:00:00 AM",
            ),
            onNavigate = {},
            onVersionLongClick = {},
        )
    }
}
