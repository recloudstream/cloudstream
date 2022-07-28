package com.lagradost.cloudstream3.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.DataStore.getDefaultSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.mapper
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
    var restoreFileSelector: ActivityResultLauncher<Array<String>>? = null

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
        @JsonProperty("settings") val settings: BackupVars
    )

    fun FragmentActivity.backup() {
        try {
            if (checkWrite()) {
                val subDir = getBasePath().first
                val date = SimpleDateFormat("yyyy_MM_dd_HH_mm").format(Date(currentTimeMillis()))
                val ext = "json"
                val displayName = "CS3_Backup_${date}"

                val allData = getSharedPrefs().all
                val allSettings = getDefaultSharedPrefs().all

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

                val backupFile = BackupFile(
                    allDataSorted,
                    allSettingsSorted
                )
                val steam =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && subDir?.isDownloadDir() == true) {
                        val cr = this.contentResolver
                        val contentUri =
                            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) // USE INSTEAD OF MediaStore.Downloads.EXTERNAL_CONTENT_URI
                        //val currentMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

                        val newFile = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                            put(MediaStore.MediaColumns.TITLE, displayName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
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
            } else {
                showToast(this, getString(R.string.backup_failed), Toast.LENGTH_LONG)
                requestRW()
                return
            }
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
                    this.let { activity ->
                        uri?.let {
                            try {
                                val input =
                                    activity.contentResolver.openInputStream(uri)
                                        ?: return@registerForActivityResult

                                val restoredValue =
                                    mapper.readValue<BackupFile>(input)
                                activity.restore(
                                    restoredValue,
                                    restoreSettings = true,
                                    restoreDataStore = true
                                )
                                activity.recreate()
                            } catch (e: Exception) {
                                logError(e)
                                try { // smth can fail in .format
                                    showToast(
                                        activity,
                                        getString(R.string.restore_failed_format).format(e.toString())
                                    )
                                } catch (e: Exception) {
                                    logError(e)
                                }
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
        isEditingAppSettings: Boolean = false
    ) {
        map?.forEach {
            setKeyRaw(it.key, it.value, isEditingAppSettings)
        }
    }

    fun Context.restore(
        backupFile: BackupFile,
        restoreSettings: Boolean,
        restoreDataStore: Boolean
    ) {
        if (restoreSettings) {
            restoreMap(backupFile.settings._Bool, true)
            restoreMap(backupFile.settings._Int, true)
            restoreMap(backupFile.settings._String, true)
            restoreMap(backupFile.settings._Float, true)
            restoreMap(backupFile.settings._Long, true)
            restoreMap(backupFile.settings._StringSet, true)
        }

        if (restoreDataStore) {
            restoreMap(backupFile.datastore._Bool)
            restoreMap(backupFile.datastore._Int)
            restoreMap(backupFile.datastore._String)
            restoreMap(backupFile.datastore._Float)
            restoreMap(backupFile.datastore._Long)
            restoreMap(backupFile.datastore._StringSet)
        }
    }
}