package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.model.SpotlightSection
import androidx.car.app.model.CondensedItem
import androidx.car.app.model.Banner
import androidx.car.app.model.BannerSection
import androidx.car.app.model.SectionHeader
import androidx.car.app.model.Background
import androidx.car.app.model.CarColor
import coil3.request.ImageRequest
import coil3.SingletonImageLoader
import coil3.asDrawable
import androidx.core.graphics.drawable.toBitmap
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
    private var featuredHeroIcon: CarIcon? = null
    private var isHeroLoading = false

    companion object {
        private const val LOADING_DELAY_MS = 500L
        private const val MAX_LOADING_ATTEMPTS = 20
        /** SectionedItemTemplate and Spotlight/Chip features require Car API level 9 */
        private const val SECTIONED_ITEM_TEMPLATE_MIN_API = 9
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
        featuredHeroIcon = null
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
                        if (carContext.carAppApiLevel >= 9) {
                            preloadFeaturedHeroImage()
                        }
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
        val hostApiLevel = carContext.carAppApiLevel
        return if (hostApiLevel >= SECTIONED_ITEM_TEMPLATE_MIN_API) {
            buildSectionedTemplate()
        } else {
            buildLegacyListTemplate()
        }
    }

    // ──────────────────────────────────────────────────────
    //  New: SectionedItemTemplate layout (Car API >= 8)
    // ──────────────────────────────────────────────────────

    @OptIn(ExperimentalCarApi::class)
    private fun buildErrorBanner(error: String): Banner {
        val retryAction = Action.Builder()
            .setTitle(CarStrings.get(R.string.reload_error).replace("…", ""))
            .setOnClickListener {
                loadData()
            }
            .build()

        return Banner.Builder()
            .setTitle(CarStrings.get(R.string.car_error))
            .setSubtitle(error)
            .addBelowAction(retryAction)
            .build()
    }

    @OptIn(ExperimentalCarApi::class)
    private fun buildNoContentBanner(): Banner {
        val switchAction = Action.Builder()
            .setTitle(CarStrings.get(R.string.car_provider))
            .setOnClickListener {
                screenManager.push(ProviderCarScreen(carContext))
            }
            .build()

        return Banner.Builder()
            .setTitle(CarStrings.get(R.string.car_no_content_from_provider))
            .setSubtitle(CarStrings.get(R.string.car_provider_not_found))
            .addBelowAction(switchAction)
            .build()
    }

    @androidx.annotation.OptIn(ExperimentalCarApi::class)
    private fun buildSectionedTemplate(): Template {
        val builder = androidx.car.app.model.SectionedItemTemplate.Builder()
            .setHeader(buildHeader())

        // Menu section
        builder.addSection(
            androidx.car.app.model.RowSection.Builder()
                .setSectionHeader(
                    SectionHeader.Builder(CarStrings.get(R.string.car_menu))
                        .setSubtitle("Access your bookmarks, history, and settings")
                        .build()
                )
                .apply { buildMenuRows().forEach { addItem(it) } }
                .build()
        )

        // Content sections – each HomePageList becomes its own RowSection
        if (isLoading) {
            builder.addSection(
                androidx.car.app.model.RowSection.Builder()
                    .setTitle(CarStrings.get(R.string.car_home_content))
                    .addItem(Row.Builder().setTitle(CarStrings.get(R.string.car_loading)).setBrowsable(false).build())
                    .build()
            )
        } else if (errorMessage != null) {
            builder.addSection(
                BannerSection.Builder()
                    .addItem(buildErrorBanner(errorMessage!!))
                    .build()
            )
        } else if (homePageLists.isEmpty()) {
            builder.addSection(
                BannerSection.Builder()
                    .addItem(buildNoContentBanner())
                    .build()
            )
        } else {
            @OptIn(ExperimentalCarApi::class)
            val useSpotlight = carContext.carAppApiLevel >= 9

            if (useSpotlight) {
                val firstList = homePageLists.first()
                val heroIcon = featuredHeroIcon ?: CarIcon.Builder(IconCompat.createWithResource(carContext, R.mipmap.ic_launcher)).build()
                val spotlightSection = SpotlightSection.Builder(heroIcon)
                    .setTitle(firstList.name)
                    .apply {
                        firstList.list.take(MAX_ITEMS_PER_SECTION).forEach { item ->
                            addItem(
                                CondensedItem.Builder()
                                    .setTitle(item.name.ifEmpty { "Untitled" })
                                    .setOnClickListener {
                                        val type = item.type
                                        if (type == com.lagradost.cloudstream3.TvType.TvSeries ||
                                            type == com.lagradost.cloudstream3.TvType.Anime ||
                                            type == com.lagradost.cloudstream3.TvType.Cartoon ||
                                            type == com.lagradost.cloudstream3.TvType.OVA ||
                                            type == com.lagradost.cloudstream3.TvType.AsianDrama ||
                                            type == com.lagradost.cloudstream3.TvType.Documentary
                                        ) {
                                            screenManager.push(TvSeriesDetailScreen(carContext, item))
                                        } else if (type == com.lagradost.cloudstream3.TvType.Live) {
                                            screenManager.push(PlayerCarScreen(carContext, item = item))
                                        } else {
                                            screenManager.push(DetailsScreen(carContext, item))
                                        }
                                    }
                                    .build()
                            )
                        }
                        if (firstList.list.size > MAX_ITEMS_PER_SECTION) {
                            addItem(
                                CondensedItem.Builder()
                                    .setTitle("${CarStrings.get(R.string.car_see_all)} (${firstList.list.size})")
                                    .setOnClickListener {
                                        screenManager.push(CategoryScreen(carContext, firstList))
                                    }
                                    .build()
                            )
                        }
                    }
                    .build()
                builder.addSection(spotlightSection)

                // Add subsequent sections as standard RowSections
                homePageLists.drop(1).forEach { homePageList ->
                    builder.addSection(
                        androidx.car.app.model.RowSection.Builder()
                            .setSectionHeader(
                                SectionHeader.Builder(homePageList.name)
                                    .setSubtitle("Popular releases from $currentApiName")
                                    .build()
                            )
                            .apply {
                                homePageList.list.take(MAX_ITEMS_PER_SECTION).forEach { item ->
                                    addItem(
                                        Row.Builder()
                                            .setTitle(item.name.ifEmpty { "Untitled" })
                                            .setOnClickListener {
                                                val type = item.type
                                                if (type == com.lagradost.cloudstream3.TvType.TvSeries ||
                                                    type == com.lagradost.cloudstream3.TvType.Anime ||
                                                    type == com.lagradost.cloudstream3.TvType.Cartoon ||
                                                    type == com.lagradost.cloudstream3.TvType.OVA ||
                                                    type == com.lagradost.cloudstream3.TvType.AsianDrama ||
                                                    type == com.lagradost.cloudstream3.TvType.Documentary
                                                ) {
                                                    screenManager.push(TvSeriesDetailScreen(carContext, item))
                                                } else if (type == com.lagradost.cloudstream3.TvType.Live) {
                                                    screenManager.push(PlayerCarScreen(carContext, item = item))
                                                } else {
                                                    screenManager.push(DetailsScreen(carContext, item))
                                                }
                                            }
                                            .setBrowsable(true)
                                            .build()
                                    )
                                }
                                if (homePageList.list.size > MAX_ITEMS_PER_SECTION) {
                                    addItem(
                                        Row.Builder()
                                            .setTitle("${CarStrings.get(R.string.car_see_all)} (${homePageList.list.size})")
                                            .setOnClickListener {
                                                screenManager.push(CategoryScreen(carContext, homePageList))
                                            }
                                            .setBrowsable(true)
                                            .build()
                                    )
                                }
                            }
                            .build()
                    )
                }
            } else {
                // Fallback: all sections are standard RowSections
                homePageLists.forEach { homePageList ->
                    builder.addSection(
                        androidx.car.app.model.RowSection.Builder()
                            .setSectionHeader(
                                SectionHeader.Builder(homePageList.name)
                                    .setSubtitle("Popular releases from $currentApiName")
                                    .build()
                            )
                            .apply {
                                homePageList.list.take(MAX_ITEMS_PER_SECTION).forEach { item ->
                                    addItem(
                                        Row.Builder()
                                            .setTitle(item.name.ifEmpty { "Untitled" })
                                            .setOnClickListener {
                                                val type = item.type
                                                if (type == com.lagradost.cloudstream3.TvType.TvSeries ||
                                                    type == com.lagradost.cloudstream3.TvType.Anime ||
                                                    type == com.lagradost.cloudstream3.TvType.Cartoon ||
                                                    type == com.lagradost.cloudstream3.TvType.OVA ||
                                                    type == com.lagradost.cloudstream3.TvType.AsianDrama ||
                                                    type == com.lagradost.cloudstream3.TvType.Documentary
                                                ) {
                                                    screenManager.push(TvSeriesDetailScreen(carContext, item))
                                                } else if (type == com.lagradost.cloudstream3.TvType.Live) {
                                                    screenManager.push(PlayerCarScreen(carContext, item = item))
                                                } else {
                                                    screenManager.push(DetailsScreen(carContext, item))
                                                }
                                            }
                                            .setBrowsable(true)
                                            .build()
                                    )
                                }
                                if (homePageList.list.size > MAX_ITEMS_PER_SECTION) {
                                    addItem(
                                        Row.Builder()
                                            .setTitle("${CarStrings.get(R.string.car_see_all)} (${homePageList.list.size})")
                                            .setOnClickListener {
                                                screenManager.push(CategoryScreen(carContext, homePageList))
                                            }
                                            .setBrowsable(true)
                                            .build()
                                    )
                                }
                            }
                            .build()
                    )
                }
            }
        }

        return builder.build()
    }

    private fun preloadFeaturedHeroImage() {
        val firstItem = homePageLists.firstOrNull()?.list?.firstOrNull() ?: return
        val url = firstItem.posterUrl ?: return
        if (featuredHeroIcon != null || isHeroLoading) return

        isHeroLoading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = ImageRequest.Builder(carContext)
                    .data(url)
                    .size(512, 512)
                    .build()
                val result = SingletonImageLoader.get(carContext).execute(request)
                val bitmap = result.image?.asDrawable(carContext.resources)?.toBitmap()
                if (bitmap != null) {
                    featuredHeroIcon = CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
                    invalidate()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isHeroLoading = false
            }
        }
    }

    // ──────────────────────────────────────────────────────
    //  Legacy: ListTemplate layout (Car API < 8, fallback)
    // ──────────────────────────────────────────────────────

    private fun buildLegacyListTemplate(): Template {
        return ListTemplate.Builder()
            .setHeader(buildHeader())
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
            .build()
    }

    // ──────────────────────────────────────────────────────
    //  Shared helpers
    // ──────────────────────────────────────────────────────

    @androidx.annotation.OptIn(ExperimentalCarApi::class)
    @OptIn(ExperimentalCarApi::class)
    private fun buildHeader(): Header {
        val builder = Header.Builder()
            .setTitle(carContext.getString(R.string.app_name))
            .addEndHeaderAction(
                Action.Builder()
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_search)).build())
                    .setOnClickListener {
                        screenManager.push(SearchCarScreen(carContext))
                    }
                    .build()
            )
            .addEndHeaderAction(
                Action.Builder()
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_refresh)).build())
                    .setOnClickListener {
                        loadData()
                    }
                    .build()
            )

        if (carContext.carAppApiLevel >= 9) {
            builder.setSubtitle("Provider: $currentApiName")
            builder.setBackground(Background.Builder().setColor(CarColor.PRIMARY).build())
        }

        return builder.build()
    }

    /** Builds menu Row list — used by both legacy and sectioned paths. */
    private fun buildMenuRows(): List<Row> {
        val rows = mutableListOf<Row>()

        rows.add(createMenuRow(
            title = CarStrings.get(R.string.car_favorites),
            iconRes = R.drawable.ic_baseline_favorite_24,
            screen = { BookmarksScreen(carContext) }
        ))

        rows.add(createMenuRow(
            title = CarStrings.get(R.string.car_history),
            iconRes = android.R.drawable.ic_menu_recent_history,
            screen = { HistoryScreen(carContext) }
        ))

        rows.add(createMenuRow(
            title = CarStrings.get(R.string.car_downloads),
            iconRes = android.R.drawable.stat_sys_download,
            screen = { DownloadsScreen(carContext) }
        ))

        rows.add(
            Row.Builder()
                .setTitle(CarStrings.get(R.string.car_provider))
                .addText("${CarStrings.get(R.string.car_current)}: $currentApiName")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_manage)).build())
                .setOnClickListener { screenManager.push(ProviderCarScreen(carContext)) }
                .setBrowsable(true)
                .build()
        )

        rows.add(
            Row.Builder()
                .setTitle(CarStrings.get(R.string.car_player_mode))
                .addText("${CarStrings.get(R.string.car_current)}: ${
                    if (DataStoreHelper.carPlayerMode == 0) CarStrings.get(R.string.car_player_mode_advanced)
                    else CarStrings.get(R.string.car_player_mode_simple)
                }")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_preferences)).build())
                .setOnClickListener {
                    DataStoreHelper.carPlayerMode = if (DataStoreHelper.carPlayerMode == 0) 1 else 0
                    invalidate()
                }
                .build()
        )

        rows.add(
            Row.Builder()
                .setTitle(CarStrings.get(R.string.car_about_me))
                .addText("Host API Level: ${carContext.carAppApiLevel}")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_info_details)).build())
                .setOnClickListener { screenManager.push(AboutMeScreen(carContext)) }
                .setBrowsable(true)
                .build()
        )

        return rows
    }

    private fun buildMenuSection(): ItemList {
        val builder = ItemList.Builder()
        buildMenuRows().forEach { builder.addItem(it) }
        return builder.build()
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
}

private const val MAX_ITEMS_PER_SECTION = 5
