package com.lagradost.cloudstream3.utils

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.WorkerThread
import androidx.fragment.app.FragmentActivity
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity.Companion.afterBackupRestoreEvent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.plugins.PLUGINS_KEY
import com.lagradost.cloudstream3.plugins.PLUGINS_KEY_LOCAL
import com.lagradost.cloudstream3.syncproviders.BackupAPI
import com.lagradost.cloudstream3.syncproviders.InAppOAuth2APIManager
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.Companion.ANILIST_CACHED_LIST
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.Companion.ANILIST_TOKEN_KEY
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.Companion.ANILIST_UNIXTIME_KEY
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.Companion.ANILIST_USER_KEY
import com.lagradost.cloudstream3.syncproviders.providers.MALApi.Companion.MAL_CACHED_LIST
import com.lagradost.cloudstream3.syncproviders.providers.MALApi.Companion.MAL_REFRESH_TOKEN_KEY
import com.lagradost.cloudstream3.syncproviders.providers.MALApi.Companion.MAL_TOKEN_KEY
import com.lagradost.cloudstream3.syncproviders.providers.MALApi.Companion.MAL_UNIXTIME_KEY
import com.lagradost.cloudstream3.syncproviders.providers.MALApi.Companion.MAL_USER_KEY
import com.lagradost.cloudstream3.syncproviders.providers.OpenSubtitlesApi.Companion.OPEN_SUBTITLES_USER_KEY
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore.getDefaultSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getSyncPrefs
import com.lagradost.cloudstream3.utils.DataStore.mapper
import com.lagradost.cloudstream3.utils.DataStore.removeKeyRaw
import com.lagradost.cloudstream3.utils.DataStore.setKeyRaw
import com.lagradost.cloudstream3.utils.UIHelper.checkWrite
import com.lagradost.cloudstream3.utils.UIHelper.requestRW
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getBasePath
import com.lagradost.cloudstream3.utils.VideoDownloadManager.isDownloadDir
import java.io.IOException
import java.io.PrintWriter
import java.lang.System.currentTimeMillis
import java.text.SimpleDateFormat
import java.util.*

object BackupUtils {
    enum class RestoreSource {
        DATA, SETTINGS, SYNC;

        val prefix = "$name/"
    }

    /**
     * No sensitive or breaking data in the backup
     * */
    private val nonTransferableKeys = listOf(
        // When sharing backup we do not want to transfer what is essentially the password
        ANILIST_TOKEN_KEY,
        ANILIST_CACHED_LIST,
        ANILIST_UNIXTIME_KEY,
        ANILIST_USER_KEY,
        MAL_TOKEN_KEY,
        MAL_REFRESH_TOKEN_KEY,
        MAL_CACHED_LIST,
        MAL_UNIXTIME_KEY,
        MAL_USER_KEY,
        InAppOAuth2APIManager.K.TOKEN.value,

        // The plugins themselves are not backed up
        PLUGINS_KEY,
        PLUGINS_KEY_LOCAL,

        OPEN_SUBTITLES_USER_KEY,
        "nginx_user", // Nginx user key
    )

    /** false if blacklisted key */
    private fun String.isTransferable(): Boolean {
        return !nonTransferableKeys.any { this.contains(it) }
    }

    private var restoreFileSelector: ActivityResultLauncher<Array<String>>? = null

    // Kinda hack, but I couldn't think of a better way
    data class BackupVars(
        @JsonProperty("_Bool") val _Bool: Map<String, Boolean>?,
        @JsonProperty("_Int") val _Int: Map<String, Int>?,
        @JsonProperty("_String") val _String: Map<String, String>?,
        @JsonProperty("_Float") val _Float: Map<String, Float>?,
        @JsonProperty("_Long") val _Long: Map<String, Long>?,
        @JsonProperty("_StringSet") val _StringSet: Map<String, Set<String>?>?,
    )

    data class BackupFile(
        @JsonProperty("datastore") val datastore: BackupVars,
        @JsonProperty("settings") val settings: BackupVars,
        @JsonProperty("sync-meta") val syncMeta: BackupVars
    )

