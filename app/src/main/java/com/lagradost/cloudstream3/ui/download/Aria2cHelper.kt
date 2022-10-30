package com.lagradost.cloudstream3.ui.download

import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import com.lagradost.cloudstream3.utils.VideoDownloadManager.KEY_DOWNLOAD_INFO
import com.lagradost.fetchbutton.aria2c.Aria2Starter
import com.lagradost.fetchbutton.aria2c.DownloadListener
import com.lagradost.fetchbutton.aria2c.Metadata
import java.io.File

const val KEY_DOWNLOAD_INFO_METADATA = "download_info_metadata"

object Aria2cHelper {
    fun deleteId(id: Long) {
        // backward compatibility
        VideoDownloadManager.downloadDeleteEvent.invoke(id.toInt())

        getMetadata(id)?.let { data ->
            Aria2Starter.deleteFiles(data.items.flatMap { it.files })
        }
        removeMetadata(id)
        AcraApplication.removeKey(KEY_DOWNLOAD_INFO, id.toString())
    }

    fun saveMetadata(id: Long, meta: Metadata) {
        AcraApplication.setKey(KEY_DOWNLOAD_INFO_METADATA, id.toString(), meta)
    }

    fun removeMetadata(id: Long) {
        AcraApplication.removeKey(KEY_DOWNLOAD_INFO_METADATA, id.toString())
    }

    fun downloadExist(data: Metadata): Boolean {
        return data.items.any {
            it.files.any { file ->
                try {
                    //println("TESTING PATH: ${file.path}")
                    File(file.path).exists()
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    fun downloadExist(id: Long): Boolean {
        return downloadExist(getMetadata(id) ?: return false)
    }

    fun getMetadata(id: Long): Metadata? {
        return AcraApplication.getKey(KEY_DOWNLOAD_INFO_METADATA, id.toString())
    }

    fun pause(id: Long) {
        DownloadListener.sessionIdToGid[id]?.let { gid ->
            Aria2Starter.pause(gid, all = true)
        }
    }

    fun unpause(id: Long) {
        DownloadListener.sessionIdToGid[id]?.let { gid ->
            Aria2Starter.unpause(gid, all = true)
        }
    }
}