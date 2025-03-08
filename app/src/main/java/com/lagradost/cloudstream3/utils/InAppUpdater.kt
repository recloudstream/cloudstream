package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity.Companion.deleteFileOnExit
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.buffer
import okio.sink
import java.io.File

class InAppUpdater {
    companion object {
        private const val GITHUB_USER_NAME = "recloudstream"
        private const val GITHUB_REPO = "cloudstream"
        private const val LOG_TAG = "InAppUpdater"

        data class GithubAsset(
            @JsonProperty("name") val name: String,
            @JsonProperty("size") val size: Int, // Size in bytes
            @JsonProperty("browser_download_url") val browserDownloadUrl: String, // Download link
            @JsonProperty("content_type") val contentType: String, // application/vnd.android.package-archive
        )

        data class GithubRelease(
            @JsonProperty("tag_name") val tagName: String, // Version code
            @JsonProperty("body") val body: String, // Description
            @JsonProperty("assets") val assets: List<GithubAsset>,
            @JsonProperty("target_commitish") val targetCommitish: String, // Branch
            @JsonProperty("prerelease") val prerelease: Boolean,
            @JsonProperty("node_id") val nodeId: String // Node ID
        )

        data class GithubObject(
            @JsonProperty("sha") val sha: String, // SHA-256 hash
            @JsonProperty("type") val type: String, // Object type
            @JsonProperty("url") val url: String,
        )

        data class GithubTag(
            @JsonProperty("object") val githubObject: GithubObject,
        )

        data class Update(
            @JsonProperty("shouldUpdate") val shouldUpdate: Boolean,
            @JsonProperty("updateURL") val updateURL: String?,
            @JsonProperty("updateVersion") val updateVersion: String?,
            @JsonProperty("changelog") val changelog: String?,
            @JsonProperty("updateNodeId") val updateNodeId: String?
        )

        private suspend fun Activity.getAppUpdate(): Update {
            return try {
                val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
                if (settingsManager.getBoolean(
                        getString(R.string.prerelease_update_key),
                        resources.getBoolean(R.bool.is_prerelease)
                    )
                ) {
                    getPreReleaseUpdate()
                } else {
                    getReleaseUpdate()
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, Log.getStackTraceString(e))
                Update(false, null, null, null, null)
            }
        }

        private suspend fun Activity.getReleaseUpdate(): Update {
            val url = "https://api.github.com/repos/$GITHUB_USER_NAME/$GITHUB_REPO/releases"
            val headers = mapOf("Accept" to "application/vnd.github.v3+json")
            val response = parseJson<List<GithubRelease>>(app.get(url, headers = headers).text)
            val versionRegex = Regex("""(.*?((\d+)\.(\d+)\.(\d+))\.apk)""")
            val versionRegexLocal = Regex("""(.*?((\d+)\.(\d+)\.(\d+)).*)""")

            val foundList = response.filter { !it.prerelease }
                .sortedWith(compareBy { release ->
                    release.assets.firstOrNull { it.contentType == "application/vnd.android.package-archive" }?.name?.let {
                        versionRegex.find(it)?.groupValues?.let {
                            it[3].toInt() * 100_000_000 + it[4].toInt() * 10_000 + it[5].toInt()
                        }
                    }
                }).toList()

            val found = foundList.lastOrNull()
            val foundAsset = found?.assets?.getOrNull(0)
            val currentVersion = packageName?.let { packageManager.getPackageInfo(it, 0) }

            foundAsset?.name?.let { assetName ->
                val foundVersion = versionRegex.find(assetName)
                val shouldUpdate =
                    if (foundAsset.browserDownloadUrl != "" && foundVersion != null) currentVersion?.versionName?.let { versionName ->
                        versionRegexLocal.find(versionName)?.groupValues?.let {
                            it[3].toInt() * 100_000_000 + it[4].toInt() * 10_000 + it[5].toInt()
                        }
                    }?.compareTo(
                        foundVersion.groupValues.let {
                            it[3].toInt() * 100_000_000 + it[4].toInt() * 10_000 + it[5].toInt()
                        }
                    )!! < 0 else false

                return if (foundVersion != null) {
                    Update(
                        shouldUpdate,
                        foundAsset.browserDownloadUrl,
                        foundVersion.groupValues[2],
                        found.body,
                        found.nodeId
                    )
                } else {
                    Update(false, null, null, null, null)
                }
            }
            return Update(false, null, null, null, null)
        }

        private suspend fun Activity.getPreReleaseUpdate(): Update {
            val tagUrl =
                "https://api.github.com/repos/$GITHUB_USER_NAME/$GITHUB_REPO/git/ref/tags/pre-release"
            val releaseUrl = "https://api.github.com/repos/$GITHUB_USER_NAME/$GITHUB_REPO/releases"
            val headers = mapOf("Accept" to "application/vnd.github.v3+json")
            val response =
                parseJson<List<GithubRelease>>(app.get(releaseUrl, headers = headers).text)
            val found = response.lastOrNull { it.prerelease || it.tagName == "pre-release" }
            val foundAsset =
                found?.assets?.filter { it.contentType == "application/vnd.android.package-archive" }
                    ?.getOrNull(0)
            val tagResponse = parseJson<GithubTag>(app.get(tagUrl, headers = headers).text)

            Log.d(LOG_TAG, "Fetched GitHub tag: ${tagResponse.githubObject.sha.take(7)}")

            val shouldUpdate =
                getString(R.string.commit_hash).trim { it.isWhitespace() }.take(7) !=
                        tagResponse.githubObject.sha.trim { it.isWhitespace() }.take(7)

            return if (foundAsset != null) {
                Update(
                    shouldUpdate,
                    foundAsset.browserDownloadUrl,
                    tagResponse.githubObject.sha.take(10),
                    found.body,
                    found.nodeId
                )
            } else {
                Update(false, null, null, null, null)
            }
        }

        private val updateLock = Mutex()

        private suspend fun Activity.downloadUpdate(
            url: String,
            autoInstall: Boolean = false
        ): File {
            try {
                Log.d(LOG_TAG, "Downloading update: $url")
                val appUpdateName = "CloudStream"
                val appUpdateSuffix = "apk"
                this.cacheDir.listFiles()?.filter {
                    it.name.startsWith(appUpdateName) && it.extension == appUpdateSuffix
                }?.forEach {
                    deleteFileOnExit(it)
                }

                val downloadedFile = withContext(Dispatchers.IO) {
                    File.createTempFile(appUpdateName, ".$appUpdateSuffix", cacheDir)
                }

                val sink: BufferedSink = downloadedFile.sink().buffer()
                updateLock.withLock {
                    sink.writeAll(app.get(url).body.source())
                    sink.close()
                    if (autoInstall) {
                        openApk(this, Uri.fromFile(downloadedFile))
                    }
                }
                return downloadedFile
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to download update: ${e.message}")
                throw e
            }
        }

        fun Activity.showInstallDialog(apkPath: String, apkVersion: String) {
            val downloadedFile = File(apkPath)
            if (!downloadedFile.exists()) {
                showToast(getString(R.string.update_file_missing), Toast.LENGTH_LONG)
                return
            }

            val currentVersion = this.packageName?.let {
                this.packageManager.getPackageInfo(it, 0)?.versionName
            }

            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder.setTitle("Install Update from $currentVersion to $apkVersion")
            builder.setPositiveButton(this.getString(R.string.install_update)) { dialog: DialogInterface, _: Int ->
                openApk(this@showInstallDialog, Uri.fromFile(downloadedFile))
                dialog.dismiss()
            }
            builder.setNegativeButton(this.getString(R.string.cancel)) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            val dialog = builder.create()
            dialog.show()
            dialog.setDefaultFocus()
        }

        /**
         * Opens the APK file for installation.
         */
        private fun openApk(context: Activity, uri: Uri) {
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
                // Clear cache and temporary files even if installation fails
            //    context.clearCacheAndTempFiles()
            }
            // Clear cache and temporary files after successful installation
            context.clearCacheAndTempFiles()
        }

