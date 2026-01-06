package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.getLastWatched
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LibraryCarScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        return ListTemplate.Builder()
            .setSingleList(
                ItemList.Builder()
                    .addItem(
                        Row.Builder()
                            .setTitle("Bookmarks")
                            .setOnClickListener { screenManager.push(BookmarksScreen(carContext)) }
                            .setBrowsable(true)
                            .build()
                    )
                    .addItem(
                        Row.Builder()
                            .setTitle("History")
                            .setOnClickListener { screenManager.push(HistoryScreen(carContext)) }
                            .setBrowsable(true)
                            .build()
                    )
                    .build()
            )
            .setTitle("Library")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
