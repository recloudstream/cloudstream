package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainCarScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {
    private var homePageLists: List<HomePageList> = emptyList()
    private var isLoading = true
    private var errorMessage: String? = null
    private var currentApiName: String = ""

    companion object {
        private const val LOADING_DELAY_MS = 500L
        private const val MAX_LOADING_ATTEMPTS = 20
    }

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
                // Retry waiting for plugins to load
                while (api == null && attempts < MAX_LOADING_ATTEMPTS) {
                    delay(LOADING_DELAY_MS)
                    api = getApiFromNameNull(currentApiName)
                    attempts++
                }

                if (api == null) {
                    errorMessage = "${CarStrings.get(R.string.car_provider_not_found)}: $currentApiName"
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
                        errorMessage = result.errorString ?: CarStrings.get(R.string.car_loading_content)
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
        return ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.app_name))
            .addSectionedList(
                SectionedItemList.create(
                    buildMenuSection(),
                    CarStrings.get(R.string.car_menu)
                )
            )
            .addSectionedList(
                SectionedItemList.create(
                    buildContentSection(),
                    CarStrings.get(R.string.car_home_content)
                )
            )
            .setActionStrip(buildActionStrip())
            .build()
    }

    private fun buildMenuSection(): ItemList {
        val menuListBuilder = ItemList.Builder()

        menuListBuilder.addItem(
            createMenuRow(
                title = CarStrings.get(R.string.car_favorites),
                iconRes = R.drawable.ic_baseline_favorite_24,
                screen = { BookmarksScreen(carContext) }
            )
        )

        menuListBuilder.addItem(
            createMenuRow(
                title = CarStrings.get(R.string.car_history),
                iconRes = android.R.drawable.ic_menu_recent_history,
                screen = { HistoryScreen(carContext) }
            )
        )

        menuListBuilder.addItem(
            createMenuRow(
                title = CarStrings.get(R.string.car_downloads),
                iconRes = android.R.drawable.stat_sys_download,
                screen = { DownloadsScreen(carContext) }
            )
        )

        menuListBuilder.addItem(
            Row.Builder()
                .setTitle(CarStrings.get(R.string.car_provider))
                .addText("${CarStrings.get(R.string.car_current)}: $currentApiName")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_manage)).build())
                .setOnClickListener { screenManager.push(ProviderCarScreen(carContext)) }
                .setBrowsable(true)
                .build()
        )

        menuListBuilder.addItem(
            createMenuRow(
                title = CarStrings.get(R.string.car_about_me),
                iconRes = android.R.drawable.ic_menu_info_details,
                screen = { AboutMeScreen(carContext) }
            )
        )

        return menuListBuilder.build()
    }

    private fun createMenuRow(title: String, iconRes: Int, screen: () -> Screen): Row {
        return Row.Builder()
            .setTitle(title)
            .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes)).build())
            .setOnClickListener { screenManager.push(screen()) }
            .setBrowsable(true)
            .build()
    }

    private fun buildContentSection(): ItemList {
        val contentListBuilder = ItemList.Builder()

        if (isLoading) {
            contentListBuilder.addItem(Row.Builder().setTitle(CarStrings.get(R.string.car_loading)).setBrowsable(false).build())
        } else if (errorMessage != null) {
            contentListBuilder.addItem(Row.Builder().setTitle("${CarStrings.get(R.string.car_error)}: $errorMessage").setBrowsable(false).build())
        } else if (homePageLists.isEmpty()) {
            contentListBuilder.addItem(Row.Builder().setTitle(CarStrings.get(R.string.car_no_content_from_provider)).setBrowsable(false).build())
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
        return contentListBuilder.build()
    }

    private fun buildActionStrip(): ActionStrip {
        return ActionStrip.Builder()
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
    }
}
