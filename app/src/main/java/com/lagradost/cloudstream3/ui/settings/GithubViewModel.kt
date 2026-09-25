package com.lagradost.cloudstream3.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream4.AppSettings
import com.lagradost.cloudstream4.compose.ActionHandler
import com.lagradost.cloudstream4.compose.DefaultStateContainer
import com.lagradost.cloudstream4.compose.SingleActiveQuery
import com.lagradost.cloudstream4.compose.StateContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileNotFoundException

@Immutable
data class GithubState(
    val dialog: GithubDialog? = null,
)

@Immutable
sealed class GithubUpdateDialogState {
    data class Error(val error: Throwable) : GithubUpdateDialogState()
    data class DownloadProgress(val progress: Long, val total: Long?) : GithubUpdateDialogState()
    object Loading : GithubUpdateDialogState()
    object NoUpdateFound : GithubUpdateDialogState()
    data class UpdateFound(
        val file: GithubReleases.GithubFile,
        val newSha: String?,
        val oldSha: String?,
    ) : GithubUpdateDialogState()
}

@Immutable
data class GithubDialog(
    val isPrerelease: Boolean,
    val isFromUser: Boolean,
    val state: GithubUpdateDialogState,
)

@Immutable
sealed class GithubAction {
    object AutoSearchForUpdate : GithubAction()
    object SearchForUpdate : GithubAction()
    object Dismiss : GithubAction()
    data class SkipThisUpdate(val file: GithubReleases.GithubFile) : GithubAction()
    data class Update(val file: GithubReleases.GithubFile) : GithubAction()
    data class SkipUpdate(val file: GithubReleases.GithubFile) : GithubAction()
}

const val APK_USERNAME = "recloudstream"
const val APK_REPOSITORY = "cloudstream"
const val APK_PRERELEASE = "pre-release"
const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"

interface AppUpdater {
    @Throws
    suspend fun update(
        settings: AppSettings,
        url: String,
        digest: DigestPair?,
        downloadProgress: (Long, Long?) -> Unit,
    )
}

