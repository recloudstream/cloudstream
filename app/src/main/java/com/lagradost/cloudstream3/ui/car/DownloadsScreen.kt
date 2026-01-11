package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DataStore.getFolderName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lagradost.cloudstream3.isEpisodeBased

class DownloadsScreen(carContext: CarContext) : Screen(carContext) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var itemList: ItemList? = null

    init {
        loadDownloads()
    }

     private fun loadDownloads() {
        scope.launch {
            val headers = carContext.getKeys(DOWNLOAD_HEADER_CACHE)
                .mapNotNull { carContext.getKey<VideoDownloadHelper.DownloadHeaderCached>(it) }
                .sortedBy { it.name }

            val builder = ItemList.Builder()
            
            // Filter and find valid items
            val validHeaders = headers.filter { header ->
                val context = carContext
                val id = header.id
                
                // Get all children (episodes or movies) for this header
                val children = context.getKeys(DOWNLOAD_EPISODE_CACHE)
                     .mapNotNull { context.getKey<VideoDownloadHelper.DownloadEpisodeCached>(it) }
                     .filter { it.parentId == id }
                
                // Check if AT LEAST ONE child is valid (> 0 bytes)
                children.any { child ->
                    val info = VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(context, child.id)
                    (info?.fileLength ?: 0L) > 0
                }
            }

            if (validHeaders.isEmpty()) {
                builder.setNoItemsMessage("Nessun download completato trovato")
            } else {
                validHeaders.forEach { header ->
                   builder.addItem(
                       Row.Builder()
                           .setTitle(header.name)
                           .setOnClickListener {
                               playDownload(header)
                           }
                           .build()
                   )
                }
            }

            val builtList = builder.build()
            withContext(Dispatchers.Main) {
                itemList = builtList
                invalidate()
            }
        }
    }

    private fun playDownload(header: VideoDownloadHelper.DownloadHeaderCached) {
        scope.launch {
             val context = carContext
             val id = header.id
             
             // Logic to find THE BEST item to play (Resume or First)
             
             val children = context.getKeys(DOWNLOAD_EPISODE_CACHE)
                 .mapNotNull { context.getKey<VideoDownloadHelper.DownloadEpisodeCached>(it) }
                 .filter { it.parentId == id }
                 // Only consider valid downloads
                 .filter { 
                     val info = VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(context, it.id)
                     (info?.fileLength ?: 0L) > 0
                 }

             if (children.isEmpty()) {
                  withContext(Dispatchers.Main) {
                     androidx.car.app.CarToast.makeText(carContext, "Nessun episodio valido trovato", androidx.car.app.CarToast.LENGTH_SHORT).show()
                  }
                  return@launch
             }

             var episodeIdToPlay: Int? = null
             
             if (header.type.isEpisodeBased()) {
                 // Series: Check Resume
                 val lastWatched = DataStoreHelper.getLastWatched(id)
                 val lastEpisodeId = lastWatched?.episodeId
                 
                 // If last watched episode exists and is valid (downloaded), pick it
                 if (lastEpisodeId != null && children.any { it.id == lastEpisodeId }) {
                     episodeIdToPlay = lastEpisodeId
                 } else {
                     // Otherwise pick first sorted by season/episode
                     episodeIdToPlay = children.sortedWith(compareBy({ it.season }, { it.episode })).firstOrNull()?.id
                 }
             } else {
                 // Movie: Just pick the first (and likely only) one
                 episodeIdToPlay = children.firstOrNull()?.id
             }

             if (episodeIdToPlay == null) return@launch

             // Get file info
             val fileInfo = VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(context, episodeIdToPlay)
             if (fileInfo?.path == null) {
                 withContext(Dispatchers.Main) {
                     androidx.car.app.CarToast.makeText(carContext, "File non trovato", androidx.car.app.CarToast.LENGTH_SHORT).show()
                 }
                 return@launch
             }

             withContext(Dispatchers.Main) {
                 screenManager.push(
                     PlayerCarScreen(
                         carContext = carContext,
                         fileUri = fileInfo.path.toString(),
                         videoId = episodeIdToPlay,
                         parentId = header.id
                     )
                 )
             }
        }
    }

    override fun onGetTemplate(): Template {
        return ListTemplate.Builder()
            .setTitle("Download")
            .setHeaderAction(Action.BACK)
            .setSingleList(itemList ?: ItemList.Builder().setNoItemsMessage("Caricamento...").build())
            .build()
    }
}
