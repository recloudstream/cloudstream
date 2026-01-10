package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllResumeStateIds
import com.lagradost.cloudstream3.utils.DataStoreHelper.getLastWatched
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.isMovieType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var itemList: ItemList? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        loadHistory()
    }

    private fun loadHistory() {
        scope.launch {
            // Strict logic copied from HomeViewModel.getResumeWatching()
            val resumeWatchingResult = withContext(Dispatchers.IO) {
                getAllResumeStateIds()?.mapNotNull { id ->
                    getLastWatched(id)
                }?.sortedBy { -it.updateTime }?.mapNotNull { resume ->
                   val data = getKey<VideoDownloadHelper.DownloadHeaderCached>(
                        DOWNLOAD_HEADER_CACHE,
                        resume.parentId.toString()
                    ) ?: return@mapNotNull null

                   // Mapping to a local helper or using the result directly
                   // We need the resume object for the episodeId/parentId/pos
                   Pair(resume, data) 
                }
            }

            val builder = ItemList.Builder()
            
            if (resumeWatchingResult.isNullOrEmpty()) {
                builder.setNoItemsMessage("Nessun elemento 'Continua a guardare' trovato")
            } else {
                 resumeWatchingResult.forEach { (resume, cachedData) ->
                     val title = cachedData.name
                     val subtitle = if (resume.episode != null && resume.season != null) {
                        "S${resume.season}:E${resume.episode}" 
                     } else {
                        cachedData.apiName
                     }

                     builder.addItem(
                         Row.Builder()
                             .setTitle(title)
                             .addText(subtitle)
                             .setOnClickListener { 
                                 playResumeItem(resume, cachedData)
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
    
    private fun playResumeItem(resume: VideoDownloadHelper.ResumeWatching, cachedData: VideoDownloadHelper.DownloadHeaderCached) {
        scope.launch {
            withContext(Dispatchers.Main) {
                androidx.car.app.CarToast.makeText(carContext, "Riprendo ${cachedData.name}...", androidx.car.app.CarToast.LENGTH_LONG).show()
            }
            
            val api = getApiFromNameNull(cachedData.apiName) ?: return@launch
            val repo = APIRepository(api)
            
            val loadResult = when(val result = repo.load(cachedData.url)) {
                 is Resource.Success -> result.value
                 else -> null
            } ?: return@launch

            if (loadResult is TvSeriesLoadResponse) {
                withContext(Dispatchers.Main) {
                     screenManager.push(EpisodeListScreen(carContext, loadResult, isExpressMode = true))
                }
            } else {
                val startTime = getViewPos(resume.episodeId)?.position ?: 0L
                
                // Prepare SearchResponse item for PlayerCarScreen
                val item: SearchResponse = if (cachedData.type.isMovieType()) {
                     api.newMovieSearchResponse(
                        name = cachedData.name,
                        url = cachedData.url,
                        type = cachedData.type,
                    ) {
                        this.posterUrl = cachedData.poster
                    }
                } else {
                     api.newTvSeriesSearchResponse(
                        name = cachedData.name,
                        url = cachedData.url,
                        type = cachedData.type,
                    ) {
                        this.posterUrl = cachedData.poster
                    }
                }

                // Resolve Episode for TV Series (Not needed if we skip player for series here, but kept for non-TvSeriesLoadResponse logic if any?)
                // Actually, if it's NOT TvSeriesLoadResponse, selectedEpisode is likely null or irrelevant for Movie
                
                withContext(Dispatchers.Main) {
                     screenManager.push(
                         PlayerCarScreen(
                             carContext = carContext,
                             item = item,
                             loadResponse = loadResult,
                             selectedEpisode = null, // movies don't have episodes usually or logic differs
                             startTime = startTime
                         )
                     )
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val list = itemList ?: ItemList.Builder().setNoItemsMessage("Caricamento cronologia...").build()
        
        return ListTemplate.Builder()
            .setTitle("Cronologia")
            .setHeaderAction(Action.BACK)
            .setSingleList(list)
            .build()
    }
}
