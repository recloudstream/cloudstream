package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.RowSection
import androidx.car.app.model.SearchCallback
import androidx.car.app.model.SearchHeader
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.SectionedItemTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.CarHelper.safePush
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil3.request.ImageRequest
import coil3.SingletonImageLoader
import coil3.asDrawable
import androidx.core.graphics.drawable.toBitmap

class SearchCarScreen(carContext: CarContext) : Screen(carContext) {

    private var itemList: ItemList? = null
    private var searchResults: List<SearchResponse> = emptyList()
    private var searchJob: Job? = null
    private var lastQuery: String = ""

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val iconCache = java.util.concurrent.ConcurrentHashMap<String, CarIcon>()
    private val loadingUrls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val searchCallback = object : SearchTemplate.SearchCallback {
        override fun onSearchTextChanged(searchText: String) {
            searchJob?.cancel()
            lastQuery = searchText
            if (searchText.isBlank()) {
                itemList = null
                searchResults = emptyList()
                invalidate()
                return
            }
            searchJob = CoroutineScope(Dispatchers.Main).launch {
                delay(400)
                performSearch(searchText)
            }
        }

        override fun onSearchSubmitted(searchText: String) {
            lastQuery = searchText
            performSearch(searchText)
        }
    }

    private fun loadIcon(imageUrl: String) {
        if (!loadingUrls.add(imageUrl)) return

        scope.launch {
            try {
                val request = ImageRequest.Builder(carContext)
                    .data(imageUrl)
                    .size(128, 128)
                    .build()
                val result = SingletonImageLoader.get(carContext).execute(request)
                val bitmap = result.image?.asDrawable(carContext.resources)?.toBitmap()
                if (bitmap != null) {
                    val icon = CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
                    iconCache[imageUrl] = icon
                    invalidate()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loadingUrls.remove(imageUrl)
            }
        }
    }

    private fun isTvSeriesType(type: TvType?): Boolean {
        return type == TvType.TvSeries ||
               type == TvType.Anime ||
               type == TvType.Cartoon ||
               type == TvType.OVA ||
               type == TvType.AsianDrama ||
               type == TvType.Documentary
    }

    private fun buildRow(item: SearchResponse): Row {
        val imageUrl = item.posterUrl
        val icon = if (!imageUrl.isNullOrEmpty()) {
            iconCache[imageUrl] ?: run {
                loadIcon(imageUrl)
                CarIcon.Builder(IconCompat.createWithResource(carContext, R.mipmap.ic_launcher)).build()
            }
        } else {
            CarIcon.Builder(IconCompat.createWithResource(carContext, R.mipmap.ic_launcher)).build()
        }

        return Row.Builder()
            .setTitle(item.name)
            .setImage(icon, Row.IMAGE_TYPE_LARGE)
            .setOnClickListener {
                if (isTvSeriesType(item.type)) {
                    screenManager.safePush(TvSeriesDetailScreen(carContext, item))
                } else {
                    screenManager.safePush(DetailsScreen(carContext, item))
                }
            }
            .build()
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = CoroutineScope(Dispatchers.IO).launch {
            val apiName = DataStoreHelper.currentHomePage ?: return@launch
            val api = getApiFromNameNull(apiName) ?: return@launch
            val repo = APIRepository(api)

            when (val result = repo.search(query, 1)) {
                is Resource.Success -> {
                    val items = result.value.items
                    searchResults = items

                    // Also build legacy ItemList for Car API < 9 fallback
                    val builder = ItemList.Builder()
                    if (items.isEmpty()) {
                        builder.setNoItemsMessage(CarStrings.get(R.string.car_no_results_found))
                    } else {
                        items.forEach { item ->
                            builder.addItem(buildRow(item))
                        }
                    }

                    itemList = builder.build()
                    invalidate()
                }
                is Resource.Failure -> {
                    searchResults = emptyList()
                    itemList = null
                    invalidate()
                }
                else -> {}
            }
        }
    }

    @androidx.annotation.OptIn(androidx.car.app.annotations.ExperimentalCarApi::class)
    override fun onGetTemplate(): Template {
        // Car API 9+: Use modern SectionedItemTemplate with SearchHeader
        if (carContext.carAppApiLevel >= 9) {
            val searchHeader = SearchHeader.Builder(searchCallback)
                .setSearchHint("${CarStrings.get(R.string.car_search)}...")
                .setStartHeaderAction(Action.BACK)
                .setShowKeyboardByDefault(true)
                .build()

            val templateBuilder = SectionedItemTemplate.Builder()
                .setSearchHeader(searchHeader)

            if (searchResults.isEmpty()) {
                val message = if (lastQuery.isBlank()) {
                    "${CarStrings.get(R.string.car_search)}..."
                } else {
                    CarStrings.get(R.string.car_no_results_found)
                }
                templateBuilder.addSection(
                    RowSection.Builder()
                        .addItem(
                            Row.Builder()
                                .setTitle(message)
                                .setEnabled(false)
                                .build()
                        )
                        .build()
                )
            } else {
                val movies = searchResults.filter { !isTvSeriesType(it.type) }
                val series = searchResults.filter { isTvSeriesType(it.type) }

                if (movies.isNotEmpty()) {
                    val moviesSection = RowSection.Builder()
                        .setTitle(CarStrings.get(R.string.car_movies))
                        .apply {
                            movies.take(6).forEach { item ->
                                addItem(buildRow(item))
                            }
                        }
                        .build()
                    templateBuilder.addSection(moviesSection)
                }

                if (series.isNotEmpty()) {
                    val seriesSection = RowSection.Builder()
                        .setTitle(CarStrings.get(R.string.car_series))
                        .apply {
                            series.take(6).forEach { item ->
                                addItem(buildRow(item))
                            }
                        }
                        .build()
                    templateBuilder.addSection(seriesSection)
                }
            }

            return templateBuilder.build()
        }

        // Car API < 9 fallback: SearchTemplate with flat list
        return SearchTemplate.Builder(searchCallback)
            .setSearchHint("${CarStrings.get(R.string.car_search)}...")
            .setHeaderAction(Action.BACK)
            .setShowKeyboardByDefault(true)
            .setItemList(itemList ?: ItemList.Builder().build())
            .build()
    }
}
