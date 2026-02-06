package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import android.util.Log
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
import com.lagradost.cloudstream3.R
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
import kotlinx.coroutines.delay

class HistoryScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var itemList: ItemList? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        loadHistory()
    }

    private fun loadHistory(retryCount: Int = 0) {
        scope.launch {
            withContext(Dispatchers.Main) {
                itemList = null
                invalidate()
            }
            try {
                // Strict logic copied from HomeViewModel.getResumeWatching()
                val resumeWatchingResult = withContext(Dispatchers.IO) {
                    val ids = getAllResumeStateIds()
                    Log.d("HistoryDebug", "Loading history. IDs found: ${ids?.size ?: 0}")
                    ids?.mapNotNull { id ->
                        getLastWatched(id)
                    }?.sortedBy { -it.updateTime }?.mapNotNull { resume ->
                       val data = getKey<VideoDownloadHelper.DownloadHeaderCached>(
                            DOWNLOAD_HEADER_CACHE,
                            resume.parentId.toString()
                        )
                        if (data == null) {
                             Log.e("HistoryDebug", "MISSING HEADER for parentId: ${resume.parentId}")
                             return@mapNotNull null
                        }
                        Log.d("HistoryDebug", "Found HEADER for parentId: ${resume.parentId} -> ${data.name}")
    
                       Pair(resume, data) 
                    }
                }
    
                val builder = ItemList.Builder()
                
                if (resumeWatchingResult.isNullOrEmpty()) {
                    builder.setNoItemsMessage(CarStrings.get(R.string.car_no_continue_watching))
                } else {
                     resumeWatchingResult.forEach { (resume, cachedData) ->
                         val title = cachedData.name
                     val subtitle = if (resume.episode != null && resume.season != null) {
                        "S${resume.season}E${resume.episode} (${cachedData.apiName})"
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
            } catch (e: Exception) {
                if (retryCount < 3) {
                    delay(3000)
                    loadHistory(retryCount + 1)
                } else {
                    withContext(Dispatchers.Main) {
                        itemList = ItemList.Builder()
                            .addItem(
                                Row.Builder()
                                    .setTitle("Errore: ${e.message}")
                                    .setOnClickListener { loadHistory() }
                                    .build()
                            )
                            .build()
                        invalidate()
                    }
                }
            }
        }
    }
    
    private fun playResumeItem(resume: VideoDownloadHelper.ResumeWatching, cachedData: VideoDownloadHelper.DownloadHeaderCached) {
        scope.launch {
            withContext(Dispatchers.Main) {
                androidx.car.app.CarToast.makeText(carContext, CarStrings.get(R.string.car_resuming, cachedData.name), androidx.car.app.CarToast.LENGTH_LONG).show()
            }
            
            val api = getApiFromNameNull(cachedData.apiName) ?: return@launch
            val repo = APIRepository(api)
            
            val loadResult = when(val result = repo.load(cachedData.url)) {
                 is Resource.Success -> result.value
                 else -> null
            } ?: return@launch

            if (loadResult is TvSeriesLoadResponse) {
                // Find the specific episode to resume
                // resume.episodeId should match episode.data.hashCode() used by PlayerCarScreen
                val episodeToResume = loadResult.episodes.find { episode ->
                    // Promiscuous check: try multiple ways to match the episode
                    // Note: Episode class does not have an 'id' field, so we skip direct ID check
                    val urlHashMatch = episode.data.hashCode() == resume.episodeId
                    val numberMatch = episode.episode == resume.episode && episode.season == resume.season
                    
                    urlHashMatch || numberMatch
                }
                
                if (episodeToResume != null) {
                    val startTime = getViewPos(resume.episodeId)?.position ?: 0L
                    val seasonEpisodes = loadResult.episodes.filter { it.season == episodeToResume.season }
                    
                    withContext(Dispatchers.Main) {
                        screenManager.push(
                            PlayerCarScreen(
                                carContext = carContext,
                                loadResponse = loadResult,
                                selectedEpisode = episodeToResume,
                                playlist = seasonEpisodes,
                                startTime = startTime
                            )
                        )
                    }
                } else {
                    // Fallback to episode list if episode not found
                    withContext(Dispatchers.Main) {
                        screenManager.push(EpisodeListScreen(carContext, loadResult, isExpressMode = true))
                    }
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
                
                withContext(Dispatchers.Main) {
                     screenManager.push(
                         PlayerCarScreen(
                             carContext = carContext,
                             item = item,
                             loadResponse = loadResult,
                             selectedEpisode = null,
                             startTime = startTime
                         )
                     )
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val list = itemList ?: ItemList.Builder().setNoItemsMessage(CarStrings.get(R.string.car_loading)).build()
        
        return ListTemplate.Builder()
            .setTitle(CarStrings.get(R.string.car_history))
            .setHeaderAction(Action.BACK)
            .setSingleList(list)
            .build()
    }
}
