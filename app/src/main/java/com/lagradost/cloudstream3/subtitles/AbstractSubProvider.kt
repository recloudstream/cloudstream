package com.lagradost.cloudstream3.subtitles

import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.MainActivity.Companion.deleteFileOnExit
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.ui.player.SubtitleOrigin
import com.lagradost.cloudstream3.utils.Coroutines.threadSafeListOf
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.util.zip.ZipInputStream

interface AbstractSubProvider {
    val idPrefix: String

    @WorkerThread
    @Throws
    suspend fun search(query: SubtitleSearch): List<SubtitleEntity>? {
        throw NotImplementedError()
    }

    @WorkerThread
    @Throws
    suspend fun load(data: SubtitleEntity): String? {
        throw NotImplementedError()
    }

    @WorkerThread
    @Throws
    suspend fun SubtitleResource.getResources(data: SubtitleEntity) {
        this.addUrl(load(data))
    }

    @WorkerThread
    @Throws
    suspend fun getResource(data: SubtitleEntity): SubtitleResource {
        return SubtitleResource().apply {
            this.getResources(data)
        }
    }
}

class SubRepository(val api: AbstractSubProvider) {
    companion object {
        data class SavedSearchResponse(
            val unixTime: Long,
            val response: List<SubtitleEntity>,
            val query: SubtitleSearch
        )

        data class SavedResourceResponse(
            val unixTime: Long,
            val response: SubtitleResource,
            val query: SubtitleEntity
        )

        // maybe make this a generic struct? right now there is a lot of boilerplate
        private val searchCache = threadSafeListOf<SavedSearchResponse>()
        private var searchCacheIndex: Int = 0
        private val resourceCache = threadSafeListOf<SavedResourceResponse>()
        private var resourceCacheIndex: Int = 0
        const val CACHE_SIZE = 20
    }

    val idPrefix: String get() = api.idPrefix

    @WorkerThread
    suspend fun getResource(data: SubtitleEntity): Resource<SubtitleResource> = safeApiCall {
        synchronized(resourceCache) {
            for (item in resourceCache) {
                // 20 min save
                if (item.query == data && (unixTime - item.unixTime) < 60 * 20) {
                    return@safeApiCall item.response
                }
            }
        }

        val returnValue = api.getResource(data)
        synchronized(resourceCache) {
            val add = SavedResourceResponse(unixTime, returnValue, data)
            if (resourceCache.size > CACHE_SIZE) {
                resourceCache[resourceCacheIndex] = add // rolling cache
                resourceCacheIndex = (resourceCacheIndex + 1) % CACHE_SIZE
            } else {
                resourceCache.add(add)
            }
        }
        returnValue
    }

    @WorkerThread
    suspend fun search(query: SubtitleSearch): Resource<List<SubtitleEntity>> {
        return safeApiCall {
            synchronized(searchCache) {
                for (item in searchCache) {
                    // 120 min save
                    if (item.query == query && (unixTime - item.unixTime) < 60 * 120) {
                        return@safeApiCall item.response
                    }
                }
            }

            val returnValue = api.search(query) ?: throw ErrorLoadingException("Null subtitles")

            // only cache valid return values
            if (returnValue.isNotEmpty()) {
                val add = SavedSearchResponse(unixTime, returnValue, query)
                synchronized(searchCache) {
                    if (searchCache.size > CACHE_SIZE) {
                        searchCache[searchCacheIndex] = add // rolling cache
                        searchCacheIndex = (searchCacheIndex + 1) % CACHE_SIZE
                    } else {
                        searchCache.add(add)
                    }
                }
            }
            returnValue
        }
    }

}

/**
 * A builder for subtitle files.
 * @see addUrl
 * @see addFile
 */
class SubtitleResource {
    fun downloadFile(source: BufferedSource): File {
        val file = File.createTempFile("temp-subtitle", ".tmp").apply {
            deleteFileOnExit(this)
        }
        val sink = file.sink().buffer()
        sink.writeAll(source)
        sink.close()
        source.close()

        return file
    }

    private fun unzip(file: File): List<Pair<String, File>> {
        val entries = mutableListOf<Pair<String, File>>()

        ZipInputStream(file.inputStream()).use { zipInputStream ->
            var zipEntry = zipInputStream.nextEntry

            while (zipEntry != null) {
                val tempFile = File.createTempFile("unzipped-subtitle", ".tmp").apply {
                    deleteFileOnExit(this)
                }
                entries.add(zipEntry.name to tempFile)

                tempFile.sink().buffer().use { buffer ->
                    buffer.writeAll(zipInputStream.source())
                }

                zipEntry = zipInputStream.nextEntry
            }
        }
        return entries
    }

    data class SingleSubtitleResource(
        val name: String?,
        val url: String,
        val origin: SubtitleOrigin
    )

    private var resources: MutableList<SingleSubtitleResource> = mutableListOf()

    fun getSubtitles(): List<SingleSubtitleResource> {
        return resources.toList()
    }

    fun addUrl(url: String?, name: String? = null) {
        if (url == null) return
        this.resources.add(
            SingleSubtitleResource(name, url, SubtitleOrigin.URL)
        )
    }

    fun addFile(file: File, name: String? = null) {
        this.resources.add(
            SingleSubtitleResource(name, file.toUri().toString(), SubtitleOrigin.DOWNLOADED_FILE)
        )
        deleteFileOnExit(file)
    }

    suspend fun addZipUrl(
        url: String,
        nameGenerator: (String, File) -> String? = { _, _ -> null }
    ) {
        val source = app.get(url).okhttpResponse.body.source()
        val zip = downloadFile(source)
        val realFiles = unzip(zip)
        zip.deleteRecursively()
        realFiles.forEach { (name, subtitleFile) ->
            addFile(subtitleFile, nameGenerator(name, subtitleFile))
        }
    }
}

interface AbstractSubApi : AbstractSubProvider, AuthAPI