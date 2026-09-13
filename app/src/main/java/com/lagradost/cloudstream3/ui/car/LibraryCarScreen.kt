package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import com.lagradost.cloudstream3.utils.CarHelper.safePush
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.getLastWatched
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LibraryCarScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val header = Header.Builder()
            .setTitle(CarStrings.get(R.string.car_library))
            .setStartHeaderAction(Action.BACK)
            .build()

        return ListTemplate.Builder()
            .setSingleList(
                ItemList.Builder()
                    .addItem(
                        Row.Builder()
                            .setTitle(CarStrings.get(R.string.car_bookmarks))
                            .setOnClickListener { screenManager.safePush(BookmarksScreen(carContext)) }
                            .setBrowsable(true)
                            .build()
                    )
                    .addItem(
                        Row.Builder()
                            .setTitle(CarStrings.get(R.string.car_history))
                            .setOnClickListener { screenManager.safePush(HistoryScreen(carContext)) }
                            .setBrowsable(true)
                            .build()
                    )
                    .build()
            )
            .setHeader(header)
            .build()
    }
}
