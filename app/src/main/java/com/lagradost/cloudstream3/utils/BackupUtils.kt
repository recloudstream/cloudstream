package com.lagradost.cloudstream3.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.AcraApplication.Companion.getActivity
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.plugins.PLUGINS_KEY
import com.lagradost.cloudstream3.plugins.PLUGINS_KEY_LOCAL
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.Companion.ANILIST_CACHED_LIST
import com.lagradost.cloudstream3.syncproviders.providers.MALApi.Companion.MAL_CACHED_LIST
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore.getDefaultSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.mapper
import com.lagradost.cloudstream3.utils.UIHelper.checkWrite
import com.lagradost.cloudstream3.utils.UIHelper.requestRW
import com.lagradost.cloudstream3.utils.VideoDownloadManager.StreamData
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getBasePath
import com.lagradost.cloudstream3.utils.VideoDownloadManager.setupStream
import com.lagradost.safefile.MediaFileContentType
import com.lagradost.safefile.SafeFile
import okhttp3.internal.closeQuietly
import java.io.IOException
import java.io.OutputStream
import java.io.PrintWriter
import java.lang.System.currentTimeMillis
import java.text.SimpleDateFormat
import java.util.Date

object BackupUtils {

    /**
     * No sensitive or breaking data in the backup
     * */
    private val nonTransferableKeys = listOf(
        ANILIST_CACHED_LIST,
        MAL_CACHED_LIST,

        // The plugins themselves are not backed up
        PLUGINS_KEY,
        PLUGINS_KEY_LOCAL,

        AccountManager.ACCOUNT_TOKEN,
        AccountManager.ACCOUNT_IDS,

        "biometric_key", // can lock down users if backup is shared on a incompatible device
        "nginx_user", // Nginx user key

        // No access rights after restore data from backup
        "download_path_key",
        "download_path_key_visual",
        "backup_path_key",
        "backup_dir_path_key",

        // When sharing backup we do not want to transfer what is essentially the password
        // Note that this is deprecated, and can be removed after all tokens have expired
        "anilist_token",
        "anilist_user",
        "mal_user",
        "mal_token",
        "mal_refresh_token",
        "mal_unixtime",
        "open_subtitles_user",
        "subdl_user",
        "simkl_token",
    )

    /** false if key should not be contained in backup */
    private fun String.isTransferable(): Boolean {
        return !nonTransferableKeys.any { this.contains(it) }
    }

    private var restoreFileSelector: ActivityResultLauncher<Array<String>>? = null

    // Kinda hack, but I couldn't think of a better way
    data class BackupVars(
        @JsonProperty("_Bool") val bool: Map<String, Boolean>?,
        @JsonProperty("_Int") val int: Map<String, Int>?,
        @JsonProperty("_String") val string: Map<String, String>?,
        @JsonProperty("_Float") val float: Map<String, Float>?,
        @JsonProperty("_Long") val long: Map<String, Long>?,
        @JsonProperty("_StringSet") val stringSet: Map<String, Set<String>?>?,
    )

    data class BackupFile(
        @JsonProperty("datastore") val datastore: BackupVars,
        @JsonProperty("settings") val settings: BackupVars
    )

    @Suppress("UNCHECKED_CAST")
    private fun getBackup(context: Context?): BackupFile? {
        if (context == null) return null

        val allData = context.getSharedPrefs().all.filter { it.key.isTransferable() }
        val allSettings = context.getDefaultSharedPrefs().all.filter { it.key.isTransferable() }

        val allDataSorted = BackupVars(
            allData.filter { it.value is Boolean } as? Map<String, Boolean>,
            allData.filter { it.value is Int } as? Map<String, Int>,
            allData.filter { it.value is String } as? Map<String, String>,
            allData.filter { it.value is Float } as? Map<String, Float>,
            allData.filter { it.value is Long } as? Map<String, Long>,
            allData.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>
        )

        val allSettingsSorted = BackupVars(
            allSettings.filter { it.value is Boolean } as? Map<String, Boolean>,
            allSettings.filter { it.value is Int } as? Map<String, Int>,
            allSettings.filter { it.value is String } as? Map<String, String>,
            allSettings.filter { it.value is Float } as? Map<String, Float>,
            allSettings.filter { it.value is Long } as? Map<String, Long>,
            allSettings.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>
        )

        return BackupFile(
            allDataSorted,
            allSettingsSorted
        )
    }