/** The digest pair to verify that a file is correctly downloaded */
data class DigestPair(
    val algorithm : String,
    val digest : ByteArray,
) {
    companion object {
        // "sha256:XXXX" or "sha256-XXXX" -> "sha256", bytearray(XXXX)
        fun parse(digestPair: String?) : DigestPair? {
            if(digestPair == null) return null

            var split = digestPair.split(":", limit = 2)
            if(split.size != 2) {
                split = digestPair.split("-", limit = 2)
            }
            if(split.size != 2) {
                return null
            }

            val digestAlgorithm = split.getOrNull(0) ?: return null
            val digestByteArray = safe { split.getOrNull(1)?.hexToByteArray() } ?: return null

            return DigestPair(digestAlgorithm, digestByteArray)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val other = other as? DigestPair ?: return false

        if (algorithm != other.algorithm) return false
        if (!digest.contentEquals(other.digest)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = algorithm.hashCode()
        result = 31 * result + digest.contentHashCode()
        return result
    }
}

/**
 * Cross-platform downloader for updates served by GitHub.
 *
 * To allow this Cross-platform behavior work we split up the UI from the Viewmodel,
 * and the Viewmodel from the installer.
 * ```
 * UI:          Renders the current viewmodel state
 * Viewmodel:   Searches for updates on GitHub
 * AppUpdater:  Installs the update from the raw GitHub url
 *
 * UI -(onAction)-> Viewmodel -(invokes)-> AppUpdater
 *   <---(state)---/        <--(callback)--/
 * ```
 * */
class GithubViewModel(
    val remoteUserName: String,
    val remoteRepository: String,
    val remotePrereleaseTag: String,
    val remoteContentType: String,
    val versionName: String,
    val isPrerelease: Boolean,
    val isDebug: Boolean,
    val buildSha: String,
    val settings: AppSettings,
    val updater: AppUpdater,
) : ViewModel(), StateContainer<GithubState> by DefaultStateContainer(GithubState()),
    ActionHandler<GithubAction> {
    private val updateDispatcher = SingleActiveQuery(Dispatchers.IO)

    override fun onAction(action: GithubAction) {
        when (action) {
            GithubAction.SearchForUpdate -> {
                ioSafe {
                    searchForUpdate(prerelease = isPrerelease, fromUser = true)
                }
            }

            GithubAction.Dismiss -> {
                viewModelScope.launch {
                    updateDispatcher.cancel()
                    updateState { copy(dialog = null) }
                }
            }

            is GithubAction.SkipThisUpdate -> {
                settings.updates.skipUpdate.set(action.file.nodeId)
            }

            GithubAction.AutoSearchForUpdate -> {
                if (!isDebug && settings.updates.showAppUpdates.get()) {
                    ioSafe {
                        searchForUpdate(prerelease = isPrerelease, fromUser = false)
                    }
                }
            }

            is GithubAction.SkipUpdate -> {
                settings.updates.skipUpdate.set(action.file.nodeId)
            }

            is GithubAction.Update -> {
                ioSafe {
                    installUpdate(action.file.downloadUrl, action.file.digest)
                }
            }
        }
    }

    /** Cancel the old update, and catch possible errors from the block and show as a new state */
    private suspend fun dispatchUpdate(block: /* @Throws */ suspend () -> Unit) {
        updateDispatcher.launch {
            try {
                block()
            } catch (t: Throwable) {
                // If it was canceled ignore it as we probably launched another update check
                if (!isActive) {
                    return@launch
                }
                // Otherwise we display the error
                updateState {
                    copy(dialog = dialog?.copy(state = GithubUpdateDialogState.Error(t)))
                }
            }
        }
    }

    private suspend fun installUpdate(url: String, digestPair: String?) = dispatchUpdate {
        updater.update(
            settings = settings,
            url = url,
            digest = DigestPair.parse(digestPair),
        ) { progress, total ->
            updateState {
                copy(
                    dialog = dialog?.copy(
                        state = GithubUpdateDialogState.DownloadProgress(
                            progress = progress,
                            total = total
                        )
                    )
                )
            }
        }
        updateState {
            copy(
                dialog = null
            )
        }
    }

    private suspend fun searchForUpdate(
        prerelease: Boolean,
        fromUser: Boolean,
    ) = dispatchUpdate {
        val baseDialog = GithubDialog(
            isPrerelease = prerelease,
            isFromUser = fromUser,
            state = GithubUpdateDialogState.Loading
        )

        updateState {
            copy(dialog = baseDialog)
        }

        // If on pre-release check if the sha matches, as we do not look at the version
        var oldSha: String? = null
        var newSha: String? = null
        if (prerelease) {
            val sha = getSha(remotePrereleaseTag)
            oldSha = buildSha.take(7)
            newSha = sha.take(7)

            // Only match the first 7 chars, as that is what is saved
            if (oldSha == newSha) {
                updateState {
                    copy(dialog = baseDialog.copy(state = GithubUpdateDialogState.NoUpdateFound))
                }
                return@dispatchUpdate
            }
        }

        val release = getRelease(prerelease)

        // If on stable, only check that the display name matches
        if (!prerelease && release.displayName == versionName) {
            updateState {
                copy(dialog = baseDialog.copy(state = GithubUpdateDialogState.NoUpdateFound))
            }
            return@dispatchUpdate
        }

        // If this was automated, and we have pressed "skip this update" then check the node-id
        if (!fromUser && release.nodeId == settings.updates.skipUpdate.get()) {
            updateState {
                copy(dialog = baseDialog.copy(state = GithubUpdateDialogState.NoUpdateFound))
            }
            return@dispatchUpdate
        }

        updateState {
            copy(
                dialog = baseDialog.copy(
                    state = GithubUpdateDialogState.UpdateFound(
                        file = release,
                        newSha = newSha,
                        oldSha = oldSha
                    )
                )
            )
        }
    }

    @Throws
    private suspend fun getRelease(prerelease: Boolean) =
        GithubReleases.getLatestReleaseFile(
            prerelease = prerelease,
            userName = remoteUserName,
            repository = remoteRepository,
            prereleaseTag = remotePrereleaseTag,
            contentType = remoteContentType
        ) ?: throw FileNotFoundException()

    @Throws
    private suspend fun getSha(tag: String) =
        GithubReleases.getShaFromTag(
            tag = tag,
            userName = remoteUserName,
            repository = remoteRepository,
        )
}