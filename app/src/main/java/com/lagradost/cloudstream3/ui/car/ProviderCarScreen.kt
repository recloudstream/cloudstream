package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.utils.DataStoreHelper

class ProviderCarScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        
        // Filter APIs mostly for simplicity, or show all
        val current = DataStoreHelper.currentHomePage
        
        apis.forEach { api ->
             val row = Row.Builder()
                .setTitle(api.name)
                .setOnClickListener {
                    DataStoreHelper.currentHomePage = api.name
                    screenManager.pop() // Go back to Home which will reload
                }
            
            if (api.name == current) {
                row.addText(CarStrings.get(R.string.car_selected))
            }
            
            listBuilder.addItem(row.build())
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle(CarStrings.get(R.string.car_select_provider))
            .setHeaderAction(Action.BACK)
            .build()
    }
}
