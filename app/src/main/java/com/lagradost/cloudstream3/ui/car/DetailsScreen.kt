package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.ui.player.DownloadedPlayerActivity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.SingletonImageLoader
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.ui.result.ResultViewModel2
import com.lagradost.cloudstream3.utils.DataStoreHelper.FavoritesData
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.MovieLoadResponse

class DetailsScreen(
    carContext: CarContext,
    private val item: SearchResponse
) : Screen(carContext) {

    private var fullDetails: LoadResponse? = null
    private var isLoading = true
    private var errorMessage: String? = null

    private var posterBitmap: android.graphics.Bitmap? = null
    private var logoBitmap: android.graphics.Bitmap? = null
    private var isFavorite: Boolean = false
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    
    // Selected source for playback
    private var selectedSource: ExtractorLink? = null

    init {
        loadData()
    }

    private fun loadData() {
        scope.launch {
            try {


                // Load Details
                val api = getApiFromNameNull(item.apiName)
                if (api != null) {
                    val repo = APIRepository(api)
                    when (val result = repo.load(item.url)) {
                        is Resource.Success -> {
                            fullDetails = result.value
                            
                            // Redirect if Type Mismatch (e.g. Provider reported Movie, but it's a Series)
                            // We check if the response is structurally a Series (has episodes etc)
                            // This fixes providers that return "Movie" type but provide series data.
                            if (result.value is TvSeriesLoadResponse) {
                                val detectedType = result.value.type
                                // If the provider says "Movie" but sends TvSeriesLoadResponse, force TvSeries type
                                val isTv = detectedType == TvType.TvSeries || 
                                           detectedType == TvType.Anime || 
                                           detectedType == TvType.Cartoon || 
                                           detectedType == TvType.OVA || 
                                           detectedType == TvType.AsianDrama || 
                                           detectedType == TvType.Documentary
                                           
                                val finalType = if (isTv) detectedType else TvType.TvSeries
                                
                                val correctItem = api.newTvSeriesSearchResponse(
                                    name = result.value.name,
                                    url = result.value.url,
                                    type = finalType,
                                ) {
                                    this.posterUrl = result.value.posterUrl ?: item.posterUrl
                                }
                                withContext(Dispatchers.Main) {
                                    screenManager.pop()
                                    screenManager.push(TvSeriesDetailScreen(carContext, correctItem))
                                }
                                return@launch
                            }

                            // Check ID logic matches standard
                            val id = result.value.url.replace(api.mainUrl, "").replace("/", "").hashCode()
                            isFavorite = DataStoreHelper.getFavoritesData(id) != null

                            // Load Main Image (Background/Landscape preferred, else Poster)
                            val bgUrl = result.value.backgroundPosterUrl
                            val posterUrl = result.value.posterUrl ?: item.posterUrl
                            val targetUrl = bgUrl ?: posterUrl

                            if (!targetUrl.isNullOrEmpty()) {
                                try {
                                    val request = ImageRequest.Builder(carContext)
                                        .data(targetUrl)
                                        .size(1200, 800) // Landscape optimized size if possible, or high res
                                        .build()
                                    val imgResult = SingletonImageLoader.get(carContext).execute(request)
                                    posterBitmap = imgResult.image?.asDrawable(carContext.resources)?.toBitmap()?.let {
                                        CarHelper.ensureSoftwareBitmap(it)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            if (!result.value.logoUrl.isNullOrEmpty()) {
                                try {
                                    val request = ImageRequest.Builder(carContext)
                                        .data(result.value.logoUrl)
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
                            isLoading = false
                        }
                        is Resource.Failure -> {
                            errorMessage = result.errorString
                            isLoading = false
                        }
                        is Resource.Loading -> {}
                    }
                } else {
                    errorMessage = CarStrings.get(R.string.car_provider_not_found)
                    isLoading = false
                }
            } catch (e: Exception) {
                errorMessage = e.message
                isLoading = false
            }
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val paneBuilder = Pane.Builder()

        if (isLoading) {
            paneBuilder.setLoading(true)
        } else {
            buildContent(paneBuilder)
        }
        
        buildActions(paneBuilder)

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle(fullDetails?.name ?: item.name)
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun buildContent(paneBuilder: Pane.Builder) {
        val details = fullDetails
        
        // Set Hero Image on the Pane itself for maximum size
        // Set Hero Image on the Pane itself for maximum size
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
            // Meta Row: Year • Rating • Duration
            val metaStringBuilder = StringBuilder()
            details.year?.let { metaStringBuilder.append("$it") }
            
            // Add Score if available
             val score = details.score
             if (score != null) {
                 if (metaStringBuilder.isNotEmpty()) metaStringBuilder.append(" • ")
                 metaStringBuilder.append(String.format("%.1f/10", score.toDouble(10)))
             } 
             
             details.duration?.let {
                 if (metaStringBuilder.isNotEmpty()) metaStringBuilder.append(" • ")
                 metaStringBuilder.append("${it}m")
             }

            if (metaStringBuilder.isNotEmpty()) {
                paneBuilder.addRow(
                    Row.Builder()
                        .setTitle(metaStringBuilder.toString())
                        .build()
                )
            }

            // Plot and Cast
            CarHelper.addPlotAndCast(paneBuilder, details)
        } else if (errorMessage != null) {
             paneBuilder.addRow(Row.Builder().setTitle("${CarStrings.get(R.string.car_error)}: $errorMessage").build())
        }
    }

    private fun buildActions(paneBuilder: Pane.Builder) {
        // Play Button: White background with Black Icon
        val playIcon = IconCompat.createWithResource(carContext, android.R.drawable.ic_media_play)
            .setTint(android.graphics.Color.BLACK)

        val playAction = Action.Builder()
            .setIcon(CarIcon.Builder(playIcon).build())
            .setBackgroundColor(CarColor.createCustom(android.graphics.Color.WHITE, android.graphics.Color.WHITE)) 
            .setOnClickListener {
                screenManager.push(PlayerCarScreen(
                    carContext = carContext,
                    item = item,
                    preSelectedSource = selectedSource
                )) 
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
                val details = fullDetails
                val dataUrl = when (details) {
                    is MovieLoadResponse -> details.dataUrl
                    else -> details?.url
                }
                if (dataUrl != null && details != null) {
                    screenManager.push(
                        SourceSelectionScreen(
                            carContext = carContext,
                            apiName = details.apiName,
                            dataUrl = dataUrl,
                            currentSourceUrl = selectedSource?.url,
                            onSourceSelected = { source ->
                                selectedSource = source
                                invalidate()
                            }
                        )
                    )
                }
            }
            .build()
            
        paneBuilder.addAction(playAction)
        paneBuilder.addAction(sourceAction)
    }
    
    private fun toggleFavorite() {
        CarHelper.toggleFavorite(carContext, fullDetails, isFavorite) { newStatus ->
            isFavorite = newStatus
            invalidate()
        }
    }
}
