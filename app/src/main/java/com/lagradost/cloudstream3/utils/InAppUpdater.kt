package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity.Companion.deleteFileOnExit
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.services.PackageInstallerService
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.BufferedSink
import okio.buffer
import okio.sink

object InAppUpdater {
    private const val GITHUB_USER_NAME = "recloudstream"
    private const val GITHUB_REPO = "cloudstream"

    private const val LOG_TAG = "InAppUpdater"

    private data class GithubAsset(
        @JsonProperty("name") val name: String,
        @JsonProperty("size") val size: Int, // Size in bytes
        @JsonProperty("browser_download_url") val browserDownloadUrl: String,
        @JsonProperty("content_type") val contentType: String, // application/vnd.android.package-archive
    )

    private data class GithubRelease(
        @JsonProperty("tag_name") val tagName: String, // Version code
        @JsonProperty("body") val body: String, // Description
        @JsonProperty("assets") val assets: List<GithubAsset>,
        @JsonProperty("target_commitish") val targetCommitish: String, // Branch
        @JsonProperty("prerelease") val prerelease: Boolean,
        @JsonProperty("node_id") val nodeId: String,
    )

    private data class GithubObject(
        @JsonProperty("sha") val sha: String, // SHA-256 hash
        @JsonProperty("type") val type: String,
        @JsonProperty("url") val url: String,
    )

    private data class GithubTag(
        @JsonProperty("object") val githubObject: GithubObject,
    )

    private data class Update(
        @JsonProperty("shouldUpdate") val shouldUpdate: Boolean,
        @JsonProperty("updateURL") val updateURL: String?,
        @JsonProperty("updateVersion") val updateVersion: String?,
        @JsonProperty("changelog") val changelog: String?,
        @JsonProperty("updateNodeId") val updateNodeId: String?,
    )

    private suspend fun Activity.getAppUpdate(): Update {
        return try {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
            if (
                settingsManager.getBoolean(
                    getString(R.string.prerelease_update_key),
                    resources.getBoolean(R.bool.is_prerelease)
                )
            ) {
                getPreReleaseUpdate()
            } else getReleaseUpdate()
        } catch (e: Exception) {
            Log.e(LOG_TAG, Log.getStackTraceString(e))
            Update(false, null, null, null, null)
        }
    }

    private suspend fun Activity.getReleaseUpdate(): Update {
        val url = "https://api.github.com/repos/$GITHUB_USER_NAME/$GITHUB_REPO/releases"
        val headers = mapOf("Accept" to "application/vnd.github.v3+json")
        val response = parseJson<List<GithubRelease>>(
            app.get(url, headers = headers).text
        )

        val versionRegex = Regex("""(.*?((\d+)\.(\d+)\.(\d+))\.apk)""")
        val versionRegexLocal = Regex("""(.*?((\d+)\.(\d+)\.(\d+)).*)""")
        val foundList = response.filter { rel ->
            !rel.prerelease
        }.sortedWith(compareBy { release ->
            release.assets.firstOrNull { it.contentType == "application/vnd.android.package-archive" }?.name?.let { it1 ->
                versionRegex.find(
                    it1
                )?.groupValues?.let {
                    it[3].toInt() * 100_000_000 + it[4].toInt() * 10_000 + it[5].toInt()
                }
            }
        }).toList()

        val found = foundList.lastOrNull()
        val foundAsset = found?.assets?.getOrNull(0)
        val currentVersion = packageName?.let {
            packageManager.getPackageInfo(it, 0)
        }

        foundAsset?.name?.let { assetName ->
            val foundVersion = versionRegex.find(assetName)
            val shouldUpdate =
                if (foundAsset.browserDownloadUrl != "" && foundVersion != null) {
                    currentVersion?.versionName?.let { versionName ->
                        versionRegexLocal.find(versionName)?.groupValues?.let {
                            it[3].toInt() * 100_000_000 + it[4].toInt() * 10_000 + it[5].toInt()
                        }
                    }?.compareTo(
                        foundVersion.groupValues.let {
                            it[3].toInt() * 100_000_000 + it[4].toInt() * 10_000 + it[5].toInt()
                        }
                    )!! < 0
                } else false
            return if (foundVersion != null) {
                Update(
                    shouldUpdate,
                    foundAsset.browserDownloadUrl,
                    foundVersion.groupValues[2],
                    found.body,
                    found.nodeId
                )
            } else Update(false, null, null, null, null)
        }

        return Update(false, null, null, null, null)
    }

    private suspend fun Activity.getPreReleaseUpdate(): Update {
        val tagUrl = "https://api.github.com/repos/$GITHUB_USER_NAME/$GITHUB_REPO/git/ref/tags/pre-release"
        val releaseUrl = "https://api.github.com/repos/$GITHUB_USER_NAME/$GITHUB_REPO/releases"
        val headers = mapOf("Accept" to "application/vnd.github.v3+json")
        val response = parseJson<List<GithubRelease>>(
            app.get(releaseUrl, headers = headers).text
        )

        val found = response.lastOrNull { rel ->
            rel.prerelease || rel.tagName == "pre-release"
        }

        val foundAsset = found?.assets?.filter { it ->
            it.contentType == "application/vnd.android.package-archive"
        }?.getOrNull(0)

        return if (foundAsset != null) {
            val tagResponse = parseJson<GithubTag>(app.get(tagUrl, headers = headers).text)
            val updateCommitHash = tagResponse.githubObject.sha.trim().take(7)
            Log.d(LOG_TAG, "Fetched GitHub tag: $updateCommitHash")

            Update(
                getString(R.string.commit_hash) != updateCommitHash,
                foundAsset.browserDownloadUrl,
                updateCommitHash,
                found.body,
                found.nodeId
            )
        } else Update(false, null, null, null, null)
    }

