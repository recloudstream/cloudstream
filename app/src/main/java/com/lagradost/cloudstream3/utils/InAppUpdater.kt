package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.BufferedSink
import okio.buffer
import okio.sink
import java.io.File
import android.text.TextUtils
import com.lagradost.cloudstream3.utils.AppUtils.setDefaultFocus
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader


class InAppUpdater {
    companion object {
        const val GITHUB_USER_NAME = "recloudstream"
        const val GITHUB_REPO = "cloudstream"

        const val LOG_TAG = "InAppUpdater"

        // === IN APP UPDATER ===
        data class GithubAsset(
            @JsonProperty("name") val name: String,
            @JsonProperty("size") val size: Int, // Size bytes
            @JsonProperty("browser_download_url") val browser_download_url: String, // download link
            @JsonProperty("content_type") val content_type: String, // application/vnd.android.package-archive
        )

        data class GithubRelease(
            @JsonProperty("tag_name") val tag_name: String, // Version code
            @JsonProperty("body") val body: String, // Desc
            @JsonProperty("assets") val assets: List<GithubAsset>,
            @JsonProperty("target_commitish") val target_commitish: String, // branch
            @JsonProperty("prerelease") val prerelease: Boolean,
            @JsonProperty("node_id") val node_id: String //Node Id
        )

        data class GithubObject(
            @JsonProperty("sha") val sha: String, // sha 256 hash
            @JsonProperty("type") val type: String, // object type
            @JsonProperty("url") val url: String,
        )

        data class GithubTag(
            @JsonProperty("object") val github_object: GithubObject,
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
            val response =
                parseJson<List<GithubRelease>>(
                    app.get(
                        url,
                        headers = headers
                    ).text
                )

            val versionRegex = Regex("""(.*?((\d+)\.(\d+)\.(\d+))\.apk)""")
            val versionRegexLocal = Regex("""(.*?((\d+)\.(\d+)\.(\d+)).*)""")
            /*
            val releases = response.map { it.assets }.flatten()
                .filter { it.content_type == "application/vnd.android.package-archive" }
            val found =
                releases.sortedWith(compareBy {
                    versionRegex.find(it.name)?.groupValues?.get(2)
                }).toList().lastOrNull()*/
            val found =
                response.filter { rel ->
                    !rel.prerelease
                }.sortedWith(compareBy { release ->
                    release.assets.filter { it.content_type == "application/vnd.android.package-archive" }
                        .getOrNull(0)?.name?.let { it1 ->
                            versionRegex.find(
                                it1
                            )?.groupValues?.get(2)
                        }
                }).toList().lastOrNull()

            val foundAsset = found?.assets?.getOrNull(0)
            val currentVersion = packageName?.let {
                packageManager.getPackageInfo(
                    it,
                    0
                )
            }

            foundAsset?.name?.let { assetName ->
                val foundVersion = versionRegex.find(assetName)
                val shouldUpdate =
                    if (foundAsset.browser_download_url != "" && foundVersion != null) currentVersion?.versionName?.let { versionName ->
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
                        foundAsset.browser_download_url,
                        foundVersion.groupValues[2],
                        found.body,
                        found.node_id
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

            val found =
                response.lastOrNull { rel ->
                    rel.prerelease || rel.tag_name == "pre-release"
                }
            val foundAsset = found?.assets?.filter { it ->
                it.content_type == "application/vnd.android.package-archive"
            }?.getOrNull(0)

            val tagResponse =
                parseJson<GithubTag>(app.get(tagUrl, headers = headers).text)

            Log.d(LOG_TAG, "Fetched GitHub tag: ${tagResponse.github_object.sha.take(7)}")

            val shouldUpdate =
                (getString(R.string.commit_hash)
                    .trim { c -> c.isWhitespace() }
                    .take(7)
                        !=
                        tagResponse.github_object.sha
                            .trim { c -> c.isWhitespace() }
                            .take(7))

            return if (foundAsset != null) {
                Update(
                    shouldUpdate,
                    foundAsset.browser_download_url,
                    tagResponse.github_object.sha,
                    found.body,
                    found.node_id
                )
            } else {
                Update(false, null, null, null, null)
            }
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
                }?.forEach {
                    it.deleteOnExit()
                }

                val downloadedFile = File.createTempFile(appUpdateName, ".$appUpdateSuffix")
                val sink: BufferedSink = downloadedFile.sink().buffer()

                updateLock.withLock {
                    sink.writeAll(app.get(url).body.source())
                    sink.close()
                    openApk(this, Uri.fromFile(downloadedFile))
                }
                return true
            } catch (e: Exception) {
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
         **/
        suspend fun Activity.runAutoUpdate(checkAutoUpdate: Boolean = true): Boolean {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

            if (!checkAutoUpdate || settingsManager.getBoolean(
                    getString(R.string.auto_update_key),
                    true
                )
            ) {
                val update = getAppUpdate()
                if (
                    update.shouldUpdate &&
                        update.updateURL != null) {

                    // Check if update should be skipped
                    val updateNodeId =
                        settingsManager.getString(getString(R.string.skip_update_key), "")

                    // Skips the update if its an automatic update and the update is skipped
                    // This allows updating manually
                    if (update.updateNodeId.equals(updateNodeId) && checkAutoUpdate) {
                        return false
                    }

                    runOnUiThread {
                        try {
                            val currentVersion = packageName?.let {
                                packageManager.getPackageInfo(
                                    it,
                                    0
                                )
                            }

                            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                            builder.setTitle(
                                getString(R.string.new_update_format).format(
                                    currentVersion?.versionName,
                                    update.updateVersion
                                )
                            )
                            builder.setMessage("${update.changelog}")

                            val context = this
                            builder.apply {
                                setPositiveButton(R.string.update) { _, _ ->
                                    // Forcefully start any delayed installations
                                    if (ApkInstaller.delayedInstaller?.startInstallation() == true) return@setPositiveButton

                                    showToast(context, R.string.download_started, Toast.LENGTH_LONG)

                                    // Check if the setting hasn't been changed
                                    if (settingsManager.getInt(
                                            getString(R.string.apk_installer_key),
                                            -1
                                        ) == -1
                                    ) {
                                        if (isMiUi()) // Set to legacy if using miui
                                            settingsManager.edit()
                                                .putInt(getString(R.string.apk_installer_key), 1)
                                                .apply()
                                    }

                                    val currentInstaller =
                                        settingsManager.getInt(
                                            getString(R.string.apk_installer_key),
                                            0
                                        )

                                    when (currentInstaller) {
                                        // New method
                                        0 -> {
                                            val intent = PackageInstallerService.getIntent(
                                                context,
                                                update.updateURL
                                            )
                                            ContextCompat.startForegroundService(context, intent)
                                        }
                                        // Legacy
                                        1 -> {
                                            ioSafe {
                                                if (!downloadUpdate(update.updateURL))
                                                    runOnUiThread {
                                                        showToast(
                                                            context,
                                                            R.string.download_failed,
                                                            Toast.LENGTH_LONG
                                                        )
                                                    }
                                            }
                                        }
                                    }
                                }

                                setNegativeButton(R.string.cancel) { _, _ -> }

                                if (checkAutoUpdate) {
                                    setNeutralButton(R.string.skip_update) { _, _ ->
                                        settingsManager.edit().putString(
                                            getString(R.string.skip_update_key),
                                            update.updateNodeId ?: ""
                                        ).apply()
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
            } catch (ex: IOException) {
                null
            }
        }
    }
}
