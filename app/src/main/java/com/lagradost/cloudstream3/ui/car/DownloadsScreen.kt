package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Template
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager
import com.lagradost.cloudstream3.R
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

    // Sectioned data for Film vs Serie split (headers view only)
    private var movieHeaders: List<DownloadObjects.DownloadHeaderCached> = emptyList()
    private var seriesHeaders: List<DownloadObjects.DownloadHeaderCached> = emptyList()
    private var isHeadersLoaded = false

    companion object {
        /** SectionedItemTemplate requires Car API level 8 */
        private const val SECTIONED_ITEM_TEMPLATE_MIN_API = 8
    }

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
                 .mapNotNull { context.getKey<DownloadObjects.DownloadEpisodeCached>(it) }
                 .filter { it.parentId == id }
                 .filter { 
                     val info = VideoDownloadManager.getDownloadFileInfo(context, it.id)
                     (info?.fileLength ?: 0L) > 0
                 }
                 .sortedWith(compareBy({ it.season }, { it.episode }))

            val builder = ItemList.Builder()
            
            if (children.isEmpty()) {
                builder.setNoItemsMessage(CarStrings.get(R.string.car_no_episodes_found))
            } else {
                children.forEach { episode ->
                    val name = "S${episode.season}:E${episode.episode} - ${episode.name ?: CarStrings.get(R.string.car_episode)}"
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
                .mapNotNull { carContext.getKey<DownloadObjects.DownloadHeaderCached>(it) }
                .sortedBy { it.name }

            // Filter and find valid items
            val validHeaders = headers.filter { header ->
                val context = carContext
                val id = header.id
                
                // Get all children (episodes or movies) for this header
                val children = context.getKeys(DOWNLOAD_EPISODE_CACHE)
                     .mapNotNull { context.getKey<DownloadObjects.DownloadEpisodeCached>(it) }
                     .filter { it.parentId == id }
                
                // Check if AT LEAST ONE child is valid (> 0 bytes)
                children.any { child ->
                    val info = VideoDownloadManager.getDownloadFileInfo(context, child.id)
                    (info?.fileLength ?: 0L) > 0
                }
            }

            // Split into movies vs series
            movieHeaders = validHeaders.filter { !it.type.isEpisodeBased() }
            seriesHeaders = validHeaders.filter { it.type.isEpisodeBased() }
            isHeadersLoaded = true

            // Also build legacy flat list for fallback
            val builder = ItemList.Builder()
            if (validHeaders.isEmpty()) {
                builder.setNoItemsMessage(CarStrings.get(R.string.car_no_downloads_found))
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
             val fileInfo = VideoDownloadManager.getDownloadFileInfo(context, episodeId)
             if (fileInfo == null) {
                 withContext(Dispatchers.Main) {
                     androidx.car.app.CarToast.makeText(carContext, CarStrings.get(R.string.car_file_not_found), androidx.car.app.CarToast.LENGTH_SHORT).show()
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

    private fun onHeaderClick(header: DownloadObjects.DownloadHeaderCached) {
        scope.launch {
             val context = carContext
             val id = header.id
             
             // Get children
             val children = context.getKeys(DOWNLOAD_EPISODE_CACHE)
                 .mapNotNull { context.getKey<DownloadObjects.DownloadEpisodeCached>(it) }
                 .filter { it.parentId == id }
                 .filter { 
                     val info = VideoDownloadManager.getDownloadFileInfo(context, it.id)
                     (info?.fileLength ?: 0L) > 0
                 }

             if (children.isEmpty()) {
                  withContext(Dispatchers.Main) {
                     androidx.car.app.CarToast.makeText(carContext, CarStrings.get(R.string.car_no_valid_episode), androidx.car.app.CarToast.LENGTH_SHORT).show()
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
        // Episodes sub-screen or legacy fallback: use simple ListTemplate
        if (parentId != null) {
            return ListTemplate.Builder()
                .setHeader(
                    Header.Builder()
                        .setTitle(headerName ?: CarStrings.get(R.string.car_downloads))
                        .setStartHeaderAction(Action.BACK)
                        .build()
                )
                .setSingleList(itemList ?: ItemList.Builder().setNoItemsMessage(CarStrings.get(R.string.car_loading)).build())
                .build()
        }

        // Top-level downloads: use SectionedItemTemplate if supported
        val hostApiLevel = carContext.carAppApiLevel
        if (hostApiLevel >= SECTIONED_ITEM_TEMPLATE_MIN_API && isHeadersLoaded) {
            return buildSectionedTemplate()
        }

        // Fallback to legacy flat ListTemplate
        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(CarStrings.get(R.string.car_downloads))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(itemList ?: ItemList.Builder().setNoItemsMessage(CarStrings.get(R.string.car_loading)).build())
            .build()
    }

    // ──────────────────────────────────────────────────────
    //  SectionedItemTemplate: Film vs Serie split (API >= 8)
    // ──────────────────────────────────────────────────────

    private fun buildSectionedTemplate(): Template {
        // If both lists are empty, show a simple message
        if (movieHeaders.isEmpty() && seriesHeaders.isEmpty()) {
            return ListTemplate.Builder()
                .setHeader(
                    Header.Builder()
                        .setTitle(CarStrings.get(R.string.car_downloads))
                        .setStartHeaderAction(Action.BACK)
                        .build()
                )
                .setSingleList(ItemList.Builder().setNoItemsMessage(CarStrings.get(R.string.car_no_downloads_found)).build())
                .build()
        }

        val builder = androidx.car.app.model.SectionedItemTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(CarStrings.get(R.string.car_downloads))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )

        // Movies section
        if (movieHeaders.isNotEmpty()) {
            builder.addSection(
                androidx.car.app.model.RowSection.Builder()
                    .setTitle(CarStrings.get(R.string.car_movies))
                    .apply {
                        movieHeaders.forEach { header ->
                            addItem(buildHeaderRow(header))
                        }
                    }
                    .build()
            )
        }

        // Series section
        if (seriesHeaders.isNotEmpty()) {
            builder.addSection(
                androidx.car.app.model.RowSection.Builder()
                    .setTitle(CarStrings.get(R.string.car_series))
                    .apply {
                        seriesHeaders.forEach { header ->
                            addItem(buildHeaderRow(header))
                        }
                    }
                    .build()
            )
        }

        return builder.build()
    }

    private fun buildHeaderRow(header: DownloadObjects.DownloadHeaderCached): Row {
        val lastWatched = DataStoreHelper.getLastWatched(header.id)
        val subtitle = if (lastWatched != null && lastWatched.season != null && lastWatched.episode != null) {
            "S${lastWatched.season}E${lastWatched.episode}"
        } else {
            null
        }

        val rowBuilder = Row.Builder()
            .setTitle(header.name)
            .setOnClickListener { onHeaderClick(header) }
            .setBrowsable(true)

        if (subtitle != null) {
            rowBuilder.addText(subtitle)
        }

        return rowBuilder.build()
    }
}