      /** Clears cache and temporary files after update installation.
         */
        private fun Activity.clearCacheAndTempFiles() {
            try {
                // Clear cache directory
                this.cacheDir.listFiles()?.forEach { file ->
                    if (file.exists()) {
                        deleteFileOnExit(file)
                    }
                }

                // Clear temporary files (if any specific directory is used for temp files)
                val tempDir = File(this.cacheDir, "temp")
                if (tempDir.exists()) {
                    tempDir.listFiles()?.forEach { file ->
                        if (file.exists()) {
                            deleteFileOnExit(file)
                        }
                    }
                }

                Log.d(LOG_TAG, "Cache and temporary files cleared successfully.")
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to clear cache and temporary files: ${e.message}")
            }
        }

        private fun Activity.showUpdateNotification() {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
            val apkPath = settingsManager.getString("downloaded_apk_path", null)
            val apkVersion = settingsManager.getString("downloaded_apk_version", null)
            if (apkPath == null || apkVersion == null) {
                Log.e(LOG_TAG, "Downloaded APK path or version missing for notification")
                return
            }
            runOnUiThread {
                try {
                    val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                    builder.setTitle(getString(R.string.update_available_notification_title))
                    builder.setMessage(
                        getString(R.string.update_available_notification_message) +
                                "\n\nChangelog:\n${settingsManager.getString("changelog", "")}"
                    )
                    builder.setPositiveButton(R.string.install_update) { _, _ ->
                        // Directly start the installation process using openApk
                        openApk(this, Uri.fromFile(File(apkPath)))

                        // Clear the saved update data only when the user installs the update
                        settingsManager.edit()
                            .remove("downloaded_apk_path")
                            .remove("downloaded_apk_version")
                            .apply()
                    }
                    builder.setNegativeButton(R.string.cancel_update) { dialog, _ ->
                        dialog.dismiss()
                        // Do not save any choice to skip or minimize the update
                    }
                    builder.setNeutralButton("Skip Update") { dialog, _ ->
                        dialog.dismiss()
                        // Do not save any choice to skip or minimize the update
                    }
                    builder.show().setDefaultFocus()
                } catch (e: Exception) {
                    logError(e)
                }
            }
        }

