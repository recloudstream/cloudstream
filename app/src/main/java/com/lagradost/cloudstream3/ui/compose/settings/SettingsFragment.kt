package com.lagradost.cloudstream3.ui.compose.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.GitInfo.currentCommitHash
import com.lagradost.cloudstream3.utils.UIHelper.clipboardHelper
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UiImage
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream4.compose.components.ProfileImage
import com.lagradost.cloudstream4.compose.screens.settings.SettingsCategory
import com.lagradost.cloudstream4.compose.screens.settings.SettingsProfileState
import com.lagradost.cloudstream4.compose.screens.settings.SettingsScreen
import com.lagradost.cloudstream4.compose.screens.settings.SettingsVersionState
import com.lagradost.cloudstream4.compose.theme.CloudStreamTheme
import com.lagradost.cloudstream4.compose.theme.loadPrimaryColor
import com.lagradost.cloudstream4.compose.theme.loadThemeMode
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * TODO: This Fragment is a temporary bridge between the old View-based navigation system
 * and the new Compose UI. It will be removed as part of the Navigation3 migration, which
 * replaces the NavGraph/Fragment back stack with a fully KMP-compatible navigation system.
 * At that point, screens will be called directly as composables with no Fragment
 * or NavGraph involvement, once:
 * 1. All fragments have been migrated to Compose screens in :composeApp
 * 2. Navigation3 has been adopted, replacing the NavGraph action ID system
 * 3. More shared logic is available in :composeApp
 */
class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        setContent {
            val profile = buildProfileState()
            val version = buildVersionState()
            CloudStreamTheme(
                mode = context.loadThemeMode(),
                primaryColor = context.loadPrimaryColor(),
            ) {
                SettingsScreen(
                    profile = profile,
                    version = version,
                    onNavigate = ::navigateTo,
                    onVersionLongClick = {
                        val v = version.appVersion
                        val h = version.commitHash
                        val d = version.buildDate
                        clipboardHelper(txt(R.string.extension_version), "$v $h $d")
                    },
                )
            }
        }
    }

    private fun buildProfileState(): SettingsProfileState {
        for (syncApi in AccountManager.allApis) {
            val login = syncApi.authUser() ?: continue
            if (login.profilePicture.isNullOrEmpty()) continue
            return SettingsProfileState(
                name = login.name ?: "",
                profilePictureUrl = login.profilePicture,
            )
        }

        val account = runCatching {
            DataStoreHelper.accounts.firstOrNull {
                it.keyIndex == DataStoreHelper.selectedKeyIndex
            } ?: DataStoreHelper.getDefaultAccount(requireActivity())
        }.getOrNull()

        val profileImage = when (account?.defaultImageIndex) {
            0 -> ProfileImage.DARK_BLUE
            1 -> ProfileImage.BLUE
            2 -> ProfileImage.ORANGE
            3 -> ProfileImage.PINK
            4 -> ProfileImage.PURPLE
            5 -> ProfileImage.RED
            6 -> ProfileImage.TEAL
            else -> ProfileImage.DARK_BLUE
        }

        return SettingsProfileState(
            name = account?.name ?: "",
            profilePictureUrl = (account?.image as? UiImage.Image)?.url,
            profileImage = profileImage,
        )
    }

    private fun buildVersionState(): SettingsVersionState {
        val buildDate = SimpleDateFormat
            .getDateTimeInstance(DateFormat.LONG, DateFormat.LONG, Locale.getDefault())
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(BuildConfig.BUILD_DATE))
            .replace("UTC", "")

        return SettingsVersionState(
            appVersion = BuildConfig.VERSION_NAME,
            commitHash = activity?.currentCommitHash() ?: "",
            buildDate = buildDate,
        )
    }

    private fun navigateTo(category: SettingsCategory) {
        val actionId = when (category) {
            SettingsCategory.GENERAL -> R.id.action_navigation_global_to_navigation_settings_general
            SettingsCategory.PLAYER -> R.id.action_navigation_global_to_navigation_settings_player
            SettingsCategory.PROVIDERS -> R.id.action_navigation_global_to_navigation_settings_providers
            SettingsCategory.LAYOUT -> R.id.action_navigation_global_to_navigation_settings_ui
            SettingsCategory.UPDATES -> R.id.action_navigation_global_to_navigation_settings_updates
            SettingsCategory.ACCOUNTS -> R.id.action_navigation_global_to_navigation_settings_account
            SettingsCategory.EXTENSIONS -> R.id.action_navigation_global_to_navigation_settings_extensions
        }
        activity?.navigate(actionId, Bundle())
    }
}
