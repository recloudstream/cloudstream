package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import androidx.car.app.CarToast

class SearchCarScreen(carContext: CarContext) : Screen(carContext) {

    private var itemList: ItemList? = null
    private var searchJob: Job? = null
    
    private val searchCallback = object : SearchTemplate.SearchCallback {
        override fun onSearchTextChanged(searchText: String) {
            // Optional: Implement autocomplete here
        }

        override fun onSearchSubmitted(searchText: String) {
            performSearch(searchText)
        }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = CoroutineScope(Dispatchers.IO).launch {
             val apiName = DataStoreHelper.currentHomePage ?: return@launch
             val api = getApiFromNameNull(apiName) ?: return@launch
             val repo = APIRepository(api)
             
             // Check if search requires page
             when(val result = repo.search(query, 1)) {
                 is Resource.Success -> {
                     val builder = ItemList.Builder()
                     val items = result.value.items
                     if (items.isEmpty()) {
                         builder.setNoItemsMessage(CarStrings.get(R.string.car_no_results_found))
                     }
                     
                     // Text-only results
                     items.forEach { item ->
                         builder.addItem(
                             Row.Builder()
                                 .setTitle(item.name)
                                 .setOnClickListener {
                                     val type = item.type
                                     if (type == com.lagradost.cloudstream3.TvType.TvSeries || 
                                         type == com.lagradost.cloudstream3.TvType.Anime || 
                                         type == com.lagradost.cloudstream3.TvType.Cartoon || 
                                         type == com.lagradost.cloudstream3.TvType.OVA || 
                                         type == com.lagradost.cloudstream3.TvType.AsianDrama || 
                                         type == com.lagradost.cloudstream3.TvType.Documentary) {
                                         screenManager.push(TvSeriesDetailScreen(carContext, item))
                                     } else {
                                         screenManager.push(DetailsScreen(carContext, item))
                                     }
                                 }
                                 .build()
                         )
                     }
                     
                     itemList = builder.build()
                     invalidate()
                 }
                 is Resource.Failure -> {
                     // Handle error
                 }
                 else -> {}
             }
        }
    }

    override fun onGetTemplate(): Template {
        return SearchTemplate.Builder(searchCallback)
            .setSearchHint("${CarStrings.get(R.string.car_search)}...")
            .setHeaderAction(Action.BACK)
            .setShowKeyboardByDefault(true)
            .setItemList(itemList ?: ItemList.Builder().build())
            .build()
    }
}