    private val updateLock = Mutex()

    private suspend fun Activity.downloadUpdate(url: String): Boolean {
        try {
            Log.d(LOG_TAG, "Downloading update: $url")
            val appUpdateName = "CloudStream"
            val appUpdateSuffix = "apk"

            // Delete all old updates
            this.cacheDir.listFiles()?.filter {
                it.name.startsWith(appUpdateName) && it.extension == appUpdateSuffix
            }?.forEach { deleteFileOnExit(it) }

            val downloadedFile = File.createTempFile(appUpdateName, ".$appUpdateSuffix")
            val sink: BufferedSink = downloadedFile.sink().buffer()

            updateLock.withLock {
                sink.writeAll(app.get(url).body.source())
                sink.close()
                openApk(this, Uri.fromFile(downloadedFile))
            }

            return true
        } catch (e: Exception) {
            logError(e)
            return false
        }
    }

    private fun openApk(context: Context, uri: Uri) {
        try {
            uri.path?.let {
                val contentUri = FileProvider.getUriForFile(
                    context,
                    BuildConfig.APPLICATION_ID + ".provider",
                    File(it)
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    data = contentUri
                }
                context.startActivity(installIntent)
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    /**
     * @param checkAutoUpdate if the update check was launched automatically
     */
    suspend fun Activity.runAutoUpdate(checkAutoUpdate: Boolean = true): Boolean {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        if (!checkAutoUpdate || settingsManager.getBoolean(
                getString(R.string.auto_update_key),
                true
            )
        ) {
            val update = getAppUpdate()
            if (update.shouldUpdate && update.updateURL != null) {
                // Check if update should be skipped
                val updateNodeId = settingsManager.getString(
                    getString(R.string.skip_update_key), ""
                )

                // Skips the update if its an automatic update and the update is skipped
                // This allows updating manually
                if (update.updateNodeId.equals(updateNodeId) && checkAutoUpdate) {
                    return false
                }

                runOnUiThread {
                    try {
                        val currentVersion = packageName?.let {
                            packageManager.getPackageInfo(it, 0)
                        }

                        val builder = AlertDialog.Builder(this, R.style.AlertDialogCustom)
                        builder.setTitle(
                            getString(R.string.new_update_format).format(
                                currentVersion?.versionName,
                                update.updateVersion
                            )
                        )

                        val logRegex = Regex("\\[(.*?)\\]\\((.*?)\\)")
                        val sanitizedChangelog = update.changelog?.replace(logRegex) { matchResult ->
                            matchResult.groupValues[1]
                        } // Sanitized because it looks cluttered

                        builder.setMessage(sanitizedChangelog)
                        builder.apply {
                            setPositiveButton(R.string.update) { _, _ ->
                                // Forcefully start any delayed installations
                                if (ApkInstaller.delayedInstaller?.startInstallation() == true) return@setPositiveButton

                                showToast(R.string.download_started, Toast.LENGTH_LONG)

                                // Check if the setting hasn't been changed
                                if (
                                    settingsManager.getInt(
                                        getString(R.string.apk_installer_key),
                                        -1
                                    ) == -1
                                ) {
                                    // Set to legacy installer if using MIUI
                                    if (isMiUi()) {
                                        settingsManager.edit {
                                            putInt(getString(R.string.apk_installer_key), 1)
                                        }
                                    }
                                }

                                val currentInstaller = settingsManager.getInt(
                                    getString(R.string.apk_installer_key),
                                    0
                                )

                                when (currentInstaller) {
                                    // New method
                                    0 -> {
                                        val intent = PackageInstallerService.Companion.getIntent(
                                            this@runAutoUpdate,
                                            update.updateURL
                                        )
                                        ContextCompat.startForegroundService(this@runAutoUpdate, intent)
                                    }
                                    // Legacy
                                    1 -> {
                                        ioSafe {
                                            if (!downloadUpdate(update.updateURL)) {
                                                runOnUiThread {
                                                    showToast(
                                                        R.string.download_failed,
                                                        Toast.LENGTH_LONG
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            setNegativeButton(R.string.cancel) { _, _ -> }

                            if (checkAutoUpdate) {
                                setNeutralButton(R.string.skip_update) { _, _ ->
                                    settingsManager.edit {
                                        putString(
                                            getString(R.string.skip_update_key),
                                            update.updateNodeId ?: ""
                                        )
                                    }
                                }
                            }
                        }
                        builder.show().setDefaultFocus()
                    } catch (e: Exception) {
                        logError(e)
                    }
                }
                return true
            }
            return false
        }
        return false
    }

    private fun isMiUi(): Boolean {
        return !TextUtils.isEmpty(getSystemProperty("ro.miui.ui.version.name"))
    }

    private fun getSystemProperty(propName: String): String? {
        return try {
            val p = Runtime.getRuntime().exec("getprop $propName")
            BufferedReader(InputStreamReader(p.inputStream), 1024).use {
                it.readLine()
            }
        } catch (_: IOException) {
            null
        }
    }
}
