package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.lagradost.cloudstream3.HomePageList

class CategoryScreen(
    carContext: CarContext,
    private val homePageList: HomePageList
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        
        homePageList.list.forEach { item ->
            val row = Row.Builder()
                .setTitle(if (item.name.isNullOrEmpty()) "Untitled" else item.name)
                .setOnClickListener {
                     val type = item.type
                     if (type == com.lagradost.cloudstream3.TvType.TvSeries || 
                         type == com.lagradost.cloudstream3.TvType.Anime || 
                         type == com.lagradost.cloudstream3.TvType.Cartoon || 
                         type == com.lagradost.cloudstream3.TvType.OVA || 
                         type == com.lagradost.cloudstream3.TvType.AsianDrama || 
                         type == com.lagradost.cloudstream3.TvType.Documentary) {
                         screenManager.push(TvSeriesDetailScreen(carContext, item))
                     } else if (type == com.lagradost.cloudstream3.TvType.Live) {
                         screenManager.push(PlayerCarScreen(carContext, item = item))
                     } else {
                         screenManager.push(DetailsScreen(carContext, item))
                     }
                }
                .build()
            listBuilder.addItem(row)
        }

        return ListTemplate.Builder()
            .setTitle(homePageList.name)
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}
