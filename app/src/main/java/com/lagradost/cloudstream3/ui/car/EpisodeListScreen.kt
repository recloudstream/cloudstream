package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.model.SectionedItemTemplate
import androidx.car.app.model.RowSection
import androidx.car.app.model.ChipSection
import androidx.car.app.model.Chip
import androidx.car.app.model.ChipStyle
import androidx.car.app.model.Shape
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.getId
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.getVideoWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.FillerEpisodeCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class EpisodeListScreen(
    carContext: CarContext,
    private val details: TvSeriesLoadResponse,
    private val isExpressMode: Boolean = false
) : Screen(carContext) {

    private val availableSeasons: List<Int> = details.episodes.mapNotNull { it.season }.distinct().sorted()
    private var currentSeasonIndex: Int = 0 // Index in availableSeasons list

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var fillerEpisodes: HashSet<Int>? = null
    private var isFillerLoaded = false

    init {
        scope.launch {
            try {
                fillerEpisodes = FillerEpisodeCheck.getFillerEpisodes(details)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isFillerLoaded = true
            invalidate()
        }
    }

    @androidx.annotation.OptIn(ExperimentalCarApi::class)
    override fun onGetTemplate(): Template {
        if (availableSeasons.isEmpty()) {
             return ListTemplate.Builder()
                .setHeader(
                    Header.Builder()
                        .setTitle(details.name)
                        .setStartHeaderAction(Action.BACK)
                        .build()
                )
                .setSingleList(ItemList.Builder().addItem(Row.Builder().setTitle(CarStrings.get(R.string.car_no_episodes_found)).build()).build())
                .build()
        }

        val currentSeason = availableSeasons[currentSeasonIndex]
        val seasonEpisodes = details.episodes.filter { it.season == currentSeason }.sortedBy { it.episode }

        val mainId = details.getId()
        val allEpisodesSorted = details.episodes.sortedBy { (it.season?.times(10_000) ?: 0) + (it.episode ?: 0) }

        val hostApiLevel = carContext.carAppApiLevel
        if (hostApiLevel >= 9) {
            @OptIn(ExperimentalCarApi::class)
            val templateBuilder = SectionedItemTemplate.Builder()
                .setHeader(
                    Header.Builder()
                        .setTitle(details.name)
                        .setStartHeaderAction(Action.BACK)
                        .build()
                )

            if (availableSeasons.size > 1) {
                val chipSectionBuilder = ChipSection.Builder()
                availableSeasons.forEachIndexed { index, season ->
                    val isSelected = index == currentSeasonIndex
                    chipSectionBuilder.addItem(
                        Chip.Builder()
                            .setTitle("S$season")
                            .setSelected(isSelected)
                            .setStyle(ChipStyle.Builder().setShape(Shape.CORNER_MEDIUM).build())
                            .setOnClickListener {
                                currentSeasonIndex = index
                                invalidate()
                            }
                            .build()
                    )
                }
                templateBuilder.addSection(chipSectionBuilder.build())
            }

            val episodesSectionBuilder = RowSection.Builder()
                .setTitle("${CarStrings.get(R.string.car_season)} $currentSeason")

            seasonEpisodes.forEach { episode ->
                val globalIndex = allEpisodesSorted.indexOf(episode)
                val episodeIndex = episode.episode ?: (globalIndex + 1)
                val id = mainId + (episode.season?.times(100_000) ?: 0) + episodeIndex + 1
                val watchState = getVideoWatchState(id)
                val isWatched = watchState == VideoWatchState.Watched

                val titleBase = "${episode.episode}. ${episode.name ?: "${CarStrings.get(R.string.car_episode)} ${episode.episode}"}"
                var title = if (isWatched) "✅ $titleBase" else titleBase
                if (fillerEpisodes?.contains(episode.episode ?: (globalIndex + 1)) == true) {
                    title = "[Filler] $title"
                }

                val rowBuilder = Row.Builder()
                    .setTitle(title)
                    .setOnClickListener {
                         if (isExpressMode) {
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
                     val desc = if (it.length > 100) it.substring(0, 97) + "..." else it
                     rowBuilder.addText(desc)
                }

                episodesSectionBuilder.addItem(rowBuilder.build())
            }

            templateBuilder.addSection(episodesSectionBuilder.build())
            return templateBuilder.build()
        } else {
            val listBuilder = ItemList.Builder()
            
            seasonEpisodes.forEach { episode ->
                val globalIndex = allEpisodesSorted.indexOf(episode)
                val episodeIndex = episode.episode ?: (globalIndex + 1)
                val id = mainId + (episode.season?.times(100_000) ?: 0) + episodeIndex + 1
                val watchState = getVideoWatchState(id)
                val isWatched = watchState == VideoWatchState.Watched

                val titleBase = "${episode.episode}. ${episode.name ?: "${CarStrings.get(R.string.car_episode)} ${episode.episode}"}"
                var title = if (isWatched) "✅ $titleBase" else titleBase
                if (fillerEpisodes?.contains(episode.episode ?: (globalIndex + 1)) == true) {
                    title = "[Filler] $title"
                }

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
                .setHeader(
                    Header.Builder()
                        .setTitle("${details.name} - ${CarStrings.get(R.string.car_season)} $currentSeason")
                        .setStartHeaderAction(Action.BACK)
                        .addEndHeaderAction(seasonAction)
                        .build()
                )
                .setSingleList(listBuilder.build())
                .build()
        }
    }
}
