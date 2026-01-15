package com.lagradost.cloudstream3.ui.car

import android.graphics.Color
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen that displays available streaming sources for a content item.
 * User can select a source, which will be used for playback.
 *
 * @param apiName The API/provider name to use for loading links
 * @param dataUrl The data URL to load links for (movie URL or episode data)
 * @param currentSourceUrl The currently selected source URL (for showing check mark)
 * @param onSourceSelected Callback when a source is selected
 */
class SourceSelectionScreen(
    carContext: CarContext,
    private val apiName: String,
    private val dataUrl: String,
    private val currentSourceUrl: String? = null,
    private val onSourceSelected: (ExtractorLink) -> Unit
) : Screen(carContext) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var isLoading = true
    private var sources: List<ExtractorLink> = emptyList()
    private var errorMessage: String? = null

    init {
        loadSources()
    }

    private fun loadSources() {
        scope.launch {
            try {
                val api = getApiFromNameNull(apiName)
                if (api == null) {
                    errorMessage = "Provider non trovato"
                    isLoading = false
                    invalidate()
                    return@launch
                }

                val links = mutableListOf<ExtractorLink>()
                api.loadLinks(dataUrl, false, {}, { link ->
                    links.add(link)
                })

                withContext(Dispatchers.Main) {
                    if (links.isEmpty()) {
                        errorMessage = "Nessuna sorgente trovata"
                    } else {
                        // Sort by quality (highest first)
                        sources = links.sortedByDescending { it.quality }
                    }
                    isLoading = false
                    invalidate()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Errore: ${e.message}"
                    isLoading = false
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val templateBuilder = ListTemplate.Builder()
            .setTitle("Sorgenti")
            .setHeaderAction(Action.BACK)

        if (isLoading) {
            // When loading, just set loading state without any list
            templateBuilder.setLoading(true)
        } else {
            // Not loading - build the list
            val listBuilder = ItemList.Builder()
            
            if (errorMessage != null) {
                listBuilder.setNoItemsMessage(errorMessage!!)
            } else if (sources.isEmpty()) {
                listBuilder.setNoItemsMessage("Nessuna sorgente disponibile")
            } else {
                // Add each source as a row
                for (source in sources) {
                    val qualityStr = Qualities.getStringByInt(source.quality)
                    val title = if (qualityStr.isNotEmpty()) {
                        "${source.name} - $qualityStr"
                    } else {
                        source.name
                    }

                    val rowBuilder = Row.Builder()
                        .setTitle(title)
                        .addText(source.source)
                        .setOnClickListener {
                            onSourceSelected(source)
                            CarToast.makeText(carContext, "Sorgente selezionata: ${source.name}", CarToast.LENGTH_SHORT).show()
                            screenManager.pop()
                        }


                    listBuilder.addItem(rowBuilder.build())
                }
            }
            
            templateBuilder.setSingleList(listBuilder.build())
            templateBuilder.setLoading(false)
        }

        return templateBuilder.build()
    }
}
