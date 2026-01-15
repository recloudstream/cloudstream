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
import com.lagradost.cloudstream3.R
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
                // Load Image
                if (!item.posterUrl.isNullOrEmpty()) {
                    try {
                        val request = ImageRequest.Builder(carContext)
                            .data(item.posterUrl)
                            .data(item.posterUrl)
                            .size(600, 900) // Higher resolution for hero image
                            .build()
                        val result = SingletonImageLoader.get(carContext).execute(request)
                        posterBitmap = result.image?.asDrawable(carContext.resources)?.toBitmap()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

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
                            isLoading = false
                        }
                        is Resource.Failure -> {
                            errorMessage = result.errorString
                            isLoading = false
                        }
                        is Resource.Loading -> {}
                    }
                } else {
                    errorMessage = "Provider non trovato"
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
            val details = fullDetails
            
            // Header: Title
// ... (Skipping context lines that are not changing to avoid huge chunks, focusing on modification)
// Wait, I need to match TARGET content exactly. I will split this into two replacements if possible or include context.
// Let's target the Play Action block specifically.

            paneBuilder.addRow(
                Row.Builder()
                    .setTitle(details?.name ?: item.name)
                    .build()
            )

            // Set Hero Image on the Pane itself for maximum size
            posterBitmap?.let {
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
                 // Note: 'score' is the new field but might be complex. Using what we strictly have or user requested.
                 // User asked for "Rating". Let's check 'rating' which is Int (0-100 usually or 0-10) or 'score'
                 
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

                // Plot Row
                if (!details.plot.isNullOrEmpty()) {
                    paneBuilder.addRow(
                        Row.Builder()
                            .setTitle("Trama")
                            .addText(details.plot!!)
                            .build()
                    )
                }

                // Cast Row
                if (!details.actors.isNullOrEmpty()) {
                    val castList = details.actors!!.groupBy { it.roleString }.flatMap { it.value }.take(5).joinToString(", ") { it.actor.name }
                    if (castList.isNotEmpty()) {
                        val s = SpannableString(castList)
                        s.setSpan(
                            ForegroundCarColorSpan.create(CarColor.SECONDARY),
                            0,
                            s.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        
                        paneBuilder.addRow(
                            Row.Builder()
                                .setTitle("Cast")
                                .addText(s)
                                .build()
                        )
                    }
                }
            } else if (errorMessage != null) {
                 paneBuilder.addRow(Row.Builder().setTitle("Errore dettagli: $errorMessage").build())
            }
        }

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
            "Sorgente"
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

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle(fullDetails?.name ?: item.name)
            .setHeaderAction(Action.BACK)
            .build()
    }
    
    private fun toggleFavorite() {
        val details = fullDetails ?: return
        val api = getApiFromNameNull(details.apiName) ?: return
        val id = details.url.replace(api.mainUrl, "").replace("/", "").hashCode()
        
        if (isFavorite) {
            DataStoreHelper.removeFavoritesData(id)
            isFavorite = false
            androidx.car.app.CarToast.makeText(carContext, "Rimosso dai preferiti", androidx.car.app.CarToast.LENGTH_SHORT).show()
        } else {
            val favoritesData = FavoritesData(
                favoritesTime = System.currentTimeMillis(),
                id = id,
                latestUpdatedTime = System.currentTimeMillis(),
                name = details.name,
                url = details.url,
                apiName = details.apiName,
                type = details.type,
                posterUrl = details.posterUrl,
                year = details.year,
                quality = null,
                posterHeaders = details.posterHeaders,
                plot = details.plot,
                score = details.score,
                tags = details.tags
            )
            DataStoreHelper.setFavoritesData(id, favoritesData)
            isFavorite = true
            androidx.car.app.CarToast.makeText(carContext, "Aggiunto ai preferiti", androidx.car.app.CarToast.LENGTH_SHORT).show()
        }
        invalidate()
    }
}
