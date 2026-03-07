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
                    builder.setNoItemsMessage(CarStrings.get(R.string.car_no_favorites_found))
                } else {
                    favorites.forEach { item ->
                         builder.addItem(
                             Row.Builder()
                                 .setTitle(item.name)
                                 .setOnClickListener {
                                     val type = item.type
                                     if (type == TvType.TvSeries || 
                                         type == TvType.Anime || 
                                         type == TvType.Cartoon || 
                                         type == TvType.OVA || 
                                         type == TvType.AsianDrama || 
                                         type == TvType.Documentary) {
                                         screenManager.push(TvSeriesDetailScreen(carContext, item))
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
                                    .setTitle("${CarStrings.get(R.string.car_error)}: ${e.message}")
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
            .setSingleList(itemList ?: ItemList.Builder().setNoItemsMessage(CarStrings.get(R.string.car_loading)).build())
            .setTitle(CarStrings.get(R.string.car_favorites))
            .setHeaderAction(Action.BACK)
            .build()
    }
}
