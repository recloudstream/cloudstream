package com.lagradost.cloudstream3.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
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
import com.lagradost.cloudstream3.syncproviders.IBackupAPI
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.Companion.ANILIST_CACHED_LIST
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.Companion.ANILIST_TOKEN_KEY
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.Companion.ANILIST_UNIXTIME_KEY
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.Companion.ANILIST_USER_KEY
import com.lagradost.cloudstream3.syncproviders.providers.GoogleDriveApi
import com.lagradost.cloudstream3.syncproviders.providers.MALApi.Companion.MAL_CACHED_LIST
import com.lagradost.cloudstream3.syncproviders.providers.MALApi.Companion.MAL_REFRESH_TOKEN_KEY
import com.lagradost.cloudstream3.syncproviders.providers.MALApi.Companion.MAL_TOKEN_KEY
import com.lagradost.cloudstream3.syncproviders.providers.MALApi.Companion.MAL_UNIXTIME_KEY
import com.lagradost.cloudstream3.syncproviders.providers.MALApi.Companion.MAL_USER_KEY
import com.lagradost.cloudstream3.syncproviders.providers.OpenSubtitlesApi.Companion.OPEN_SUBTITLES_USER_KEY
import com.lagradost.cloudstream3.ui.result.txt
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
import com.lagradost.cloudstream3.utils.VideoDownloadManager.setupStream
import okhttp3.internal.closeQuietly
import java.io.OutputStream
import java.io.PrintWriter
import java.lang.System.currentTimeMillis
import java.text.SimpleDateFormat
import java.util.*

object BackupUtils {
    enum class RestoreSource {
        DATA, SETTINGS, SYNC;

        val prefix = "$name/"
        val syncPrefix = "${IBackupAPI.SYNC_HISTORY_PREFIX}$prefix"
    }

    /**
     * No sensitive or breaking data in the backup
     * */
    val nonTransferableKeys = listOf(
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
        GoogleDriveApi.K.TOKEN.value,
        GoogleDriveApi.K.IS_READY.value,

        // The plugins themselves are not backed up
        PLUGINS_KEY,
        PLUGINS_KEY_LOCAL,

        OPEN_SUBTITLES_USER_KEY,
        "nginx_user", // Nginx user key
        DOWNLOAD_HEADER_CACHE,
        DOWNLOAD_EPISODE_CACHE
    )

    /** false if blacklisted key */
    private fun String.isTransferable(): Boolean {
        return !nonTransferableKeys.any { this.contains(it) }
    }

    private var restoreFileSelector: ActivityResultLauncher<Array<String>>? = null

    // Kinda hack, but I couldn't think of a better way
    data class RestoreMapData(
        val wantToRestore: MutableSet<String> = mutableSetOf(),
        val successfulRestore: MutableSet<String> = mutableSetOf()
    ) {
        fun addAll(data: RestoreMapData) {
            wantToRestore.addAll(data.wantToRestore)
            successfulRestore.addAll(data.successfulRestore)
        }
    }

    data class BackupVars(
        @JsonProperty("_Bool") val _Bool: Map<String, Boolean>?,
        @JsonProperty("_Int") val _Int: Map<String, Int>?,
        @JsonProperty("_String") val _String: Map<String, String>?,
        @JsonProperty("_Float") val _Float: Map<String, Float>?,
        @JsonProperty("_Long") val _Long: Map<String, Long>?,
        @JsonProperty("_StringSet") val _StringSet: Map<String, Set<String>?>?,
    ) {
        constructor() : this(
            mapOf(),
            mapOf(),
            mapOf(),
            mapOf(),
            mapOf(),
            mapOf(),
        )
    }

