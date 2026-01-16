package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ListTemplate // Added
import androidx.car.app.model.Row // Added
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.R
import coil3.request.ImageRequest
import coil3.SingletonImageLoader
import coil3.asDrawable
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class CategoryScreen(
    carContext: CarContext,
    private val homePageList: HomePageList
) : Screen(carContext) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var itemList: ItemList? = null

    init {
        loadData()
    }

    private fun loadData() {
        scope.launch {
            val listBuilder = ItemList.Builder()
            val listItems = homePageList.list.map { item ->
                async {
                    val image = try {
                        if (!item.posterUrl.isNullOrEmpty()) {
                            val request = ImageRequest.Builder(carContext)
                                .data(item.posterUrl)
                            .size(256, 256) // Increased size for IMAGE_TYPE_LARGE
                                .build()
                            val result = SingletonImageLoader.get(carContext).execute(request)
                            val bitmap = result.image?.asDrawable(carContext.resources)?.toBitmap()
                            if (bitmap != null) {
                                CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
                            } else null
                        } else null
                    } catch (e: Exception) {
                        null
                    } ?: CarIcon.Builder(IconCompat.createWithResource(carContext, R.mipmap.ic_launcher)).build()

                    Row.Builder()
                        .setTitle(if (item.name.isNullOrEmpty()) "Untitled" else item.name)
                        .setImage(image, Row.IMAGE_TYPE_LARGE)
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
                }
            }.awaitAll()

            listItems.forEach { listBuilder.addItem(it) }
            itemList = listBuilder.build()
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val builder = ListTemplate.Builder()
            .setTitle(homePageList.name)
            .setHeaderAction(Action.BACK)

        if (itemList == null) {
            builder.setLoading(true)
        } else {
            builder.setLoading(false)
            builder.setSingleList(itemList!!)
        }
        
        return builder.build()
    }
}
