package com.lagradost.cloudstream3.ui.settings

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.profileImages
import com.lagradost.cloudstream3.utils.GitInfo.currentCommitHash
import com.lagradost.cloudstream3.utils.UIHelper.clipboardHelper
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream4.compose.Screen
import com.lagradost.cloudstream4.compose.TV
import com.lagradost.cloudstream4.compose.circle
import com.lagradost.cloudstream4.compose.focusOutline
import com.lagradost.cloudstream4.compose.isLayout
import com.lagradost.cloudstream4.theme.CloudStreamPreviewTheme
import com.mihon.material.padding
import com.mihon.presentation.settings.Preference
import com.mihon.presentation.settings.SearchableSettings
import com.mihon.presentation.settings.SettingSearchResults
import com.mihon.presentation.settings.SettingsData
import com.mihon.presentation.settings.widget.TextPreferenceWidget
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SettingsFragmentScreen : Screen {
    val screens = persistentListOf(
        SettingsNavigation(
            title = R.string.category_general,
            navigation = R.id.action_navigation_global_to_navigation_settings_general,
            screen = SettingsGeneralScreen,
            icon = R.drawable.build_24px,
        ),
        SettingsNavigation(
            title = R.string.category_player,
            navigation = R.id.action_navigation_global_to_navigation_settings_player,
            screen = SettingsPlayerScreen,
            icon = R.drawable.play_arrow_24px,
        ),
        /*SettingsNavigation(
            title = R.string.category_providers,
            navigation = R.id.action_navigation_global_to_navigation_settings_providers,
            screen = SettingsProvidersScreen,
            icon = R.drawable.build_24px,
        ),*/
        SettingsNavigation(
            title = R.string.category_ui,
            navigation = R.id.action_navigation_global_to_navigation_settings_ui,
            screen = SettingsUIScreen,
            icon = R.drawable.format_paint_24px,
        ),
        SettingsNavigation(
            title = R.string.category_updates,
            navigation = R.id.action_navigation_global_to_navigation_settings_updates,
            screen = SettingsUpdatesScreen,
            icon = R.drawable.mobile_arrow_down_24px,
        ),
        SettingsNavigation(
            title = R.string.category_account,
            navigation = R.id.action_navigation_global_to_navigation_settings_account,
            screen = SettingsAccountScreen,
            icon = R.drawable.encrypted_24px,
        ),
        SettingsNavigation(
            title = R.string.pref_category_extensions,
            navigation = R.id.action_navigation_global_to_navigation_settings_extensions,
            screen = null,
            icon = R.drawable.extension_24px,
            subtitle = R.string.add_repository
        ),
    )

    data class SettingsNavigation(
        val title: Int,
        val navigation: Int,
        val screen: SearchableSettings?,
        val icon: Int,
        val subtitle: Int? = null,
    )


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun Content() {
        val textFieldState = rememberTextFieldState()
        val searchBarState = rememberSearchBarState()

        val screen = screens.mapNotNull { item ->
            val contents = item.screen?.getPreferences() ?: return@mapNotNull null
            SettingsData(
                title = stringResource(item.title),
                navigation = item.navigation,
                contents = contents
            )
        }.toPersistentList()

        val outerListState = rememberScrollState()

        val parentFirstScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    return if (delta < 0 && outerListState.canScrollForward) {
                        val consumed = outerListState.dispatchRawDelta(-delta)
                        Offset(0f, -consumed)
                    } else {
                        Offset.Zero
                    }
                }
            }
        }

        Scaffold { _ ->
            Column(modifier = Modifier.verticalScroll(outerListState)) {
                Spacer(modifier = Modifier.height(MaterialTheme.padding.small))

                val default = DataStoreHelper.getDefaultAccount(
                    LocalContext.current
                )
                val flow by DataStoreHelper.selectedAccountNumberFlow.collectAsState()
                val account = remember(flow) {
                    DataStoreHelper.getCurrentAccount() ?: default
                }

                Row(
                    modifier = Modifier.fillMaxSize().focusOutline().clickable {
                        activity.navigate(
                            R.id.accountSelectActivity,
                            Bundle().apply { putBoolean("isFromMainActivity", true) }
                        )
                    }.padding(
                        vertical = MaterialTheme.padding.large,
                        horizontal = MaterialTheme.padding.medium
                    )
                ) {
                    val image =
                        account.customImage ?: profileImages.getOrNull(account.defaultImageIndex)
                        ?: profileImages.first()

                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .border(
                                2.dp,
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                                CircleShape
                            )
                            .circle(),
                    ) {
                        AsyncImage(
                            contentScale = ContentScale.Crop,
                            model = image,
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = null,
                        )
                    }
                    Spacer(modifier = Modifier.width(MaterialTheme.padding.medium))
                    Column {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.title_settings),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
                SettingsSearch(searchBarState = searchBarState, textFieldState = textFieldState)
                Spacer(modifier = Modifier.height(MaterialTheme.padding.small))

                SettingSearchResults(
                    nestedScrollConnection = parentFirstScrollConnection,
                    searchKey = textFieldState.text.toString(),
                    items = screen,
                    onItemClick = { item ->
                        SearchableSettings.highlightKey = item.highlightKey
                        activity?.navigate(item.navigation)
                    }, empty = {
                        Column(
                            modifier = Modifier
                                .nestedScroll(parentFirstScrollConnection)
                        ) {
                            screens.forEach { settingsTab ->
                                SettingsTab(settingsTab)
                            }
                            BuildStamp()
                        }
                    })

            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun SettingsSearch(searchBarState: SearchBarState, textFieldState: TextFieldState) {
        val scope = rememberCoroutineScope()
        val inputField =
            @Composable {
                SearchBarDefaults.InputField(
                    textFieldState = textFieldState,
                    searchBarState = searchBarState,
                    modifier = Modifier.onFocusChanged { newFocus ->
                        if (newFocus.hasFocus) {
                            scope.launch {
                                searchBarState.animateToExpanded()
                            }
                        }
                    },
                    onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
                    placeholder = {
                        Text(modifier = Modifier.clearAndSetSemantics {}, text = "Search")
                    },
                    leadingIcon = {
                        Crossfade(
                            targetState = searchBarState.targetValue,
                            label = "results",
                        ) { value ->
                            when (value) {
                                SearchBarValue.Expanded -> {
                                    IconButton(onClick = {
                                        textFieldState.edit { replace(0, length, "") }
                                        scope.launch {
                                            searchBarState.animateToCollapsed()
                                        }
                                    }) {
                                        Icon(
                                            painter = painterResource(R.drawable.keyboard_arrow_left_24px),
                                            tint = MaterialTheme.colorScheme.onBackground,
                                            contentDescription = null
                                        )
                                    }
                                }

                                SearchBarValue.Collapsed -> {
                                    IconButton(onClick = {
                                        scope.launch {
                                            searchBarState.animateToExpanded()
                                        }
                                    }) {
                                        Icon(
                                            painter = painterResource(R.drawable.search_icon),
                                            tint = MaterialTheme.colorScheme.onBackground,
                                            contentDescription = null
                                        )
                                    }
                                }
                            }
                        }

                        /*SampleLeadingIcon(searchBarState, scope)*/
                    },
                    trailingIcon = {
                        Crossfade(
                            targetState = searchBarState.targetValue,
                            label = "results",
                        ) { value ->
                            when (value) {
                                SearchBarValue.Expanded -> {
                                    IconButton(onClick = {
                                        textFieldState.edit { replace(0, length, "") }
                                    }) {
                                        Icon(
                                            painter = painterResource(R.drawable.close_24px),
                                            tint = MaterialTheme.colorScheme.onBackground,
                                            contentDescription = null
                                        )
                                    }
                                }

                                SearchBarValue.Collapsed -> {
                                }
                            }
                        }
                    },
                )
            }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp - 12.dp * searchBarState.progress)
                .focusOutline(enabled = isLayout(TV), CircleShape)
                .onGloballyPositioned { searchBarState.collapsedCoords = it },
            shape = SearchBarDefaults.inputFieldShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = SearchBarDefaults.TonalElevation,
            shadowElevation = SearchBarDefaults.ShadowElevation,
            content = inputField,
        )

        BackHandler(enabled = searchBarState.targetValue == SearchBarValue.Expanded) {
            textFieldState.edit { replace(0, length, "") }
            scope.launch {
                searchBarState.animateToCollapsed()
            }
        }
    }

    @Composable
    fun SettingsTab(settingsTab: SettingsNavigation) {
        val pref = settingsTab.screen?.getPreferences() ?: emptyList()
        val groups = pref.filterIsInstance<Preference.PreferenceGroup>()
            .filter { it.enabled }
        TextPreferenceWidget(
            title = stringResource(settingsTab.title),
            icon = painterResource(settingsTab.icon),
            subtitle = settingsTab.subtitle?.let { stringResource(it) }
                ?: groups.joinToString { it.title }) {
            activity?.navigate(settingsTab.navigation)
        }
    }

    @Composable
    fun BuildStamp() {
        val (commitHash, buildTimestamp) = remember {
            val commitHash = activity?.currentCommitHash() ?: ""
            val buildTimestamp = SimpleDateFormat.getDateTimeInstance(
                DateFormat.LONG, DateFormat.MEDIUM,
                Locale.getDefault()
            ).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(BuildConfig.BUILD_DATE)).replace("UTC", "")
            commitHash to buildTimestamp
        }

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .focusOutline()
                .clickable {
                    clipboardHelper(
                        txt(R.string.extension_version),
                        "${BuildConfig.VERSION_NAME} $commitHash $buildTimestamp"
                    )
                },
        ) {
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                Text(text = BuildConfig.VERSION_NAME)
                if (commitHash != "") {
                    Text("•")
                    Text(text = commitHash)
                }
                Text("•")
                Text(text = buildTimestamp)
            }
        }
    }
}


@PreviewLightDark
@Composable
fun Preview() {
    CloudStreamPreviewTheme {
        SettingsFragmentScreen.Content()
    }
}