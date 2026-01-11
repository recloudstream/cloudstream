package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.lagradost.cloudstream3.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.asDrawable

class BookmarksScreen(carContext: CarContext) : Screen(carContext), androidx.lifecycle.DefaultLifecycleObserver {
    private var itemList: ItemList? = null
    
    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
        loadBookmarks()
    }
    
    private fun loadBookmarks(retryCount: Int = 0) {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                itemList = null
                invalidate()
            }
            try {
                // Fetch only Favorites (Heart icon)
                val favorites = com.lagradost.cloudstream3.utils.DataStoreHelper.getAllFavorites()
                    .sortedByDescending { it.favoritesTime }
                
                val builder = ItemList.Builder()
                if (favorites.isEmpty()) {
                    builder.setNoItemsMessage("Nessun preferito trovato")
                } else {
                    favorites.forEach { item ->
                         builder.addItem(
                             Row.Builder()
                                 .setTitle(item.name)
                                 .setOnClickListener {
                                     val type = item.type
                                     if (type == TvType.TvSeries || type == TvType.Anime || type == TvType.OVA) {
                                         androidx.car.app.CarToast.makeText(carContext, "Caricamento episodi...", androidx.car.app.CarToast.LENGTH_SHORT).show()
                                         CoroutineScope(Dispatchers.IO).launch {
                                             val api = com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(item.apiName)
                                             if (api != null) {
                                                 val repo = com.lagradost.cloudstream3.ui.APIRepository(api)
                                                 when (val result = repo.load(item.url)) {
                                                     is com.lagradost.cloudstream3.mvvm.Resource.Success -> {
                                                         val data = result.value
                                                         withContext(Dispatchers.Main) {
                                                             if (data is com.lagradost.cloudstream3.TvSeriesLoadResponse) {
                                                                 screenManager.push(EpisodeListScreen(carContext, data, isExpressMode = true))
                                                             } else {
                                                                 screenManager.push(DetailsScreen(carContext, item))
                                                             }
                                                         }
                                                     }
                                                     is com.lagradost.cloudstream3.mvvm.Resource.Failure -> {
                                                          withContext(Dispatchers.Main) {
                                                              androidx.car.app.CarToast.makeText(carContext, "Errore: ${result.errorString}", androidx.car.app.CarToast.LENGTH_LONG).show()
                                                          }
                                                     }
                                                     else -> {}
                                                 }
                                             } else {
                                                 withContext(Dispatchers.Main) {
                                                     androidx.car.app.CarToast.makeText(carContext, "Provider non trovato", androidx.car.app.CarToast.LENGTH_SHORT).show()
                                                 }
                                             }
                                         }
                                     } else {
                                         screenManager.push(DetailsScreen(carContext, item))
                                     }
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
                    loadBookmarks(retryCount + 1)
                } else {
                    withContext(Dispatchers.Main) {
                        itemList = ItemList.Builder()
                            .addItem(
                                Row.Builder()
                                    .setTitle("Errore: ${e.message}")
                                    .setOnClickListener { loadBookmarks() }
                                    .build()
                            )
                            .build()
                        invalidate()
                    }
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        return ListTemplate.Builder()
            .setSingleList(itemList ?: ItemList.Builder().setNoItemsMessage("Caricamento preferiti...").build())
            .setTitle("Preferiti")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
