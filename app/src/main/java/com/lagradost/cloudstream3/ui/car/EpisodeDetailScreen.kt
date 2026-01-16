package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.SingletonImageLoader
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class EpisodeDetailScreen(
    carContext: CarContext,
    private val seriesDetails: TvSeriesLoadResponse,
    private val episode: Episode,
    private val playlist: List<Episode>
) : Screen(carContext) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var posterBitmap: android.graphics.Bitmap? = null
    private var isLoadingImage = true
    
    // Selected source for playback
    private var selectedSource: ExtractorLink? = null

    init {
        loadImage()
    }

    private fun loadImage() {
        scope.launch {
            // Try episode poster first, then series poster
            val url = episode.posterUrl ?: seriesDetails.posterUrl
            if (!url.isNullOrEmpty()) {
                try {
                    val request = ImageRequest.Builder(carContext)
                        .data(url)
                        .size(600, 900)
                        .build()
                    val result = SingletonImageLoader.get(carContext).execute(request)
                    posterBitmap = result.image?.asDrawable(carContext.resources)?.toBitmap()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            isLoadingImage = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val paneBuilder = Pane.Builder()

        // Title Row
        val title = "${episode.episode}. ${episode.name ?: "${CarStrings.get(R.string.car_episode)} ${episode.episode}"}"
        paneBuilder.addRow(
            Row.Builder()
                .setTitle(title)
                .addText("${CarStrings.get(R.string.car_season)} ${episode.season ?: "?"}")
                .build()
        )

        // Image
        posterBitmap?.let {
            paneBuilder.setImage(CarIcon.Builder(IconCompat.createWithBitmap(it)).build())
        } ?: run {
             // Fallback icon if no image loaded yet or error
             paneBuilder.setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.mipmap.ic_launcher)).build())
        }

        // Description Row
        if (!episode.description.isNullOrEmpty()) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle(CarStrings.get(R.string.car_plot))
                    .addText(episode.description!!)
                    .build()
            )
        }

        // Play Button action
        val playIcon = IconCompat.createWithResource(carContext, android.R.drawable.ic_media_play)
            .setTint(android.graphics.Color.BLACK)

        val playAction = Action.Builder()
            .setIcon(CarIcon.Builder(playIcon).build())
            .setBackgroundColor(CarColor.createCustom(android.graphics.Color.WHITE, android.graphics.Color.WHITE))
            .setOnClickListener {
                // Get saved position for resume
                val startTime = getViewPos(episode.data.hashCode())?.position ?: 0L
                screenManager.push(
                    PlayerCarScreen(
                        carContext = carContext,
                        loadResponse = seriesDetails,
                        selectedEpisode = episode,
                        playlist = playlist,
                        startTime = startTime,
                        preSelectedSource = selectedSource
                    )
                )
            }
            .build()
        
        // Source Selection Button
        val sourceIcon = IconCompat.createWithResource(carContext, R.drawable.ic_baseline_source_24)
        
        val sourceActionTitle = if (selectedSource != null) {
            selectedSource!!.name
        } else {
            CarStrings.get(R.string.car_source)
        }
        
        val sourceAction = Action.Builder()
            .setIcon(CarIcon.Builder(sourceIcon).build())
            .setTitle(sourceActionTitle)
            .setOnClickListener {
                screenManager.push(
                    SourceSelectionScreen(
                        carContext = carContext,
                        apiName = seriesDetails.apiName,
                        dataUrl = episode.data,
                        currentSourceUrl = selectedSource?.url,
                        onSourceSelected = { source ->
                            selectedSource = source
                            invalidate()
                        }
                    )
                )
            }
            .build()
        
        paneBuilder.addAction(playAction)
        paneBuilder.addAction(sourceAction)

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle(seriesDetails.name) // Header Title is Series Name
            .setHeaderAction(Action.BACK)
            .build()
    }
}