        suspend fun Activity.runAutoUpdate(checkAutoUpdate: Boolean = true): Boolean {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
            val apkPath = settingsManager.getString("downloaded_apk_path", null)
            val apkVersion = settingsManager.getString("downloaded_apk_version", null)

            if (!checkAutoUpdate && apkPath != null && apkVersion != null) {
                runOnUiThread {
                    try {
                        showInstallDialog(apkPath, apkVersion)
                    } catch (e: Exception) {
                        logError(e)
                    }
                }
                return true
            }

            if (!checkAutoUpdate || settingsManager.getBoolean(
                    getString(R.string.auto_update_key),
                    true
                )
            ) {
                val update = getAppUpdate()
                if (update.shouldUpdate && update.updateURL != null) {
                    ioSafe {
                        try {
                            val downloadedFile = downloadUpdate(update.updateURL, false)
                            val apkPath = downloadedFile.absolutePath
                            settingsManager.edit()
                                .putString("downloaded_apk_path", apkPath)
                                .putString("downloaded_apk_version", update.updateVersion)
                                .putString("changelog", update.changelog)
                                .apply()
                            Log.d(LOG_TAG, "Saved APK path: $apkPath")
                            downloadedFile.deleteOnExit()
                            showUpdateNotification()

                            // Perform post-update operations after successful download
                            // performPostUpdateOperations(this@runAutoUpdate)
                        } catch (e: Exception) {
                            Log.e(
                                LOG_TAG,
                                "Update download failed during silent download: ${e.message}"
                            )
                        }
                    }
                    return true
                }
            }
            return false
        }
    }
}
