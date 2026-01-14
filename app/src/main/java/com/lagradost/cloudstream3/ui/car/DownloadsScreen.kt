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

class DownloadsScreen(
    carContext: CarContext,
    private val parentId: Int? = null,
    private val headerName: String? = null
) : Screen(carContext) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var itemList: ItemList? = null

    init {
        loadContent()
    }

     private fun loadContent() {
        if (parentId != null) {
            loadEpisodes(parentId)
        } else {
            loadHeaders()
        }
    }

    private fun loadEpisodes(id: Int) {
        scope.launch {
            val context = carContext
            val children = context.getKeys(DOWNLOAD_EPISODE_CACHE)
                 .mapNotNull { context.getKey<VideoDownloadHelper.DownloadEpisodeCached>(it) }
                 .filter { it.parentId == id }
                 .filter { 
                     val info = VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(context, it.id)
                     (info?.fileLength ?: 0L) > 0
                 }
                 .sortedWith(compareBy({ it.season }, { it.episode }))

            val builder = ItemList.Builder()
            
            if (children.isEmpty()) {
                builder.setNoItemsMessage("Nessun episodio trovato")
            } else {
                children.forEach { episode ->
                    val name = "S${episode.season}:E${episode.episode} - ${episode.name ?: "Episodio"}"
                    builder.addItem(
                        Row.Builder()
                            .setTitle(name)
                            .setOnClickListener {
                                playEpisode(episode.id, id)
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

    private fun loadHeaders() {
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
                   val lastWatched = DataStoreHelper.getLastWatched(header.id)
                   val subtitle = if (lastWatched != null && lastWatched.season != null && lastWatched.episode != null) {
                       "S${lastWatched.season}E${lastWatched.episode}"
                   } else {
                       null
                   }

                   val rowBuilder = Row.Builder()
                       .setTitle(header.name)
                       .setOnClickListener {
                           onHeaderClick(header)
                       }
                   
                   if (subtitle != null) {
                       rowBuilder.addText(subtitle)
                   }
                   
                   builder.addItem(rowBuilder.build())
                }
            }

            val builtList = builder.build()
            withContext(Dispatchers.Main) {
                itemList = builtList
                invalidate()
            }
        }
    }

    private fun playEpisode(episodeId: Int, parentId: Int) {
         scope.launch {
             val context = carContext
             val fileInfo = VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(context, episodeId)
             if (fileInfo?.path == null) {
                 withContext(Dispatchers.Main) {
                     androidx.car.app.CarToast.makeText(carContext, "File non trovato", androidx.car.app.CarToast.LENGTH_SHORT).show()
                 }
                 return@launch
             }

             // Get saved resume position
             val savedPos = DataStoreHelper.getViewPos(episodeId)
             val startTime = savedPos?.position ?: 0L

             withContext(Dispatchers.Main) {
                 screenManager.push(
                     PlayerCarScreen(
                         carContext = carContext,
                         fileUri = fileInfo.path.toString(),
                         videoId = episodeId,
                         parentId = parentId,
                         startTime = startTime
                     )
                 )
             }
         }
    }

    private fun onHeaderClick(header: VideoDownloadHelper.DownloadHeaderCached) {
        scope.launch {
             val context = carContext
             val id = header.id
             
             // Get children
             val children = context.getKeys(DOWNLOAD_EPISODE_CACHE)
                 .mapNotNull { context.getKey<VideoDownloadHelper.DownloadEpisodeCached>(it) }
                 .filter { it.parentId == id }
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

             if (header.type.isEpisodeBased()) {
                 // Series: Always go to episode list
                 withContext(Dispatchers.Main) {
                     screenManager.push(DownloadsScreen(carContext, id, header.name))
                 }
             } else {
                 // Movie: Just pick the first/only one
                 val episodeId = children.firstOrNull()?.id
                 if (episodeId != null) {
                    playEpisode(episodeId, id)
                 }
             }
        }
    }

    override fun onGetTemplate(): Template {
        return ListTemplate.Builder()
            .setTitle(headerName ?: "Download")
            .setHeaderAction(Action.BACK)
            .setSingleList(itemList ?: ItemList.Builder().setNoItemsMessage(if (headerName == null) "Caricamento download..." else "Caricamento episodi...").build())
            .build()
    }
}