    @WorkerThread
    fun restore(
        context: Context?,
        backupFile: BackupFile,
        restoreSettings: Boolean,
        restoreDataStore: Boolean
    ) {
        if (context == null) return
        if (restoreSettings) {
            context.restoreMap(backupFile.settings.bool, true)
            context.restoreMap(backupFile.settings.int, true)
            context.restoreMap(backupFile.settings.string, true)
            context.restoreMap(backupFile.settings.float, true)
            context.restoreMap(backupFile.settings.long, true)
            context.restoreMap(backupFile.settings.stringSet, true)
        }

        if (restoreDataStore) {
            context.restoreMap(backupFile.datastore.bool)
            context.restoreMap(backupFile.datastore.int)
            context.restoreMap(backupFile.datastore.string)
            context.restoreMap(backupFile.datastore.float)
            context.restoreMap(backupFile.datastore.long)
            context.restoreMap(backupFile.datastore.stringSet)
        }
    }

    @SuppressLint("SimpleDateFormat")
    fun backup(context: Context?) = ioSafe {
        if (context == null) return@ioSafe

        var fileStream: OutputStream? = null
        var printStream: PrintWriter? = null
        try {
            if (!context.checkWrite()) {
                showToast(R.string.backup_failed, Toast.LENGTH_LONG)
                context.getActivity()?.requestRW()
                return@ioSafe
            }

            val date = SimpleDateFormat("yyyy_MM_dd_HH_mm").format(Date(currentTimeMillis()))
            val displayName = "CS3_Backup_${date}"
            val backupFile = getBackup(context)
            val stream = setupBackupStream(context, displayName)

            fileStream = stream.openNew()
            printStream = PrintWriter(fileStream)
            printStream.print(mapper.writeValueAsString(backupFile))

            showToast(
                R.string.backup_success,
                Toast.LENGTH_LONG
            )
        } catch (e: Exception) {
            logError(e)
            try {
                showToast(
                    txt(R.string.backup_failed_error_format, e.toString()),
                    Toast.LENGTH_LONG
                )
            } catch (e: Exception) {
                logError(e)
            }
        } finally {
            printStream?.closeQuietly()
            fileStream?.closeQuietly()
        }
    }

    @Throws(IOException::class)
    private fun setupBackupStream(context: Context, name: String, ext: String = "txt"): StreamData {
        return setupStream(
            baseFile = getCurrentBackupDir(context).first ?: getDefaultBackupDir(context)
            ?: throw IOException("Bad config"),
            name,
            folder = null,
            extension = ext,
            tryResume = false
        )
    }

    fun FragmentActivity.setUpBackup() {
        try {
            restoreFileSelector =
                registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                    if (uri == null) return@registerForActivityResult
                    val activity = this
                    ioSafe {
                        try {
                            val input = activity.contentResolver.openInputStream(uri)
                                ?: return@ioSafe

                            val restoredValue =
                                mapper.readValue<BackupFile>(input)

                            restore(
                                activity,
                                restoredValue,
                                restoreSettings = true,
                                restoreDataStore = true
                            )
                            activity.runOnUiThread { activity.recreate() }
                        } catch (e: Exception) {
                            logError(e)
                            main { // smth can fail in .format
                                showToast(
                                    getString(R.string.restore_failed_format).format(e.toString())
                                )
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun FragmentActivity.restorePrompt() {
        runOnUiThread {
            try {
                restoreFileSelector?.launch(
                    arrayOf(
                        "text/plain",
                        "text/str",
                        "text/x-unknown",
                        "application/json",
                        "unknown/unknown",
                        "content/unknown",
                        "application/octet-stream",
                    )
                )
            } catch (e: Exception) {
                showToast(e.message)
                logError(e)
            }
        }
    }

    private fun <T> Context.restoreMap(
        map: Map<String, T>?,
        isEditingAppSettings: Boolean = false
    ) {
        val editor = DataStore.editor(this, isEditingAppSettings)
        map?.forEach {
            if (it.key.isTransferable()) {
                editor.setKeyRaw(it.key, it.value)
            }
        }
        editor.apply()
    }

    /**
     * Copy of [VideoDownloadManager.basePathToFile], [VideoDownloadManager.getDefaultDir] and [VideoDownloadManager.getBasePath]
     * modded for backup specific paths
     * */

    fun getDefaultBackupDir(context: Context): SafeFile? {
        return SafeFile.fromMedia(context, MediaFileContentType.Downloads)
    }

    fun getCurrentBackupDir(context: Context): Pair<SafeFile?, String?> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
        val basePathSetting =
            settingsManager.getString(context.getString(R.string.backup_path_key), null)
        return baseBackupPathToFile(context, basePathSetting) to basePathSetting
    }

    private fun baseBackupPathToFile(context: Context, path: String?): SafeFile? {
        return when {
            path.isNullOrBlank() -> getDefaultBackupDir(context)
            path.startsWith("content://") -> SafeFile.fromUri(context, path.toUri())
            else -> SafeFile.fromFilePath(context, path)
        }
    }
}