    data class BackupFile(
        @JsonProperty("datastore") val datastore: BackupVars,
        @JsonProperty("settings") val settings: BackupVars,
        @JsonProperty("sync-meta") val syncMeta: BackupVars = BackupVars(),
    ) {
        fun restore(
            ctx: Context,
            source: RestoreSource,
            restoreKeys: Set<String>? = null
        ): RestoreMapData {
            val data = getData(source)
            val successfulRestore = RestoreMapData()

            successfulRestore.addAll(ctx.restoreMap(data._Bool, source, restoreKeys))
            successfulRestore.addAll(ctx.restoreMap(data._Int, source, restoreKeys))
            successfulRestore.addAll(ctx.restoreMap(data._String, source, restoreKeys))
            successfulRestore.addAll(ctx.restoreMap(data._Float, source, restoreKeys))
            successfulRestore.addAll(ctx.restoreMap(data._Long, source, restoreKeys))
            successfulRestore.addAll(ctx.restoreMap(data._StringSet, source, restoreKeys))

            return successfulRestore
        }

        fun getData(source: RestoreSource) = when (source) {
            RestoreSource.SYNC -> syncMeta
            RestoreSource.DATA -> datastore
            RestoreSource.SETTINGS -> settings
        }
    }

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
    fun Context.restore(backupFile: BackupFile, restoreKeys: Set<String>? = null) = restore(
        backupFile,
        restoreKeys,
        RestoreSource.SYNC,
        RestoreSource.DATA,
        RestoreSource.SETTINGS
    )

    @WorkerThread
    fun Context.restore(
        backupFile: BackupFile,
        restoreKeys: Set<String>? = null,
        vararg restoreSources: RestoreSource
    ) {
        Log.d(BackupAPI.LOG_KEY, "will restore keys = $restoreKeys")

        for (restoreSource in restoreSources) {
            val restoreData = RestoreMapData()

            restoreData.addAll(backupFile.restore(this, restoreSource, restoreKeys))

            // we must remove keys that are not present
            if (!restoreKeys.isNullOrEmpty()) {
                Log.d(BackupAPI.LOG_KEY, "successfulRestore for src=[${restoreSource.name}]: ${restoreData.successfulRestore}")
                val removedKeys = restoreData.wantToRestore - restoreData.successfulRestore
                Log.d(BackupAPI.LOG_KEY, "removed keys for src=[${restoreSource.name}]: $removedKeys")

                removedKeys.forEach { removeKeyRaw(it, restoreSource) }
            }
        }

        Log.d(BackupAPI.LOG_KEY, "restore on ui event fired")
        afterBackupRestoreEvent.invoke(Unit)
    }

    @SuppressLint("SimpleDateFormat")
    fun FragmentActivity.backup() = ioSafe {
        var fileStream: OutputStream? = null
        var printStream: PrintWriter? = null
        try {
            if (!checkWrite()) {
                showToast(R.string.backup_failed, Toast.LENGTH_LONG)
                requestRW()
                return@ioSafe
            }

            val date = SimpleDateFormat("yyyy_MM_dd_HH_mm").format(Date(currentTimeMillis()))
            val ext = "txt"
            val displayName = "CS3_Backup_${date}"
            val backupFile = getBackup()
            val stream = setupStream(this@backup, displayName, null, ext, false)

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

                            activity.restore(mapper.readValue(input))
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
        restoreSource: RestoreSource,
        restoreKeys: Set<String>? = null
    ): RestoreMapData {
        val restoreOnlyThese = mutableSetOf<String>()
        val successfulRestore = mutableSetOf<String>()

        if (!restoreKeys.isNullOrEmpty()) {
            var prefixToMatch = restoreSource.syncPrefix
            var prefixToRemove = prefixToMatch

            if (restoreSource == RestoreSource.SYNC) {
                prefixToMatch = IBackupAPI.SYNC_HISTORY_PREFIX
                prefixToRemove = ""
            }

            val restore = restoreKeys.filter {
                it.startsWith(prefixToMatch)
            }.map {
                it.removePrefix(prefixToRemove)
            }

            restoreOnlyThese.addAll(restore)
        }


        map?.filter {
            var isTransferable = it.key.withoutPrefix(restoreSource).isTransferable()

            if (isTransferable && restoreOnlyThese.isNotEmpty()) {
                isTransferable = restoreOnlyThese.contains(it.key.withoutPrefix(restoreSource))
            }

            if (isTransferable) {
                successfulRestore.add(it.key.withoutPrefix(restoreSource))
            }

            isTransferable
        }?.forEach {
            setKeyRaw(it.key.withoutPrefix(restoreSource), it.value, restoreSource)
        }

        return RestoreMapData(
            restoreOnlyThese,
            successfulRestore
        )
    }
}

private fun String.withoutPrefix(restoreSource: BackupUtils.RestoreSource) =
    // will not remove sync prefix because it wont match (its not a bug its a feature ¯\_(ツ)_/¯ )
    removePrefix(restoreSource.prefix)