    @Suppress("UNCHECKED_CAST")
    fun Context.getBackup(): BackupFile {
        val syncDataPrefs = getSyncPrefs().all.filter { it.key.isTransferable() }
        val allData = getSharedPrefs().all.filter { it.key.isTransferable() }
        val allSettings = getDefaultSharedPrefs().all.filter { it.key.isTransferable() }

        val syncData = BackupVars(
            syncDataPrefs.filter { it.value is Boolean } as? Map<String, Boolean>,
            syncDataPrefs.filter { it.value is Int } as? Map<String, Int>,
            syncDataPrefs.filter { it.value is String } as? Map<String, String>,
            syncDataPrefs.filter { it.value is Float } as? Map<String, Float>,
            syncDataPrefs.filter { it.value is Long } as? Map<String, Long>,
            syncDataPrefs.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>
        )

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
            allSettingsSorted,
            syncData
        )
    }

    @WorkerThread
    fun Context.restore(
        backupFile: BackupFile,
        restoreKeys: List<String>? = null,
        restoreSettings: Boolean,
        restoreDataStore: Boolean,
        restoreSyncData: Boolean
    ) {
        if (restoreSyncData) {
            restoreMap(backupFile.syncMeta._Bool, RestoreSource.SYNC, restoreKeys)
            restoreMap(backupFile.syncMeta._Int, RestoreSource.SYNC, restoreKeys)
            restoreMap(backupFile.syncMeta._String, RestoreSource.SYNC, restoreKeys)
            restoreMap(backupFile.syncMeta._Float, RestoreSource.SYNC, restoreKeys)
            restoreMap(backupFile.syncMeta._Long, RestoreSource.SYNC, restoreKeys)
            restoreMap(backupFile.syncMeta._StringSet, RestoreSource.SYNC, restoreKeys)
        }

        if (restoreSettings) {
            restoreMap(backupFile.settings._Bool, RestoreSource.SETTINGS, restoreKeys)
            restoreMap(backupFile.settings._Int, RestoreSource.SETTINGS, restoreKeys)
            restoreMap(backupFile.settings._String, RestoreSource.SETTINGS, restoreKeys)
            restoreMap(backupFile.settings._Float, RestoreSource.SETTINGS, restoreKeys)
            restoreMap(backupFile.settings._Long, RestoreSource.SETTINGS, restoreKeys)
            restoreMap(backupFile.settings._StringSet, RestoreSource.SETTINGS, restoreKeys)
        }

        if (restoreDataStore) {
            restoreMap(backupFile.datastore._Bool, RestoreSource.DATA, restoreKeys)
            restoreMap(backupFile.datastore._Int, RestoreSource.DATA, restoreKeys)
            restoreMap(backupFile.datastore._String, RestoreSource.DATA, restoreKeys)
            restoreMap(backupFile.datastore._Float, RestoreSource.DATA, restoreKeys)
            restoreMap(backupFile.datastore._Long, RestoreSource.DATA, restoreKeys)
            restoreMap(backupFile.datastore._StringSet, RestoreSource.DATA, restoreKeys)
        }
    }

    @SuppressLint("SimpleDateFormat")
    fun FragmentActivity.backup() {
        try {
            if (!checkWrite()) {
                showToast(this, getString(R.string.backup_failed), Toast.LENGTH_LONG)
                requestRW()
                return
            }

            val subDir = getBasePath().first
            val date = SimpleDateFormat("yyyy_MM_dd_HH_mm").format(Date(currentTimeMillis()))
            val ext = "json"
            val displayName = "CS3_Backup_${date}"
            val backupFile = getBackup()

            val steam = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && subDir?.isDownloadDir() == true
            ) {
                val cr = this.contentResolver
                val contentUri =
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) // USE INSTEAD OF MediaStore.Downloads.EXTERNAL_CONTENT_URI
                //val currentMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

                val newFile = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.TITLE, displayName)
                    // While it a json file we store as txt because not
                    // all file managers support mimetype json
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    //put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                }

                val newFileUri = cr.insert(
                    contentUri,
                    newFile
                ) ?: throw IOException("Error creating file uri")
                cr.openOutputStream(newFileUri, "w")
                    ?: throw IOException("Error opening stream")
            } else {
                val fileName = "$displayName.$ext"
                val rFile = subDir?.findFile(fileName)
                if (rFile?.exists() == true) {
                    rFile.delete()
                }
                val file =
                    subDir?.createFile(fileName)
                        ?: throw IOException("Error creating file")
                if (!file.exists()) throw IOException("File does not exist")
                file.openOutputStream()
            }

            val printStream = PrintWriter(steam)
            printStream.print(mapper.writeValueAsString(backupFile))
            printStream.close()

            showToast(
                this,
                R.string.backup_success,
                Toast.LENGTH_LONG
            )
        } catch (e: Exception) {
            logError(e)
            try {
                showToast(
                    this,
                    getString(R.string.backup_failed_error_format).format(e.toString()),
                    Toast.LENGTH_LONG
                )
            } catch (e: Exception) {
                logError(e)
            }
        }
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

                            activity.restore(
                                mapper.readValue(input),
                                restoreSettings = true,
                                restoreDataStore = true,
                                restoreSyncData = true
                            )
                            activity.runOnUiThread { activity.recreate() }
                        } catch (e: Exception) {
                            logError(e)
                            main { // smth can fail in .format
                                showToast(
                                    activity,
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
                showToast(this, e.message)
                logError(e)
            }
        }
    }

    private fun <T> Context.restoreMap(
        map: Map<String, T>?,
        restoreSource: RestoreSource,
        restoreKeys: List<String>?
    ) {
        val restoreOnlyThese = mutableListOf<String>()
        val successfulRestore = mutableListOf<String>()

        if (!restoreKeys.isNullOrEmpty()) {
            val prefixToMatch = "${BackupAPI.SYNC_HISTORY_PREFIX}${restoreSource.prefix}"

            val restore = restoreKeys.filter {
                it.startsWith(prefixToMatch)
            }.map {
                it.removePrefix(prefixToMatch)
            }

            restoreOnlyThese.addAll(restore)
        }

        map?.filter {
            var isTransferable = it.key.isTransferable()
            var canRestore = isTransferable
            if (restoreOnlyThese.isNotEmpty()) {
                canRestore = canRestore && restoreOnlyThese.contains(it.key)
            }

            if (isTransferable && canRestore) {
                successfulRestore.add(it.key)
            }

            canRestore
        }?.forEach {
            setKeyRaw(it.key, it.value, restoreSource)
        }

        // we must remove keys that are not present
        if (!restoreKeys.isNullOrEmpty()) {
            var removedKeys = restoreOnlyThese - successfulRestore.toSet()
            removedKeys.forEach { removeKeyRaw(it, restoreSource) }
        }
    }
}