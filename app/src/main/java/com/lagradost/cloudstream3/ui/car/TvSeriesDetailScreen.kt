package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.SingletonImageLoader
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.FavoritesData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.text.SpannableString
import android.text.Spanned

class TvSeriesDetailScreen(
    carContext: CarContext,
    private val item: SearchResponse
) : Screen(carContext) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var fullDetails: TvSeriesLoadResponse? = null
    private var isLoading = true
    private var errorMessage: String? = null
    private var posterBitmap: android.graphics.Bitmap? = null
    private var logoBitmap: android.graphics.Bitmap? = null
    private var isFavorite: Boolean = false

    init {
        loadDetails()
    }

    private fun loadDetails() {
        scope.launch {


            // Load full details
            val api = getApiFromNameNull(item.apiName)
            if (api != null) {
                val repo = APIRepository(api)
                when (val result = repo.load(item.url)) {
                    is Resource.Success -> {
                        val data = result.value
                        if (data is TvSeriesLoadResponse) {
                            fullDetails = data
                            // Check Favorite status
                            val id = data.url.replace(api.mainUrl, "").replace("/", "").hashCode()
                            isFavorite = DataStoreHelper.getFavoritesData(id) != null

                            // Load Main Image (Background/Landscape preferred, else Poster)
                            val bgUrl = data.backgroundPosterUrl
                            val posterUrl = data.posterUrl ?: item.posterUrl
                            val targetUrl = bgUrl ?: posterUrl

                            if (!targetUrl.isNullOrEmpty()) {
                                try {
                                    val request = ImageRequest.Builder(carContext)
                                        .data(targetUrl)
                                        .size(1200, 800)
                                        .build()
                                    val imgResult = SingletonImageLoader.get(carContext).execute(request)
                                    posterBitmap = imgResult.image?.asDrawable(carContext.resources)?.toBitmap()?.let {
                                        CarHelper.ensureSoftwareBitmap(it)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            if (!data.logoUrl.isNullOrEmpty()) {
                                try {
                                    val request = ImageRequest.Builder(carContext)
                                        .data(data.logoUrl)
                                        .size(600, 200)
                                        .build()
                                    val imgResult = SingletonImageLoader.get(carContext).execute(request)
                                    logoBitmap = imgResult.image?.asDrawable(carContext.resources)?.toBitmap()?.let {
                                        CarHelper.ensureSoftwareBitmap(it)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        } else {
                            errorMessage = CarStrings.get(R.string.car_not_tv_series)
                        }
                        isLoading = false
                    }
                    is Resource.Failure -> {
                        errorMessage = result.errorString ?: CarStrings.get(R.string.car_error_loading_details)
                        isLoading = false
                    }
                    is Resource.Loading -> {}
                }
            } else {
                errorMessage = CarStrings.get(R.string.car_provider_not_found)
                isLoading = false
            }
            invalidate()
        }
    }

    private fun toggleFavorite() {
        CarHelper.toggleFavorite(carContext, fullDetails, isFavorite) { newStatus ->
            isFavorite = newStatus
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val paneBuilder = Pane.Builder()

        if (isLoading) {
            paneBuilder.setLoading(true)
        } else {
            val details = fullDetails
            
            // Include Hero Image if available
            // Overlay Strategy: Gradient at bottom + Logo Bottom-Left
            // Force Square construction even if logo is null, to ensure we control the aspect ratio
            val finalBitmap = if (posterBitmap != null) {
                CarHelper.generateSquareImageWithLogo(posterBitmap!!, logoBitmap)
            } else {
                posterBitmap
            }

            finalBitmap?.let {
                paneBuilder.setImage(CarIcon.Builder(IconCompat.createWithBitmap(it)).build())
            } ?: run {
                 paneBuilder.setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.mipmap.ic_launcher)).build())
            }

            if (details != null) {
                // Meta Row: Year • Score • Duration/Seasons
                val metaStringBuilder = StringBuilder()
                details.year?.let { metaStringBuilder.append("$it") }

                val score = details.score
                if (score != null) {
                    if (metaStringBuilder.isNotEmpty()) metaStringBuilder.append(" • ")
                    metaStringBuilder.append(String.format("%.1f/10", score.toDouble(10)))
                }
                
                // Show season count in meta
                // details.seasonNames could be used, or just aggregating episodes
                val seasonCount = details.episodes.mapNotNull { it.season }.distinct().count()
                if (metaStringBuilder.isNotEmpty()) metaStringBuilder.append(" • ")
                metaStringBuilder.append("$seasonCount ${CarStrings.get(R.string.car_seasons)}")

                if (metaStringBuilder.isNotEmpty()) {
                    paneBuilder.addRow(
                        Row.Builder()
                            .setTitle(metaStringBuilder.toString())
                            .build()
                    )
                }

                // Favorite Action
                val favoriteIconRes = if (isFavorite) R.drawable.ic_baseline_favorite_24 else R.drawable.ic_baseline_favorite_border_24
                val favoriteIcon = IconCompat.createWithResource(carContext, favoriteIconRes)
                if (isFavorite) {
                    favoriteIcon.setTint(0xFFFF0000.toInt()) // Red
                } else {
                    favoriteIcon.setTint(android.graphics.Color.WHITE) // White
                }

                val favoriteAction = Action.Builder()
                    .setIcon(CarIcon.Builder(favoriteIcon).build())
                    .setOnClickListener {
                        toggleFavorite()
                    }
                    .build()

                // Episodes Button Action
                // "Episode List >"
                paneBuilder.addAction(
                    Action.Builder()
                        .setTitle(CarStrings.get(R.string.car_episode_list))
                        .setBackgroundColor(CarColor.PRIMARY)
                        .setOnClickListener {
                            screenManager.push(EpisodeListScreen(carContext, details))
                        }
                        .build()
                )

                paneBuilder.addAction(favoriteAction)
                
                // Plot and Cast
                CarHelper.addPlotAndCast(paneBuilder, details)

            } else if (errorMessage != null) {
                 paneBuilder.addRow(Row.Builder().setTitle("${CarStrings.get(R.string.car_error)}: $errorMessage").build())
            }
        }

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle(item.name)
            .setHeaderAction(Action.BACK)
            .build()
    }
}
