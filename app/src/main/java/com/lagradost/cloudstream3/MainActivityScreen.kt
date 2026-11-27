package com.lagradost.cloudstream3

import android.text.format.Formatter.formatFileSize
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lagradost.cloudstream3.mvvm.getStackTracePretty
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.ui.settings.ApkUpdater
import com.lagradost.cloudstream3.ui.settings.GithubReleases
import com.lagradost.cloudstream3.ui.settings.GithubReleases.APK_CONTENT_TYPE
import com.lagradost.cloudstream3.ui.settings.GithubReleases.APK_PRERELEASE
import com.lagradost.cloudstream3.ui.settings.GithubReleases.APK_REPOSITORY
import com.lagradost.cloudstream3.ui.settings.GithubReleases.APK_USERNAME
import com.lagradost.cloudstream3.utils.GitInfo.currentCommitHash
import com.lagradost.cloudstream3.utils.parseMarkdown
import com.lagradost.cloudstream4.compose.BlackButton
import com.lagradost.cloudstream4.compose.Screen
import com.lagradost.cloudstream4.compose.WhiteButton
import com.lagradost.cloudstream4.rememberAppSettings
import com.lagradost.cloudstream4.viewmodels.GithubAction
import com.lagradost.cloudstream4.viewmodels.GithubAction.AutoSearchForUpdate
import com.lagradost.cloudstream4.viewmodels.GithubAction.Dismiss
import com.lagradost.cloudstream4.viewmodels.GithubAction.SearchForUpdate
import com.lagradost.cloudstream4.viewmodels.GithubAction.SkipUpdate
import com.lagradost.cloudstream4.viewmodels.GithubAction.Update
import com.lagradost.cloudstream4.viewmodels.GithubState
import com.lagradost.cloudstream4.viewmodels.GithubUpdateDialogState
import com.lagradost.cloudstream4.viewmodels.GithubViewModel
import com.mihon.material.padding

object MainActivityScreen : Screen {
    private var hasSearchedForUpdate: Boolean = false

    @Composable
    fun githubViewModel(): GithubViewModel? {
        // By having the ComponentActivity as the owner we can have a singleton viewmodel
        val activity = LocalActivity.current as? ComponentActivity ?: return null
        val settings = rememberAppSettings()
        return viewModel(viewModelStoreOwner = activity) {
            GithubViewModel(
                remoteUserName = APK_USERNAME,
                remoteRepository = APK_REPOSITORY,
                remotePrereleaseTag = APK_PRERELEASE,
                remoteContentType = APK_CONTENT_TYPE,
                versionName = BuildConfig.VERSION_NAME,
                isPrerelease = BuildConfig.FLAVOR == "prerelease",
                isDebug = BuildConfig.DEBUG,
                buildSha = activity.currentCommitHash(),
                settings = settings,
                updater = ApkUpdater,
                repository = GithubReleases,
            ).apply {
                /** Only search once per session when not in safe mode */
                if (!hasSearchedForUpdate && !PluginManager.isSafeMode()) {
                    hasSearchedForUpdate = true
                    onAction(AutoSearchForUpdate)
                }
            }
        }
    }

    @Composable
    override fun Content() {
        GithubView()
    }

    @Composable
    fun GithubView() {
        val updateViewModel = githubViewModel() ?: return
        val state by updateViewModel.state.collectAsState()
        GithubState(state, updateViewModel::onAction)
    }

    @Composable
    fun GithubState(updaterState: GithubState, onAction: (GithubAction) -> Unit) {
        val dialog = updaterState.dialog ?: return

        val title: String
        val body: @Composable () -> Unit
        val confirmButton: @Composable () -> Unit
        val dismissButton: @Composable () -> Unit
        val cancelable: Boolean

        when (val state = dialog.state) {
            is GithubUpdateDialogState.Error -> {
                // Only show error if it is initialized by the user
                if (!dialog.isFromUser) return

                cancelable = true
                title = stringResource(R.string.download_failed)
                body = { Text(text = state.error.getStackTracePretty()) }
                confirmButton = {
                    WhiteButton(text = stringResource(R.string.check_for_update), onClick = {
                        onAction(SearchForUpdate)
                    })
                }
                dismissButton = {
                    BlackButton(text = stringResource(R.string.ok), onClick = {
                        onAction(Dismiss)
                    })
                }
            }

            GithubUpdateDialogState.Loading -> {
                // Only show loading if it is initialized by the user
                if (!dialog.isFromUser) return

                cancelable = true
                title = stringResource(R.string.loading)
                body = {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onBackground,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                confirmButton = {}
                dismissButton = {
                    BlackButton(text = stringResource(R.string.cancel), onClick = {
                        onAction(Dismiss)
                    })
                }
            }

            GithubUpdateDialogState.NoUpdateFound -> {
                // Only show error if it is initialized by the user
                if (!dialog.isFromUser) return

                cancelable = true
                title = stringResource(R.string.no_update_found)
                body = {}
                confirmButton = {
                    WhiteButton(text = stringResource(R.string.ok), onClick = {
                        onAction(Dismiss)
                    })
                }
                dismissButton = {}
            }

            is GithubUpdateDialogState.UpdateFound -> {
                cancelable = true
                title = stringResource(
                    R.string.new_update_format,
                    (state.oldSha ?: BuildConfig.VERSION_NAME),
                    (state.newSha ?: state.file.displayName)
                )
                body = {
                    Text(
                        text = parseMarkdown(state.file.changeLog),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
                confirmButton = {
                    WhiteButton(text = stringResource(R.string.update), onClick = {
                        onAction(Update(state.file))
                    })
                }
                dismissButton = {
                    BlackButton(text = stringResource(R.string.skip_update), onClick = {
                        onAction(SkipUpdate(state.file))
                        onAction(Dismiss)
                    })
                    BlackButton(text = stringResource(R.string.cancel), onClick = {
                        onAction(Dismiss)
                    })
                }
            }

            is GithubUpdateDialogState.DownloadProgress -> {
                cancelable = false
                title = stringResource(R.string.update_notification_downloading)
                body = {
                    val total = state.total
                    if (total != null) {
                        val progress =
                            (state.progress.toFloat() / total.toFloat()).coerceIn(0.0f, 1.0f)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                // Round down the progress to avoid 100%
                                text = "${(progress * 100.0f).toInt()}% (${
                                    formatFileSize(
                                        LocalContext.current,
                                        state.progress
                                    )
                                } / ${
                                    formatFileSize(
                                        LocalContext.current,
                                        total
                                    )
                                })"
                            )
                            Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onBackground,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                progress = { progress }
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                text = formatFileSize(
                                    LocalContext.current,
                                    state.progress
                                )
                            )
                            Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onBackground,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }
                confirmButton = {}
                dismissButton = {
                    BlackButton(text = stringResource(R.string.cancel), onClick = {
                        onAction(Dismiss)
                    })
                }
            }
        }

        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            containerColor = MaterialTheme.colorScheme.background,
            onDismissRequest = {
                if (cancelable) {
                    onAction(Dismiss)
                }
            },
            title = { Text(text = title) },
            text = body,
            confirmButton = confirmButton,
            dismissButton = dismissButton
        )
    }
}