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
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.asDrawable

class BookmarksScreen(carContext: CarContext) : Screen(carContext) {
    private var itemList: ItemList? = null
    
    init {
        loadBookmarks()
    }
    
    private fun loadBookmarks() {
        CoroutineScope(Dispatchers.IO).launch {
            // Fetch only Favorites (Heart icon)
            val favorites = com.lagradost.cloudstream3.utils.DataStoreHelper.getAllFavorites()
                .sortedByDescending { it.favoritesTime }
            
            val builder = ItemList.Builder()
            if (favorites.isEmpty()) {
                builder.setNoItemsMessage("No bookmarks (Favorites) found")
            }
            
            favorites.forEach { item ->
                 builder.addItem(
                     Row.Builder()
                         .setTitle(item.name)
                         .setOnClickListener {
                             val type = item.type
                             if (type == TvType.TvSeries || type == TvType.Anime || type == TvType.OVA) {
                                 screenManager.push(TvSeriesDetailScreen(carContext, item))
                             } else {
                                 screenManager.push(DetailsScreen(carContext, item))
                             }
                         }
                         // .setImage(...) Removed as per user request
                         .build()
                 )
            }
            itemList = builder.build()
            
            invalidate()
            // Async image loading removed
        }
    }

    override fun onGetTemplate(): Template {
        return ListTemplate.Builder()
            .setSingleList(itemList ?: ItemList.Builder().setNoItemsMessage("Loading...").build())
            .setTitle("Bookmarks")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
