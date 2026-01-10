package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainCarScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {
    private var homePageLists: List<HomePageList> = emptyList()
    private var isLoading = true
    private var errorMessage: String? = null
    private var currentApiName: String = ""

    init {
        lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        // Reload data on resume to catch provider changes
        loadData()
    }

    private fun loadData() {
        isLoading = true
        errorMessage = null
        currentApiName = DataStoreHelper.currentHomePage ?: ""
        invalidate()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var api = getApiFromNameNull(currentApiName)
                var attempts = 0
                // Retry for up to 10 seconds (20 * 500ms) waiting for plugins to load
                while (api == null && attempts < 20) {
                    kotlinx.coroutines.delay(500)
                    api = getApiFromNameNull(currentApiName)
                    attempts++
                }

                if (api == null) {
                    errorMessage = "Provider not found: $currentApiName"
                    isLoading = false
                    invalidate()
                    return@launch
                }

                val repo = APIRepository(api)
                when (val result = repo.getMainPage(1, null)) {
                    is Resource.Success -> {
                        homePageLists = result.value.filterNotNull().flatMap { it.items }
                        isLoading = false
                    }
                    is Resource.Failure -> {
                        errorMessage = result.errorString ?: "Error loading content"
                        isLoading = false
                    }
                    is Resource.Loading -> {}
                }
            } catch (e: Exception) {
                errorMessage = e.message
                isLoading = false
            }
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        // 1. Menu Section Builder
        val menuListBuilder = ItemList.Builder()

        menuListBuilder.addItem(
            Row.Builder()
                .setTitle("Preferiti")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_baseline_favorite_24)).build())
                .setOnClickListener { screenManager.push(BookmarksScreen(carContext)) }
                .setBrowsable(true)
                .build()
        )

        menuListBuilder.addItem(
            Row.Builder()
                .setTitle("Cronologia")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_recent_history)).build())
                .setOnClickListener { screenManager.push(HistoryScreen(carContext)) }
                .setBrowsable(true)
                .build()
        )

        menuListBuilder.addItem(
            Row.Builder()
                .setTitle("Provider")
                .addText("Attuale: $currentApiName")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_manage)).build())
                .setOnClickListener { screenManager.push(ProviderCarScreen(carContext)) }
                .setBrowsable(true)
                .build()
        )



        // 2. Content Section Builder
        val contentListBuilder = ItemList.Builder()

        if (isLoading) {
            contentListBuilder.addItem(Row.Builder().setTitle("Caricamento contenuti...").setBrowsable(false).build())
        } else if (errorMessage != null) {
             contentListBuilder.addItem(Row.Builder().setTitle("Errore: $errorMessage").setBrowsable(false).build())
        } else if (homePageLists.isEmpty()) {
             contentListBuilder.addItem(Row.Builder().setTitle("Nessun contenuto dal provider").setBrowsable(false).build())
        } else {
            homePageLists.forEach { homePageList ->
                contentListBuilder.addItem(
                    Row.Builder()
                        .setTitle(homePageList.name)
                        .setOnClickListener {
                            screenManager.push(CategoryScreen(carContext, homePageList))
                        }
                        .setBrowsable(true)
                        .build()
                )
            }
        }

        // 3. Assemble ListTemplate
        return ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.app_name))
            //.setHeaderAction(Action.APP_ICON) // Removed to prevent action limit crash
            .addSectionedList(
                SectionedItemList.create(
                    menuListBuilder.build(),
                    "Menu"
                )
            )
            .addSectionedList(
                SectionedItemList.create(
                    contentListBuilder.build(),
                    "Contenuti Home"
                )
            )
            .setActionStrip(
                androidx.car.app.model.ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_search)).build())
                            .setOnClickListener {
                                screenManager.push(SearchCarScreen(carContext))
                            }
                            .build()
                    )
                    .addAction(
                        Action.Builder()
                            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_refresh)).build())
                            .setOnClickListener {
                                loadData()
                            }
                            .build()
                    )
                    .build()
            )
            .build()
    }
}
