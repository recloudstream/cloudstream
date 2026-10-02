package com.lagradost.cloudstream3.ui.settings.testing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component1
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component2
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.settings.SettingsFragmentScreen.SettingsSearch
import com.lagradost.cloudstream3.ui.settings.logcat.toHumanReadable
import com.lagradost.cloudstream3.utils.UIHelper.clipboardHelper
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream4.compose.BlackButton
import com.lagradost.cloudstream4.compose.LogBoxBlack
import com.lagradost.cloudstream4.compose.LogBoxWhite
import com.lagradost.cloudstream4.compose.LogText
import com.lagradost.cloudstream4.compose.PHONE
import com.lagradost.cloudstream4.compose.Screen
import com.lagradost.cloudstream4.compose.WhiteButton
import com.lagradost.cloudstream4.compose.WhiteFilterChip
import com.lagradost.cloudstream4.compose.isLayout
import com.lagradost.cloudstream4.state.LogItem
import com.lagradost.cloudstream4.state.LogLevel
import com.mihon.material.AppBar
import com.mihon.material.Scaffold
import com.mihon.material.padding
import com.mihon.presentation.LocalBackPress
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.toPersistentList

object TestScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel = viewModel { TestViewModel2() }

        LaunchedEffect(Unit) {
            viewModel.onAction(TestAction.Init(allProviders.withLock { allProviders.toPersistentList() }))
        }

        val state by viewModel.state.collectAsState()
        Content(state, viewModel::onAction)
    }

    @Composable
    fun Content(
        state: TestState,
        onAction: (TestAction) -> Unit,
    ) {
        val (top, bottom) = remember { FocusRequester.createRefs() }
        val handleBack = LocalBackPress.current
        Scaffold(
            // TODO @WindowInsets remove this when we have converted everything to compose
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                AppBar(
                    modifier = Modifier
                        .focusRequester(top)
                        .focusProperties {
                            down = bottom
                        },
                    title = stringResource(R.string.category_provider_test),
                    navigateUp = handleBack,
                    actions = { },
                    scrollBehavior = it,
                )
            },
            floatingActionButton = {
                if (isLayout(PHONE)) {
                    val loading = state.activeQueries > 0
                    FloatingActionButton(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        onClick = {
                            if (loading) {
                                onAction(TestAction.Cancel)
                            } else {
                                onAction(TestAction.Start)
                            }
                        }) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.play_arrow_24px),
                                contentDescription = stringResource(R.string.test_extensions)
                            )
                        }
                    }
                }
            },
            content = { contentPadding ->
                TestContent(
                    state = state,
                    onAction = onAction,
                    contentPadding = contentPadding,
                    modifier = Modifier
                        .focusRequester(bottom)
                        .focusProperties {
                            up = top
                        })
            },
        )
    }

    @Composable
    fun SelectSingleButton(
        name: Int,
        filtering: PersistentSet<LogLevel>,
        level: LogLevel,
        highestLogLevelCount: PersistentMap<LogLevel, Int>,
        onAction: (TestAction) -> Unit,
    ) {
        val selected = filtering.contains(level)
        WhiteFilterChip(
            selected = selected,
            label = "${stringResource(name)} ${highestLogLevelCount[level] ?: 0}"
        ) {
            if (selected) {
                onAction(TestAction.SetLevel(filtering.removing(level)))
            } else {
                onAction(TestAction.SetLevel(filtering.adding(level)))
            }
        }
    }

    @Composable
    fun TestContent(
        state: TestState,
        onAction: (TestAction) -> Unit,
        contentPadding: PaddingValues,
        modifier: Modifier = Modifier,
    ) {
        LazyColumn(
            modifier = modifier,
            contentPadding = contentPadding,
        ) {
            item(key = "header") {
                ProviderHeader(onAction = onAction)
            }

            item(key = "filter") {
                FilterRow(state = state, onAction = onAction)
            }

            items(
                items = state.items.sorted,
                key = { api -> api }
            ) { uuid ->
                val result = state.items.data[uuid]!!

                TestResult(
                    result = result,
                    onAction = onAction,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    /** This is not a .toString() because toHumanReadable is jvm */
    fun LogItem.format() = "${date.toHumanReadable()} $tag ${level.identifier} $message"

    @Composable
    fun LogItem(item: LogItem, modifier: Modifier = Modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            LogBoxWhite(item.level.identifier)
            LogBoxBlack(item.date.toHumanReadable())
            LogBoxBlack(item.tag)
        }

        LogText(level = item.level, message = item.message, modifier = modifier, onClick = {
            clipboardHelper(txt("Log"), item.format())
        })
    }

    @Composable
    fun FilterRow(state: TestState, onAction: (TestAction) -> Unit) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            modifier = Modifier
                .fillMaxWidth()
                .scrollable(
                    state = rememberScrollState(),
                    orientation = Orientation.Horizontal
                )
        ) {
            Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
            val highestLogLevelCount = state.items.state.highestLogLevelCount
            SelectSingleButton(
                name = R.string.no_data,
                filtering = state.filtering.levelFilter,
                level = LogLevel.Verbose,
                highestLogLevelCount = highestLogLevelCount,
                onAction = onAction,
            )
            SelectSingleButton(
                name = R.string.test_passed,
                filtering = state.filtering.levelFilter,
                level = LogLevel.Info,
                highestLogLevelCount = highestLogLevelCount,
                onAction = onAction,
            )
            SelectSingleButton(
                name = R.string.test_warning,
                filtering = state.filtering.levelFilter,
                level = LogLevel.Warning,
                highestLogLevelCount = highestLogLevelCount,
                onAction = onAction,
            )
            SelectSingleButton(
                name = R.string.test_failed,
                filtering = state.filtering.levelFilter,
                level = LogLevel.Error,
                highestLogLevelCount = highestLogLevelCount,
                onAction = onAction,
            )
            Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
        }
    }

    @Composable
    fun TestResultDialog(
        result: ImmutableTestResult,
        onAction: (TestAction) -> Unit,
        dismiss: () -> Unit,
    ) {
        val (dismissFocus, confirmFocus) = remember { FocusRequester.createRefs() }
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.background,
            onDismissRequest = dismiss,
            title = {
                Text(text = result.name)
            },
            text = {
                LazyColumn(
                    modifier = Modifier.focusProperties {
                        start = dismissFocus
                        end = confirmFocus
                    }
                ) {
                    item(key = "loading header") {
                        if (result.isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onBackground,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Spacer(Modifier.height(height = MaterialTheme.padding.small))
                        } else if (result.log.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_data),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    items(items = result.log, key = { item -> item.uuid }) { item ->
                        LogItem(item, modifier = Modifier.focusProperties {
                            start = dismissFocus
                            end = confirmFocus
                        })
                    }
                }
            },
            confirmButton = {
                WhiteButton(
                    text = if (result.isLoading) stringResource(R.string.stop) else stringResource(R.string.start),
                    modifier = Modifier.focusRequester(confirmFocus)
                ) {
                    if (result.isLoading) {
                        onAction(TestAction.CancelSingle(result.uuid))
                    } else {
                        onAction(TestAction.StartSingle(result.uuid))
                    }
                }

                WhiteButton(text = stringResource(R.string.sort_copy)) {
                    clipboardHelper(
                        txt("Log"),
                        result.log.joinToString(separator = "\n\n") { it.format() }
                    )
                }
                WhiteButton(text = stringResource(R.string.sort_clear)) {
                    onAction(TestAction.ClearSingle(result.uuid))
                }
            },
            dismissButton = {
                BlackButton(
                    text = stringResource(R.string.sort_close),
                    onClick = dismiss,
                    modifier = Modifier.focusRequester(dismissFocus)
                )
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        )
    }

    @Composable
    fun TestResult(
        result: ImmutableTestResult,
        onAction: (TestAction) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        var dialogShown by remember { mutableStateOf(false) }

        if (dialogShown) {
            TestResultDialog(result = result, onAction = onAction, dismiss = {
                dialogShown = false
            })
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .clickable(onClick = {
                    dialogShown = !dialogShown
                })
                .padding(horizontal = MaterialTheme.padding.medium)
        ) {
            Column(modifier = Modifier.weight(1.0f)) {
                Text(text = result.name)

                Row {
                    if (result.plugin != null) {
                        Text(
                            text = result.plugin,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (result.result != null) {
                        val (text, color) = when (result.highestLogLevel) {
                            LogLevel.Fatal, LogLevel.Error -> R.string.test_failed to R.color.colorTestFail
                            LogLevel.Warning -> R.string.test_warning to R.color.colorTestWarning
                            LogLevel.Verbose, LogLevel.Debug, LogLevel.Info -> R.string.test_passed to R.color.colorTestPass
                        }
                        Text(text = stringResource(text), color = colorResource(color))
                    }
                }
            }

            Spacer(modifier = Modifier.width(MaterialTheme.padding.small))

            IconButton(
                onClick = {
                    if (result.isLoading) {
                        onAction(TestAction.CancelSingle(result.uuid))
                    } else {
                        onAction(TestAction.StartSingle(result.uuid))
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
            ) {
                if (result.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(MaterialTheme.padding.small),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.play_arrow_24px),
                        contentDescription = null,
                    )
                }
            }
        }
    }

    @Composable
    fun ProviderHeader(
        onAction: (TestAction) -> Unit,
    ) {
        val textFieldState = rememberTextFieldState()

        Box(modifier = Modifier.padding(vertical = MaterialTheme.padding.small)) {
            SettingsSearch(textFieldState)
        }

        LaunchedEffect(textFieldState.text) {
            onAction(TestAction.Search(textFieldState.text.toString()))
        }
    }
}