package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.getId
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.getVideoWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos

class EpisodeListScreen(
    carContext: CarContext,
    private val details: TvSeriesLoadResponse,
    private val isExpressMode: Boolean = false
) : Screen(carContext) {

    private val availableSeasons: List<Int> = details.episodes.mapNotNull { it.season }.distinct().sorted()
    private var currentSeasonIndex: Int = 0 // Index in availableSeasons list

    override fun onGetTemplate(): Template {
        if (availableSeasons.isEmpty()) {
             return ListTemplate.Builder()
                .setTitle(details.name)
                .setHeaderAction(Action.BACK)
                .setSingleList(ItemList.Builder().addItem(Row.Builder().setTitle(CarStrings.get(R.string.car_no_episodes_found)).build()).build())
                .build()
        }

        val currentSeason = availableSeasons[currentSeasonIndex]
        val seasonEpisodes = details.episodes.filter { it.season == currentSeason }.sortedBy { it.episode }

        val mainId = details.getId()
        val allEpisodesSorted = details.episodes.sortedBy { (it.season?.times(10_000) ?: 0) + (it.episode ?: 0) }

        val listBuilder = ItemList.Builder()
        
        seasonEpisodes.forEach { episode ->
            val globalIndex = allEpisodesSorted.indexOf(episode)
            val episodeIndex = episode.episode ?: (globalIndex + 1)
            val id = mainId + (episode.season?.times(100_000) ?: 0) + episodeIndex + 1
            val watchState = getVideoWatchState(id)
            val isWatched = watchState == VideoWatchState.Watched

            val titleBase = "${episode.episode}. ${episode.name ?: "${CarStrings.get(R.string.car_episode)} ${episode.episode}"}"
            val title = if (isWatched) "✅ $titleBase" else titleBase

            val rowBuilder = Row.Builder()
                .setTitle(title)
                .setOnClickListener {
                     if (isExpressMode) {
                         // Get saved position for resume
                         val startTime = getViewPos(episode.data.hashCode())?.position ?: 0L
                         screenManager.push(
                            PlayerCarScreen(
                                carContext = carContext,
                                loadResponse = details,
                                selectedEpisode = episode,
                                playlist = seasonEpisodes,
                                startTime = startTime
                            )
                        )
                     } else {
                         screenManager.push(
                            EpisodeDetailScreen(
                                carContext = carContext,
                                seriesDetails = details,
                                episode = episode,
                                playlist = seasonEpisodes
                            )
                        )
                     }
                }
            
            episode.description?.let { 
                 // Truncate to avoid huge texts, though AA handles some wrapping
                 val desc = if (it.length > 100) it.substring(0, 97) + "..." else it
                 rowBuilder.addText(desc)
            }
            
            listBuilder.addItem(rowBuilder.build())
        }

        val seasonAction = Action.Builder()
            .setTitle("${CarStrings.get(R.string.car_season)} $currentSeason")
            .setOnClickListener {
                // Cycle through seasons
                currentSeasonIndex = (currentSeasonIndex + 1) % availableSeasons.size
                invalidate()
            }
            .build()

        return ListTemplate.Builder()
            .setTitle("${details.name} - ${CarStrings.get(R.string.car_season)} $currentSeason")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(seasonAction)
                    .build()
            )
            .build()
    }
}
